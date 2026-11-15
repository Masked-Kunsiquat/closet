# KMP Phase 4: `core/ui` → Compose Multiplatform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `core/ui` into a Kotlin Multiplatform + Compose Multiplatform module (theme, components, Compose resources, Coil 3, preferences in `commonMain`), with Android behavior unchanged.

**Architecture:**
- **PR A** (Task 1) swaps Coil 2 for Coil 3 app-wide while every module is still Android-only.
- **PR B** (Tasks 2–3):
  - Task 2 converts `core/ui` to KMP. The theme and preferences go to `commonMain`. The Android pieces stay in `androidMain`: the dynamic color, contrast and system-bar code, and anything that still uses Android `R`.
  - Task 3 moves the drawables and strings to Compose Multiplatform resources. The remaining components follow them to `commonMain`, and the feature and `app` call sites switch from `R` to `Res`.
- Features stay Android-only (Phase 5). They use `core/ui`'s public `Res` class through Compose resources.

**Tech Stack:**
- Compose Multiplatform 1.12.2 (plugin `org.jetbrains.compose`); CMP Material 3 1.9.0.
- Coil 3.6.3.
- Kotlin 2.4.21, AGP 9.1.0 with the KMP Android library plugin.
- DataStore KMP 1.3.0, Koin 4.2.2.

**Spec:** `docs/superpowers/specs/2026-11-14-kmp-migration-design.md`. Phase 4 is "`core/ui` → CMP. Theme, components, resources, Coil 3"; see also "UI" and "Images and files". Versions come from `docs/superpowers/notes/kmp-toolchain.md`.

## Global Constraints

