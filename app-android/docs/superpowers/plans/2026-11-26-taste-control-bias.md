# Taste learning: unbiased control outfits. Implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** taste learning stops inverting the rules. Its controls become uniform seeded samples, with completions that use the same slots as the chosen outfit. Learned weights are bounded at `[β₀−3, β₀+6]` (that is, [−2, 7]), the acceptance gate is stricter (2·SE gate), and everyone retrains.

**Architecture:**
- Only `HistoricalStratumBuilder` changes how it constructs controls. The scoring and features of each control are unchanged (`OutfitFeatureExtractor`, `scoreCombo` for the baseline offset).
- `ConditionalLogitOptimizer` gains a drift bound that applies at every data size.
- The weights schema bump forces a retrain.

**Tech stack:** Kotlin Multiplatform (`features/recommendations` commonMain), kotlin.test, `kotlin.random.Random`.

**Spec:** `docs/superpowers/specs/2026-11-26-taste-control-bias-design.md` (D1–D5). Owner ruling (revised): the drift bound is asymmetric, `[β₀−3, β₀+6]`, i.e. [−2, 7].

## Global Constraints
- **Seeding:** use `Random(seed = logId)` per stratum, created once and used for every draw in that stratum, in a fixed order. Never use the clock or an unseeded `Random`.
- **Sampling is uniform, without replacement:**
  - core role candidates: at most `MAX_CORE_ROLE_CANDIDATES = 20` per core role;
  - competing cores: at most `MAX_CONTROLS_PER_STRATUM = 30`.
  - Nothing may rank or filter controls by a default score.
- **Exclusions:** the worn core and other cores worn on the same day stay excluded.
- **Slot-matched completions:** each control gets exactly the chosen outfit's count of items per optional role (`Outerwear`, `Footwear`, `Accessory`). Each item is drawn uniformly, without replacement within the control, from that role's full available pool. If the pool is smaller than the count, use the whole pool.
- **Drift bound:** `MAX_DRIFT_DOWN = 3.0` and `MAX_DRIFT_UP = 6.0`, so every weight lies in `[β₀−3, β₀+6]` = [−2, 7] at every data size. The existing `THIN_DATA_MAX_DRIFT = 0.5` still applies below 30 strata.
- **Schema:** `TASTE_WEIGHTS_SCHEMA_VERSION = "v4"`; `FEATURE_COUNT` stays 10.
- **Purity:** commonMain and the engine stay pure (no I/O, no clock). commonTest test names use words only, with none of `, . ; : / < > [ ] \ ( )`.
- **Gradle:** one invocation at a time, in the foreground. Never use `--rerun-tasks`, and never kill daemons.
- **Commits:** signed. Never bypass signing; report BLOCKED on a signing error. Never stage `.claude/settings.json`.

## Review Focus
1. **A wardrobe with fewer competing cores than 30, or fewer role items than 20,** uses all of them, with no crash and no duplicates. Test: Task 1, `small pools use every candidate once`.
2. **A worn outfit with two accessories** gets controls with two accessories each. When the pool has only one accessory, the control gets that one and nothing crashes. Test: Task 1, `slot counts match the chosen outfit`.
3. **A worn outfit that is a OnePiece core** is handled the same way as a Top + Bottom core. Test: Task 1, `one-piece strata sample one-piece and top-bottom cores alike`.
4. **Determinism under reordering:** feeding the same logs in a different list order gives identical strata. The builder already sorts logs, so seeds tie to `logId`. Test: Task 1, `strata are identical for shuffled log order`.
5. **NaN or infinite learned weights** still fall back to β₀ before clamping. Test: Task 2, `drift bound clamps every data size and keeps the non-finite fallback`.

---

### Task 1: Uniform seeded controls with slot-matched completions

**Files:**
- Modify: `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/learning/HistoricalStratumBuilder.kt`. Change `buildSingleStratum`; delete `selectStratifiedControls`, `findBestCompletion`, `BEAM_PER_CORE_COMPLETION` and `MAX_OPTIONAL_ROLE_CANDIDATES`, plus the optional-slot dedup if nothing else uses it; update the KDocs that describe hard negatives and best completion.
- Test: `features/recommendations/src/commonTest/kotlin/com/closet/features/recommendations/engine/learning/HistoricalStratumBuilderTest.kt`. Rewrite the tests that assert stratified, hard-negative or best-completion behaviour; add the tests below.

