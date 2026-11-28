# Two-stage engine, PR 1 (item warmth and rain-readiness): implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** every item has an automatic warmth level (none, light, medium, warm or heavy) and a rain-ready flag. The user can override either on the item form. The engine receives the effective values. PR 1 does not change engine behaviour: the scoreboard baseline must pass unchanged.

**Architecture:**
- **The derivation** is a pure function in `core/data` commonMain (`com.closet.core.data.weather`), so both the item form (`features/wardrobe`) and the pipeline (`features/recommendations`) can use it.
- **The overrides** are two nullable columns (MIGRATION_4_5, schema v5).
- **The recommendation pipeline** computes the effective values (the override, else the history-refined value, else the derived value) and puts them on `EngineItem`. The engine doesn't read them until PR 2.

**Tech stack:** Kotlin Multiplatform, Room KMP (migrations with `MigrationTestHelper`), Compose Multiplatform (the item form), Koin, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-11-28-two-stage-engine-design.md` (approved 2026-11-28), §1 and the "Resolutions" section. Issue: #147.

## Global Constraints
- **The warmth scale:** `enum class WarmthLevel { NONE, LIGHT, MEDIUM, WARM, HEAVY }`, ordinal 0–4. It is grounded in ASHRAE garment insulation (clo) ordering, documented in KDoc with the reference values: T-shirt 0.08, long-sleeve shirt 0.25, thick sweater 0.36, lined suit jacket about 0.44, shoes 0.02, boots 0.10.
- **The derivation must reuse the existing rules, not duplicate them.** `WeatherFitPrior` (features/recommendations `engine/WeatherFitPrior.kt`) already holds the rain-protective subcategory and material sets (around lines 150–166). Move those sets to `core/data` (`com.closet.core.data.weather`) and make `WeatherFitPrior` use the moved sets. Its behaviour must not change, and its existing tests must pass unchanged.
- **Overrides:** `clothing_items.warmth_override INTEGER NULL` (0–4) and `rain_ready_override INTEGER NULL` (0 or 1). Null means Auto.
- **Migration:** MIGRATION_4_5, schema version 5. Follow `core/data/src/commonMain/kotlin/com/closet/core/data/migrations/AGENTS.md`: drop `one_ootd_per_day` first, register the migration in `configureClosetDatabase()`, test it with `MigrationTestHelper`, and export the schema JSON. Update app-android/CLAUDE.md's "current version" line.
- **No engine behaviour change in PR 1.** `OutfitRecommendationEngine` must not read the new fields. `:features:recommendations:scoreboard` must pass with the baseline unchanged.
- **No circularity:** nothing in `core/data` or `features/*` main code imports scoreboard code, and the scoreboard's tags stay test-only. `FixtureWriter` leaves both override columns null.
- **Code rules:**
  - commonMain stays pure;
  - rethrow `CancellationException`;
  - repository returns go through `DataResult`;
  - user-visible strings are Compose resources;
  - backticked test names use words only (none of `, . ; : / < > [ ] \ ( )`);
  - one Gradle invocation at a time, in the foreground; signed commits with the Opus 5.5 and Claude-Session trailers; never stage `.claude/settings.json`.

## Review Focus
1. **An item with no subcategory and no materials** (for example a quick-add item) derives NONE and not rain-ready, and doesn't crash. Test: `an item with no subcategory or materials derives none and not rain ready` (Task 2).
2. **A material modifier can't push the level past HEAVY or below NONE.** Test: `material modifiers stay within the scale` (Task 2).
3. **An existing v4 database upgrades with both overrides null,** and saving an item with the dropdowns left on Auto keeps them null, never writing 0. Tests: `migration four to five adds nullable override columns` (Task 1) and `saving with auto keeps overrides null` (Task 3).
4. **History refinement only raises warmth, by at most one level,** and only when at least 5 logs carry weather. Test: `history raises warmth by at most one level` (Task 4).
5. **An override wins over both the derivation and history.** Test: `an override wins over derivation and history` (Task 4).

---

### Task 1: Override columns, migration and repository plumbing

**Files:**
- Modify:
  - `core/data/src/commonMain/kotlin/com/closet/core/data/model/ClothingItemEntity.kt`: `warmthOverride: Int? = null` (`warmth_override`) and `rainReadyOverride: Boolean? = null` (`rain_ready_override`, stored 0/1);
  - `ClothingDatabase.kt`: version 5;
  - `configureClosetDatabase()`: register MIGRATION_4_5;
  - `core/data/.../dao/RecommendationDao.kt`: `CandidateItem` gains `warmthOverride` and `rainReadyOverride`, selected in `getCandidates`;
  - the training-item projection, if it builds `EngineItem`s: also carry the overrides.
- Create:
  - `core/data/src/commonMain/kotlin/com/closet/core/data/migrations/Migration4To5.kt`;
  - the schema `core/data/schemas/com.closet.core.data.ClothingDatabase/5.json`, generated by `./gradlew :core:data:kspAndroidMain`.
- Test: `core/data/src/jvmTest/.../MigrationTest.kt` (add 4→5), `FreshInstallSchemaTest` (v5) and a repository round-trip test.

**Interfaces:**
- **Produces:**
  - `ClothingItemEntity.warmthOverride: Int?` (0–4) and `rainReadyOverride: Boolean?`;
  - `CandidateItem.warmthOverride: Int?` and `rainReadyOverride: Boolean?`;
  - the existing item save path (`ClothingRepository` insert and update) persists both as given.

- [ ] **Step 1: Write the failing tests:**
  - `migration four to five adds nullable override columns`: a v4 database with an item upgrades, and both columns exist and are null;
  - `fresh install has override columns`;
  - `item overrides round trip`: save warmth 3 and rain-ready false, then read them back;
  - `candidates carry overrides`.
- [ ] **Step 2: Run** `./gradlew :core:data:jvmTest --console=plain -q`. **Expected:** the new tests fail.
- [ ] **Step 3: Implement.** Check the backup restore schema guard (`app/.../backup/BackupRepository.kt`). A v5 backup must restore on a v5 app. If the guard compares versions, make sure its existing tests still pass, and add one for v5 if a version constant is involved.
- [ ] **Step 4: Run** `./gradlew :core:data:jvmTest --console=plain -q`, then `./gradlew :core:data:compileCommonMainKotlinMetadata --console=plain -q`, then `./gradlew :app:testFullDebugUnitTest --console=plain -q`. **Expected:** PASS.
- [ ] **Step 5: Commit** `feat(data): item warmth and rain-ready override columns, schema v5 (#147)`.

### Task 2: The derivation in core/data

**Files:**
- Create: `core/data/src/commonMain/kotlin/com/closet/core/data/weather/ItemWarmth.kt`
- Modify: `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/WeatherFitPrior.kt` (use the moved rain sets)
- Test: `core/data/src/commonTest/kotlin/com/closet/core/data/weather/ItemWarmthTest.kt`

**Interfaces:**
- **Produces:**
  ```kotlin
  enum class WarmthLevel { NONE, LIGHT, MEDIUM, WARM, HEAVY }
  object ItemWarmth {
      /** Rain-protective subcategories and materials, moved from WeatherFitPrior (same contents). */
      val RAIN_PROTECTIVE_SUBCATEGORIES: Set<String>
      val RAIN_PROTECTIVE_MATERIALS: Set<String>
      fun derivedWarmth(categoryName: String?, subcategoryName: String?, materials: Set<String>): WarmthLevel
      fun derivedRainReady(categoryName: String?, subcategoryName: String?, materials: Set<String>): Boolean
  }
  ```
- **Derivation table** (spec §1, indicative; the implementer finalises it in KDoc):
  - **NONE:** T-Shirt, Tank Top, Blouse, Shirt, Polo, Bodysuit, all Bottoms and Dresses & Jumpsuits, all Footwear, Activewear, Underwear & Intimates, Swimwear, Bags.
  - **LIGHT:** Sweatshirt, Hoodie, Cardigan, Blazer, Vest, Raincoat, Track Jacket, Scarf, Hat/Cap.
  - **MEDIUM:** Sweater, Jacket.
  - **WARM:** Coat.
  - **Material modifiers:** Wool, Cashmere or Fleece raise the level by one, only for items that start at LIGHT or above. Linen or Mesh lower it by one. The result is clamped to NONE..HEAVY.
- **Rain-ready:** Raincoat or Boots always. Jacket or Coat only when a material is in `RAIN_PROTECTIVE_MATERIALS`. Otherwise false. Keep the meaning of WeatherFitPrior's sets; document any difference.

- [ ] **Step 1: Write the failing tests:**
  - `a tee derives none`;
  - `a wool sweater derives warm`;
  - `a coat derives warm and a wool coat heavy`;
  - `a raincoat is rain ready`;
  - `a nylon jacket is rain ready and a cotton jacket is not`;
  - `boots are rain ready`;
  - `material modifiers stay within the scale` (Review Focus 2);
  - `an item with no subcategory or materials derives none and not rain ready` (Review Focus 1);
  - `linen lowers a light item to none`.
- [ ] **Step 2: Run** `./gradlew :core:data:jvmTest --console=plain -q`. **Expected:** compile failure, then behavioural failures against TODO() stubs.
- [ ] **Step 3: Implement.** Move the sets and point `WeatherFitPrior` at them.
- [ ] **Step 4: Run** `./gradlew :core:data:jvmTest --console=plain -q`, then `./gradlew :features:recommendations:jvmTest --console=plain -q` (WeatherFitPrior's tests unchanged), then `./gradlew :core:data:compileCommonMainKotlinMetadata --console=plain -q`. **Expected:** PASS.
- [ ] **Step 5: Commit** `feat(data): derive item warmth level and rain-readiness (#147)`.

### Task 3: Item form overrides

**Files:**
- Modify: `features/wardrobe/src/commonMain/kotlin/com/closet/features/wardrobe/ClothingFormViewModel.kt`, `ClothingFormScreen.kt` (or `FormComponents.kt`) and the wardrobe `composeResources` strings.
- Test: `features/wardrobe/src/jvmTest/.../ClothingFormViewModelTest.kt` (or the existing form VM test file).

**Interfaces:**
- **Consumes:** Task 1's entity fields and Task 2's `ItemWarmth` and `WarmthLevel`.
- **Produces:**
  - form state `warmthOverride: WarmthLevel?` and `rainReadyOverride: Boolean?`;
  - derived values for the Auto label, recomputed whenever the category, subcategory or materials change;
  - two dropdowns:
    - **Warmth:** Auto (<derived>) / None / Light / Medium / Warm / Heavy;
    - **Rain-ready:** Auto (<yes|no>) / Yes / No.

    Auto stores null. Editing an item shows its saved override.

- [ ] **Step 1: Write the failing VM tests:**
  - `auto label follows the derived value` (change the subcategory and the label updates);
  - `saving with auto keeps overrides null` (Review Focus 3);
  - `saving an explicit override stores it`;
  - `editing an item shows its saved override`.
- [ ] **Step 2: Run** `./gradlew :features:wardrobe:jvmTest --console=plain -q`. **Expected:** failures.
- [ ] **Step 3: Implement** the VM state and the two dropdowns, following the form's existing dropdown pattern. Strings go in compose resources.
- [ ] **Step 4: Run** `./gradlew :features:wardrobe:jvmTest --console=plain -q`, then `./gradlew :features:wardrobe:compileCommonMainKotlinMetadata --console=plain -q`. **Expected:** PASS.
- [ ] **Step 5: Commit** `feat(wardrobe): warmth and rain-ready overrides on the item form (#147)`.

### Task 4: Effective values on EngineItem (no engine behaviour change)

**Files:**
- Modify:
  - `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/EngineModels.kt`: `EngineItem` gains `warmthLevel: WarmthLevel = WarmthLevel.NONE` and `rainReady: Boolean = false`;
  - `RecommendationPipeline.kt`: compute the effective values when building `EngineItem`s;
  - the training-items path in `RecommendationViewModel`, if it builds `EngineItem`s: same.
- Create: `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/EffectiveWeather.kt`.
- Test: `features/recommendations/src/commonTest/.../engine/EffectiveWeatherTest.kt`, plus a pipeline test.

**Interfaces:**
- **Produces:**
  ```kotlin
  internal object EffectiveWeather {
      fun warmth(derived: WarmthLevel, override: Int?, temps: ItemTempPercentiles?): WarmthLevel
      fun rainReady(derived: Boolean, override: Boolean?, rain: ItemRainSuitability?): Boolean
  }
  ```
- **The refinement:**
  - **Warmth:** with at least 5 logs carrying weather (`MIN_LOGS_FOR_SIGNAL`), if the item's 10th-percentile worn temperature is below the level's documented typical lower comfort bound by more than 3 °C, raise the level by one. Never lower it, and never raise it by more than one. Document the per-level lower-bound table with its ASHRAE-grounded reasoning.
  - **Rain-ready:** with at least 5 logs, a not-rain-ready item becomes rain-ready when its credibility-blended rain suitability (WeatherFitPrior's blending, K = 5) is at least 0.6. It never turns a rain-ready item off.
  - **An override** (non-null) always wins.
- **The engine must not read** `warmthLevel` or `rainReady` in PR 1.

- [ ] **Step 1: Write the failing tests:**
  - `history raises warmth by at most one level` (Review Focus 4: p10 far below the bound raises exactly one level; fewer than 5 logs leaves it unchanged);
  - `history never lowers warmth`;
  - `an override wins over derivation and history` (Review Focus 5);
  - `frequent rainy wear makes an item rain ready`;
  - a pipeline test, `engine items carry effective warmth and rain readiness`, with a mocked repository: an item with a subcategory, materials and an override yields the expected `EngineItem` fields.
- [ ] **Step 2: Run** `./gradlew :features:recommendations:jvmTest --console=plain -q`. **Expected:** failures.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :features:recommendations:jvmTest --console=plain -q`, then `./gradlew :features:recommendations:scoreboard --console=plain -q` (the baseline must pass unchanged, which is the no-behaviour-change proof), then `./gradlew :features:recommendations:compileCommonMainKotlinMetadata --console=plain -q`. **Expected:** PASS.
- [ ] **Step 5: Commit** `feat(recommendations): effective item warmth and rain-readiness on engine items (#147)`.

## Whole-branch verification (controller)
- **The full pre-PR command** in `app-android/CLAUDE.md`, plus `:core:data:connectedAndroidDeviceTest` on the test device, because the schema changed.
- **Device smoke test** on the test S10+:
  1. Install over the existing data, so the v4 → v5 migration runs.
  2. Open an item: the two dropdowns show Auto with sensible derived values.
  3. Set an override, save, and reopen it.
  4. Get suggestions, which must still work.
