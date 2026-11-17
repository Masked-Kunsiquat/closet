# KMP Phase 5c: `features/outfits` → KMP/CMP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `features/outfits` into a Kotlin Multiplatform + Compose Multiplatform module, with Android behavior unchanged. That covers the outfits gallery, the outfit builder, the wardrobe picker, the OOTD journal (calendar, day sheet, log editing, forecast sheet), the four ViewModels and the Koin module, all in `commonMain`.

**Architecture:**
- One PR, four tasks. Stats and settings had three; this module is the biggest so far (≈3.6k lines).
- **Task 1 (build):** replace the `skikoRuntimeForHost()` copies in stats and settings with one shared Gradle script. This is the "third copy" the settings PR deferred.
- **Task 2 (core):** add the two platform pieces outfits needs. Both are reusable by wardrobe later.
  - `core/data`: a common `ShortcutUsageReporter`. It replaces `ShortcutManagerCompat.reportShortcutUsed` in `JournalViewModel`.
  - `core/ui`: common localized date labels (`expect`). They replace `java.time` `DateTimeFormatter` / `getDisplayName` in four screens.
- **Task 3 (`features/outfits` module):**
  - Convert the build to KMP.
  - Move the four ViewModels and the pure logic to `commonMain`:
    - `java.time` → `kotlinx.datetime` (`LocalDate`, `YearMonth`, `DayOfWeek`)
    - `File` → `ImagePathResolver` (`String?`)
    - Timber → Kermit
    - `toRoute` behind a small `expect`
  - Move the `@Serializable` route classes to `commonMain`.
  - The screens still use `R`, so they stay in `androidMain`.
- **Task 4:** strings to Compose resources; every `Icons.*` to Material Symbols; screens to `commonMain`.
- **Stays in `androidMain`:**
  - the `NavGraphBuilder` extensions (`OutfitsNavigation.kt`, `JournalNavigation.kt`): they move with the `NavHost` in Phase 6;
  - `@Preview`s, split into `OutfitsPreviews.kt` if any exist;
  - the `toRoute` actual.

**Tech Stack:**
- Kotlin 2.4.21, AGP 9.1.0 KMP library.
- CMP 1.12.2, Material 3 1.9.0, Compose resources.
- kotlinx-datetime **0.8.0**, which has `YearMonth`.
- Koin 4.2.2 (`koin-compose-viewmodel`), JetBrains lifecycle 2.10.0 (common `SavedStateHandle`), Coil 3.6.3, Kermit 2.2.0.

**Spec:** `docs/superpowers/specs/2026-11-14-kmp-migration-design.md`, Phase 5. Models:
- `plans/2026-11-16-kmp-phase5-settings.md`: platform hooks, Koin module in commonMain with a JVM graph verify;
- `plans/2026-11-16-kmp-phase5-stats.md`: `ImagePathResolver`, Vico, `StatsColors`.

Gotchas are in `docs/superpowers/notes/kmp-toolchain.md`, the CLAUDE.md "Resources" paragraph, and `features/settings/build.gradle.kts`. Copy that build file's shape.

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
- **`commonMain` stays platform-free:** no `android.*`, `java.*`, `androidx.core`, `androidx.navigation`, `androidx.activity` or `timber` imports. `:features:outfits:compileCommonMainKotlinMetadata` catches leaks.
- **Resources:**
  - Positional placeholders only.
  - `'`, not `\'`.
  - A literal `%` is written `%`, not `%%`. The settings test proved this.
  - `material-icons-extended` is not used in `features/outfits` after this PR.
- **Icons:**
  - Material Symbols **Outlined**, wght 400, GRAD 0, opsz 24.
  - FILL 0 by default. Use FILL 1 only where the current icon is `Icons.Filled.*` with a filled meaning: the filled star (OOTD) and the filled check-circle.
  - `android:fillColor="#FF000000"`.
  - Add `android:autoMirrored="true"` only for the icons the current code takes from `Icons.AutoMirrored.*`.
  - Name them `ic_symbol_<name>`, with a `_filled` suffix for FILL 1.
  - Reuse the existing `ic_symbol_arrow_back` / `ic_symbol_chevron_right`.
