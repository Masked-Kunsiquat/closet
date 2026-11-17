# KMP Phase 5d: `features/recommendations` → KMP/CMP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `features/recommendations` into a Kotlin Multiplatform + Compose Multiplatform module, with Android behavior unchanged. That covers the recommendation engine, the AI providers (Anthropic, OpenAI-compatible) and coherence scorer, `RecommendationViewModel`, the screen, the occasion/weather sheets and the combo card. All of it moves to `commonMain`.

**Architecture:**
- **One PR, two tasks.** The module is already nearly platform-free: Ktor and kotlinx-serialization are multiplatform, and the engine is pure Kotlin.
- **Task 1:** convert the module.
  - The engine, models, providers, scorer, ViewModel, UI state and the Koin modules go to `commonMain`.
  - `StorageRepository` → `ImagePathResolver`, Timber → Kermit, `java.time` season lookup → `kotlinx-datetime`.
  - The screens still use `R`, so they stay in `androidMain`.
- **Task 2:** strings to Compose resources, the two `Icons.*` to Material Symbols, and the 12 `@Preview`s into an androidMain `RecommendationPreviews.kt`. Then the screens move to `commonMain`.
- **Stays in `androidMain`:**
  - `RecommendationNavigation.kt` (Phase 6);
  - `RecommendationPreviews.kt`.
- **Flavor binding is unchanged:**
  - `OnDeviceRecommendationProvider` is still bound by `gmsModule` (full) or `recommendationsNoOpModule` (foss).
  - `platform/gms` keeps depending on this module's Android variant.

**Tech Stack:** Kotlin 2.4.21, AGP 9.1.0 KMP library, CMP 1.12.2 / Material 3 1.9.0, Ktor 3.1.3, kotlinx-serialization, kotlinx-datetime 0.8.0, Koin 4.2.2, Coil 3.6.3, Kermit 2.2.0.

**Spec:** `docs/superpowers/specs/2026-11-14-kmp-migration-design.md` (Phase 5). The model is `plans/2026-11-16-kmp-phase5-outfits.md` (Tasks 3–4): copy `features/outfits/build.gradle.kts`'s shape, its JVM Koin graph test, its strings/icon tests and the `gradle/skiko-jvm-test-runtime.gradle.kts` apply. Gotchas are in CLAUDE.md "Resources" and `docs/superpowers/notes/kmp-toolchain.md`.

## Global Constraints

- **Exit criteria:**
  - `assembleFullDebug` and `assembleFossDebug` succeed.
  - All unit tests pass, including every module's `jvmTest`.
  - The foss APK has no GMS classes.
  - A manual smoke test passes on the owner's **test** device.
  - CI `ios-compile` is green.
- **Targets:** `android`, `jvm()`, `iosArm64()`, `iosSimulatorArm64()`.
- **Versions:**
  - No new libraries. Any new iOS klib must have ABI ≤ **2.4.0**.
  - Declare CMP libraries by catalog coordinates, never `compose.*` accessors.
- **`commonMain` stays platform-free:** no `android.*`, `java.*`, `androidx.core`, `androidx.navigation`, `androidx.activity`, `androidx.work` or `timber` imports. `:features:recommendations:compileCommonMainKotlinMetadata` catches leaks.
- **Resources:**
  - Positional placeholders only.
  - `'`, not `\'`.
  - Compose resources format only `%N$s` / `%N$d`. Any number with decimals is formatted in Kotlin and passed as `%1$s`.
  - `material-icons-extended` is not used in `features/recommendations` after this PR.
- **Icons:** Material Symbols Outlined, wght 400, FILL 0, GRAD 0, opsz 24, `#FF000000`, named `ic_symbol_<name>`. Reuse `ic_symbol_checkroom`. Add `ic_symbol_auto_awesome`. No auto-mirroring.
- **Flavors:**
  - Never bind `OnDeviceRecommendationProvider` in the feature's main Koin module.
  - `recommendationsNoOpModule` stays the foss binding, and moves to commonMain unchanged.
