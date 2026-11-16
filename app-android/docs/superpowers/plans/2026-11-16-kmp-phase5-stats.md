# KMP Phase 5a: `features/stats` → KMP/CMP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `features/stats` into a Kotlin Multiplatform + Compose Multiplatform module: the ViewModel, the screen and every stats component in `commonMain`, with Android behavior unchanged.

**Architecture:**
- One PR (Phase 5 is one PR per feature), three tasks.
  - Task 1 adds a common `ImagePathResolver` to `core/data`, so common ViewModels can turn a stored relative path into something Coil 3 loads, without `StorageRepository` (Android-only).
  - Task 2 converts `features/stats` to a KMP module. The ViewModel and the pure logic go to `commonMain`. The Composables still use Android `R`, so they stay in `androidMain` for now.
  - Task 3 moves the stats strings to the module's own Compose resources and swaps the three `material-icons-extended` icons for Material Symbols drawables in `core/ui`. Then the Composables follow to `commonMain`.
- **Stays in `androidMain`:**
  - `StatsNavigation.kt`. Navigation moves in Phase 6 with the `NavHost`, so the app isn't pulled from androidx navigation 2.9.7 to JetBrains navigation 2.10.0 one feature at a time.
  - `StatsPreviews.kt`, because it uses `@Preview` and `Configuration.UI_MODE_NIGHT_YES`.
  - `di/StatsKoinModule.kt`. It's a three-line `viewModelOf`, and `app`'s `allModules` imports it unchanged.

**Tech Stack:**
- Kotlin 2.4.21 and AGP 9.1.0, with the KMP Android library plugin.
- CMP 1.12.2, CMP Material 3 1.9.0, and Compose resources.
- Vico `compose-m3` 3.0.3: already multiplatform, with android, desktop (JVM), iosArm64 and iosSimulatorArm64 variants.
- kotlinx-datetime, Koin 4.2.2 (`koin-compose-viewmodel`), Coil 3.6.3.

**Spec:** `docs/superpowers/specs/2026-11-14-kmp-migration-design.md`. Phase 5 is "Features → KMP/CMP, one per PR … Stats goes first". See also "UI", "Platform gaps" and "Logging". Versions and checks come from `docs/superpowers/notes/kmp-toolchain.md`. The model for this plan is `plans/2026-11-15-kmp-phase4-core-ui.md`.

## Global Constraints