- **Dates:**
  - Stored dates stay ISO `YYYY-MM-DD` strings.
  - Week starts Monday, as today.
  - "Today" comes from `Clock.System.todayIn(TimeZone.currentSystemDefault())`.
  - Displayed labels come from the Task 2 date-label functions in the default locale, never kotlinx-datetime's English-only `MonthNames`.
- **Logging:** Timber → Kermit (`Logger.withTag("<ClassName>")`), with the same levels.
- **No users:** no compat shims.
- **Gradle hygiene:**
  - Run from `app-android/`, one invocation at a time, in the foreground, with no `--rerun-tasks`.
  - Never stop or kill daemons.
  - If a task sits with no output for ~5 min, or fails with "Failed to clean up output files" (a Windows lock), stop and report.

## Review Focus

1. **Calendar month math and "future" guards.** The calendar must not page past the current month. `navigateToDate` and `logOutfitOnDate` must ignore future dates and unparseable strings. `previousMonth` must cross a year boundary correctly (2026-01 → 2025-12). All of this moves from `java.time.YearMonth` to `kotlinx.datetime.YearMonth`. (Task 3: `JournalViewModelTest` + `JournalDatesTest`.)
2. **Localized date labels.** The day-sheet title (`EEEE, MMMM d`), picker title (`MMMM d`), calendar header (`MMMM yyyy`), forecast day (`EEE d`) and weekday initials must read the same as before in the default locale, not in English everywhere. (Task 2: `DateLabelsJvmTest` with `Locale.US` and `Locale.FRANCE`.)
3. **Preselected items in the outfit builder.** "Log these" from recommendations and chat navigates with `OutfitBuilderDestination(preselectedItemIds = …)`. It must still preselect them; edit mode (`outfitId`) must still load the outfit. Both move behind the `toRoute` `expect`. (Task 3: `OutfitBuilderViewModelTest` on JVM via the plain-key actual, plus the device smoke test on Android.)
4. **Shortcut usage reporting.** Logging an outfit from the journal must still report `"log_fit"` on Android; JVM/iOS must do nothing. (Task 2 binding test + Task 3 `JournalViewModelTest` verifying the reporter call.)
5. **Weather icons and temperature.** The forecast sheet and day sheet map each `WeatherCondition` to an icon. The new Material Symbols set must cover all 11 conditions with no missing drawable, and `toDisplayTemp` must stay unchanged. (Task 4: `WeatherConditionIconTest` iterating `WeatherCondition.entries`.)

---

### Task 1: One shared Skiko JVM-test runtime script

**Files:**
- **Create** `gradle/skiko-jvm-test-runtime.gradle.kts`.
  - It adds the host's `skiko-awt-runtime-*` catalog library to `jvmTestImplementation`.
  - It looks the library up with `extensions.getByType<VersionCatalogsExtension>().named("libs").findLibrary(...)`.
  - The OS/arch mapping and the `error(...)` on unmapped hosts are copied unchanged from `features/stats/build.gradle.kts`.
- **Modify** `features/stats/build.gradle.kts` and `features/settings/build.gradle.kts`:
  - delete `fun Project.skikoRuntimeForHost()` and its `jvmTest` `implementation(skikoRuntimeForHost())` line;
  - add `apply(from = rootProject.file("gradle/skiko-jvm-test-runtime.gradle.kts"))` after the `kotlin { }` block.
- **Modify** `CLAUDE.md`, in the Resources paragraph: JVM tests that call Compose resources apply `gradle/skiko-jvm-test-runtime.gradle.kts`.

**Interfaces:**
- Produces: the script applies to any module that has a `jvmTest` source set (`jvmTestImplementation` configuration).

