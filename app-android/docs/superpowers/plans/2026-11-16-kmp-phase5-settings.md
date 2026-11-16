# KMP Phase 5b: `features/settings` → KMP/CMP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `features/settings` into a Kotlin Multiplatform + Compose Multiplatform module: the ViewModel, the UI state, both screens (Settings, AI & Image Tools) and the Koin module in `commonMain`, with Android behavior unchanged.

**Architecture:**
- One PR, three tasks, the same shape as stats (`plans/2026-11-16-kmp-phase5-stats.md`).
  - **Task 1 (`core/data`)** removes the three things that keep `SettingsViewModel` Android-only:
    - `CaptionEnrichmentProvider` (androidMain, `describe(Bitmap)`) is split. Its batch half becomes a common `BatchCaptioner`.
    - `BatchCaptionResult` moves to commonMain on `kotlin.uuid.Uuid`.
    - The `Context.filesDir` walk becomes a common `ImageStorageStats`.
    - It also adds the honest JVM/iOS stubs, so a common Koin graph resolves.
  - **Task 2** converts the module. The ViewModel, `SettingsUiState`, `NanoStatus`, `NanoReadiness`, a common `formatBytes` and **the Koin module** go to `commonMain`. The Koin module moves now because of the stats "next time" lesson. The screens still use `R`, so they stay in `androidMain`.
  - **Task 3** moves strings to Compose resources and the two `Icons.AutoMirrored.*` to Material Symbols. The screens follow to `commonMain`, and the Android-only screen bits go behind three small `expect` Composables.
- **Stays in `androidMain`:**
  - `SettingsNavigation.kt`: navigation moves in Phase 6.
  - A new `SettingsPreviews.kt`, holding the four `@Preview`s pulled out of the two screen files.
  - The `actual`s for the platform hooks.

**Tech Stack:** Kotlin 2.4.21, AGP 9.1.0 KMP Android library, CMP 1.12.2 / Material 3 1.9.0 / Compose resources, Koin 4.2.2 (`koin-compose-viewmodel`), JetBrains lifecycle 2.10.0, Kermit 2.2.0.

**Spec:** `docs/superpowers/specs/2026-11-14-kmp-migration-design.md`. Phase 5 is "Features → KMP/CMP, one per PR". See also "Platform gaps" and "Logging". The model is `plans/2026-11-16-kmp-phase5-stats.md`. Read the gotchas in `docs/superpowers/notes/kmp-toolchain.md` first. Covered there: positional placeholders, the `\'` escape, the Skiko JVM test runtime, `androidResources.enable`, and `ui-tooling` via `androidRuntimeClasspath`.

## Global Constraints

- **Exit criteria:**
  - `assembleFullDebug` and `assembleFossDebug` succeed.
  - All unit tests pass, including every module's `jvmTest`.
  - The foss APK has no GMS classes.
  - A manual smoke test passes on the owner's **test** device.
  - CI `ios-compile` is green.
- **Targets:** `android`, `jvm()`, `iosArm64()`, `iosSimulatorArm64()`, the same as `features/stats`.
- **Versions:**
  - No new libraries. Settings reuses what stats added: `koin-compose-viewmodel`, `jetbrains-lifecycle-*` and the `skiko-awt-runtime-*` catalog entries.
  - Any new iOS klib must have ABI ≤ **2.4.0**.
  - Declare CMP libraries by catalog coordinates, never `compose.*` accessors.
- **`commonMain` stays platform-free:** no `android.*`, `java.*`, `androidx.core`, `androidx.activity` or `timber` imports. `:features:settings:compileCommonMainKotlinMetadata` catches leaks.
- **Resources:**
  - Strings use `Res.string.*` / `Res.plurals.*` with **positional** placeholders.
  - Write `'`, not `\'`.
  - `material-icons-extended` is not used in `features/settings` after this PR.
- **Logging:** Timber → Kermit (`Logger.withTag("SettingsViewModel")`), with the same levels (`w` stays `w`, `e` stays `e`).
- **Flavors:**
  - `BatchCaptioner`/`CaptionEnrichmentProvider` and `NanoInitializer` stay bound per flavor on Android: `gmsModule` for full; `wardrobeNoOpModule` / `recommendationsNoOpModule` for foss.
  - Never bind the new stubs in `androidDataModule`.