- **Logging:** Timber → Kermit (`Logger.withTag("<ClassName>")`), with the same levels.
- **No users:** no compat shims.
- **Gradle hygiene:**
  - Run from `app-android/`, one invocation at a time, in the foreground, with no `--rerun-tasks`.
  - Never stop or kill daemons.
  - If a task sits with no output for ~5 min, or fails with "Failed to clean up output files", stop and report.
  - iOS compiles locally with `-Pkotlin.native.enableKlibsCrossCompilation=true`.

## Review Focus

1. **Season for scoring.** `LocalDate.now().month` → `Clock.System.todayIn(TimeZone.currentSystemDefault()).month`. The month→season map must be identical:
   - MAR–MAY → `"Spring"`
   - JUN–AUG → `"Summer"`
   - SEP–NOV → `"Autumn"`
   - DEC–FEB → the current winter value

   (Task 1: `SeasonForMonthTest` over all 12 months.)
2. **Provider HTTP and error mapping.** The Anthropic and OpenAI-compatible providers move unchanged. Request bodies, headers (`AiHttpClient` 120 s client) and the `CancellationException` rethrows must not change. Only their Timber lines become Kermit. (Task 1: reviewer diff check. The existing provider behavior has no unit tests, so don't invent network tests.)
3. **Images in combo cards.** `resolveImage` changes from `(String?) -> File?` to `(String?) -> String?`. Cards must still show photos, and the placeholder appears only when the path is null. (Task 1 + device smoke.)
4. **Score/vibe/page strings.**
   - `recs_style_vibe_label` (`Style: %s`) → `Style: %1$s`.
   - `recs_page_indicator_description` keeps `%1$d of %2$d`.
   - `recs_combo_score_label` (`Score: %.2f`) is unreferenced, so delete it if `git grep` confirms.

   (Task 2: `RecommendationsStringsTest`.)
5. **Foss/full Koin graph.**
   - On Android: the foss graph still resolves `OnDeviceRecommendationProvider` → `UnavailableOnDeviceRecommendationProvider`, which also binds `OutfitAiProvider` and `NanoInitializer`. The full graph still resolves the gms `NanoProvider`.
   - On JVM: the graph test supplies a test-local `OnDeviceRecommendationProvider` binding, and **does not** include `recommendationsNoOpModule`, whose `NanoInitializer` bind would clash with `jvmDataModule`'s `UnavailableNanoInitializer`.

   (Task 1: `RecommendationsJvmKoinGraphTest` + existing `AppKoinGraphTest`.)

---

### Task 1: `features/recommendations` as a KMP module; logic, ViewModel and Koin in `commonMain`

**Files:**
- **Rewrite `features/recommendations/build.gradle.kts`** by copying `features/outfits/build.gradle.kts`.
  - Namespace `com.closet.features.recommendations`, with `androidResources { enable = true }`.
  - **commonMain** adds Ktor client core, content-negotiation and `kotlinx-serialization-json`, taking from the current file whatever the providers use.
  - Keep `material-icons-extended` and `ui-tooling-preview` in androidMain temporarily; Task 2 removes the first. Previews need `ui-tooling` through `androidRuntimeClasspath`, per CLAUDE.md "Previews".
  - Apply the Skiko script.
  - Drop `timber` and `work-runtime-ktx` from main.
- **Moves (`git mv`):**
  - **To `commonMain`:** `engine/**`, `model/**`, `ai/**` (all providers, the scorer, `OnDeviceRecommendationProvider`, `UnavailableOnDeviceRecommendationProvider`), `RecommendationViewModel.kt`, `RecommendationUiState.kt` and `di/**`.
  - **To `androidMain`:** `RecommendationScreen.kt`, `ui/**`, `RecommendationNavigation.kt` and `src/main/res`.
  - **Tests:**
    - `OutfitRecommendationEngineTest` and `UnavailableOnDeviceRecommendationProviderTest` → `src/commonTest/**`, rewritten to `kotlin.test` with unchanged assertions. If one needs a JVM-only library, put it in `src/jvmTest/**` instead.
    - `di/RecommendationsKoinTest` → `src/androidHostTest/**`.
- **Create:** `src/commonMain/.../Season.kt` with `internal fun seasonFor(month: Month): String`, holding the exact mapping now inline in `RecommendationViewModel` (~line 531). The ViewModel calls it with `Clock.System.todayIn(TimeZone.currentSystemDefault()).month`.
- **Tests:**
  - `src/commonTest/.../SeasonForMonthTest.kt`
  - `src/jvmTest/.../di/RecommendationsJvmKoinGraphTest.kt`
  - `src/jvmTest/.../RecommendationViewModelImageTest.kt`. It can be a test in an existing ViewModel test if one exists. There is none today, so keep it minimal: construct the ViewModel with mocks the way the settings/outfits ViewModel tests do, and assert only `resolveImage`.

**Interfaces:**
- `RecommendationViewModel(...)`: `storageRepository: StorageRepository` is replaced by `imagePathResolver: ImagePathResolver`, plus `fun resolveImage(path: String?): String? = path?.let(imagePathResolver::absolutePath)`. Callers in androidMain change from `File?` to `String?`. `viewModelOf` is unchanged.
- `internal fun seasonFor(month: kotlinx.datetime.Month): String`.

- [ ] **Step 1: Write the failing tests**
  - **`SeasonForMonthTest`:** cover all 12 months. MAR/APR/MAY → "Spring", JUN/JUL/AUG → "Summer", SEP/OCT/NOV → "Autumn", DEC/JAN/FEB → the exact string the current `else`/winter branch returns. Copy it verbatim.
  - **`RecommendationsJvmKoinGraphTest`:**
    - Include `jvmDataModule(tmp)`, `commonDataModule`, `jvmUiModule(tmp)`, `recommendationsModule` and `module { single<OnDeviceRecommendationProvider> { UnavailableOnDeviceRecommendationProvider() } }`.
    - Use only the `extraTypes` from `OutfitsJvmKoinGraphTest`, then `verify()`.
    - Delete the temp dir after.
  - **`RecommendationViewModelImageTest`:** with `ImagePathResolver { "/root/$it" }`, `resolveImage("a.jpg") == "/root/a.jpg"` and `resolveImage(null) == null`.
  - **Ported engine/provider tests:** assertions unchanged.
- [ ] **Step 2: Run the tests to verify they fail.** Run `./gradlew :features:recommendations:jvmTest`; expect a compile failure.
- [ ] **Step 3: Implement** the build conversion, the moves, `Season.kt`, the ViewModel changes, Timber → Kermit, and the androidMain call-site updates.
- [ ] **Step 4: Verify**
  - `./gradlew :features:recommendations:jvmTest :features:recommendations:testAndroidHostTest :features:recommendations:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
  - `./gradlew :features:recommendations:compileKotlinIosSimulatorArm64 -Pkotlin.native.enableKlibsCrossCompilation=true` → BUILD SUCCESSFUL.
  - `git grep -nE "^import (android\.|java\.|timber|androidx\.(core|navigation|work))" -- features/recommendations/src/commonMain` prints nothing.
  - `./gradlew test` → BUILD SUCCESSFUL, including `AppKoinGraphTest` in both flavors and `platform/gms`'s `GmsKoinTest`.
- [ ] **Step 5: Commit** with message `refactor(recommendations): features/recommendations as a KMP module; engine, providers, ViewModel and Koin in commonMain (KMP Phase 5)`.

### Task 2: Compose resources, Material Symbols; screens to `commonMain`; PR checkpoint

**Files:**
- **Strings:** move them to `src/commonMain/composeResources/values/strings.xml`.
  - `recs_style_vibe_label` → `Style: %1$s`.
  - Fix the two `\'`.
  - Delete `recs_combo_score_label` and any other string with no reference, checked with `git grep`.
  - Add `compose.resources { packageOfResClass = "com.closet.features.recommendations.resources"; generateResClass = always }`.
- **Icons:**
  - Add `core/ui/src/commonMain/composeResources/drawable/ic_symbol_auto_awesome.xml`, from Google's Material Symbols set, the same source as the earlier PRs.
  - `Icons.Filled.AutoAwesome` → `ic_symbol_auto_awesome`; `Icons.Default.Checkroom` → the existing `ic_symbol_checkroom`.
  - Remove `material-icons-extended`.
- **Previews:** cut all 12 `@Preview` functions, with their `Configuration` imports, from `RecommendationScreen.kt`, `ui/OccasionSheet.kt`, `ui/OutfitComboCard.kt` and `ui/WeatherSheet.kt` into `src/androidMain/.../RecommendationPreviews.kt`. Make the previewed Composables `internal` where they're `private`.
- **Moves (`git mv` androidMain → commonMain):** `RecommendationScreen.kt` and `ui/**`. Then switch imports:
  - CMP `stringResource`;
  - `painterResource(CoreRes.drawable.ic_symbol_*)`;
  - the JetBrains `collectAsStateWithLifecycle`.
- **Docs and changelog:**
  - **CLAUDE.md module list:** `features/recommendations` is KMP (`commonMain` engine, providers, UI, ViewModel and Koin; `androidMain` navigation and previews).
  - **CLAUDE.md full-verification command:** add `:features:recommendations:jvmTest :features:recommendations:compileCommonMainKotlinMetadata`.
  - **CHANGELOG** `[Unreleased]` → `### Changed`: "**Outfit recommendations are Kotlin Multiplatform** (KMP Phase 5): shared engine, AI providers and UI, Compose resources, Material Symbols icons."
- **Tests:** `src/jvmTest/.../RecommendationsStringsTest.kt`:
  - `getString(Res.string.recs_style_vibe_label, "Minimalist") == "Style: Minimalist"`
  - `getString(Res.string.recs_page_indicator_description, 2, 3) == "Page 2 of 3"`
  - one of the two fixed-apostrophe strings renders `'`. Pick a string that is still referenced.

**Interfaces:**
- Consumes Task 1's layout.
- Produces the internal `com.closet.features.recommendations.resources.Res` and `CoreRes.drawable.ic_symbol_auto_awesome`.

- [ ] **Step 1: Write the failing test.**
- [ ] **Step 2: Run it to verify it fails.** Run `./gradlew :features:recommendations:jvmTest`; expect a compile failure.
- [ ] **Step 3: Implement.**
  - `git grep -nE "R\.(string|plurals)|material\.icons|@Preview|android\.content" -- features/recommendations/src/commonMain` prints nothing.
  - Only `RecommendationNavigation.kt` and `RecommendationPreviews.kt` are left in androidMain.
- [ ] **Step 4: Verify.**
  - Run the full verification command from CLAUDE.md, including the recommendations tasks, in separate invocations if needed → BUILD SUCCESSFUL.
  - The recommendations iOS simulator compile → BUILD SUCCESSFUL.
  - `unzip -l` on each debug APK lists `composeResources/com.closet.features.recommendations.resources/` strings and `ic_symbol_auto_awesome.xml`.
- [ ] **Step 5: Commit** with message `refactor(recommendations): Compose resources, Material Symbols; recommendations UI in commonMain (KMP Phase 5)`.
- [ ] **Step 6: PR checkpoint** (the controller does this).
  - Push `feat/kmp-phase5-recommendations`, open the PR, and wait for CI.
  - Smoke-test on the **test device only**, both flavors:
    - Get suggestions → the occasion sheet shows, and Skip works.
    - The weather sheet autofills and "Use these conditions" works.
    - The results pages show photos; the page indicator reads "Page 1 of 3" to TalkBack; the style vibe label reads "Style: …".
    - "Log it" opens the builder with the items preselected.
    - "Save for later" and "Regenerate" work.
    - With AI on and a cloud provider set, an AI pick shows the sparkle icon. If no key is configured, check the AI-off path only.
    - Foss: the on-device path is unavailable, and there's no crash.

    Wait for the owner to merge.
