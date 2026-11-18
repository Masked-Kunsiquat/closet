# KMP Phase 6: shared root (`App()` + `NavHost` in commonMain) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the KMP migration. A new multiplatform `shared` module holds the root `App()` composable (theme, Scaffold, bottom bar, `NavHost`), and every feature's navigation moves to `commonMain`. That reduces `app` to Android start-up: Koin, `MainActivity`, flavors, launcher shortcuts and backup/restore. Android behaviour is unchanged. `shared` declares an iOS framework export, but no iOS app shell is created (spec non-goal).

**Architecture:**
- **One PR, three tasks.**
- **Navigation library (owner decision, 2026-11-18):** JetBrains `org.jetbrains.androidx.navigation:navigation-compose` **2.9.2**, the multiplatform port of what `app` uses today.
  - Its Android variant depends on `androidx.navigation:navigation-compose` **2.9.7**, exactly the version in the catalog, so Android resolves to the same library as now.
  - It uses the same `androidx.navigation*` package names, so moved files keep their imports.
  - Navigation 3 was considered and deferred to #86 (adaptive layouts).
- **Task 1 (features):**
  - Add the catalog entry.
  - Move the 7 feature `*Navigation.kt` files from androidMain to commonMain.
  - Replace the per-platform route readers (`OutfitBuilderArgs.*`, `WardrobeArgs.*`: 8 files) with the common `SavedStateHandle.toRoute<T>()`, keeping each reader's current fallback semantics.
- **Task 2 (`shared`):**
  - A new KMP + CMP module `:shared` with `App()`, the bottom bar, the top-level tab list, the `NavHost` (moved from `app/.../navigation/ClosetNavGraph.kt`) and the theme/preference reading moved from `MainActivity`.
  - Shortcut intents become a common `AppShortcut` type; `app` maps `Intent` → `AppShortcut`.
  - Android-only destinations (backup/restore) plug in through a `NavGraphBuilder` parameter.
  - The iOS framework export is declared.
- **Task 3 (`app` + docs):**
  - `MainActivity` hosts `App()`. `ClosetNavGraph.kt` is deleted. The backup destination is passed in.
  - CLAUDE.md, the migration spec status, the CHANGELOG, and the iOS check aggregation (automatic for KMP modules).

**Tech Stack:** Kotlin 2.4.21, AGP 9.1.0 KMP library, CMP 1.12.2 / Material 3 1.9.0, JetBrains navigation-compose 2.9.2 (→ androidx 2.9.7 on Android), JetBrains lifecycle 2.10.0, Koin 4.2.2, kotlinx-serialization.

**Spec:** `docs/superpowers/specs/2026-11-14-kmp-migration-design.md`. The relevant parts are "Target module layout" (`shared/`) and phase 6 "Shared root". Tracked in #97.

## Global Constraints

- **Exit criteria:**
  - `assembleFullDebug` and `assembleFossDebug` succeed.
  - All unit tests pass, including every module's `jvmTest`.
  - The foss APK has no GMS classes.
  - A manual smoke test passes on the owner's **test** device.
  - CI `ios-compile` is green, and it now includes `:shared`.
- **Targets:** `:shared` is `android`, `jvm()`, `iosArm64()` and `iosSimulatorArm64()`, like the features. iOS targets declare `binaries.framework { baseName = "Shared"; isStatic = true }`. No `iosApp`/`desktopApp` modules.
- **Versions:**
  - The only new library is JetBrains `navigation-compose` 2.9.2 (catalog `jetbrains-navigation-compose`, version ref `jetbrainsNavigation = "2.9.2"`), with a catalog comment that it pairs with `androidxNavigation` 2.9.7.
  - `./gradlew :app:dependencyInsight --configuration fullDebugRuntimeClasspath --dependency androidx.navigation:navigation-compose` must show **2.9.7**.
  - iOS klib ABI ≤ **2.4.0**.
  - Declare CMP libraries by catalog coordinates, never `compose.*` accessors.