**Interfaces:**
- Consumes: `buildStrata(items, logs, createdDates, itemNeighbors, engine, maxControls)`, unchanged; and `ChoiceStratum(chosenFeatures, controlFeatures, logId, chosenBaselineOffset, controlBaselineOffsets)`, unchanged.
- Produces: the same `ChoiceStratum` shape. Controls are now uniform samples. The footwear (`IDX_FOOTWEAR`) and layering (`IDX_LAYERING`) features are constant across `chosenFeatures` and every `controlFeatures` within a stratum.

- [ ] **Step 0: Record the baseline before changing anything.** Add a temporary, uncommitted `println` in `ReplayCheckTest` of fixed and learned `hitRateAt10` and `heldOutLogLik`. Run `./gradlew :features:recommendations:jvmTest --tests "*ReplayCheckTest*" --console=plain -q`, record the numbers in the report, then revert the file so `git diff` shows nothing for it. Task 2 records the after numbers the same way.
- [ ] **Step 1: Write the failing tests.** Use the existing builder-test fixtures, and extend them with a helper wardrobe that has tops, bottoms, shoes, outerwear, accessories and seeded colours.
  - **`footwear and layering are constant within every stratum`:** logs mix with-shoes and without-shoes outfits. Every stratum's `controlFeatures[k][IDX_FOOTWEAR]` equals `chosenFeatures[IDX_FOOTWEAR]`; the same holds for `IDX_LAYERING`.
  - **`slot counts match the chosen outfit`:** a log with 1 shoe and 2 accessories yields controls with 1 shoe and 2 accessories. Inspect this through a test seam: expose `internal fun sampleControlOutfits(...)` (or similar) that returns the control item lists alongside the features. With only 1 accessory in the pool, the controls have 1.
  - **`controls are not rule ranked`:** over a wardrobe with many cores, the mean default `scoreCombo` of sampled control cores is within 0.02 of the mean over **all** competing cores. Under the old builder, it was clearly above.
  - **`small pools use every candidate once`:** fewer than 30 competing cores gives each of them exactly once, minus the worn core and same-day cores.
  - **`strata are identical for shuffled log order`:** the same logs, shuffled, give equal strata.
  - **`one-piece strata sample one-piece and top-bottom cores alike`.**
- [ ] **Step 2: Run** `./gradlew :features:recommendations:jvmTest --console=plain -q`. **Expected:** the new tests fail.
- [ ] **Step 3: Implement D1 and D2.** In each stratum, in this order:
  1. Create `val rnd = Random(logId)`.
  2. Index the competing cores over the full space of `T·B + O` (tops, bottoms and one-pieces each sorted by id), excluding the worn core and same-day cores. No per-role cap: capping a role first breaks uniformity (final review I1).
  3. Uniformly draw at most `maxControls` distinct valid indices (all of them when fewer are valid), in O(`maxControls`), never materialising the T·B pairs.
  4. Score the chosen outfit on its projection to the controls' structure: its core plus its Outerwear, Footwear and Accessory items (final review I2).
  5. For each sampled core, in sampled order, and for each optional role in the fixed order Outerwear, Footwear, Accessory: draw the worn count of items uniformly without replacement from that role's full available pool.
  6. Compute features and baseline offsets exactly as today.

  Use `shuffled(rnd).take(n)` or an equivalent partial Fisher–Yates, applied to lists **sorted by item id first**, so the input order can't leak into the draws.
- [ ] **Step 4: Run** `./gradlew :features:recommendations:jvmTest --console=plain -q`. **Expected:** PASS, except tests that only assert the learned *outcome* of training (`TasteWeightTrainerTest` and others), whose values Task 2 revisits. List any such failure in the report rather than weakening it.
- [ ] **Step 5: Commit** `fix(recommendations): taste-learning controls are uniform samples with slot-matched completions (#135)`.

### Task 2: Drift bound, schema v4, and learning-outcome tests