- [ ] **Step 1: Write the failing test.** None new: the existing `StatsStringsTest` and `SettingsStringsTest` are the tests. They fail without a Skiko runtime (`LibraryLoadException`), which proves the script supplies it.
- [ ] **Step 2: Prove the old path is gone.** Run `git grep -n "skikoRuntimeForHost" -- features` and check that it prints nothing after the edit.
- [ ] **Step 3: Implement** the script and the two module edits.
- [ ] **Step 4: Verify**
  - `./gradlew :features:stats:jvmTest :features:settings:jvmTest` → BUILD SUCCESSFUL, with `StatsStringsTest` and `SettingsStringsTest` passing.
  - `./gradlew help --configuration-cache` → no configuration-cache problems reported for the script.
- [ ] **Step 5: Commit** with message `build: one shared Skiko JVM-test runtime script (KMP Phase 5)`.

### Task 2: `ShortcutUsageReporter` (`core/data`) and date labels (`core/ui`)

**Files:**
- **Create** `core/data/src/commonMain/.../shortcuts/ShortcutUsageReporter.kt` and `NoOpShortcutUsageReporter`, beside it, in commonMain.
- **Create** `core/data/src/androidMain/.../shortcuts/AndroidShortcutUsageReporter.kt`. It calls `ShortcutManagerCompat.reportShortcutUsed(context, id)`.
- **Modify** `androidDataModule` to bind the Android implementation; `jvmDataModule` / `iosDataModule` bind `NoOpShortcutUsageReporter`.
- **Create** `core/ui/src/commonMain/.../format/DateLabels.kt` (expect), plus `DateLabels.android.kt` and `DateLabels.jvm.kt`, which share a body: a `java.time` bridge via `toJavaLocalDate()`, using `Locale.getDefault()`. Also create `DateLabels.ios.kt`, using `NSDateFormatter` with `NSLocale.currentLocale` and the same patterns.
- **Tests:**
  - `core/data/src/jvmTest/...`: the JVM module resolves `ShortcutUsageReporter` to the no-op;
  - `core/data/src/androidHostTest/...`: `SingletonParityTest` or the module test resolves the Android implementation;
  - `core/ui/src/jvmTest/.../format/DateLabelsJvmTest.kt`.

**Interfaces:**
- Produces, in `core/data` commonMain:
  - `fun interface ShortcutUsageReporter { fun reportUsed(shortcutId: String) }`
  - `object NoOpShortcutUsageReporter : ShortcutUsageReporter`
- Produces, in `core/ui` commonMain package `com.closet.core.ui.format`:
  - `expect fun formatDayMonthLong(date: LocalDate): String`, pattern `EEEE, MMMM d`
  - `expect fun formatMonthDay(date: LocalDate): String`, pattern `MMMM d`
  - `expect fun formatMonthYear(yearMonth: YearMonth): String`, pattern `MMMM yyyy`
  - `expect fun formatWeekdayDay(date: LocalDate): String`, pattern `EEE d`
  - `expect fun weekdayNarrow(day: DayOfWeek): String`, the `TextStyle.NARROW` display name

  The types are `kotlinx.datetime.LocalDate` / `YearMonth` / `DayOfWeek`. Android/JVM use `Locale.getDefault()`. iOS uses `NSDateFormatter.dateFormat` with the same pattern; for the narrow weekday it uses `veryShortStandaloneWeekdaySymbols`, Monday-first index mapping.

- [ ] **Step 1: Write the failing tests**
  - **`DateLabelsJvmTest`**: set `Locale.setDefault` in `@BeforeTest` and restore it in `@AfterTest`.
    - US:
      - `formatDayMonthLong(LocalDate(2026, 3, 15)) == "Sunday, March 15"`
      - `formatMonthDay(LocalDate(2026, 3, 15)) == "March 15"`
      - `formatMonthYear(YearMonth(2026, 3)) == "March 2026"`
      - `formatWeekdayDay(LocalDate(2026, 3, 16)) == "Mon 16"`
      - `weekdayNarrow(DayOfWeek.MONDAY) == "M"`
    - FRANCE:
      - `formatMonthYear(YearMonth(2026, 3)) == "mars 2026"`
      - `formatDayMonthLong(LocalDate(2026, 3, 15)) == "dimanche, mars 15"` (same pattern, localized names)
  - **JVM data module test:** `get<ShortcutUsageReporter>() === NoOpShortcutUsageReporter`.
  - **Android host test:** `get<ShortcutUsageReporter>()` is an `AndroidShortcutUsageReporter`.