- **No users:** no compat shims.
- **Gradle hygiene:**
  - Run from `app-android/`, one invocation at a time, in the foreground, with no `--rerun-tasks`.
  - On a Windows file lock, stop and report; never kill daemons.

## Review Focus

1. **`%%` and `%d` in strings.**
   - `settings_ai_nano_downloading` is `Downloading model (%d%%)…`. It must render `Downloading model (42%)…` after the switch to Compose resources.
   - It's not known whether CMP unescapes `%%`; pick whichever source form makes `SettingsStringsTest` pass.
   - The non-positional `%d` / `%s` in `settings_accent_selected`, `settings_ai_nano_failed`, `settings_image_storage_used_summary` and the five `%d` plurals must become `%1$…`.
   - (Task 3: `SettingsStringsTest`.)
2. **Weather permission flow (Android).**
   - Toggling weather on with the permission granted enables it.
   - First denial shows the snackbar.
   - "Don't ask again" (or a second denial) shows the rationale dialog, whose Open Settings opens this app's system settings page.
   - Toggling off never asks.
   - This logic moves into an `actual` and must not change. (Task 3, device smoke test.)
3. **Snackbar results after batch work.**
   - Background removal, captioning and compression results move from `context.resources.getQuantityString` to the suspend `getPluralString`, and each must show once per run.
   - The caption result is de-duplicated by `resultId`, now a `Uuid`: rotating the screen after a result must not show it twice.
   - (Task 2: `SettingsViewModelTest` for the id hand-off; device for the rest.)
4. **Storage size.**
   - "N used by wardrobe images" must show the same number as before.
   - The new `ImageStorageStats` walks `<files>/closet_images` (`IMAGES_DIR_NAME`), not a re-typed literal.
   - It must refresh after a compression run.
   - `formatBytes` must match the old `String.format(Locale.ROOT, "%.1f …")` output.
   - (Tasks 1–2 tests.)
5. **Back arrow in RTL.**
   - `Icons.AutoMirrored.Filled.ArrowBack` / `KeyboardArrowRight` mirror in right-to-left locales. The Material Symbols replacements need `android:autoMirrored="true"` and must still mirror under CMP.
   - (Task 3: device check with the developer option "Force RTL layout direction" on the test device, then turn it back off.)

---

### Task 1: `core/data`: common `BatchCaptioner`, `BatchCaptionResult`, `ImageStorageStats`, JVM/iOS stubs

**Files:**
- **Create** `core/data/src/commonMain/.../repository/BatchCaptioner.kt`.
  - It holds the batch half of today's `CaptionEnrichmentProvider`: `isSupported`, `progress`, `result`, `startBatchEnrichment()`, `consumeResult()`.
  - Move those members' KDoc with them.
- **Modify** `core/data/src/androidMain/.../repository/CaptionEnrichmentProvider.kt`: `interface CaptionEnrichmentProvider : BatchCaptioner`, keeping `describe(Bitmap)`, `isModelDownloaded()` and `ensureModelDownloaded()`.
- **Move** `BatchCaptionResult.kt` with `git mv` from androidMain to commonMain (`ai/`). Its `resultId` becomes `kotlin.uuid.Uuid`, defaulting to `Uuid.random()`. Opt in to `ExperimentalUuidApi` only if Kotlin 2.4.21 still requires it.
- **Create** `core/data/src/commonMain/.../repository/ImageStorageStats.kt`.
- **Create** `core/data/src/commonMain/.../ai/UnavailableNanoInitializer.kt` and `core/data/src/commonMain/.../repository/NoOpBatchCaptioner.kt`, beside the existing `NoOp*Scheduler` stubs.
- **Create** `core/data/src/jvmMain/.../repository/JvmImageStorageStats.kt` and `core/data/src/iosMain/.../repository/IosImageStorageStats.kt`.
- **Modify** `StorageRepository` (androidMain) to also implement `ImageStorageStats`.
- **Modify** `DataKoinModule.kt`: `singleOf(::StorageRepository) { bind<ImagePathResolver>(); bind<ImageStorageStats>() }`.
- **Modify** `jvmDataModule` and `iosDataModule` to bind `ImageStorageStats`, `NanoInitializer` (the unavailable stub) and `BatchCaptioner` (the no-op stub).
- **Modify** `platform/gms/.../di/GmsKoinModule.kt` and `features/wardrobe/.../di/WardrobeNoOpModule.kt`: `single<CaptionEnrichmentProvider> { … } bind BatchCaptioner::class`.
- **Tests:**
  - `core/data/src/jvmTest/.../repository/JvmImageStorageStatsTest.kt`
  - extend `core/data/src/jvmTest/.../repository/JvmImagePathResolverTest.kt`'s module check, or add a sibling test
  - extend `core/data/src/androidHostTest/.../di/SingletonParityTest.kt`
  - extend `app/src/test/.../di/AppKoinGraphTest.kt`

