# Recommendation scoreboard, PR 1 (machinery): implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a deterministic scoreboard that:
1. seeds a real JVM Room database with a simulated person's wardrobe and year of wear logs;
2. asks the app's real recommendation pipeline for suggestions on evaluation days;
3. runs the 9 common-sense checks and hit@10;
4. writes a per-persona report.

It runs on one small hand-written persona. The real personas and the baseline come in PR 2.

**Architecture:**
- **The pipeline:** the ViewModel's fetch-and-run becomes an internal `RecommendationPipeline` (commonMain) that the ViewModel and the scoreboard both call.
- **The scoreboard code:** lives in `features/recommendations` `jvmTest`, package `com.closet.features.recommendations.scoreboard`, and runs under its own Gradle `Test` task `scoreboard`. The default `jvmTest` excludes it.

**Tech stack:** Kotlin Multiplatform, Room KMP with the bundled SQLite driver (`getDatabaseBuilder(file)` from `:core:data` jvmMain), kotlinx-datetime, kotlin.test, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-27-scoreboard-design.md` (approved 2026-11-27). Issue: #148. This plan covers its "Delivery 1. Machinery".

## Global Constraints
- **No circular checks:** scoreboard checks and the simulator never import engine constants or rules (`OutfitRecommendationEngine.*` thresholds or multipliers). They use fixture tags (`warmth` 0–3, `rainReady`, `formality` 1–5) and thresholds written in the scoreboard.
- **No re-implemented production logic:** suggestions come only from `RecommendationPipeline` over the real repositories. Test code never filters candidates itself.
- **No dataset content in the repo:** no Kaggle names, ids or images. The PR 1 persona is written by hand.
- **Deterministic:** every random choice comes from a `kotlin.random.Random` seeded from the persona seed, or from the date for engine jitter (`Random(date.toEpochDays())`, matching the app's first run of a day). Two runs give byte-identical reports.
- **Writes go through real DAOs:**
  - items via `ClothingDao` (`insertClothingItem` plus the `updateItem*` junction helpers) inside a transaction. Not `insertItemWithAttributes`, whose vectorizer side effect needs an encoder;
  - favourite-outfit wears via `OutfitDao` saved outfits plus `LogDao.insertLogAndSnapshot`;
  - all other wears via `LogDao.insertAdHocLogAndSnapshot`;
  - laundry via `ClothingDao.updateWashStatus`.
- **Evaluation:**
  - every 3rd **logged** day after a 60-day warm-up;
  - per evaluation day, a fresh `FixedAppClock` and two pipeline calls (`topN = 3` for the checks, `topN = 10` for hit@10), each with `Random(date.toEpochDays())`;
  - the occasion is requested on about 70% of evaluation days, otherwise null.
- **The simulator:**
  - about 70% of days logged;
  - the occasion mapping: office → Work/Business; campus, errands and weekend → Casual; home → Loungewear; gym → Athletic; hike → Outdoor/Hiking; evening → Date Night.
- **N/A is not a pass:** a check that can't apply on a day is excluded from that check's denominator.
- **Code rules:**
  - commonMain stays pure: no I/O, no clock reads (read through `AppClock`), no `java.*`;
  - rethrow `CancellationException`;
  - backticked test names use words only, with none of `, . ; : / < > [ ] \ ( )`.
- **Gradle and git:**
  - one Gradle invocation at a time, in the foreground; never `--rerun-tasks`; never kill daemons;
  - commits are signed: never bypass signing, and on "failed to fill whole buffer" report BLOCKED with the work staged;
  - never stage `.claude/settings.json`.

## Review Focus
1. **A persona whose wardrobe has no clean footwear that day.** Check 1 must count "complete" as passing without shoes, not as a fail. Test: `complete passes without shoes when no clean footwear exists` (Task 4).
2. **An evaluation day where the pipeline returns nothing.** It counts toward the no-results rate. Checks 1–9 are N/A that day, not failures, and hit@10 is a miss. Test: `empty pipeline result counts as no results and a miss` (Task 5).
3. **A day when every catalogue outfit is blocked by laundry or weather.** The simulator logs the closest outfit, relaxing rain first and then temperature, and never logs a dirty item. Test: `fallback relaxes rain then temperature and never logs dirty items` (Task 3).
4. **The first evaluation day** sees only earlier logs (strict `<` date), never that day's own log. Test: `pipeline sees only logs before the evaluation day` (Task 5).
5. **Fewer than 3 suggestions returned.** Check 8 ("the three differ") compares only the pairs that exist, and is N/A with fewer than 2. Test: `three differ is not applicable with fewer than two suggestions` (Task 4).

---

### Task 1: Extract `RecommendationPipeline`

**Files:**
- Create: `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/RecommendationPipeline.kt`
- Modify: `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/RecommendationViewModel.kt`. `fetchAndRun` delegates. `resolveTasteWeights` and `trainTasteWeightsFromRepo` stay in the ViewModel, passed in as a function.
- Modify: `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/di/*KoinModule.kt`, only if the pipeline is bound. Recommended: the ViewModel builds it internally, so Koin and `AppKoinGraphTest` stay unchanged.
- Test: `features/recommendations/src/jvmTest/kotlin/com/closet/features/recommendations/RecommendationPipelineTest.kt`

**Interfaces:**
- **Produces:**
  ```kotlin
  internal data class PipelineRequest(
      val today: LocalDate,
      val occasionId: Long?,
      val weather: WeatherConditions?,
      val topN: Int,
      val exclude: Set<Set<Long>> = emptySet(),
      val jitter: Random,
  )
  internal class RecommendationPipeline(
      private val recommendationRepository: RecommendationRepository,
      private val engine: OutfitRecommendationEngine,
      private val computationDispatcher: CoroutineDispatcher,
      private val tasteWeights: suspend (totalLogCount: Int) -> TasteWeights,
  ) {
      /** Candidates → parallel queries → EngineInput → one engine.recommend. Empty candidates → emptyList(). */
      suspend fun run(request: PipelineRequest): List<OutfitCombo>
      /** Builds the EngineInput without running the engine; null when there are no candidates. */
      suspend fun engineInput(request: PipelineRequest): EngineInput?
  }
  ```
- **Calendar season:** the pipeline derives it from `request.today` via `seasonFor`.
- **What stays in the ViewModel:**
  - the exclusion wrap-around (shown and last-page keys);
  - the `topN = 25`-when-AI-is-ready choice (otherwise `OutfitRecommendationEngine.TOP_N`);
  - the `firstOfSession` choice of `Random`;
  - `itemScoresForScorer`, which uses `pipeline.engineInput`, or the input returned alongside the combos; the implementer picks one and must not run the queries twice per `run`;
  - the AI scorer.

- [ ] **Step 1: Write the failing test.** In `RecommendationPipelineTest`:
  - `same request and seed give the same combos`;
  - `no candidates gives an empty list without further queries`, which verifies with mockk that `getTempPercentiles` is never called.

  Use a mockk `RecommendationRepository` shaped like the existing `RecommendationViewModelSessionTest` setup.
- [ ] **Step 2: Run** `./gradlew :features:recommendations:jvmTest --console=plain -q`. **Expected:** compile failure (no `RecommendationPipeline`).
- [ ] **Step 3: Implement** `RecommendationPipeline` by moving the body of `fetchAndRun` steps 1–6 verbatim. Rewire the ViewModel. Behaviour must not change.
- [ ] **Step 4: Run** `./gradlew :features:recommendations:jvmTest --console=plain -q`. **Expected:** PASS, including every `RecommendationViewModelSessionTest` test, unchanged.
- [ ] **Step 5: Run** `./gradlew :features:recommendations:compileCommonMainKotlinMetadata --console=plain -q`. **Expected:** PASS.
- [ ] **Step 6: Commit** `refactor(recommendations): extract RecommendationPipeline from the ViewModel`.

### Task 2: Scoreboard database, persona model and fixture writer

**Files:**
- Create in `features/recommendations/src/jvmTest/kotlin/com/closet/features/recommendations/scoreboard/`:
  - `ScoreboardDatabase.kt`: a temp-file `ClothingDatabase` via `getDatabaseBuilder(file)`. It is `AutoCloseable` and deletes its directory on close.
  - `Persona.kt`: the fixture model.
  - `FixtureWriter.kt`: inserts a persona's wardrobe and saved outfits.
  - `MiniPersona.kt`: the small hand-written persona.
- Test: `scoreboard/FixtureWriterTest.kt`

**Interfaces:**
- **Produces:**
  ```kotlin
  data class FixtureItem(
      val key: String,                 // stable fixture id, e.g. "navy-oxford"
      val description: String,         // our own generic name
      val category: String,            // seeded category name
      val subcategory: String?,        // seeded subcategory name
      val colors: List<Pair<String, Double>>, // hex "#RRGGBB" to share
      val materials: Set<String>, val seasons: Set<String>, val occasions: Set<String>,
      val warmth: Int,                 // 0..3, scoreboard-only
      val rainReady: Boolean,          // scoreboard-only
      val formality: Int,              // 1..5, scoreboard-only
  )
  data class FixtureOutfit(
      val key: String, val itemKeys: List<String>, val occasions: Set<String>,
      val minTempC: Double, val maxTempC: Double, val rainOk: Boolean,
      val favouriteWeight: Double,     // > 0
      val saved: Boolean,              // persona saves it in the app
  )
  enum class DayKind { OFFICE, CAMPUS, ERRANDS, WEEKEND, HOME, GYM, HIKE, EVENING }
  data class Climate(val monthlyHighC: List<Double>, val monthlyLowC: List<Double>,
                     val monthlyRainChance: List<Double>, val windyChance: Double) // 12 entries each
  data class Persona(
      val name: String, val seed: Long, val items: List<FixtureItem>, val outfits: List<FixtureOutfit>,
      val weekSchedule: List<DayKind>, // 7 entries, Monday first
      val climate: Climate, val wearsBeforeWash: Int, val laundryDay: DayOfWeek,
      val swapChance: Double, val homeNoShoesChance: Double,
  )
  class WrittenFixture(val itemIds: Map<String, Long>, val savedOutfitIds: Map<String, Long>)
  object FixtureWriter { suspend fun write(db: ClothingDatabase, persona: Persona): WrittenFixture }
  object MiniPersona { fun build(): Persona }
  ```
- **The writer resolves names to seeded lookup ids** (categories, subcategories, materials, seasons, occasions) through the lookup DAOs. An unknown name throws `IllegalArgumentException` naming it.
- **`MiniPersona`:**
  - menswear, about 14 items: 3 tops, 2 bottoms, 1 outerwear (warmth 3, rainReady), 1 light jacket (warmth 2), 2 footwear (1 rainReady), 1 belt, 1 Activewear item, and the rest of our choosing;
  - 8 outfits, 3 of them saved;
  - a four-season climate, and a schedule of 5 office days plus a weekend and a home day.

- [ ] **Step 1: Write the failing tests** in `FixtureWriterTest`:
  - `written items are returned as candidates for their season and occasion`: write `MiniPersona`, then `RecommendationRepository(db.recommendationDao()).getCandidates("Winter", null, null, workOccasionId)` returns exactly the items tagged Winter and Work/Business;
  - `a dirty item is not a candidate`, after `updateWashStatus(id, WashStatus.Dirty)`;
  - `unknown lookup names fail loudly`;
  - `saved outfits are written with their items`, for each `saved = true` outfit.
- [ ] **Step 2: Run** `./gradlew :features:recommendations:jvmTest --console=plain -q`. **Expected:** compile failure.
- [ ] **Step 3: Implement** the files above.
- [ ] **Step 4: Run** the same command. **Expected:** PASS.
- [ ] **Step 5: Commit** `test(recommendations): scoreboard database, persona model and fixture writer`.

### Task 3: The simulator

**Files:**
- Create: `scoreboard/Simulator.kt`, `scoreboard/Weather.kt`
- Test: `scoreboard/SimulatorTest.kt`

**Interfaces:**
- **Consumes:** `Persona`, `WrittenFixture`, `ScoreboardDatabase` (Task 2).
- **Produces:**
  ```kotlin
  data class DayWeather(val lowC: Double, val highC: Double, val raining: Boolean, val windy: Boolean)
  object WeatherModel { fun day(climate: Climate, date: LocalDate, random: Random): DayWeather }
  data class SimDay(
      val date: LocalDate, val kind: DayKind, val occasion: String?, // mapped seeded occasion name
      val weather: DayWeather, val logged: Boolean,
      val wornItemKeys: List<String>,  // empty when not logged
      val viaSavedOutfit: String?,     // outfit key when logged through the saved path
      val dirtyAtMorning: Set<String>, // item keys dirty before dressing
  )
  class Simulator(private val persona: Persona) {
      /** Pure plan of the whole year: same persona gives the same list. */
      fun plan(start: LocalDate, days: Int): List<SimDay>
      /** Writes one planned day's log and laundry changes into the db. */
      suspend fun apply(db: ClothingDatabase, fixture: WrittenFixture, day: SimDay)
  }
  ```
- **Weather:** the monthly mean, plus noise from `random`, with the low ≤ the high. Rain comes from `monthlyRainChance`; precipitation is stored as 5.0 mm when raining, else 0.0; wind as 40.0 km/h when windy, else 10.0. These are stored values only, never compared against engine thresholds in checks.
- **Choosing the outfit:**
  - eligible outfits suit the occasion, `minTempC ≤ high` and `low ≤ maxTempC` (or simply the day's mean in the band; the implementer picks and documents it), `rainOk` if raining, and have every item clean;
  - pick by `favouriteWeight`;
  - then apply `swapChance` (replace one item with a clean item of the same category), or `homeNoShoesChance` on HOME days (drop footwear);
  - **fallback:** when nothing is eligible, ignore rain, then temperature, then occasion; never log a dirty item; if even that fails, the day isn't logged.
- **How a wear is logged:** only an unmodified `saved` outfit is logged through the saved path. Swapped or shoe-less wears are ad hoc.
- **Laundry:** worn items count wears. At `wearsBeforeWash` an item becomes Dirty, and every Dirty item becomes Clean on `laundryDay`.

- [ ] **Step 1: Write the failing tests** in `SimulatorTest`:
  - `same persona gives the same plan`;
  - `about seventy percent of days are logged`: over 365 days, the logged share is within 0.60–0.80;
  - `raining days pick a rain ok outfit when one is clean`;
  - `fallback relaxes rain then temperature and never logs dirty items` (Review Focus 3);
  - `home days sometimes drop footwear`: count > 0 when `homeNoShoesChance` > 0, and exactly 0 when it is 0.0;
  - `apply writes saved and ad hoc logs through the right paths`: the saved-path log has a non-null `outfit_id`, and ad hoc logs have a null `outfit_id` with snapshot rows.
- [ ] **Step 2: Run** `./gradlew :features:recommendations:jvmTest --console=plain -q`. **Expected:** compile failure.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same command. **Expected:** PASS.
- [ ] **Step 5: Commit** `test(recommendations): deterministic persona simulator`.

### Task 4: The 9 checks

**Files:**
- Create: `scoreboard/Checks.kt`
- Test: `scoreboard/ChecksTest.kt`

**Interfaces:**
- **Produces:**
  ```kotlin
  enum class CheckId { COMPLETE, WARM_ENOUGH, NOT_TOO_WARM, RAIN_READY, FORMALITY, CLEAN, NO_DOUBLED_SLOTS, THREE_DIFFER, NOT_ON_REPEAT }
  enum class Outcome { PASS, FAIL, NOT_APPLICABLE }
  data class CheckContext(
      val persona: Persona, val day: SimDay,
      val requestedOccasion: String?,
      val yesterdayWornKeys: List<String>?,   // null when yesterday wasn't logged
  )
  object Checks {
      /** Per-suggestion checks 1-7 and 9; each suggestion is a list of fixture item keys. */
      fun perSuggestion(suggestion: List<String>, ctx: CheckContext): Map<CheckId, Outcome>
      /** Check 8 over the shown page. */
      fun threeDiffer(page: List<List<String>>): Outcome
      /** Item warmth needed for a day's low; a plain table defined here, documented in KDoc. */
      fun warmthNeeded(lowC: Double): Int
  }
  ```
- **Pinned definitions** (from spec §6):
  1. **COMPLETE:** has Top+Bottom or a OnePiece (by category), and has a Footwear item when the persona has a clean footwear item whose seasons include the day's calendar season. Otherwise footwear isn't required.
  2. **WARM_ENOUGH:** the sum of `warmth` ≥ `warmthNeeded(low)`.
  3. **NOT_TOO_WARM:** N/A when the high < 24 °C; otherwise FAIL if any item has `warmth == 3`.
  4. **RAIN_READY:** N/A unless raining and the persona has a clean `rainReady` Footwear or Outerwear item; then PASS iff the suggestion includes one.
  5. **FORMALITY:** max − min `formality` ≤ 2. When an occasion was requested, every item's formality must also be within the occasion's band. The band table lives in `Checks.kt`, for example Work/Business 3–5 and Athletic 1–2; the implementer writes the full table.
  6. **CLEAN:** FAIL if any item is in `dirtyAtMorning`.
  7. **NO_DOUBLED_SLOTS:** at most one Footwear, one Outerwear and one Belts-subcategory item.
  8. **THREE_DIFFER:** every pair of suggestions differs in at least 2 items; N/A with fewer than 2 suggestions.
  9. **NOT_ON_REPEAT:** N/A when yesterday wasn't logged; FAIL if the suggestion's item set equals yesterday's and it isn't a catalogue outfit with `favouriteWeight` ≥ the persona's median.

- [ ] **Step 1: Write the failing tests.** For each check, one PASS, one FAIL and, where defined, one N/A case, named after the behaviour. They must include:
  - `complete passes without shoes when no clean footwear exists` (Review Focus 1);
  - `three differ is not applicable with fewer than two suggestions` (Review Focus 5);
  - `not too warm is not applicable below twenty four degrees`.
- [ ] **Step 2: Run** `./gradlew :features:recommendations:jvmTest --console=plain -q`. **Expected:** compile failure.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same command. **Expected:** PASS.
- [ ] **Step 5: Commit** `test(recommendations): scoreboard common-sense checks`.

### Task 5: Evaluation loop, report, Gradle task and wiring

**Files:**
- Create: `scoreboard/Scoreboard.kt` (the loop), `scoreboard/Report.kt`, `scoreboard/ScoreboardTest.kt`
- Modify:
  - `features/recommendations/build.gradle.kts`: register the `scoreboard` `Test` task, and exclude the scoreboard package from `jvmTest`;
  - `app-android/CLAUDE.md`: add `:features:recommendations:scoreboard` to the full pre-PR command, and a sentence on what it is;
  - `.github/workflows/ci.yml` (repo root): add `:features:recommendations:scoreboard` to the `test` matrix entry's `tasks`.

**Interfaces:**
- **Consumes:** Tasks 1–4.
- **Produces:**
  ```kotlin
  data class CheckRate(val fails: Int, val applicable: Int)
  data class PersonaResult(
      val persona: String, val evaluatedDays: Int, val hitsAt10: Int, val noResultDays: Int,
      val savedShare: Double, val checks: Map<CheckId, CheckRate>,
  )
  object Scoreboard {
      suspend fun run(persona: Persona, start: LocalDate = LocalDate(2025, 1, 6), days: Int = 365): PersonaResult
  }
  object Report { fun markdown(results: List<PersonaResult>): String }
  ```
- **The loop:**
  1. Open a `ScoreboardDatabase`, write the fixture and plan the year.
  2. For each day in order:
     - when it is an evaluation day (every 3rd logged day with index ≥ 60 days from the start), set that morning's wash states;
     - build a `RecommendationPipeline` over `RecommendationRepository(db.recommendationDao())` with a fresh `FixedAppClock` at that date, `UnconfinedTestDispatcher` or `Dispatchers.Unconfined`, and `tasteWeights = { TasteWeights.DEFAULT }`. While `LEARNED_TASTE_ENABLED` is false that mirrors production; note it in KDoc;
     - make two `run` calls (`topN = 3`, then `topN = 10`), each with a new `Random(date.toEpochDays())`;
     - request the occasion on about 70% of evaluation days, decided by a seeded draw;
     - map the combos' item ids back to fixture keys and score;
     - then `Simulator.apply` the day.
- **The report:**
  - **Contents:** a table per persona of each check's fail % (fails/applicable), hit@10 %, the no-results %, evaluated days and the saved share;
  - **The embeddings note:** the line "neighbour backoff off: no JVM embeddings";
  - **Where it goes:** `build/reports/scoreboard/scoreboard.md` (under the module's build dir) and stdout.
- **Gradle** (a sketch; the implementer adapts it to the KMP plugin's task types):
  ```kotlin
  val jvmTestTask = tasks.named<Test>("jvmTest")
  jvmTestTask { filter { excludeTestsMatching("com.closet.features.recommendations.scoreboard.*") } }
  tasks.register<Test>("scoreboard") {
      group = "verification"; description = "Runs the recommendation scoreboard."
      testClassesDirs = jvmTestTask.get().testClassesDirs; classpath = jvmTestTask.get().classpath
      filter { includeTestsMatching("com.closet.features.recommendations.scoreboard.*") }
  }
  ```
  **Important:** the Task 2–4 unit tests also live in the scoreboard package, so after this task they run under `scoreboard`, not `jvmTest`. That's intended: the CLAUDE.md command and CI run both.

- [ ] **Step 1: Write the failing tests** in `ScoreboardTest`:
  - `mini persona run is deterministic`: two `Scoreboard.run(MiniPersona.build())` calls give equal `PersonaResult`s and identical `Report.markdown` output;
  - `mini persona produces evaluated days and sane rates`: `evaluatedDays` > 0, `hitsAt10` ≤ `evaluatedDays`, and every `applicable` ≤ `evaluatedDays × 3`;
  - `pipeline sees only logs before the evaluation day` (Review Focus 4): on the first evaluation day, `getTotalLogCount()` equals the number of logged plan days strictly before it;
  - `empty pipeline result counts as no results and a miss` (Review Focus 2): with a persona whose only items are dirty on an evaluation day, that day counts in `noResultDays`, its checks are N/A, and it is not a hit;
  - `report is written` to the path above, containing the persona's name.
- [ ] **Step 2: Run** `./gradlew :features:recommendations:scoreboard --console=plain -q`. **Expected:** the new tests fail or don't compile.
- [ ] **Step 3: Implement** the loop, the report, the Gradle task, the CLAUDE.md line and the CI matrix entry.
- [ ] **Step 4: Run each of these, one at a time:**
  - `./gradlew :features:recommendations:scoreboard --console=plain -q` → PASS, and print the report path and the wall time (target < 3 min);
  - `./gradlew :features:recommendations:jvmTest --console=plain -q` → PASS, with no scoreboard tests run;
  - `./gradlew :features:recommendations:compileTestKotlinIosSimulatorArm64 --console=plain -q` → PASS.
- [ ] **Step 5: Commit** `test(recommendations): scoreboard loop, report and Gradle task`. Paste the mini-persona report into the commit body.

## Whole-branch verification (controller)
- **The full pre-PR command** from `app-android/CLAUDE.md`, now including `:features:recommendations:scoreboard` and `iosCompileCheck`.
- **No device smoke test.** No app behaviour changes, so instead confirm the ViewModel session tests pass unchanged.
- **Follow-up issue:** #149 (embeddings) is already filed and linked to #148. The PR says "Part of #148".