- [ ] **Step 2: Run the tests to verify they fail.** Run `./gradlew :core:ui:jvmTest`; expect a compile failure.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Verify**
  - `./gradlew :core:data:jvmTest :core:data:testAndroidHostTest :core:data:compileCommonMainKotlinMetadata :core:ui:jvmTest :core:ui:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
  - `./gradlew :app:testFullDebugUnitTest :app:testFossDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** with message `feat(core): common ShortcutUsageReporter and localized date labels (KMP Phase 5)`.

### Task 3: `features/outfits` as a KMP module; ViewModels and routes in `commonMain`

**Files:**
- **Rewrite `features/outfits/build.gradle.kts`** by copying `features/settings/build.gradle.kts`.
  - Namespace `com.closet.features.outfits`; `androidResources { enable = true }`.
  - **commonMain** adds Coil 3 and `kotlinx-serialization-json`, because the routes are `@Serializable`.
  - **androidMain:** `androidx.navigation.compose`, plus `material-icons-extended` temporarily (Task 4 removes it).
  - **jvmTest:** mockk, turbine, coroutines-test, koin-test.
  - Apply the Task 1 Skiko script.
  - **androidHostTest:** the current Koin test set.
  - Drop `timber`.
- **Moves (`git mv`):**
  - **To `commonMain`:** `OutfitsViewModel.kt`, `OutfitBuilderViewModel.kt`, `WardrobePickerViewModel.kt`, `JournalViewModel.kt`, `OutfitMember.kt`, `OutfitComposition.kt`, `di/OutfitsKoinModule.kt`, and `WeatherConditionExt.kt`'s `toDisplayTemp`. The icon mapping stays with the screens until Task 4.
  - **To `androidMain`:** every screen/sheet Composable file, `OutfitsNavigation.kt`, `JournalNavigation.kt` and `src/main/res`.
  - **Tests:**
    - `JournalViewModelTest` → `src/jvmTest/**`;
    - `di/OutfitsKoinModuleTest` → `src/androidHostTest/**`.
- **Create** `src/commonMain/.../OutfitsRoutes.kt` holding `JournalRoute`, `OutfitsRoute`, `OutfitBuilderDestination` and `WardrobePickerDestination`. They move out of the navigation files with the same package, names and fields, so `app` imports don't change.
- **Create** `src/commonMain/.../OutfitBuilderArgs.kt`: `internal expect fun SavedStateHandle.preselectedItemIds(): List<Long>`.
  - The androidMain actual is `toRoute<OutfitBuilderDestination>().preselectedItemIds`, the code there today.
  - The jvm/ios actual is `get<List<Long>>("preselectedItemIds") ?: emptyList()`.
  - `editOutfitId` keeps `savedStateHandle.get<Long>("outfitId")`, which is already common.
- **Create** `src/commonMain/.../JournalDates.kt` with `internal fun todayLocal(): LocalDate`, `internal fun parseIsoDateOrNull(s: String): LocalDate?` and `internal fun YearMonth.isAfterCurrent(today: LocalDate): Boolean`. Only add helpers the ViewModel actually uses.
- **Tests:**
  - `src/jvmTest/.../OutfitBuilderViewModelTest.kt` (new)
  - `src/commonTest/.../JournalDatesTest.kt`
  - `src/jvmTest/.../di/OutfitsJvmKoinGraphTest.kt`

**Interfaces:**
- Consumes `ImagePathResolver` (stats PR) and Task 2's `ShortcutUsageReporter`.
- Produces:
  - Every `resolveImagePath(path: String?): String?` uses `ImagePathResolver`. Composable parameters change `(String?) -> File?` → `(String?) -> String?`.
  - `JournalViewModel(...)`: `appContext: Context` is replaced by `shortcutUsageReporter: ShortcutUsageReporter`, which calls `reportUsed("log_fit")` where `ShortcutManagerCompat` is called today.
  - `JournalUiState.currentYearMonth: kotlinx.datetime.YearMonth`.
  - `viewModelOf(...)` is unchanged.

