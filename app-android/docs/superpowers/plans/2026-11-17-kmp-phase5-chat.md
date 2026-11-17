# KMP Phase 5e: `features/chat` → KMP/CMP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `features/chat` into a Kotlin Multiplatform + Compose Multiplatform module, with Android behavior unchanged. Everything except navigation and previews moves to `commonMain`: the chat repository and tool path, the routers and date parser, the cloud providers (Anthropic, Gemini, OpenAI-compatible) and their tool formats, `ChatViewModel`, the screen, and the Koin modules.

**Architecture:**
- **One PR, two tasks**, the same shape as recommendations (#93). The module is already nearly platform-free: Ktor, kotlinx-serialization and `kotlin.text.Regex` are multiplatform, and the tool layer already lives in `core/data` commonMain.
- **Task 1:** convert the module.
  - Logic, providers, routers, the ViewModel, models and Koin go to `commonMain`.
  - Replacements:
    - `StorageRepository` → `ImagePathResolver`;
    - `java.io.File` → a `String?` path;
    - Timber → Kermit;
    - `java.time` → `kotlinx-datetime`;
    - `java.net.URI` → Ktor `Url`.
  - The screen still uses `R`, so it stays in `androidMain` until Task 2.
- **Task 2:** strings (including the one `string-array`) to Compose resources, the four `Icons.*` to Material Symbols, and the 4 `@Preview`s into an androidMain `ChatPreviews.kt`. Then `ChatScreen.kt` moves to `commonMain`.
- **Stays in `androidMain`:** `ChatNavigation.kt` (Phase 6) and `ChatPreviews.kt`.
- **Flavor binding is unchanged:**
  - `OnDeviceChatProvider`, `ChatDateParser`, `ChatRouter` and `LanguageDetector` are bound by `gmsModule` (full) or `chatNoOpModule` (foss).
  - `platform/gms` keeps depending on this module's Android variant: `MlKitChatDateParser`, `MlKitLanguageDetector`, `NanoChatProvider`, and `PatternChatRouter` in `gmsModule`.

**Tech Stack:** Kotlin 2.4.21, AGP 9.1.0 KMP library, CMP 1.12.2 / Material 3 1.9.0, Ktor 3.1.3, kotlinx-serialization, kotlinx-datetime 0.8.0, Koin 4.2.2, Coil 3.6.3, Kermit 2.2.0, Turbine.

**Spec:** `docs/superpowers/specs/2026-11-14-kmp-migration-design.md` (Phase 5). The model is `plans/2026-11-17-kmp-phase5-recommendations.md`. Copy `features/recommendations/build.gradle.kts`'s shape, its JVM Koin graph test, its strings test and the `gradle/skiko-jvm-test-runtime.gradle.kts` apply. Gotchas are in CLAUDE.md "Resources" and `docs/superpowers/notes/kmp-toolchain.md`.

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
- **`commonMain` stays platform-free:** no `android.*`, `java.*`, `androidx.core`, `androidx.navigation`, `androidx.activity`, `androidx.work` or `timber` imports. `:features:chat:compileCommonMainKotlinMetadata` catches leaks.
- **Resources:**
  - Positional placeholders only.
  - `'`, not `\'`.
  - Write `…` and `→` as literal characters, not `…` or `→`.
  - Keep the `\n` line breaks in the welcome strings.
  - `material-icons-extended` is not used in `features/chat` after this PR.
- **Icons:** Material Symbols Outlined, wght 400, FILL 0, GRAD 0, opsz 24, `#FF000000`, named `ic_symbol_<name>`, from Google's Material Symbols set, the same source as the earlier PRs. No auto-mirroring.
  - Reuse `ic_symbol_auto_awesome`, `ic_symbol_checkroom` and `ic_symbol_edit`.
  - Add `ic_symbol_error`, the outlined "error" glyph, to replace `ErrorOutline`.
- **Flavors:**
  - Never bind `OnDeviceChatProvider`, `ChatDateParser`, `ChatRouter` or `LanguageDetector` in `chatModule`.
  - `chatNoOpModule` stays the foss binding and moves to commonMain unchanged.
- **Logging:** Timber → Kermit (`Logger.withTag("<ClassName>")`, or the existing `TAG`), with the same levels. Timber's `%s`/`%d` arguments become string templates.
  - Keep each throwable argument as it is. The providers pass a *redacted* throwable, so never swap in the raw exception.
- **No users:** no compat shims.
- **Gradle hygiene:**
  - Run from `app-android/`, one invocation at a time, in the foreground, with no `--rerun-tasks`.
  - Never stop or kill daemons.
  - If a task sits with no output for ~5 min, or fails with "Failed to clean up output files", stop and report.
  - iOS compiles locally with `-Pkotlin.native.enableKlibsCrossCompilation=true`. Compile both main and **test** sources: `compileTestKotlinIosSimulatorArm64`. Kotlin/Native rejects `()`, `.`, `:`, `;`, `/`, `<>`, `[]` and `\` in backtick test names.

## Review Focus

1. **Explicit date parsing.** `regexParseDate` keeps its exact behavior:
   - the ISO fast path;
   - "Month Day[, Year]" with ordinals and 3-letter abbreviations;
   - the current year when none is given (now `Clock.System.todayIn(TimeZone.currentSystemDefault()).year`);
   - an impossible date (April 31) → `null`. The `kotlinx.datetime.LocalDate(y, m, d)` constructor throws `IllegalArgumentException`; catch it.
   - The output is ISO `YYYY-MM-DD` (`LocalDate.toString()`).

   (Task 1: `RegexDateParserTest` ported to commonTest with unchanged assertions.)
2. **Router date output and cutoff.**
   - `formatDisplayDate` still renders `"April 4, 2026"` in English for any device locale. Use a `LocalDate.Format { monthName(MonthNames.ENGLISH_FULL); char(' '); day(padding = Padding.NONE); chars(", "); year() }`. A non-ISO input still returns the input unchanged.
   - The "not worn in N days" cutoff is `today.minus(N, DateTimeUnit.DAY).toString()`.

   (Task 1: make `formatDisplayDate` an `internal` top-level function and add `ChatDisplayDateTest` for `2026-04-04` → `April 4, 2026`, `2026-12-25` → `December 25, 2026`, and `"garbage"` → `"garbage"`.)
3. **OpenAI-compatible base URL.** `buildCompletionsUrl` keeps its rules with `io.ktor.http.Url(base).encodedPath` in place of `URI.path`:
   - blank → the default base + `/v1/chat/completions`;
   - no path or `/` → append `/v1/chat/completions`;
   - an existing path → append `/chat/completions`.

   Keep the `throw IllegalArgumentException("Invalid OpenAI base URL — …")` for whatever `Url(...)` throws.

   (Task 1: make it `internal` (top-level or companion) and add `OpenAiCompletionsUrlTest`:
   - `""` → `https://api.openai.com/v1/chat/completions`, using whatever `DEFAULT_BASE_URL` is;
   - `http://localhost:11434` and `http://localhost:11434/` → `…/v1/chat/completions`;
   - `https://api.groq.com/openai/v1` → `…/openai/v1/chat/completions`.)