- **`commonMain` stays platform-free:** no `android.*`, `java.*`, `androidx.core`, `androidx.activity`, `androidx.work` or `timber` imports. `androidx.navigation.*` is now allowed in commonMain; it comes from the JetBrains artifact.
- **Public names:** keep every route class's name, package and `@Serializable` shape. The extension function names (`chatScreen`, `navigateToRecommendations` and so on) keep their names and signatures.
- **Resources:** the bottom-bar labels (`nav_closet`, `nav_outfits`, `nav_journal`, `nav_stats`, `nav_chat`) move from `app` `res/values/strings.xml` to `shared` `composeResources`, with an internal `Res` package `com.closet.shared.resources`. Positional placeholders only, if any are needed.
- **No users:** no compat shims.
- **Gradle hygiene:**
  - Run from `app-android/`, one invocation at a time, in the foreground, with no `--rerun-tasks`.
  - Never stop or kill daemons.
  - If a task sits with no output for ~5 min, or fails with "Failed to clean up output files", stop and report.
  - iOS compiles locally with `-Pkotlin.native.enableKlibsCrossCompilation=true`, main and test.
  - Kotlin/Native rejects `()`, `.`, `:`, `;`, `/`, `<>`, `[]` and `\` in backtick test names.

## Review Focus

1. **Launcher shortcuts route exactly as today.**
   - Quick add → `AddClothingDestination(openCamera = true)`.
   - Log fit → `OutfitBuilderDestination()`.
   - Laundry day → `BulkWashDestination`.
   - Category → `ClosetDestination(initialCategoryId = id)`, where an extra of −1 or a missing extra means `null`.
   - Each one uses `popUpTo(startDestination) { inclusive = false }` and `launchSingleTop = true`, and the shortcut is consumed afterwards, so recomposition and rotation don't re-navigate. Shortcut IDs, actions and extras (`ShortcutActions`) stay in `app`.
   - **Tests (Task 2):**
     - `shared` commonTest for a pure `AppShortcut.destination(): Any` mapping over all four cases;
     - `app` androidHostTest for `Intent` → `AppShortcut?`: each action, the category extra present, the extra absent or −1, and an unknown action → `null`.
2. **Route arguments survive the common `toRoute`.**
   - `OutfitBuilderDestination(preselectedItemIds: List<Long>)` still preselects its items.
   - `ClosetDestination.initialCategoryId`, `ClothingDetailDestination.itemId`, `EditClothingDestination.itemId` and `AddClothingDestination.openCamera` are read with the same fallbacks the actuals use today: null, null, false, and a throw for detail.
   - **Test (Task 1):** jvmTest per reader, building the `SavedStateHandle` the way navigation does for that route and asserting the value. If no common helper can build a route-encoded `SavedStateHandle` on JVM, the reviewer accepts an androidHostTest with `androidx.navigation:navigation-testing`'s `SavedStateHandle(route)` instead. The plan's preference is jvmTest.
3. **Bottom bar and tab state.**
   - The bar shows only on the 5 top-level destinations.
   - Tab switches keep `popUpTo(start) { saveState = true }`, `launchSingleTop` and `restoreState`.
   - Labels and icons are unchanged. (Device smoke.)
4. **Backup/restore is still reachable.** Settings → Backup & Restore opens the Android-only backup screen through the `platformDestinations` hook. (Device smoke.) Restore's restart path is unchanged by this phase (the backup screen, `RestoreRepository` and the restart stay in `app` untouched), so the ordinary smoke test never runs a restore; restart verification is deferred to the next PR that touches backup, using the test device and a known demo backup (as done for #102).
5. **Theme.** Accent and dynamic color still follow the preferences, with dynamic color applied on Android 12+ through the existing core/ui actual. (Device smoke, by switching the accent in Settings.)

---

### Task 1: Feature navigation to `commonMain`; common `toRoute` replaces the route-reader actuals

**Files:**
- **`gradle/libs.versions.toml`:** add `jetbrainsNavigation = "2.9.2"` (with the pairing comment) and `jetbrains-navigation-compose = { group = "org.jetbrains.androidx.navigation", name = "navigation-compose", version.ref = "jetbrainsNavigation" }`.
- **Each feature's `build.gradle.kts`** (chat, outfits, recommendations, settings, stats, wardrobe): add `implementation(libs.jetbrains.navigation.compose)` to `commonMain` and remove `libs.androidx.navigation.compose` from `androidMain`.
- **`git mv` androidMain → commonMain:** `ChatNavigation.kt`, `JournalNavigation.kt`, `OutfitsNavigation.kt`, `RecommendationNavigation.kt`, `SettingsNavigation.kt`, `StatsNavigation.kt`, `WardrobeNavigation.kt`. Any Android-only call inside them stays in androidMain behind the smallest possible seam; report any you find.
- **Delete** `OutfitBuilderArgs.kt` and its `.android`/`.jvm`/`.ios` actuals, and `WardrobeArgs.kt` and its three actuals. Their callers use `savedStateHandle.toRoute<…>()` directly, wrapped in the same try/catch fallbacks the Android actuals have today.
- **Tests:** the route-reader tests from Review Focus 2 (jvmTest), for example `features/outfits/src/jvmTest/.../OutfitBuilderRouteTest.kt` and `features/wardrobe/src/jvmTest/.../WardrobeRouteTest.kt`.

**Interfaces:** the route classes and extension functions are unchanged. The new catalog alias is `libs.jetbrains.navigation.compose`.

- [ ] **Step 1:** Write the route-reader tests.
- [ ] **Step 2:** Run `./gradlew :features:outfits:jvmTest :features:wardrobe:jvmTest` and expect a failure.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Verify:
  - `./gradlew test` passes, including `AppKoinGraphTest` and `GmsKoinTest`.
  - Every feature's `compileCommonMainKotlinMetadata` and iOS simulator main and test compiles pass.
  - The dependencyInsight check (Global Constraints) shows 2.9.7.
  - `./gradlew assembleFullDebug` passes.
  - No `*Navigation.kt` is left under `features/*/src/androidMain`.
- [ ] **Step 5:** Commit: `refactor(features): navigation in commonMain on JetBrains navigation-compose; common toRoute replaces route-reader actuals (KMP Phase 6)`.

### Task 2: `:shared` module: `App()`, bottom bar, `NavHost`, `AppShortcut`, iOS framework export

**Files:**
- **Create `shared/build.gradle.kts`** by copying `features/chat/build.gradle.kts`'s shape.
  - Namespace `com.closet.shared`, with `androidResources { enable = true }`.
  - The `compose.resources` package is `com.closet.shared.resources`.
  - The iOS targets declare the framework (Global Constraints).
  - **Dependencies:** `core:data`, `core:ui`, every `features:*` module, `jetbrains-navigation-compose`, Koin compose, and lifecycle.
  - Apply the Skiko test script.
  - Add `include(":shared")` to `settings.gradle.kts`.
- **Create `shared/src/commonMain/kotlin/com/closet/shared/App.kt`:**
  ```kotlin
  @Composable
  fun App(
      modifier: Modifier = Modifier,
      navController: NavHostController = rememberNavController(),
      pendingShortcut: StateFlow<AppShortcut?> = MutableStateFlow(null),
      onShortcutConsumed: () -> Unit = {},
      onNavigateToBackup: (NavHostController) -> Unit = {},
      platformDestinations: NavGraphBuilder.(NavHostController) -> Unit = {},
  )
  ```
  - **Theme:** reads accent and dynamic color from `PreferencesRepository` via `koinInject()` and wraps everything in `ClosetTheme`, as `MainActivity` does today.
  - **Body:** the Scaffold, bottom bar, top-level route list and the `NavHost` with every destination, moved from `ClosetNavGraph.kt`. Settings' `onNavigateToBackup` calls the parameter, and `platformDestinations(navController)` is invoked inside the `NavHost` builder.
- **Create `shared/src/commonMain/.../AppShortcut.kt`:**
  - `sealed interface AppShortcut { data object QuickAdd; data object LogFit; data object LaundryDay; data class Category(val categoryId: Long?) }`
  - `internal fun AppShortcut.destination(): Any`, which maps per Review Focus 1.
- **Resources:** the five `nav_*` strings in `shared/src/commonMain/composeResources/values/strings.xml`.
- **Tests:**
  - `shared/src/commonTest/.../AppShortcutTest.kt` (Review Focus 1);
  - `shared/src/jvmTest/.../SharedStringsTest.kt`, with one label via `getString`.

**Interfaces:** produces `com.closet.shared.App`, `AppShortcut`, and the `:shared` project. It consumes the Task 1 feature navigation (now common).

- [ ] **Step 1:** Write the tests.
- [ ] **Step 2:** Run `./gradlew :shared:jvmTest` and expect a failure.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Verify:
  - `./gradlew :shared:jvmTest :shared:compileCommonMainKotlinMetadata` passes.
  - `./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64 :shared:linkDebugFrameworkIosSimulatorArm64 -Pkotlin.native.enableKlibsCrossCompilation=true` passes. If linking can't run on Windows, report it and keep the compiles.
  - `app` is untouched in this task, so it still builds against `ClosetNavGraph`.
- [ ] **Step 5:** Commit: `feat(shared): :shared module with App(), bottom bar, NavHost and AppShortcut; iOS framework export (KMP Phase 6)`.

### Task 3: `app` hosts `App()`; docs; PR checkpoint

**Files:**
- **`app/build.gradle.kts`:** add `implementation(project(":shared"))`. Drop `androidx.navigation.compose` if nothing in `app` still needs it. The backup navigation does, so it switches to `libs.jetbrains.navigation.compose` (it resolves to the same androidx artifact).
- **`MainActivity.kt`:**
  - `setContent { App(modifier = Modifier.fillMaxSize(), pendingShortcut = …, onShortcutConsumed = …, onNavigateToBackup = { it.navigateToBackup() }, platformDestinations = { nav -> backupScreen(…) }) }`.
  - The `Intent` flow becomes a `StateFlow<AppShortcut?>` via a new `app/.../shortcuts/ShortcutIntents.kt` `fun Intent.toAppShortcut(): AppShortcut?`. It uses `ShortcutActions`, maps a category extra of −1 to null, and returns null for unknown actions.
  - The theme and preference reading move out, because `App()` owns them now.
- **Delete** `app/.../navigation/ClosetNavGraph.kt` and the `nav_*` strings from `app` `res/values/strings.xml`.
- **Tests:** `app/src/test/.../shortcuts/ShortcutIntentsTest.kt` (Review Focus 1), run as Robolectric-free host tests if `Intent` can be built. Otherwise mock it with mockk, the same way existing `app` tests handle Android types.
- **Docs and changelog:**
  - **CLAUDE.md:**
    - module list (`shared/` — KMP root: `App()`, bottom bar, `NavHost`, `AppShortcut`; iOS framework export; `app/` — Android start-up, flavors, launcher shortcuts, backup/restore);
    - "Navigation" section: destinations live in each feature's commonMain; the root `NavHost` is in `shared`; the JetBrains navigation-compose pairing;
    - "Key files": `ClosetNavGraph.kt` → `shared/.../App.kt`;
    - the dependency line (`app` → `shared` → `features/*` → …);
    - the verification command (add `:shared:jvmTest :shared:compileCommonMainKotlinMetadata`).
  - **Migration spec:** a status line saying phases 0–6 are complete, with iOS/desktop deferred per the spec.
  - **CHANGELOG** `[Unreleased]` → `### Changed`: "**The app's navigation and root screen are Kotlin Multiplatform** (KMP Phase 6): a shared `App()` with the bottom bar and navigation graph. The Kotlin Multiplatform migration is complete; Android behaviour is unchanged."

- [ ] **Step 1:** Write `ShortcutIntentsTest`.
- [ ] **Step 2:** Run `./gradlew :app:testFullDebugUnitTest --tests "*ShortcutIntents*"` and expect a failure.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Verify:
  - The full CLAUDE.md verification command, including `:shared`, passes (in separate invocations if needed).
  - The `iosCompileCheck` aggregate passes with cross-compilation; `:shared` is included automatically.
  - The dependencyInsight check shows 2.9.7.
  - `unzip -l` on both APKs lists `composeResources/com.closet.shared.resources/`.
- [ ] **Step 5:** Commit: `refactor(app): MainActivity hosts the shared App(); app reduced to Android start-up (KMP Phase 6)`.
- [ ] **Step 6: PR checkpoint** (the controller does this).
  - Push `feat/kmp-phase6-shared-root`, open the PR (it closes #97), and wait for CI.
  - Smoke-test on the **test device only**, both flavors, with the demo wardrobe:
    - **Bottom bar:** all 5 tabs switch, each tab keeps its scroll and back stack when you come back, and the bar hides on detail screens.
    - **Launcher shortcuts:** long-press the app icon, then Quick add, Log fit and Laundry day each land on the right screen; back from it returns to Closet; rotate after landing and confirm it doesn't re-navigate.
    - **Pinned category shortcut:** check whether one already exists; don't create one.
    - **Outfit builder preselection:** recommendations "Log it" opens the builder with the items preselected.
    - **Item routes:** item detail opens, Edit opens the form with data, and Add opens an empty form.
    - **Backup:** Settings → Backup & Restore opens; back returns. Don't run a restore.
    - **Theme:** change the accent in Settings and the theme updates.
    - **Foss:** no crash.

    Wait for the owner to merge.