- **Exit criteria (every phase):** `assembleFullDebug` and `assembleFossDebug` succeed, unit tests pass (including every module's `jvmTest`), the foss APK has no GMS classes, a manual smoke test on an Android device passes, and the macOS CI job compiles iOS.
- **Targets:** `core/ui` uses the same targets as `core/data`: `android`, `jvm()`, `iosArm64()`, `iosSimulatorArm64()`.
- **Pinned versions (from `kmp-toolchain.md`):**
  - Compose Multiplatform libraries and the `org.jetbrains.compose` plugin: **1.12.2**.
  - CMP Material 3: **1.9.0**, versioned separately.
  - Coil 3 (`io.coil-kt.coil3:coil-compose`): **3.6.3**.
  - iOS klib ABI must be ≤ **2.4.0**; check any other new library this way before adding it.
  - Declare CMP libraries by catalog coordinates, not the deprecated `compose.*` accessors.
- **Spec UI rules:**
  - Material 3 theme, accent palettes and the dark-first palette move to `core/ui` `commonMain`.
  - Dynamic color (Material You) stays an Android-only option; other platforms use the selected accent.
  - `R.drawable` / `R.string` references move to `Res.drawable.*` / `Res.string.*`.
  - The `AlertDialog`, `DataResult`, `AppError` and `CancellationException` rules are unchanged.
- **Images:** store relative paths only. Coil 3 loads the reconstructed absolute path (or `File`) on all platforms. No Coil network module: the app loads local files only.
- **No users:** saved settings don't need to survive this phase. Don't add upgrade tests or keep old paths just for compatibility.
- **`commonMain` stays platform-free:** no `android.*`, `java.*` or `androidx.core` imports. `:core:ui:compileCommonMainKotlinMetadata` catches leaks.
- **Gradle hygiene:** run Gradle from `app-android/`, one invocation at a time, in the foreground, with no `--rerun-tasks`. On a Windows file lock, stop and report; never kill daemons.

## Review Focus

1. **Compose version drift.** Adding CMP 1.12.2 / Material 3 1.9.0 can pull newer `androidx.compose.*` artifacts into every Android module than BOM 2026.03.00 pins. Anything that changes must be reported and smoke-tested. (Task 2, Step 4.)
2. **Compose resources missing from the APK.** A KMP Android library only packages `composeResources` when Android resources are enabled. Otherwise icons fail at runtime with `MissingResourceException` while the build passes. (Task 3: APK content check plus device smoke test.)
3. **Launcher shortcut icons.** Four shortcut XMLs in `app` and `wardrobe` reference `core/ui` drawables (`ic_icon_washing_machine`, `ic_icon_t_shirt`, `ic_icon_coat_hanger`, `ic_icon_dresser`). These must keep resolving once `core/ui` has no Android `res/`. (Task 3.)
4. **Error snackbars showing a literal `%s`.** `error_database_constraint`, `error_validation_invalid` and `error_validation_missing` contain a placeholder, but `asUserMessage()` passes no args today. That's an existing bug, and it gets fixed when the mapping moves. (Task 3: `ErrorMappingTest`.)
5. **Theme selection regressions.** Accent, dark mode, contrast level and dynamic color must pick the same scheme as before. Status-bar icon contrast must follow dark mode. (Task 2: `AccentColorSchemeTest` plus smoke test.)

---

## PR A — Coil 3

### Task 1: Coil 2.7 → Coil 3 across the app

**Files:**
- Modify: `gradle/libs.versions.toml`: `coil = "3.6.3"`; `coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }`.
- Modify (imports `coil.compose.AsyncImage` → `coil3.compose.AsyncImage`):
  - `core/ui/.../components/ClothingItemCard.kt`
  - `features/chat/.../ChatScreen.kt`
  - `features/outfits/.../DayDetailSheet.kt`, `OutfitPreview.kt`
  - `features/recommendations/.../ui/OutfitComboCard.kt`
  - `features/stats/.../StatsActivityComponents.kt`, `StatsSharedUi.kt`
  - `features/wardrobe/.../BulkWashScreen.kt`, `ClothingDetailScreen.kt`, `ClothingFormScreen.kt`

**Interfaces:**
- Produces: `libs.coil.compose` now resolves to Coil 3, which Task 3 uses from `core/ui` `commonMain`. Models passed to `AsyncImage` stay the same types: `File`, absolute path `String`, or `Uri` in `ClothingFormScreen`. All three are supported by Coil 3 on Android with no extra mapper.

- [ ] **Step 1: Bump the catalog and swap the imports.** There's no unit test: `AsyncImage` is UI-only and has no test today.
- [ ] **Step 2: Verify**
  - `git grep -n "import coil\." -- '*.kt'` prints nothing.
  - `./gradlew assembleFullDebug assembleFossDebug` → BUILD SUCCESSFUL.
  - `./gradlew test` → BUILD SUCCESSFUL.
  - `./gradlew lint` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** with message `build: Coil 2.7 → 3.6.3`.
- [ ] **Step 4: PR A checkpoint.** Push, open the PR and wait for CI to pass. Then install `fullDebug` and smoke-test that a photo renders on each surface: the closet grid, item detail, the edit form (existing photo and newly picked photo), bulk wash, the outfit builder and preview, the journal day sheet, recommendations, stats, and chat results. Wait for the owner to merge.

---

## PR B — `core/ui` → KMP/CMP

### Task 2: `core/ui` as a KMP module: theme and preferences in `commonMain`

**Files:**
- **Build setup:**
  - Modify `gradle/libs.versions.toml`.
    - Versions: `composeMultiplatform = "1.12.2"`, `composeMultiplatformMaterial3 = "1.9.0"`.
    - Libraries: `compose-mp-runtime` (`org.jetbrains.compose.runtime:runtime`), `compose-mp-foundation` (`org.jetbrains.compose.foundation:foundation`), `compose-mp-ui` (`org.jetbrains.compose.ui:ui`), `compose-mp-material3` (`org.jetbrains.compose.material3:material3`, Material 3 version), `compose-mp-components-resources` (`org.jetbrains.compose.components:components-resources`).
    - Plugin: `compose-multiplatform` (`org.jetbrains.compose`).
  - Modify root `build.gradle.kts`: `alias(libs.plugins.compose.multiplatform) apply false`.
  - Rewrite `core/ui/build.gradle.kts`.
    - Plugins: the KMP plugin, the Android KMP library plugin, `compose.multiplatform`, `kotlin.compose`, `android.lint`.
    - `android { namespace = "com.closet.core.ui"; androidResources { enable = true }; withHostTestBuilder {} }`.
    - `jvm()`, `iosArm64()`, `iosSimulatorArm64()`.
    - **`commonMain`:** `project(":core:data")`, CMP runtime/foundation/ui/material3, Coil 3, `koin-core`, `datastore-preferences-core`, coroutines.
    - **`androidMain`:** `androidx.core.ktx`, `koin-android`.
    - **`commonTest`:** `kotlin("test")`, coroutines-test.
    - **`androidHostTest`:** the current `testImplementation` set.
- **Moves:**
  - `git mv` `core/ui/src/main/kotlin/**` to `src/commonMain/kotlin/**`, except `Theme.kt`, `IconMapper.kt`, `ErrorMapping.kt` and `components/ErrorSnackbarHost.kt`, which go to `src/androidMain/kotlin/**`.
  - `ClothingItemCard.kt` goes to `androidMain` for now. It moves to `commonMain` in Task 3, where it needs nothing Android-specific.
  - `res/` → `src/androidMain/res/`.
  - `UiKoinModuleTest.kt` → `src/androidHostTest/kotlin/**`.
- **Theme split:**
  - Create (commonMain) `theme/Theme.kt` holding `ClosetTheme` and `accentColorScheme`.
  - Create `theme/PlatformTheme.kt` with the `expect` declarations below.
  - Create the actuals: `PlatformTheme.android.kt` (androidMain), `PlatformTheme.jvm.kt`, `PlatformTheme.ios.kt`.
- **Preferences and Koin:**
  - Modify (commonMain) `preferences/PreferencesRepository.kt`.
  - Modify `di/UiKoinModule.kt`, which becomes `commonUiModule`.
  - Create `androidMain/.../di/AndroidUiKoinModule.kt` (`androidUiModule`, plus `val uiModule`), `jvmMain/.../di/JvmUiKoinModule.kt` and `iosMain/.../di/IosUiKoinModule.kt`.
- **Tests:**
  - `commonTest/.../theme/AccentColorSchemeTest.kt`
  - `jvmTest/.../preferences/PreferencesRepositoryTest.kt`
  - `jvmTest/.../di/JvmUiKoinModuleTest.kt`

**Interfaces:**
- Consumes: `createPreferencesDataStore(path: String)`, `androidPreferencesPath(filesDir, name)`, `jvmPreferencesPath(directory, name)` and `iosPreferencesPath(name)` (all in `core/data`), plus `jvmDataModule(dataDirectory: File)` and `iosDataModule`.
- Produces (commonMain): `@Composable fun ClosetTheme(accent: ClosetAccent = ClosetAccent.Amber, darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit)`. The signature is unchanged, so `MainActivity` and the features don't change.
- Produces (commonMain): `internal fun accentColorScheme(accent: ClosetAccent, darkTheme: Boolean, contrastLevel: Float): ColorScheme`, with today's thresholds (≥ 1f high, ≥ 0.5f medium).
- Produces `expect` declarations:
  - `@Composable internal fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?`. Android returns the dynamic scheme on API 31+ and `null` below that; JVM and iOS always return `null`.
  - `@Composable internal fun rememberContrastLevel(): Float`. Android keeps today's `UiModeManager` listener code; the others return `0f`.
  - `@Composable internal fun SystemBarAppearance(darkTheme: Boolean)`. Android keeps today's `WindowCompat` `SideEffect`, guarded by `isInEditMode`; the others do nothing.
  - `ClosetTheme` uses `platformDynamicColorScheme(darkTheme)` when `dynamicColor` is true and the result is non-null, and `accentColorScheme(...)` otherwise.
- Produces `class PreferencesRepository(private val dataStore: DataStore<Preferences>)`. Its members are unchanged.
- **Koin:**
  - `commonUiModule` binds `single { PreferencesRepository(get(named("closet_prefs"))) }`.
  - `androidUiModule` binds `single(named("closet_prefs")) { createPreferencesDataStore(androidPreferencesPath(androidContext().filesDir, "closet_prefs")) }`.
  - `val uiModule = module { includes(commonUiModule, androidUiModule) }` in androidMain. The name is kept, so `allModules` and the feature tests don't change.
  - `fun jvmUiModule(dataDirectory: File): Module` and `val iosUiModule` bind the same qualified DataStore using their path helpers, then include `commonUiModule`.

- [ ] **Step 1: Write the failing tests**
  - **`AccentColorSchemeTest`:**
    - `accentColorScheme(Amber, darkTheme = true, contrastLevel = 1f) === amberHighContrastDarkScheme`
    - `(Amber, true, 0.5f) === amberMediumContrastDarkScheme`
    - `(Amber, true, 0f) === amberDarkScheme`
    - `(Sky, false, 1f) === skyHighContrastLightScheme`
    - `(Rose, false, 0.49f) === roseLightScheme`
    - every `ClosetAccent` with `(false, 0f)` returns its `*LightScheme`
  - **`PreferencesRepositoryTest`** (temp-file DataStore):
    - the defaults are `getAccent().first() == ClosetAccent.Amber` and `getDynamicColor().first() == false`;
    - `setAccent(Coral)` reads back `Coral`;
    - `setDynamicColor(true)` reads back `true`;
    - an unknown stored accent name falls back to `Amber`: write `stringPreferencesKey("accent")` = `"Teal"` directly to the DataStore;
    - `setLastHandledBatchId("b1").isSuccess` and `lastHandledBatchId.first() == "b1"`.
  - **`JvmUiKoinModuleTest`:** `module { includes(jvmDataModule(tmp), jvmUiModule(tmp)) }.verify()` passes, and `startKoin` resolves `PreferencesRepository` from the full graph.
- [ ] **Step 2: Run them to verify they fail.** Run `./gradlew :core:ui:jvmTest` → compile FAIL.
- [ ] **Step 3: Implement the module conversion, the theme split, the preferences and the Koin split.**
- [ ] **Step 4: Verify**
  - `./gradlew :core:ui:jvmTest :core:ui:testAndroidHostTest :core:ui:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
  - `./gradlew test` → BUILD SUCCESSFUL; `AppKoinGraphTest` passes in both flavors.
  - `./gradlew lint assembleFullDebug assembleFossDebug` → BUILD SUCCESSFUL.
  - Run `./gradlew :app:dependencyInsight --configuration fullDebugRuntimeClasspath --dependency androidx.compose.material3:material3` and do the same for `androidx.compose.ui:ui`. Paste the resolved versions into the report, compared with what `main` resolves. Do the klib ABI check from `kmp-toolchain.md` for the new CMP artifacts.
- [ ] **Step 5: Commit** with message `refactor(ui): core/ui as a KMP module; theme and preferences in commonMain (KMP Phase 4)`.

### Task 3: Compose resources; components and mappers to `commonMain`; phase checkpoint

**Files:**
- **Resources:**
  - `git mv` `core/ui/src/androidMain/res/drawable/*.xml` → `core/ui/src/commonMain/composeResources/drawable/`.
  - `res/values/strings.xml` → `composeResources/values/strings.xml`, with the placeholders written as `%1$s`.
  - Delete the emptied `androidMain/res`.
- **Shortcut icons:** copy `ic_icon_washing_machine.xml`, `ic_icon_t_shirt.xml` and `ic_icon_coat_hanger.xml` into `app/src/main/res/drawable/`, and `ic_icon_dresser.xml` into `features/wardrobe/src/main/res/drawable/`. The shortcut XMLs keep their `@drawable/...` names.
- **Build:** modify `core/ui/build.gradle.kts`.
  - Add `compose.resources { publicResClass = true; packageOfResClass = "com.closet.core.ui.resources"; generateResClass = always }`.
  - `commonMain`: `api(libs.compose.mp.components.resources)`. It's `api` so feature modules get `painterResource(DrawableResource)`, `stringResource` and `getString` without their own catalog entry.
- **Move to commonMain:** `IconMapper.kt`, `ErrorMapping.kt` and `components/ClothingItemCard.kt` (`git mv`). Split `components/ErrorSnackbarHost.kt`: `UserMessageSnackbarEffect` and `StringErrorSnackbarEffect` go to commonMain; `ResErrorSnackbarEffect` stays in androidMain, because it takes Android `@StringRes` ids from feature modules.
- **Consumers:**
  - `app/.../navigation/ClosetNavGraph.kt`: `TopLevelRoute.iconRes: Int` becomes `icon: DrawableResource`.
  - `features/wardrobe/.../MultiSelectSheet.kt`, `ClothingDetailComponents.kt` and `ClothingDetailScreen.kt`: `iconResId: Int?` becomes `icon: DrawableResource?`.
  - `features/wardrobe/.../ClothingFormViewModel.kt`, plus `features/wardrobe/src/main/res/values/strings.xml`: add `error_database_query` and `error_unexpected` with the same text, so the form's `@StringRes` error field points at wardrobe's own `R`.
- **Docs:** `app-android/CLAUDE.md`, covering the `core/ui` layout, the `Res` usage rule, Coil 3, and the verification command with the `:core:ui` tasks.
- **Tests:** `commonTest/.../util/IconMapperTest.kt`, `commonTest/.../util/ErrorMappingTest.kt`.

**Interfaces:**
- Consumes: Task 2's module layout and `uiModule`.
- Produces: the generated `com.closet.core.ui.resources.Res`, with `Res.drawable.ic_*` and `Res.string.error_*`.
- Produces: `object IconMapper { fun getIconResource(iconName: String?): DrawableResource? }`. The name-matching table and normalization (`lowercase()`, `-` → `_`) are unchanged.
- Produces: `class UserMessage(val res: StringResource, val args: List<Any> = emptyList())` as a `data class`. A `List` replaces the array, so the hand-written `equals`/`hashCode` go away.
- Produces: `fun AppError.asUserMessage(): UserMessage`, now passing the placeholder args:
  - `ConstraintViolation` → `args = listOf(message)`
  - `InvalidInput` → `listOf(message)`
  - `MissingField` → `listOf(fieldName)`
  - all other cases → no args
- Produces: `@Composable fun UserMessageSnackbarEffect(messages: Flow<UserMessage>, snackbarHostState: SnackbarHostState)`, which resolves text with Compose resources' `getString(res, *args.toTypedArray())`.

- [ ] **Step 1: Write the failing tests**
  - **`IconMapperTest`:**
    - `getIconResource("t-shirt") == Res.drawable.ic_icon_t_shirt`
    - `getIconResource("Plaid/Tartan") == Res.drawable.ic_pattern_plaid_tartan`
    - `getIconResource("ic_icon_sun") == Res.drawable.ic_icon_sun`
    - `getIconResource("unknown") == null`
    - `getIconResource(null) == null`
  - **`ErrorMappingTest`:**
    - `AppError.DatabaseError.NotFound().asUserMessage() == UserMessage(Res.string.error_database_not_found)`
    - `ConstraintViolation("UNIQUE brand.name").asUserMessage() == UserMessage(Res.string.error_database_constraint, listOf("UNIQUE brand.name"))`
    - `MissingField("name").asUserMessage().args == listOf("name")`
    - `Unexpected(null).asUserMessage() == UserMessage(Res.string.error_unexpected)`
- [ ] **Step 2: Run them to verify they fail.** Run `./gradlew :core:ui:jvmTest` → compile FAIL.
- [ ] **Step 3: Implement**
  - Move the resources and components, update the consumers, copy the shortcut drawables, and add the wardrobe strings.
  - Confirm the following prints nothing: `git grep -nE "core\.ui\.R\b|CoreUiR|CoreR\." -- '*.kt'`.
- [ ] **Step 4: Verify**
  - Run the full verification command, now including `:core:ui:jvmTest :core:ui:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
  - `unzip -l app/build/outputs/apk/full/debug/*.apk | grep "composeResources/com.closet.core.ui.resources/drawable/ic_icon_t_shirt"` prints one entry.
  - Do the same check for the foss APK.
- [ ] **Step 5: Update `CLAUDE.md`**
  - **Design system:** theme tokens live in `core/ui/src/commonMain/.../theme/`, and dynamic color is Android-only in `PlatformTheme.android.kt`.
  - **Resources:** shared drawables and strings live in `core/ui/src/commonMain/composeResources/` and are referenced as `com.closet.core.ui.resources.Res`. Feature modules keep their own Android `R` until Phase 5. Launcher shortcut icons are copies in `app/`/`wardrobe` `res/`.
  - **Images:** Coil 3 (`coil3.compose.AsyncImage`).
- [ ] **Step 6: Commit** with message `refactor(ui): Compose resources; core/ui components and mappers in commonMain (KMP Phase 4)`.
- [ ] **Step 7: PR B checkpoint.** Push, open the PR, and wait for `android` and `ios-compile` to pass. Then smoke-test on device, on both flavors:
  - the bottom-nav icons;
  - the season, occasion and pattern icons on item detail and in the multi-select sheets;
  - the washing-machine icon on item detail;
  - all four launcher shortcuts (long-press the app icon);
  - the accent change, dark/light, and dynamic color on/off;
  - status-bar icons readable in both modes;
  - an error snackbar shows real text with no `%1$s`;
  - images still render (closet grid, outfit builder).

  Wait for the owner to merge.