4. **Item thumbnails in chat.**
   - `ChatItemSummary.imageFile: File?` becomes `imagePath: String?`, resolved through `ImagePathResolver.absolutePath`. The `File.exists()` check can't run in common code.
   - A missing file must still show the Checkroom placeholder, not an empty tile. In both thumbnail sites, replace `AsyncImage` with Coil 3 `SubcomposeAsyncImage`, whose `error` slot renders the same tinted placeholder `Icon` the null branch uses.

   (Task 1: `ChatViewModelTest` asserts the resolved path for an item with an image and `null` for one without. Task 2: the device smoke.)
5. **Foss/full Koin graph.**
   - **On Android:** foss still resolves `UnavailableOnDeviceChatProvider`, `RegexChatDateParser` and `UnroutedChatRouter`. Full still resolves the gms `NanoChatProvider`, `MlKitChatDateParser`, `PatternChatRouter` and `MlKitLanguageDetector`. `AppKoinGraphTest` and `GmsKoinTest` (which includes `chatModule`) still pass.
   - **On JVM:** the graph test includes `chatNoOpModule`. It binds only `OnDeviceChatProvider`, `ChatDateParser` and `ChatRouter`, with no `NanoInitializer` clash.

   (Task 1: `ChatJvmKoinGraphTest`; the existing `ChatKoinTest` moves to `androidHostTest`.)

---

### Task 1: `features/chat` as a KMP module; logic, providers, ViewModel and Koin in `commonMain`

**Files:**
- **Rewrite `features/chat/build.gradle.kts`** by copying `features/recommendations/build.gradle.kts`.
  - Namespace `com.closet.features.chat`.
  - `commonMain` has the same Ktor and serialization set (drop `ktor-client-okhttp`; the engine comes from `core/data`), plus `kotlinx-datetime`.
  - `commonTest` gets `kotlin("test")`, `coroutines-test` and `turbine`; `jvmTest` gets `mockk`. `androidHostTest` gets what `ChatKoinTest` needs today: `koin-test-junit4`, `mockk` and `work-runtime-ktx`.
  - Keep `material-icons-extended` and `ui-tooling-preview` in androidMain temporarily; Task 2 removes the first. Previews need `ui-tooling` through `androidRuntimeClasspath`, per CLAUDE.md "Previews".
  - Apply the Skiko script.
  - Drop `timber`.