- [ ] **Step 1: Write the failing tests**
  - **`JournalViewModelTest`** (ported from the Android test):
    - drop `mockkStatic(ShortcutManagerCompat)`;
    - inject a mock `ShortcutUsageReporter`;
    - after a successful `logOutfitOnDate`, `verify { reporter.reportUsed("log_fit") }`; after a failed one, `verify(exactly = 0)`;
    - keep the two existing tests;
    - `logOutfitOnDate` with tomorrow's date → no repository call;
    - `navigateToDate("not-a-date")` → `currentYearMonth` unchanged;
    - `navigateToDate` with a past date → `currentYearMonth == YearMonth(y, m)` of that date;
    - `nextMonth()` at the current month stays put.
  - **`JournalDatesTest`:**
    - `parseIsoDateOrNull("2026-02-30") == null`
    - `parseIsoDateOrNull("2026-03-15") == LocalDate(2026, 3, 15)`
    - `YearMonth(2026, 1).minusMonth() == YearMonth(2025, 12)`, using whatever kotlinx-datetime 0.8 calls it (e.g. `minus(1, DateTimeUnit.MONTH)`)
  - **`OutfitBuilderViewModelTest`** (JVM; `SavedStateHandle(mapOf("preselectedItemIds" to listOf(3L, 5L)))`):
    - preselected ids 3 and 5 end up selected;
    - with `mapOf("outfitId" to 7L)`, the ViewModel loads outfit 7 (stubbed repository);
    - `resolveImagePath("a.jpg") == "/root/a.jpg"`.
  - **`OutfitsJvmKoinGraphTest`:** the settings pattern, with **no** whitelist for `ShortcutUsageReporter`.
- [ ] **Step 2: Run the tests to verify they fail.** Run `./gradlew :features:outfits:jvmTest`; expect a compile failure.
- [ ] **Step 3: Implement** the build, the moves, the ViewModel changes and the route/args split. Update the androidMain screen call sites to `kotlinx.datetime` types and `String?` paths.
  - **`ForecastSheet`'s `toJavaLocalDate()`:** keep it in androidMain until Task 4.
- [ ] **Step 4: Verify**
  - `./gradlew :features:outfits:jvmTest :features:outfits:testAndroidHostTest :features:outfits:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
  - `git grep -nE "^import (android\.|java\.|timber|androidx\.(core|navigation))" -- features/outfits/src/commonMain` prints nothing.
  - `./gradlew test` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** with message `refactor(outfits): features/outfits as a KMP module; ViewModels, routes and Koin in commonMain (KMP Phase 5)`.

### Task 4: Compose resources, Material Symbols; screens to `commonMain`; PR checkpoint

**Files:**
- **Strings:** move them to `src/commonMain/composeResources/values/strings.xml`, then:
  - `outfits_picker_done_count` → `Done (%1$d)`;
  - `outfits_gallery_item_count` → `%1$d items`;
  - fix the one `\'`;
  - delete strings with no remaining reference;
  - add `compose.resources { packageOfResClass = "com.closet.features.outfits.resources"; generateResClass = always }`.
- **Icons:** add the Material Symbols the current `Icons.*` imports need to `core/ui/src/commonMain/composeResources/drawable/`, following the Global Constraints naming and FILL rules:
  - **actions:** add, close, delete, edit, more_vert, check_circle (+ `_filled`), star (+ `_filled`);
  - **arrows:** chevron_left (auto-mirrored), reusing `arrow_back` and `chevron_right`;
  - **weather:** sunny (WbSunny), partly_cloudy_day (WbCloudy), cloud, grain, ac_unit, air, thunderstorm.

  Get the path data from Google's Material Symbols set (Apache 2.0), the same source as earlier PRs. Never hand-draw paths.
  - `WeatherConditionExt.icon()` returns a `DrawableResource` and moves to commonMain.
  - Remove `material-icons-extended`.