**Interfaces:**
- Produces, in commonMain:
  - `interface BatchCaptioner`, with members `val isSupported: Boolean`, `val progress: StateFlow<BatchCaptionProgress?>`, `val result: StateFlow<BatchCaptionResult?>`, `fun startBatchEnrichment()` and `fun consumeResult()`.
  - `data class BatchCaptionResult(val done: Int, val failed: Int, val resultId: Uuid = Uuid.random(), val stoppedReason: String? = null)`.
  - `fun interface ImageStorageStats { suspend fun usedBytes(): Long }`. It returns the total size of the files under the images directory, recursively, or `0L` when the directory doesn't exist.
  - `object UnavailableNanoInitializer : NanoInitializer`. `initNanoFlow()` emits `NanoInitResult.NotSupported` once.
  - `object NoOpBatchCaptioner : BatchCaptioner`. `isSupported = false`, the flows are fixed at `null`, and both functions do nothing.
- Produces, in androidMain: `StorageRepository.usedBytes()` walks `File(context.filesDir, IMAGES_DIR_NAME)` on `Dispatchers.IO`, using the same walk as `SettingsViewModel.computeStorageBytes()` today.
- Produces, in jvmMain: `class JvmImageStorageStats(imagesDir: File)`, bound with `File(dataDirectory, IMAGES_DIR_NAME)`.
- Produces, in iosMain: `class IosImageStorageStats`.
  - It sums the file sizes under the same root as `IosImagePathResolver`, using `NSFileManager` (`enumeratorAtPath` + `attributesOfItemAtPath` `NSFileSize`).
  - It returns `0L` when the directory is missing.

- [ ] **Step 1: Write the failing tests**
  - **`JvmImageStorageStatsTest`** (temp dir):
    - with the files `a.jpg` (3 bytes) and `sub/b.jpg` (5 bytes), `usedBytes() == 8L`;
    - a missing directory gives `0L`;
    - an empty directory gives `0L`.
  - **JVM module test:** `module { includes(commonDataModule, jvmDataModule(tmp)) }` resolves `ImageStorageStats`, `NanoInitializer` and `BatchCaptioner`, and `get<BatchCaptioner>().isSupported == false`.
  - **`UnavailableNanoInitializer`** (commonTest): `initNanoFlow().toList() == listOf(NanoInitResult.NotSupported)`.
  - **`SingletonParityTest`:** `get<ImageStorageStats>() === get<StorageRepository>()`.
  - **`AppKoinGraphTest`**, both flavors: `get<BatchCaptioner>() === get<CaptionEnrichmentProvider>()`. Use the existing graph-start helper in that test; if it only runs `verify()`, add one `koinApplication { … }` start for this assertion.
- [ ] **Step 2: Run the tests to verify they fail.** Run `./gradlew :core:data:jvmTest`; expect a compile failure (`ImageStorageStats` unresolved).
- [ ] **Step 3: Implement** the split, the move, the stubs, the implementations and the bindings.
  - `SettingsViewModel` keeps compiling: `CaptionEnrichmentProvider` still has every member it uses.
  - Run `git grep -n "resultId\|BatchCaptionResult("` and fix any caller still on `UUID`.