- **Moves (`git mv`):**
  - **To `commonMain`:** every file under `src/main/kotlin` except `ChatScreen.kt` and `ChatNavigation.kt`. That covers `ai/**`, `di/**`, `model/**`, the routers, the date parsers, `LanguageDetector`, `ChatRepository`, `ChatViewModel`, `ChatUiState` and `ChatErrorMessage`.
  - **To `androidMain`:** `ChatScreen.kt`, `ChatNavigation.kt` and `src/main/res`.
  - **Tests:**
    - Tests that use `mockk` (for example `ChatViewModelTest`, `ChatRepositoryToolPathTest` and `PatternChatRouterTest`, if it mocks the DAOs) → `src/jvmTest/**`.
    - Tests that don't → `src/commonTest/**`.
    - Rewrite both kinds to `kotlin.test` with unchanged assertions; JUnit `@Before`/`@After` become `@BeforeTest`/`@AfterTest`.
    - `di/ChatKoinTest` → `src/androidHostTest/**`, unchanged.
    - `RegexDateParserTest` reads the current year with `Clock.System.todayIn(TimeZone.currentSystemDefault()).year`.
- **Code changes:**
  - **`model/ChatMessage.kt`:** `ChatItemSummary(id, name, imagePath: String?)`, with the KDoc updated (the path is resolved and absolute, and existence is not checked).
  - **`ChatViewModel`:** `storageRepository: StorageRepository` → `imagePathResolver: ImagePathResolver`, with `imagePath = detail.item.imagePath?.let(imagePathResolver::absolutePath)`.
  - **`RegexDateParser.kt` and `PatternChatRouter.kt`:** `kotlinx-datetime` per Review Focus 1–2. `Locale.ENGLISH` lowercasing becomes `lowercase()`, which is locale-invariant in Kotlin.
  - **`OpenAiChatProvider`:** Ktor `Url` per Review Focus 3.
  - **androidMain `ChatScreen.kt`:** use `item.imagePath` with the `SubcomposeAsyncImage` error slot, per Review Focus 4.
- **New tests:**
  - `src/commonTest/.../ChatDisplayDateTest.kt`
  - `src/commonTest/.../ai/OpenAiCompletionsUrlTest.kt`
  - `src/jvmTest/.../di/ChatJvmKoinGraphTest.kt`

**Interfaces:**
- `ChatViewModel(chatRepository, clothingDao, imagePathResolver: ImagePathResolver, providerSelector, embeddingIndex)`. `viewModelOf` is unchanged.
- `internal fun formatDisplayDate(isoDate: String): String` and `internal fun buildCompletionsUrl(raw: String): String`.
- `ChatItemSummary.imagePath: String?`.

- [ ] **Step 1: Write the failing tests**
  - `ChatDisplayDateTest` and `OpenAiCompletionsUrlTest` with the cases from Review Focus 2–3.
  - **`ChatJvmKoinGraphTest`:**
    - Include `jvmDataModule(tmp)`, `commonDataModule`, `jvmUiModule(tmp)`, `chatModule` and `chatNoOpModule`.
    - Use only the `extraTypes` from `RecommendationsJvmKoinGraphTest`, then `verify()`.
    - Delete the temp dir after.
  - **`ChatViewModelTest`** (ported): add an assertion that an item's summary carries `"/root/<path>"` with `ImagePathResolver { "/root/$it" }`, and `null` when the item has no image.
- [ ] **Step 2: Run the tests to verify they fail.** Run `./gradlew :features:chat:jvmTest`; expect a compile failure.
- [ ] **Step 3: Implement** the build conversion, the moves and the code changes above.
- [ ] **Step 4: Verify**
  - `./gradlew :features:chat:jvmTest :features:chat:testAndroidHostTest :features:chat:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
  - `./gradlew :features:chat:compileKotlinIosSimulatorArm64 :features:chat:compileTestKotlinIosSimulatorArm64 -Pkotlin.native.enableKlibsCrossCompilation=true` → BUILD SUCCESSFUL.
  - `git grep -nE "^import (android\.|java\.|timber|androidx\.(core|navigation|work))" -- features/chat/src/commonMain` prints nothing.
  - `./gradlew test` → BUILD SUCCESSFUL, including `AppKoinGraphTest` in both flavors and `platform/gms`'s `GmsKoinTest`.
- [ ] **Step 5: Commit** with message `refactor(chat): features/chat as a KMP module; repository, providers, routers, ViewModel and Koin in commonMain (KMP Phase 5)`.

### Task 2: Compose resources, Material Symbols; chat UI to `commonMain`; PR checkpoint

**Files:**
- **Strings:** move them to `src/commonMain/composeResources/values/strings.xml`, including the `chat_welcome_suggestions` `string-array`.
  - `chat_powered_by` → `Powered by %1$s`.
  - Fix the four `\'`.
  - `…` → `…` and `→` → `→`.
  - Every string is referenced today (checked), so none are deleted.
  - Add `compose.resources { packageOfResClass = "com.closet.features.chat.resources"; generateResClass = always }`.