- **Date labels:** every `DateTimeFormatter` / `getDisplayName` / `LocalConfiguration.locales` call switches to the Task 2 functions. `LocalDate.now()` in screens → `todayLocal()`.
- **Moves (`git mv` androidMain → commonMain):** every screen and sheet file, after their `@Preview`s are cut into `src/androidMain/.../OutfitsPreviews.kt`, if there are any. Then the import switches: `stringResource`/`pluralStringResource` → CMP, `Icons.*` → `painterResource(CoreRes.drawable.ic_symbol_*)`, `collectAsStateWithLifecycle` → the JetBrains import.
- **Docs and changelog:**
  - **CLAUDE.md module list:** `features/outfits` is KMP.
  - **CLAUDE.md full-verification command:** add `:features:outfits:jvmTest :features:outfits:compileCommonMainKotlinMetadata`.
  - **CLAUDE.md:** mention `ShortcutUsageReporter` and `com.closet.core.ui.format` date labels.
  - **CHANGELOG** `[Unreleased]` → `### Changed`: "**Outfits and journal are Kotlin Multiplatform** (KMP Phase 5): shared UI and view models, Compose resources, Material Symbols icons."
- **Tests:**
  - `src/jvmTest/.../OutfitsStringsTest.kt`:
    - `getString(Res.string.outfits_picker_done_count, 3) == "Done (3)"`
    - `getString(Res.string.outfits_gallery_item_count, 4) == "4 items"`
    - `getString(Res.string.journal_forecast_temp_range, "18°", "9°") == "18° / 9°"`
    - the fixed `'` string renders an apostrophe
  - `src/jvmTest/.../WeatherConditionIconTest.kt`: for each `WeatherCondition.entries`, `icon()` returns a `DrawableResource` whose bytes load. Use `getDrawableResourceBytes(getSystemResourceEnvironment(), …)` or the CMP equivalent, so all 11 conditions are checked.

**Interfaces:**
- Consumes Task 2's date labels, Task 3's layout and `todayLocal()`.
- Produces the internal `com.closet.features.outfits.resources.Res` and the new `ic_symbol_*` drawables in `core/ui`.

- [ ] **Step 1: Write the failing tests.**
- [ ] **Step 2: Run them to verify they fail.** Run `./gradlew :features:outfits:jvmTest`; expect a compile failure.
- [ ] **Step 3: Implement.**
  - `git grep -nE "R\.(string|plurals)|material\.icons|java\.time|LocalConfiguration" -- features/outfits/src/commonMain` prints nothing.
  - Only the navigation files, the previews and the `toRoute` actual are left in androidMain.
- [ ] **Step 4: Verify.**
  - Run the full verification command from CLAUDE.md, including the outfits tasks, → BUILD SUCCESSFUL. Run it in separate invocations if needed.
  - `unzip -l` on each debug APK lists `composeResources/com.closet.features.outfits.resources/` strings and one new weather `ic_symbol_*` drawable.
- [ ] **Step 5: Commit** with message `refactor(outfits): Compose resources, Material Symbols; outfits and journal UI in commonMain (KMP Phase 5)`.
- [ ] **Step 6: PR checkpoint** (the controller does this).
  - Push branch `feat/kmp-phase5-outfits`, open the PR, and wait for `android` and `ios-compile`.
  - Smoke-test on the **test device only**, both flavors:
    - **Outfits gallery:** it shows outfits with photos and "N items", and opens the builder.
    - **Outfit builder:** it picks items in the wardrobe picker, with "Done (N)", saves, and edits an existing outfit.
    - **From recommendations:** "Log these" there preselects the items.
    - **Journal:**
      - the month header is localized, and the weekday initials start on Monday;
      - prev/next month works, and next stops at the current month;
      - a day opens the day sheet with the full localized date title;
      - log an outfit on a past day;
      - toggle OOTD (the filled star);
      - delete a log, with the confirm dialog;
      - edit a log.
    - **Forecast sheet:** the day labels and the weather icons show for every row.
    - **Launcher shortcut:** "Log fit" still opens the builder, and its usage ranking updates (best-effort; no visible check needed).
    - **RTL:** the owner toggles "Force RTL" by hand. The chevrons and back arrows mirror, and the other icons don't.

    Wait for the owner to merge.