- **Exit criteria (every phase):** `assembleFullDebug` and `assembleFossDebug` succeed; unit tests pass (including every module's `jvmTest`); the foss APK has no GMS classes; a manual smoke test on the owner's **test** device passes; CI `ios-compile` compiles iOS.
- **Targets:** `features/stats` uses the same targets as `core/ui`: `android`, `jvm()`, `iosArm64()`, `iosSimulatorArm64()`.
- **Pinned versions:**
  - CMP **1.12.2**, CMP Material 3 **1.9.0**, Coil **3.6.3**, Vico **3.0.3**.
  - iOS klib ABI must be ≤ **2.4.0** for every new artifact (the check is in `kmp-toolchain.md`).
  - Declare CMP libraries by catalog coordinates, not the `compose.*` accessors.
- **`commonMain` stays platform-free:** no `android.*`, `java.*` or `androidx.core` imports. `:features:stats:compileCommonMainKotlinMetadata` catches leaks.
- **Resources:** strings use `Res.string.*` / `Res.plurals.*` with **positional** placeholders (`%1$d`, `%2$d`, `%1$s`). `material-icons-extended` is not used in `features/stats` after this PR (#86 item 3).
- **Images:** store relative paths only. The UI gets an absolute path `String` from `ImagePathResolver` and passes it to Coil 3's `AsyncImage`.
- **Logging:** stats has no Timber calls, so drop the `timber` dependency; nothing goes to Kermit.
- **No users:** no compat shims or upgrade tests.
- **Gradle hygiene:**
  - Run Gradle from `app-android/`, one invocation at a time, in the foreground, with no `--rerun-tasks`.
  - On a Windows file lock, stop and report; never kill daemons.
  - The verification commands below are written as one line each for readability. Run them as separate invocations if memory is tight.

## Review Focus

1. **Placeholders render literally.** `stats_cd_worn_pct` is `%d of %d`. Compose resources only substitute positional placeholders, so it must become `%1$d of %2$d`, and every other `%d`/`%s` must become `%1$d`/`%1$s`. (Task 3: `StatsStringsTest`.)
2. **Apostrophes in plurals.** `stats_total_logs` uses `You\'ve`. The `\'` escape must render as `'`, not `\'`. (Task 3: `StatsStringsTest`.)
3. **Swatch and bar colors.**
   - `android.graphics.Color.parseColor` is replaced by a common parser. Seeded hexes (`#RRGGBB`) must give the same colors.
   - A null, empty or malformed hex must fall back to the theme color, not crash.
   - `HSVToColor` is replaced by `Color.hsv`; the subcategory shades must match. (Task 2: `StatsColorsTest`.)
4. **Images in stats lists.** Most worn, cost per wear and never worn must still show photos after the `File` → absolute-path `String` switch. An item with no photo shows the placeholder. (Task 1 tests, plus the device smoke test.)
5. **Dependency drift.** Adding Vico's KMP graph, `koin-compose-viewmodel` and the KMP lifecycle-compose artifacts can raise the `androidx.lifecycle`, `androidx.compose.*` or Vico versions that `app` resolves. Report any change, compared with `main`, and smoke-test it. (Task 2, Step 4.)

---

### Task 1: `ImagePathResolver` in `core/data`

**Files:**
- Create `core/data/src/commonMain/kotlin/com/closet/core/data/repository/ImagePathResolver.kt`: the interface, plus a top-level `const val IMAGES_DIR_NAME`, moved from `StorageRepository.Companion` with its value unchanged.
- Modify `core/data/src/androidMain/.../repository/StorageRepository.kt`: implement `ImagePathResolver` and use the common `IMAGES_DIR_NAME`.
- Modify `core/data/src/androidMain/.../worker/ImageCompressionWorker.kt`: import the moved constant.
- Modify `core/data/src/androidMain/.../di/DataKoinModule.kt`: `singleOf(::StorageRepository) { bind<ImagePathResolver>() }`.
- Create `core/data/src/jvmMain/.../repository/JvmImagePathResolver.kt` and bind it in `jvmDataModule(dataDirectory)`.
- Create `core/data/src/iosMain/.../repository/IosImagePathResolver.kt` and bind it in `iosDataModule`. Its root is the documents directory, resolved the same way as `iosPreferencesPath`, plus `/$IMAGES_DIR_NAME`.
- Tests:
  - `core/data/src/jvmTest/.../repository/JvmImagePathResolverTest.kt`
  - extend `core/data/src/androidHostTest/.../di/SingletonParityTest.kt`

**Interfaces:**
- Produces (commonMain): `fun interface ImagePathResolver { fun absolutePath(relativePath: String): String }`.
- Produces: `class StorageRepository(...) : ImagePathResolver`, where `absolutePath(p) = getFile(p).absolutePath`. `getFile` stays for the features that haven't migrated yet.
- Produces: `class JvmImagePathResolver(imagesDir: File) : ImagePathResolver`. `jvmDataModule` binds it with `File(dataDirectory, IMAGES_DIR_NAME)`.
- Produces: `class IosImagePathResolver : ImagePathResolver`.

- [ ] **Step 1: Write the failing tests**
  - **`JvmImagePathResolverTest`** (temp dir):
    - `JvmImagePathResolver(tmp).absolutePath("a/b.jpg") == File(tmp, "a/b.jpg").absolutePath`
    - `module { includes(jvmDataModule(tmp)) }` resolves `ImagePathResolver`, and its `absolutePath("x.jpg")` ends with `"$IMAGES_DIR_NAME${File.separator}x.jpg"`.
  - **`SingletonParityTest`:** `get<ImagePathResolver>() === get<StorageRepository>()`.
- [ ] **Step 2: Run the tests to verify they fail.** Run `./gradlew :core:data:jvmTest` → compile FAIL (`ImagePathResolver` unresolved).
- [ ] **Step 3: Implement** the interface, the constant move, the three implementations and the bindings.
- [ ] **Step 4: Verify**
  - `./gradlew :core:data:jvmTest :core:data:testAndroidHostTest :core:data:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
  - `./gradlew :app:testFullDebugUnitTest` → `AppKoinGraphTest` passes.
- [ ] **Step 5: Commit** with message `feat(data): common ImagePathResolver for relative image paths (KMP Phase 5)`.

### Task 2: `features/stats` as a KMP module; ViewModel and logic in `commonMain`

**Files:**
- **Catalog (`gradle/libs.versions.toml`)**
  - Add `koin-compose-viewmodel` (`io.insert-koin:koin-compose-viewmodel`, no version, from the BOM).
  - Add `jetbrains-lifecycle-runtime-compose` and `jetbrains-lifecycle-viewmodel` (`org.jetbrains.androidx.lifecycle:*`). Choose the JetBrains version whose Android variant resolves to `androidx.lifecycle` **2.10.0** (read its `.module` file). If none exists, use **2.11.0**, bump `androidxLifecycle` to the matching androidx version, and report the drift.
  - Keep `vico-compose-m3` unchanged: the same coordinate already resolves per platform.
- **Rewrite `features/stats/build.gradle.kts`**, modeled on `core/ui/build.gradle.kts`.
  - Plugins: `kotlin.multiplatform`, `android.kotlin.multiplatform.library`, `compose.multiplatform`, `kotlin.compose`, `kotlin.serialization`, `android.lint`.
  - `android { namespace = "com.closet.features.stats"; androidResources { enable = true }; withHostTestBuilder {} }`, plus `jvm()`, `iosArm64()`, `iosSimulatorArm64()`, and JVM 17 targets.
  - **`commonMain`:** `:core:data`, `:core:ui`, CMP runtime/foundation/ui/material3, Coil 3, Vico `compose-m3`, `koin-core`, `koin-compose-viewmodel`, the JetBrains lifecycle artifacts, `kotlinx-datetime`, coroutines.
  - **`androidMain`:** `koin-android`, `androidx.navigation.compose` (catalog version), `kotlinx-serialization-json`, Compose BOM + `ui-tooling-preview`.
  - **`commonTest`:** `kotlin("test")`, coroutines-test, turbine.
  - **`jvmTest`:** mockk.
  - **`androidHostTest`:** the current Koin test set (junit, `koin-test-junit4`, `ktor-client-okhttp`, `work-runtime-ktx`).
  - The same `tasks.register("test")` lifecycle task as `core/ui`, so root `./gradlew test` still runs stats.
  - No `timber` and no `material-icons-extended` in commonMain. The icons stay usable from `androidMain` until Task 3, so add `material-icons-extended` to `androidMain` temporarily; Task 3 removes it.
- **Moves (`git mv`):**
  - To `src/commonMain/kotlin/**`: `StatsViewModel.kt`, `SegmentedBar.kt`, and a new `StatsColors.kt`.
  - To `src/androidMain/kotlin/**`: `StatsScreen.kt`, `StatsSharedUi.kt`, `StatsActivityComponents.kt`, `StatsCompositionComponents.kt`, `StatsPreviews.kt`, `StatsNavigation.kt` and `di/StatsKoinModule.kt`.
  - `src/main/res` → `src/androidMain/res`.
  - Tests:
    - `StatPeriodTest.kt` and `SegmentedBarTest.kt` → `src/commonTest/**`, rewritten to `kotlin.test`.
    - `StatsViewModelTest.kt` → `src/jvmTest/**`.
    - `di/StatsKoinModuleTest.kt` → `src/androidHostTest/**`.
- **Create (commonMain) `StatsColors.kt`**, holding the color logic that `StatsCompositionComponents.kt` currently has inline. `categoryBaseHues` moves with it.
- **Create `formatCurrency` actuals:** `StatsFormat.kt` (commonMain `expect`), `StatsFormat.android.kt`, `StatsFormat.jvm.kt`, `StatsFormat.ios.kt`.
- **Tests:** `commonTest/.../StatsColorsTest.kt`, `jvmTest/.../StatsFormatTest.kt`.

**Interfaces:**
- Consumes: Task 1's `ImagePathResolver`.
- Produces (commonMain): `fun StatPeriod.toFromDate(today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): String?`, with `kotlinx.datetime.LocalDate`. The rules are unchanged:
  - `ALL_TIME` → `null`
  - `LAST_30` → `today - 30 days`
  - `LAST_90` → `today - 90 days`
  - `THIS_YEAR` → `YYYY-01-01`
- Produces: `class StatsViewModel(statsRepository: StatsRepository, imagePathResolver: ImagePathResolver)`. The constructor change is picked up by `viewModelOf` with no Koin edit. Also `fun resolveImagePath(path: String?): String? = path?.let(imagePathResolver::absolutePath)`.
- Produces: `internal fun parseHexColor(hex: String?): Color?`. It accepts `#RRGGBB` and `#AARRGGBB`, case-insensitive. Anything else (null, empty, no `#`, wrong length, non-hex) returns `null`. Call sites keep their fallback (`?: otherColor` / `?: fallbackColor`).
- Produces: `internal fun subcategoryColors(categoryIndex: Int, count: Int): List<Color>`, using `Color.hsv(hue, saturation, 0.90f)` with the same hue table and saturation ramp as today.
- Produces: `internal expect fun formatCurrency(amount: Double): String`.
  - Android and JVM: `NumberFormat.getCurrencyInstance()` with 2 fraction digits, as today.
  - iOS: `NSNumberFormatter` with `NSNumberFormatterCurrencyStyle` and 2 fraction digits.
- `resolveImagePath` parameters in the Composables change from `(String?) -> File?` to `(String?) -> String?`.

- [ ] **Step 1: Write the failing tests**
  - **`StatPeriodTest`** (commonTest), with `today = LocalDate(2026, 3, 15)`:
    - `ALL_TIME.toFromDate(today) == null`
    - `LAST_30` → `"2026-02-13"`
    - `LAST_90` → `"2025-12-15"`
    - `THIS_YEAR` → `"2026-01-01"`
    - Leap-year edge, `today = LocalDate(2024, 3, 1)`: `LAST_30` → `"2024-01-31"`.
    - Keep the existing format-regex test and the `LAST_30` after `LAST_90` test, using the default `today`.
  - **`StatsColorsTest`** (commonTest):
    - `parseHexColor("#FF0000") == Color(0xFFFF0000)`
    - `parseHexColor("#f5f5dc") == Color(0xFFF5F5DC)`
    - `parseHexColor("#80000080") == Color(0x80000080)`
    - `null` for each of: `null`, `""`, `"FF0000"`, `"#FFF"`, `"#GG0000"`, `"#FF00000"`
    - Compare colors per channel (red, green, blue, alpha) within `0.002f`, not with `==`, because the saturation ramp is float arithmetic.
    - `subcategoryColors(0, 1).single()` ≈ `Color.hsv(210f, 0.65f, 0.90f)`
    - `subcategoryColors(1, 3)` ≈ `[Color.hsv(20f, 0.85f, 0.9f), Color.hsv(20f, 0.575f, 0.9f), Color.hsv(20f, 0.30f, 0.9f)]`
    - `subcategoryColors(8, 1).single()` ≈ `Color.hsv(210f, 0.65f, 0.90f)` (hue wraps around).
  - **`StatsFormatTest`** (jvmTest): with `Locale.setDefault(Locale.US)` (restored after), `formatCurrency(1234.5) == "$1,234.50"` and `formatCurrency(0.0) == "$0.00"`.
  - **`StatsViewModelTest`** (jvmTest): replace the `StorageRepository` mock with `ImagePathResolver { "/root/$it" }`.
    - `resolveImagePath("some/path.jpg") == "/root/some/path.jpg"`
    - `resolveImagePath(null) == null`
    - Every other test is kept as-is.
  - **`SegmentedBarTest`:** ported to `kotlin.test` with its assertions unchanged.
- [ ] **Step 2: Run the tests to verify they fail.** Run `./gradlew :features:stats:jvmTest` → compile FAIL.
- [ ] **Step 3: Implement** the build conversion, the moves, `StatsColors.kt`, `formatCurrency`, the ViewModel changes and the call-site updates.
  - `StatsCompositionComponents.kt` and `StatsActivityComponents.kt` call the new helpers instead of `android.graphics.Color` and `NumberFormat`.
  - The `remember { NumberFormat… }` blocks go away.
- [ ] **Step 4: Verify**
  - `./gradlew :features:stats:jvmTest :features:stats:testAndroidHostTest :features:stats:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
  - `git grep -nE "^import (android\.|java\.)" -- features/stats/src/commonMain` prints nothing.
  - `./gradlew test` → BUILD SUCCESSFUL, including `AppKoinGraphTest` in both flavors.
  - `./gradlew lint assembleFullDebug assembleFossDebug` → BUILD SUCCESSFUL.
  - **Drift check:** run `./gradlew :app:dependencyInsight --configuration fullDebugRuntimeClasspath --dependency <X>` for:
    - `androidx.lifecycle:lifecycle-runtime-compose`
    - `androidx.compose.ui:ui`
    - `com.patrykandpatrick.vico:compose-m3`

    Paste the resolved versions into the report next to what `main` resolves.
  - **iOS ABI check:** do the klib ABI check from `kmp-toolchain.md` for Vico's iOS klibs, `koin-compose-viewmodel` and the lifecycle artifacts. All must be ≤ 2.4.0.
- [ ] **Step 5: Commit** with message `refactor(stats): features/stats as a KMP module; ViewModel and logic in commonMain (KMP Phase 5)`.

### Task 3: Compose resources and Material Symbols; Composables to `commonMain`; PR checkpoint

**Files:**
- **Strings**
  - `git mv features/stats/src/androidMain/res/values/strings.xml features/stats/src/commonMain/composeResources/values/strings.xml`, then delete the emptied `androidMain/res`.
  - Make every placeholder positional:
    - `stats_cd_worn_pct` → `%1$d of %2$d items worn`
    - `%d` → `%1$d` and `%s` → `%1$s` everywhere else, plurals included.
  - Delete the strings that have no reference left: `stats_section_subcategory`, `stats_section_never_worn` and `stats_cost_per_wear_suffix`, if `git grep` confirms they're unused.
- **Build (`features/stats/build.gradle.kts`):**
  - Add `compose.resources { packageOfResClass = "com.closet.features.stats.resources"; generateResClass = always }`. It stays internal, so no `publicResClass`.
  - Remove `material-icons-extended` from `androidMain`.
- **Icons (#86 item 3):** add three Material Symbols vector drawables to `core/ui/src/commonMain/composeResources/drawable/`, so later features reuse them:
  - `ic_symbol_keyboard_arrow_down.xml`
  - `ic_symbol_keyboard_arrow_up.xml`
  - `ic_symbol_checkroom.xml`

  Use Material Symbols **Outlined**, weight 400, fill 0, grade 0, optical size 24, Android vector-drawable format, from Google's Material Symbols set (Apache 2.0). Keep `android:viewportWidth`/`Height` as downloaded. Set `android:fillColor="#FF000000"`: `Icon` tints it.
- **Moves (`git mv` androidMain → commonMain):** `StatsScreen.kt`, `StatsSharedUi.kt`, `StatsActivityComponents.kt` and `StatsCompositionComponents.kt`. Then switch them:
  - `androidx.compose.ui.res.stringResource` / `pluralStringResource` → `org.jetbrains.compose.resources.stringResource` / `pluralStringResource`, with `com.closet.features.stats.resources.Res`.
  - `Icons.*` → `Icon(painterResource(CoreRes.drawable.ic_symbol_*), …)`. `CoreRes` is an import alias for `com.closet.core.ui.resources.Res`.
  - `collectAsStateWithLifecycle` → the JetBrains lifecycle-runtime-compose import. It's the same package, so check that the import still resolves.
- **Docs (`app-android/CLAUDE.md`):**
  - Module structure: `features/stats` is KMP (`commonMain` UI + ViewModel; `androidMain` navigation, previews, Koin module).
  - Resources: a feature's own strings live in its `composeResources` with an internal `Res` (`com.closet.features.<name>.resources`). Shared icons are Material Symbols `ic_symbol_*` in `core/ui`.
  - Add `:features:stats:jvmTest :features:stats:compileCommonMainKotlinMetadata` to the full-verification command.
  - Images: common ViewModels use `ImagePathResolver`.
  - Charts: Vico 3.0.3 in commonMain. The import prefix stays `com.patrykandpatrick.vico.compose`.
- **CHANGELOG:** under `[Unreleased]` → `### Changed`, add one line: "**Stats screen is Kotlin Multiplatform** (KMP Phase 5): shared UI and logic, Compose resources, Material Symbols icons."
- **Tests:** `jvmTest/.../StatsStringsTest.kt`.

**Interfaces:**
- Consumes: Task 2's layout and helpers.
- Produces: the internal generated `com.closet.features.stats.resources.Res`, and `com.closet.core.ui.resources.Res.drawable.ic_symbol_{keyboard_arrow_down,keyboard_arrow_up,checkroom}`.

- [ ] **Step 1: Write the failing test.** **`StatsStringsTest`** (jvmTest, `runTest`, Compose resources' suspend `getString` / `getPluralString`):
  - `getString(Res.string.stats_cd_worn_pct, 3, 10) == "3 of 10 items worn"`
  - `getString(Res.string.stats_cost_per_wear_formatted, "$2.50") == "$2.50 / wear"`
  - `getPluralString(Res.plurals.stats_total_logs, 1, 1) == "You've logged 1 outfit"`
  - `getPluralString(Res.plurals.stats_total_logs, 4, 4) == "You've logged 4 outfits"`
  - `getPluralString(Res.plurals.stats_wear_badge, 2, 2) == "2 wears"`
  - `getString(Res.string.stats_section_never_worn_count, 5) == "Never Worn (5)"`
- [ ] **Step 2: Run it to verify it fails.** Run `./gradlew :features:stats:jvmTest` → compile FAIL (`Res` unresolved).
- [ ] **Step 3: Implement** the resources, the icons, the moves and the import switches.
  - `git grep -nE "R\.(string|plurals)|material\.icons" -- features/stats/src` prints nothing.
  - Only `StatsPreviews.kt`, `StatsNavigation.kt` and `di/StatsKoinModule.kt` remain in `androidMain`.
- [ ] **Step 4: Verify**
  - Run the full verification command (from `CLAUDE.md`, plus the two stats tasks) → BUILD SUCCESSFUL.
  - `unzip -l` on each debug APK (full, foss) lists `composeResources/com.closet.features.stats.resources/values/strings.commonMain.cvr`, or the strings file at the path the APK actually uses; record that path. It also lists `composeResources/com.closet.core.ui.resources/drawable/ic_symbol_checkroom.xml`.
  - Android Studio renders `StatsPreviews.kt` (spot-check one preview with `render_compose_preview`). This proves `androidMain` previews still see the `commonMain` Composables.
- [ ] **Step 5: Commit** with message `refactor(stats): Compose resources, Material Symbols icons; stats UI in commonMain (KMP Phase 5)`.
- [ ] **Step 6: PR checkpoint**
  - Push branch `feat/kmp-phase5-stats`, open the PR, and wait for `android` and `ios-compile` to pass.
  - Smoke-test on the **test device only** (ask the owner if unsure which device), on both flavors:
    - the period chips switch and the numbers change;
    - the headline value shows a currency amount, or `—` with no prices;
    - most worn, cost per wear and never worn all show photos, and an item without a photo shows the placeholder;
    - tapping an item opens its detail;
    - the Wear by Category chart renders, with labels;
    - the color swatches and category bars show the right colors;
    - a segmented-bar tooltip opens on tap;
    - the never-worn arrow icons toggle the section;
    - the empty-wardrobe state shows the hanger icon;
    - TalkBack reads "N of M items worn" (no `%d`).

    Wait for the owner to merge.
