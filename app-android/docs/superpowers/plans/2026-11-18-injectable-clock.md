# Injectable clock (`AppClock`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One injectable, testable source for "now" and "today" (#112), so date logic can be tested with a fixed clock. It replaces the direct `Clock.System` / `TimeZone.currentSystemDefault()` reads in `core/data` and the features. There is no behaviour change: "today" stays the device's local date in its current time zone, read per call.

**Architecture:** One PR, two tasks.
- **Task 1 (`core/data`):**
  - Add `AppClock` (`SystemAppClock` for production, `FixedAppClock` for tests), bound in `commonDataModule`.
  - Convert the `core/data` call sites to it.
  - The demo tool passes `SystemAppClock`.
- **Task 2 (features):** convert the feature call sites, then docs.

**Out of scope (from #112's scope note):**
- Entity timestamp defaults (`createdAt`/`updatedAt = Clock.System.now()` in `ClothingItemEntity` and the outfit entities).
- The timestamps that the Android workers (`EmbeddingWorker`, `BatchSegmentationWorker`) write.

**Tech Stack:** Kotlin 2.4.21, `kotlin.time.Clock`/`Instant`, kotlinx-datetime 0.8.0, Koin 4.2.2.

**Spec:** issue #112, including its scope comment. No design doc: the change is mechanical and has no behaviour change.

## Global Constraints

- **The `AppClock` contract,** in `core/data/src/commonMain/kotlin/com/closet/core/data/time/AppClock.kt`:
  ```kotlin
  interface AppClock {
      fun now(): Instant                 // kotlin.time.Instant
      fun timeZone(): TimeZone
      fun today(): LocalDate = now().toLocalDateTime(timeZone()).date
  }
  object SystemAppClock : AppClock { /* Clock.System.now(); TimeZone.currentSystemDefault(), both read per call */ }
  class FixedAppClock(private val instant: Instant, private val zone: TimeZone = TimeZone.UTC) : AppClock
  ```
  `FixedAppClock` lives in commonMain so tests in every module can use it. It is a few lines, and production never binds it.
- **Koin:** `commonDataModule` adds `single<AppClock> { SystemAppClock }`. Classes take `clock: AppClock` as a constructor parameter, resolved through the existing `singleOf`/`viewModelOf`. No default argument values on Koin-built classes, except where noted below.
- **After this PR,** `git grep -nE "Clock\.System|currentSystemDefault\(\)" -- '*/src/commonMain/*' '*/src/androidMain/*'` finds only:
  - `AppClock.kt`;
  - the entity timestamp defaults (`model/ClothingItemEntity.kt`, `model/OutfitEntities.kt`);
  - the two workers (`EmbeddingWorker`, `BatchSegmentationWorker`).
- **No behaviour change.** Every converted site computes the same value as before when given `SystemAppClock`.
- **Tests that read the real date** switch to `FixedAppClock` (for example `RegexDateParserTest`'s current year).
- **Kotlin/Native** rejects `()`, `.`, `:`, `;`, `/`, `<>`, `[]` and `\` in backtick test names.
- **Gradle hygiene:**
  - Run from `app-android/`, one invocation at a time, in the foreground, with no `--rerun-tasks`.
  - Never stop daemons.
  - iOS compiles locally with `-Pkotlin.native.enableKlibsCrossCompilation=true`, main and test.

## Review Focus

1. **The day boundary.** `today()` uses the zone's local date, not UTC.
   - **Test (Task 1):** `FixedAppClock(Instant.parse("2026-01-15T07:30:00Z"), TimeZone.of("America/Los_Angeles")).today() == LocalDate(2026, 1, 14)` (23:30 the previous evening, at UTC−8). The same instant with `TimeZone.UTC` gives 2026-01-15.
2. **Same values with the system clock.**
   - The wear-log date (`LogRepository`), the not-worn cutoffs (`WardrobeTools`, `PatternChatRouter`), the Google forecast base date and the weather cache age (`WeatherRepository`) must all compute exactly what they did before.
   - The chat date parser's default year, the journal's "today", recommendations' season and `EngineInput.today`, the stats period window, and the form's `createdAt`/`updatedAt` likewise.
   - **Tests:** one fixed-clock test per converted site that has an observable date or time value, in the module's existing test style:
     - **`LogRepository`:** wearing an outfit "today" logs the clock's date.
     - **`WardrobeTools`:** the not-worn tool's cutoff equals clock date − N days.
     - **`WeatherRepository`:** a cache exactly at the age limit vs. 1 ms past it.
     - **`RegexDateParser`:** "April 4" with no year uses the clock's year.
     - **`PatternChatRouter`:** the cutoff date.
     - **`RecommendationViewModel`:** the season for the clock's month.
     - **`StatsViewModel`:** the period window.
3. **Koin graphs.** `AppKoinGraphTest` (both flavors), `GmsKoinTest`, and every module's JVM or androidHost graph test resolve `AppClock`. Feature module tests that whitelist sibling types must not need `AppClock` in `extraTypes`: `commonDataModule` binds it, and those tests already include data modules. If one doesn't, bind `FixedAppClock` test-locally rather than whitelisting.
4. **The demo tool and `platform/gms` still build.**
   - `tools/demo-backup` constructs `ClothingRepository` and `OutfitRepository` directly, so it passes `SystemAppClock`.
   - `platform/gms`'s `PatternChatRouter` binding and its `RegexChatDateParser` use get the clock from Koin.

---

### Task 1: `AppClock` in `core/data`; core call sites

**Files:**
- **Create:** `core/data/src/commonMain/.../time/AppClock.kt` (contract above).
- **Modify:**
  - `di/` `commonDataModule` (the binding);
  - `repository/LogRepository.kt` (2 sites);
  - `repository/ClothingRepository.kt` (4 `now()` sites);
  - `repository/OutfitRepository.kt`;
  - `repository/WeatherRepository.kt` (2 sites);
  - `weather/GoogleWeatherClient.kt`;
  - `ai/tools/WardrobeTools.kt`;
  - whatever constructs these outside Koin, which is the demo tool's `DemoBackup.kt` and `History.kt` plus any test fixtures.
- **Tests (`core/data/src/commonTest` or `jvmTest`, matching where each class's tests live):**
  - `time/AppClockTest.kt` (Review Focus 1);
  - fixed-clock tests for `LogRepository`, `WardrobeTools` and `WeatherRepository` (Review Focus 2).

**Interfaces:** produces `AppClock`, `SystemAppClock`, `FixedAppClock` and the Koin binding. These constructors gain a trailing `clock: AppClock` parameter: `LogRepository`, `ClothingRepository`, `OutfitRepository`, `WeatherRepository`, `GoogleWeatherClient`, and `WardrobeTools` (or whatever holds the cutoff).

- [ ] **Step 1:** Write the tests.
- [ ] **Step 2:** Run `./gradlew :core:data:jvmTest` and expect a failure.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Verify:
  - `./gradlew :core:data:jvmTest :core:data:testAndroidHostTest :core:data:compileCommonMainKotlinMetadata :tools:demo-backup:test test` passes.
  - The `core/data` iOS main and test compiles pass.
- [ ] **Step 5:** Commit: `feat(data): injectable AppClock; core/data reads now/today through it (#112)`.

### Task 2: Feature call sites; docs

**Files:**
- **`features/chat`:**
  - `PatternChatRouter` takes `clock`.
  - `regexParseDate(text, today: LocalDate)` takes the date. `RegexChatDateParser(clock: AppClock)` passes `clock.today()`.
  - Update `chatNoOpModule`'s `RegexChatDateParser()` to `RegexChatDateParser(get())`.
  - Check `platform/gms` `MlKitChatDateParser` / `GmsKoinModule` for the same constructor.
- **`features/outfits`:** `JournalDates.todayLocal()` becomes a call on an injected clock in its callers. Remove the helper if nothing else uses it.
- **`features/recommendations`:** `RecommendationViewModel` takes `clock` (the season, `EngineInput.today` and the date seed all come from one `clock.today()` per run).
- **`features/stats`:** `StatsViewModel`'s `today` default becomes `clock.today()`. Keep it testable.
- **`features/wardrobe`:** `ClothingFormViewModel`'s `createdAt`/`updatedAt` use `clock.now()`.
- **Tests:** the Review Focus 2 feature cases. Existing tests that read the real date switch to `FixedAppClock`.
- **Docs:**
  - **CLAUDE.md, `core/data` "Platform interfaces" paragraph:** add "`AppClock` (`com.closet.core.data.time`; `SystemAppClock` in production, `FixedAppClock` in tests) — read now/today through it, never `Clock.System`, except entity timestamp defaults and worker timestamps."
  - **CHANGELOG:** none, since there's no user-visible change.

- [ ] **Step 1:** Write the tests.
- [ ] **Step 2:** Run the affected modules' `jvmTest` and expect a failure.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Verify:
  - The full CLAUDE.md verification command passes.
  - `iosCompileCheck` with cross-compilation passes.
  - The Global Constraints grep finds only the allowed files.
- [ ] **Step 5:** Commit: `refactor(features): read now/today through AppClock; docs (#112)`.
- [ ] **Step 6:** PR checkpoint (controller).
  - Push `feat/injectable-clock`, open the PR (it closes #112), and wait for CI.
  - Device smoke (test device, full flavor):
    - log a wear and check the journal shows today;
    - the stats period chips show the same numbers as before;
    - recommendations run;
    - the chat "what did I wear on <Month Day>" answer uses this year.
  - No data changes beyond one logged wear, which you delete afterwards via the journal's delete confirmation.