**Files:**
- Modify:
  - `.../engine/learning/ConditionalLogitOptimizer.kt`: add `MAX_DRIFT_DOWN = 3.0` and `MAX_DRIFT_UP = 6.0` and apply them in the unstandardize step. Keep the non-finite fallback first, then clamp: ±0.5 below 30 strata, otherwise `[β₀−3, β₀+6]`.
  - `.../engine/learning/TasteWeightTrainer.kt`: the stricter acceptance gate (D3b), a 2·SE gate. Learned weights are accepted only when the chosen λ's mean held-out Δℓ is greater than 2 × its standard error (`GATE_SE_MULTIPLIER = 2.0`); otherwise `DEFAULT`. The owner chose 2·SE over 1·SE after the final review: at 1·SE, 3 of 20 pure-noise seeds were accepted and inverted rules.
  - `.../RecommendationViewModel.kt`: `TASTE_WEIGHTS_SCHEMA_VERSION = "v4"`.
  - `RecommendationViewModelSessionTest`: update the watermark literal.
  - `CHANGELOG.md` `[Unreleased]`, under Fixed: "Learned taste no longer inverts the rules (suggestions without shoes); weights retrain once."
- Create: `features/recommendations/src/commonTest/kotlin/com/closet/features/recommendations/engine/learning/LearningOutcomeTest.kt`, and a fixture builder beside it (`LearningFixtures.kt`).
- Test: also `ConditionalLogitOptimizerTest.kt` and `TasteWeightTrainerTest.kt`. Fix the Task 1 fallout honestly: re-derive expectations, and never loosen an assertion just to pass.

**Interfaces:**
- Consumes: Task 1's builder, and `TasteWeightTrainer.trainFromHistory(...)` (read its signature).
- Produces: `ConditionalLogitOptimizer.MAX_DRIFT_DOWN = 3.0` and `MAX_DRIFT_UP = 6.0`, and the 2·SE gate (`TasteWeightTrainer.GATE_SE_MULTIPLIER = 2.0`).

- [ ] **Step 1: Write the failing tests.**
  - **`drift bound clamps every data size and keeps the non-finite fallback`:** with ≥ 30 strata, a separable synthetic set has every weight in `[1 − 3, 1 + 6]` = [−2, 7]. A NaN path still gives β₀.
  - **`shoe-less logs do not teach shoe-less outfits`:** 120 logs, 20% without shoes and otherwise random choices. The trained footwear and layering weights are exactly 1.0, and after training the engine's top suggestion contains footwear.
  - **`random choices invert nothing`:** 120 uniformly random logs. Every weight is within `1 ± 1.0`.
  - **`random choices invert nothing whenever the gate admits a fit`:** the same fixture over 20 seeds. The 2·SE gate accepts at most 1 of them, and every accepted fit is within `1 ± 1.0`.
  - **`a colour matching user learns a positive hue weight`:** 120 logs where 80% of cores are complementary or analogous pairs (use seeded hexes). The hue harmony weight is ≥ 1.0.
  - **`training is deterministic and clock free`:** identical weights twice, and identical weights when every log date is shifted by the same offset. (If the trainer reads `today`, pass a different one.)
- [ ] **Step 2: Run** `./gradlew :features:recommendations:jvmTest --console=plain -q`. **Expected:** the drift-bound test fails before the bound exists. Record whether each outcome test passes or fails at the Task 1 commit. Any outcome test that already passes at Task 1 is a regression guard: say so.
- [ ] **Step 3: Implement** the bound, the schema bump and the CHANGELOG entry.
- [ ] **Step 4: Run**, one at a time:
  1. `./gradlew :features:recommendations:jvmTest --console=plain -q`
  2. `./gradlew :features:recommendations:compileCommonMainKotlinMetadata --console=plain -q`
  3. `./gradlew :features:recommendations:compileTestKotlinIosSimulatorArm64 --console=plain -q`

  **Expected:** PASS, with both `ReplayCheckTest` floors held and `EnginePerformanceTest` within budget. The no-planted-taste replay check uses the owner's tolerance: learned held-out LL ≥ fixed − 0.05 × the number of held-out strata (about 1 nat at 20). Record the replay numbers after the change. If a floor fails, report it. Never lower floors or tune constants.
- [ ] **Step 5: Commit** `fix(recommendations): bound learned weights at [-3,+6], stricter acceptance, retrain (schema v4) (#135)`.

## Whole-branch verification (controller)
- **Tests:** the full pre-PR command from `app-android/CLAUDE.md`, which includes `iosCompileCheck`.
- **Device smoke on the test device:** after the upgrade, suggestions retrain. Check that the stored `taste_weights` (`ai_prefs`) have footwear and layering at 1.0 and every weight within [−2, 7], and that the top suggestion includes shoes.
- **PR:** report the replay numbers before and after.