- **Icons:**
  - Add `core/ui/src/commonMain/composeResources/drawable/ic_symbol_error.xml`.
  - `Icons.Default.AutoAwesome` → `ic_symbol_auto_awesome`; `Checkroom` → `ic_symbol_checkroom`; `Edit` → `ic_symbol_edit`; `ErrorOutline` → `ic_symbol_error`.
  - Use `Icon(painter = painterResource(CoreRes.drawable.…))`. Some call sites pass the vector positionally (lines ~708–718): keep their tint and size.
  - Remove `material-icons-extended`.
- **Previews:** cut the 4 `@Preview` functions and the `android.content.res.Configuration` import from `ChatScreen.kt` into `src/androidMain/.../ChatPreviews.kt`. Make the previewed Composables `internal` where they're `private`.
- **Move (`git mv` androidMain → commonMain):** `ChatScreen.kt`. Then switch imports:
  - CMP `stringResource`/`stringArrayResource`;
  - `painterResource(CoreRes.drawable.ic_symbol_*)`;
  - the JetBrains `collectAsStateWithLifecycle`.
- **Docs and changelog:**
  - **CLAUDE.md module list:** `features/chat` is KMP (`commonMain` repository, providers, routers, UI, ViewModel and Koin; `androidMain` navigation and previews). Also add `features/chat` to the Kermit list under "Logging" and to the Compose-resources feature list under "Resources".
  - **CLAUDE.md full-verification command:** add `:features:chat:jvmTest :features:chat:compileCommonMainKotlinMetadata`.
  - **CHANGELOG** `[Unreleased]` → `### Changed`: "**Wardrobe chat is Kotlin Multiplatform** (KMP Phase 5): shared chat pipeline, AI providers and UI, Compose resources, Material Symbols icons."
- **Tests:** `src/jvmTest/.../ChatStringsTest.kt`:
  - `getString(Res.string.chat_powered_by, "Gemini") == "Powered by Gemini"`
  - `getStringArray(Res.array.chat_welcome_suggestions)` has 4 entries, and entry 2 is `What haven't I worn lately?`, with a plain `'`.
  - `getString(Res.string.chat_action_alternatives) == "Alternatives →"`

**Interfaces:**
- Consumes Task 1's layout.
- Produces the internal `com.closet.features.chat.resources.Res` and `CoreRes.drawable.ic_symbol_error`.

- [ ] **Step 1: Write the failing test.**
- [ ] **Step 2: Run it to verify it fails.** Run `./gradlew :features:chat:jvmTest`; expect a compile failure.
- [ ] **Step 3: Implement.**
  - `git grep -nE "R\.(string|plurals|array)|material\.icons|@Preview|android\.content" -- features/chat/src/commonMain` prints nothing.
  - Only `ChatNavigation.kt` and `ChatPreviews.kt` are left in androidMain.
- [ ] **Step 4: Verify.**
  - Run the full verification command from CLAUDE.md, including the chat tasks, in separate invocations if needed → BUILD SUCCESSFUL.
  - The chat iOS main and test compiles → BUILD SUCCESSFUL.
  - `unzip -l` on each debug APK lists `composeResources/com.closet.features.chat.resources/` strings and `ic_symbol_error.xml`.
- [ ] **Step 5: Commit** with message `refactor(chat): Compose resources, Material Symbols; chat UI in commonMain (KMP Phase 5)`.
- [ ] **Step 6: PR checkpoint** (the controller does this).
  - Push `feat/kmp-phase5-chat`, open the PR (it closes nothing; reference #97), and wait for CI.
  - Smoke-test on the **test device only**, both flavors, with the demo wardrobe:
    - The welcome state shows the title, subtitle and 4 suggestion chips. Tapping one sends it.
    - **Full flavor only** (foss binds `UnroutedChatRouter`): routed questions answer offline, even with no provider configured.
      - "what haven't I worn in 30 days" → a list;
      - "what did I wear on <a logged date written as Month Day>" → the answer shows the date as "Month D, YYYY".
    - Item thumbnails show photos. An item with no photo shows the Checkroom placeholder.
    - "New chat" opens the clear dialog, and Clear empties the conversation.
    - The top bar reads "Powered by …".
    - With a cloud key configured on the test device, a free-form question gets an answer, and an outfit reply shows "Log it" and "Alternatives →". If no key is set, check the routed path only and say so.
    - Foss: no crash. The on-device provider is unavailable, and with no cloud key a question shows the "not configured" error message instead of crashing.

    Wait for the owner to merge.