- [ ] **Step 4: Verify**
  - `./gradlew :core:data:jvmTest :core:data:testAndroidHostTest :core:data:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
  - `./gradlew :app:testFullDebugUnitTest :app:testFossDebugUnitTest` → BUILD SUCCESSFUL.
  - `./gradlew :platform:gms:testDebugUnitTest :features:wardrobe:testDebugUnitTest` → BUILD SUCCESSFUL (`GmsKoinTest`, `WardrobeKoinTest`).
- [ ] **Step 5: Commit** with message `feat(data): common BatchCaptioner, ImageStorageStats and JVM/iOS stubs (KMP Phase 5)`.

### Task 2: `features/settings` as a KMP module; ViewModel, state and Koin module in `commonMain`

**Files:**
- **Rewrite `features/settings/build.gradle.kts`** by copying `features/stats/build.gradle.kts` and changing the names.
  - Namespace `com.closet.features.settings`; keep `androidResources { enable = true }`.
  - **commonMain:** `:core:data`, `:core:ui`, the CMP runtime/foundation/ui/material3, `koin-core`, `koin-compose-viewmodel`, the JetBrains lifecycle artifacts, coroutines, `kermit`.
  - **androidMain:** `koin-android`, `androidx.navigation.compose`, `kotlinx-serialization-json`, `activity-compose`, `androidx.core`, plus the Compose BOM and `ui-tooling-preview`. Add `material-icons-extended` temporarily (Task 3 removes it).
  - **commonTest:** `kotlin("test")`, coroutines-test, turbine.
  - **jvmTest:** mockk, plus the Skiko host runtime. Copy `skikoRuntimeForHost()` from stats verbatim. A shared build-logic helper is deferred until the third copy (outfits); note it in the PR body.
  - **androidHostTest:** junit, `koin-test-junit4`, `ktor-client-okhttp`, `work-runtime-ktx`.
  - **Runtime-only:** `"androidRuntimeClasspath"` gets the Compose BOM plus `ui-tooling`.
  - The same `tasks.register("test")` as stats.
  - Drop `timber` and `work-runtime-ktx` from main.
- **Moves (`git mv`):**
  - To `src/commonMain/kotlin/**`: `SettingsViewModel.kt`, `SettingsUiState.kt`, `NanoStatus.kt`, `NanoReadiness.kt`, `di/SettingsKoinModule.kt`.
  - To `src/androidMain/kotlin/**`: `SettingsScreen.kt`, `AiSettingsScreen.kt`, `SettingsNavigation.kt`.
  - `src/main/res` → `src/androidMain/res`.
  - Tests:
    - `NanoReadinessTest` → `src/commonTest/**`, rewritten to `kotlin.test`.
    - `di/SettingsKoinModuleTest` → `src/androidHostTest/**`. Its `extraTypes` swap `CaptionEnrichmentProvider` for `BatchCaptioner`.
- **Create** `src/commonMain/.../ByteFormat.kt` with `internal fun formatBytes(bytes: Long): String`, moved out of `AiSettingsScreen.kt`. The screen calls it.
- **Tests:**
  - `src/jvmTest/.../SettingsViewModelTest.kt`: new; settings has no ViewModel test today.
  - `src/commonTest/.../ByteFormatTest.kt`
  - `src/jvmTest/.../di/SettingsJvmKoinGraphTest.kt`

**Interfaces:**
- Consumes Task 1's `BatchCaptioner`, `BatchCaptionResult` (with a `Uuid` id), `ImageStorageStats`, `UnavailableNanoInitializer` and `NoOpBatchCaptioner`.
- Produces `class SettingsViewModel(...)` with the same parameters minus `context: Context`, plus:
  - `captionEnrichmentProvider: CaptionEnrichmentProvider` becomes `batchCaptioner: BatchCaptioner`;
  - `imageStorageStats: ImageStorageStats` is added;
  - `viewModelOf(::SettingsViewModel)` needs no edit.
- Produces `fun onCaptionResultHandled(id: Uuid)` and `SettingsUiState.lastHandledCaptionId: Uuid?`.
- Produces `internal fun formatBytes(bytes: Long): String`.
  - ≥ 1 GiB → `"x.y GB"`; ≥ 1 MiB → `"x.y MB"`; ≥ 1 KiB → `"x.y KB"`; else `"N B"`.
  - One decimal, rounding half up, `.` as the separator in every locale.
- `settingsModule` lives in commonMain unchanged (`viewModelOf(::SettingsViewModel)`). `app`'s `allModules` import stays the same.

- [ ] **Step 1: Write the failing tests**
  - **`ByteFormatTest`:**
    - `0L` → `"0 B"`; `1023L` → `"1023 B"`
    - `1024L` → `"1.0 KB"`; `1536L` → `"1.5 KB"`; `1126L` → `"1.1 KB"`
    - `1_048_575L` → `"1024.0 KB"`, which matches today's `String.format`
    - `1_572_864L` → `"1.5 MB"`; `1_073_741_824L` → `"1.0 GB"`
  - **`SettingsViewModelTest`** (jvmTest; `StandardTestDispatcher` as `Dispatchers.Main`; mockk for the repositories; `MutableStateFlow`s for the scheduler `workStatus` and the captioner flows):
    - **Storage:** with `ImageStorageStats` returning `2048L`, `uiState.storageUsedBytes` becomes `2048L`.
      - After the compression `workStatus` emits `WorkStatus(state = SUCCEEDED, …)` and the stats return `4096L`, it becomes `4096L`.
      - A `RUNNING` status doesn't recompute (`coVerify(exactly = 2)` over the two calls).
    - **Nano success:** `NanoInitializer` emitting `NanoInitResult.Success(tokenLimit = 4096)` leads to `setAiReady(true)`, `setNanoAvailability(READY)` and `setTokenLimit(4096)`, and `uiState.nanoStatus == NanoStatus.Ready`.
    - **Nano downloading:** `Downloading(40)` doesn't call `setAiReady`, and `nanoStatus == NanoStatus.Downloading(40)`.
    - **Persist failure:** `setNanoAvailability` throwing `IllegalStateException` still gives `nanoStatus == Ready`, and the ViewModel doesn't crash.
    - **Caption hand-off:** `onCaptionResultHandled(id)` makes `uiState.lastHandledCaptionId == id`. `onCaptionResultConsumed()` calls `batchCaptioner.consumeResult()`.
    - `uiState.captionSupported` mirrors `batchCaptioner.isSupported`.
  - **`SettingsJvmKoinGraphTest`:** `module { includes(commonDataModule, jvmDataModule(tmp), commonUiModule, jvmUiModule(tmp), settingsModule) }.verify(extraTypes = listOf(SavedStateHandle::class, HttpClientEngine::class, HttpClientConfig::class))`. Take the exact `extraTypes` from `core/ui`'s `JvmUiKoinModuleTest`. It must **not** whitelist `NanoInitializer` or `BatchCaptioner`: the JVM stubs prove them.
  - **`NanoReadinessTest`:** ported to `kotlin.test`, with its assertions unchanged.
- [ ] **Step 2: Run the tests to verify they fail.** Run `./gradlew :features:settings:jvmTest`; expect a compile failure.
- [ ] **Step 3: Implement** the build conversion, the moves, the ViewModel changes (constructor; `UUID` → `Uuid`; `computeStorageBytes` → `imageStorageStats.usedBytes()`; Timber → Kermit), `formatBytes`, and the screen call-site updates in androidMain.
- [ ] **Step 4: Verify**
  - `./gradlew :features:settings:jvmTest :features:settings:testAndroidHostTest :features:settings:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
  - `git grep -nE "^import (android\.|java\.|timber)" -- features/settings/src/commonMain` prints nothing.
  - `./gradlew test` → BUILD SUCCESSFUL, including `AppKoinGraphTest` in both flavors.
  - `./gradlew :app:dependencyInsight --configuration fullDebugRuntimeClasspath --dependency androidx.lifecycle:lifecycle-runtime-compose` resolves the same version as `main`; report it.
- [ ] **Step 5: Commit** with message `refactor(settings): features/settings as a KMP module; ViewModel, state and Koin in commonMain (KMP Phase 5)`.

### Task 3: Compose resources, Material Symbols, platform hooks; screens to `commonMain`; PR checkpoint

**Files:**
- **Strings:**
  - `git mv features/settings/src/androidMain/res/values/strings.xml features/settings/src/commonMain/composeResources/values/strings.xml`, then delete the emptied `androidMain/res`.
  - Make every placeholder positional (see Review Focus 1).
  - Replace the three `\'` with `'`.
  - Delete the strings that have no reference left, if `git grep` confirms they're unused.
- **Build:**
  - Add `compose.resources { packageOfResClass = "com.closet.features.settings.resources"; generateResClass = always }` (internal).
  - Remove `material-icons-extended`.
- **Icons:** add these to `core/ui/src/commonMain/composeResources/drawable/`:
  - `ic_symbol_arrow_back.xml`
  - `ic_symbol_chevron_right.xml`, which replaces `KeyboardArrowRight`, the nav-row affordance

  Use Material Symbols Outlined, weight 400, fill 0, grade 0, optical size 24, `android:fillColor="#FF000000"`, and **`android:autoMirrored="true"`** on both. Use the same source and format as the stats icons.
- **Platform hooks:**
  - **commonMain** `src/commonMain/.../SettingsPlatform.kt`:
    - `@Composable internal expect fun rememberWeatherEnabler(onEnabled: () -> Unit, onDeniedCanAskAgain: () -> Unit, onDeniedPermanently: () -> Unit): () -> Unit`
    - `@Composable internal expect fun rememberOpenAppSettings(): () -> Unit`
    - `@Composable internal expect fun KeepScreenOn(enabled: Boolean)`
  - **androidMain** `SettingsPlatform.android.kt`: today's code from `SettingsScreen` / `AiSettingsScreen`, unchanged:
    - checking the `ACCESS_COARSE_LOCATION` permission, the launcher and `shouldShowRequestPermissionRationale`;
    - the `ACTION_APPLICATION_DETAILS_SETTINGS` intent;
    - `view.keepScreenOn`, with its `DisposableEffect` reset.
  - **jvmMain** / **iosMain**: the enabler calls `onEnabled` directly (`NoLocationProvider` / the iOS stub already make weather fall back to cached coordinates); `rememberOpenAppSettings` is a no-op lambda; `KeepScreenOn` does nothing. Give each a one-line KDoc saying why.
- **Dynamic color:**
  - Add `expect val supportsDynamicColor: Boolean` (public) next to `platformDynamicColorScheme` in `core/ui/.../theme/PlatformTheme.kt`.
  - Android: `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`; JVM/iOS: `false`.
  - `SettingsScreen` reads it instead of `Build.VERSION`.
- **Moves (`git mv` androidMain → commonMain):** `SettingsScreen.kt` and `AiSettingsScreen.kt`.
  - First cut their four `@Preview` functions (and the `Configuration` import) into a new `src/androidMain/.../SettingsPreviews.kt`. Make the previewed Composables `internal` if they're `private`.
  - Then switch the imports:
    - `stringResource` / `pluralStringResource` → `org.jetbrains.compose.resources.*`, with `com.closet.features.settings.resources.Res`;
    - `context.resources.getQuantityString(R.plurals.x, n, …)` inside `LaunchedEffect` → `getPluralString(Res.plurals.x, n, …)`;
    - `Icons.*` → `painterResource(CoreRes.drawable.ic_symbol_*)`;
    - `collectAsStateWithLifecycle` → the JetBrains import.
- **Docs (`app-android/CLAUDE.md`):**
  - Module structure: `features/settings` is KMP (`commonMain` UI, ViewModel and Koin; `androidMain` navigation, previews and the permission / keep-screen-on actuals).
  - Add `:features:settings:jvmTest :features:settings:compileCommonMainKotlinMetadata` to the full-verification command.
  - DI: `BatchCaptioner` is the common batch half of `CaptionEnrichmentProvider`; JVM/iOS bind `UnavailableNanoInitializer` / `NoOpBatchCaptioner`.
  - Feature Koin modules go in commonMain with a jvmTest graph `verify()`.
- **CHANGELOG:** under `[Unreleased]` → `### Changed`: "**Settings screens are Kotlin Multiplatform** (KMP Phase 5): shared UI, view model and DI, Compose resources, Material Symbols icons."
- **Tests:** `src/jvmTest/.../SettingsStringsTest.kt`.

**Interfaces:**
- Consumes Task 2's layout.
- Produces the internal `com.closet.features.settings.resources.Res`, `CoreRes.drawable.ic_symbol_{arrow_back,chevron_right}`, and the public `com.closet.core.ui.theme.supportsDynamicColor`.

- [ ] **Step 1: Write the failing test.** **`SettingsStringsTest`** (jvmTest, `runTest`):
  - `getString(Res.string.settings_ai_nano_downloading, 42) == "Downloading model (42%)…"`
  - `getString(Res.string.settings_accent_selected, "Amber") == "Amber, selected"`
  - `getString(Res.string.settings_ai_nano_failed, "boom") == "Setup failed: boom"`
  - `getString(Res.string.settings_image_storage_used_summary, "1.5 MB") == "1.5 MB used by wardrobe images"`
  - `getString(Res.string.settings_weather_enabled_summary) == "Fetch today's forecast using your location"`
  - `getPluralString(Res.plurals.settings_image_compress_result_with_failures, 3, 3, 1, 2) == "Compressed 3 images, 2 failed (1 skipped)"`
  - `getString(Res.string.settings_section_ai_tools) == "AI & Image Tools"`
  - One `%d` plural, using the indexed-items plural whose name `git grep` gives: quantity 1 → `"1 item indexed"`.
- [ ] **Step 2: Run it to verify it fails.** Run `./gradlew :features:settings:jvmTest`; expect a compile failure (`Res` unresolved).
- [ ] **Step 3: Implement** the resources, the icons, the hooks, `supportsDynamicColor`, the previews split, the moves and the import switches.
  - `git grep -nE "R\.(string|plurals)|material\.icons|LocalContext|LocalView|Build\.VERSION" -- features/settings/src/commonMain` prints nothing.
  - Only `SettingsNavigation.kt`, `SettingsPreviews.kt` and `SettingsPlatform.android.kt` are left in `androidMain`.
- [ ] **Step 4: Verify**
  - Run the full verification command (CLAUDE.md, including the settings tasks) → BUILD SUCCESSFUL.
  - `unzip -l` on each debug APK lists `composeResources/com.closet.features.settings.resources/` strings, and `composeResources/com.closet.core.ui.resources/drawable/ic_symbol_arrow_back.xml`.
  - Android Studio renders one preview from `SettingsPreviews.kt`. If it fails with the same "Unresolved reference 'Configuration'" as stats, record that in the PR and don't fix it here.
- [ ] **Step 5: Commit** with message `refactor(settings): Compose resources, Material Symbols, platform hooks; settings UI in commonMain (KMP Phase 5)`.
- [ ] **Step 6: PR checkpoint**
  - Push branch `feat/kmp-phase5-settings`, open the PR, and wait for `android` and `ios-compile` to pass.
  - Smoke-test on the **test device only** (ask the owner if unsure which device), on both flavors:
    - **Appearance:** the accent swatches switch the theme and TalkBack reads "Amber, selected". The dynamic-color row appears (Android 12+) and toggles.
    - **Weather:** the permission flow from Review Focus 2. Revoke the permission in system settings between runs with `adb shell pm revoke <pkg> android.permission.ACCESS_COARSE_LOCATION`, on the test device only. Also check the service / units / API-key fields and Clear cache.
    - **AI & Image Tools:**
      - the row opens it, and the back arrow returns;
      - provider switching works, and typing a key fills the model list;
      - the Nano status line shows a percentage (no `%%` / `%d`) on full;
      - foss shows the not-supported path;
      - compress images shows the result snackbar once, and the storage summary updates;
      - rebuild index shows "N items indexed";
      - batch captioning (full) keeps the screen on while it runs and shows one result snackbar, even after a rotation.
    - **RTL:** turn on "Force RTL layout direction" in developer options; the back and chevron icons mirror. Turn it off again.

    Wait for the owner to merge.
