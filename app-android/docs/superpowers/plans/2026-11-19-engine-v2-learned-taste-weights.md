# Recommendation Engine v2: Learned Taste Weights — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Issue #114 (under umbrella #94 / Engine v2). Replace static hand-tuned scoring weights with **personalized taste weights** learned on-device from the user's wear logs using a regularized conditional logit (McFadden choice model) and a pure-Kotlin Newton-Raphson solver.

**Issue:** #114 (part of #96, following Deliveries 1–3 in PR #115 and PR #116).  
**Spec & Research References:**
- `docs/superpowers/specs/2026-11-17-recommendation-engine-v2-design.md`
- Issue #114 comments 1–3 (McFadden Conditional Logit, IIA core deduplication, and Reid & Tibshirani 2014 `clogitL1` penalized conditional logistic regression).
- Related open issues: #81 (Color v2 measured shades + shares + `wears_as_neutral`), #106 (weather-fit prior), #105 (laundry wears-before-wash), #67 (Preference repository `DataResult` + `.catch` error contract).

---

## Architecture & Mathematical Formulation

### 1. Choice Model (Conditional Logit)
Each logged outfit wear is a stratum $d$. The worn outfit is the single case ($y = 1$), and competing clean, in-season candidate outfits available on day $d$ are the controls ($y = 0$).
- If a day has two logs (e.g., an OOTD log plus a second non-OOTD log), each valid core-containing log forms its own 1-case stratum, with any same-day other logged core removed from that stratum's control candidates (Comment 2, §4).
- Conditioning removes per-stratum intercepts (mood, raw ambient temperature, day-of-week), meaning utilities only rank candidates *within* a day and any feature constant across a stratum's candidates cancels out (Comment 2, §2; Comment 3, §5).

The probability that outfit $j$ was chosen among candidates $C_d$ is:
$$P(\text{worn} = j \mid C_d) = \frac{\exp(u_j)}{\sum_{k \in C_d} \exp(u_k)}$$
where utility $u_j = \beta^\top x_j$.

Note on Gumbel-top-k alignment (Comment 1, §2): Because `BeamSelect` samples using $\text{key}_j = \ln(\text{score}_j)/T + \text{Gumbel}(0, 1)$, setting $\ln(\text{score}_j) = \beta^\top x_j$ fits the exact latent-utility model the beam samples from.

### 2. Candidate Outfit Feature Vector ($x \in \mathbb{R}^8$)
Features correspond directly to the terms in `OutfitRecommendationEngine.scoreCombo` (using the companion constants from `OutfitRecommendationEngine`):
- $x_0 = \ln(\text{meanItem})$ — weather suitability (`0.55` temp, `0.60` rain, `0.70` wind) and wear recency (`0.90` $\le 3$d, `1.05` $\ge 30$d).
- $x_1 = (1 - W) \ln(\text{hueMultiplier})$ — hue-based color harmony across chromatic hex pairs (`1.05` analogous/complementary, `0.90` clash).
- $x_2 = (1 - W) \ln(\text{familyMultiplier})$ — coarse color family harmony rule (`1.10` same-family, `1.05` Earth+Warm, `0.70` Bright+Bright, `0.85` Warm+Cool).
  - *Exact Factorization Rule (and #81 Forward Compatibility):* In `colorHarmonyMultiplier`, across the $P_{\text{color}}$ non-Neutral item pairs, each pair $(a, b)$ uses the hue rule iff `hue != null && (familyA != familyB || hue >= familyRule)`; otherwise it uses `familyRule`. To guarantee $\text{hueMultiplier} \times \text{familyMultiplier} \equiv \text{colorHarmonyMultiplier}$ at $\beta_1 = \beta_2 = 1.0$, pairs governed by hue contribute `hue` to `hueProduct` (and `1.0` to `familyProduct`), while pairs governed by family rules contribute `familyRule` to `familyProduct` (and `1.0` to `hueProduct`), both raised to $1 / P_{\text{color}}$. Keeping this logic centralized in `OutfitFeatureExtractor` / `PairScorer` ensures that when #81 (Color v2: OKLCh share-weighted harmony and `wears_as_neutral`) or #106 (weather-fit prior) lands, the feature semantics remain invariant and only the watermark needs to trigger a re-fit.
- $x_3 = (1 - W) \ln(\text{patternMultiplier})$ — single patterned bonus (`1.10`) / multi-patterned clash penalty (`0.75`).
- $x_4 = (1 - W) \ln(\text{formalityMultiplier})$ — geometric mean of pairwise formality gap penalty (`0.85` when $|a - b| \ge 2$).
- $x_5 = W \ln(H)$ — co-wear habit lift $H \in [0.7, 1.3]$ (including saved-outfit co-membership at $0.5$ weight and 2-level embedding/subcategory backoff), weighted by mean evidence confidence $W \in [0, 0.7]$. Kept as a single combined history feature to avoid collinearity (Comment 1, §4).
- $x_6 = \ln(\text{layeringMultiplier})$ — weather layering fit (`0.50` cold-without-outer, `0.75` warm-with-outer).
- $x_7 = \ln(\text{footwearMultiplier})$ — missing shoes penalty (`0.85`) when footwear exists in pool.

Default weights: $\beta_0 = [1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0]^\top$.
- *Cold-Start & Blend Parity:* When `tasteWeights.isDefault` is true, `scoreCombo` preserves the exact legacy arithmetic blend $\text{meanItem} \times [W \cdot H + (1 - W) \cdot R] \times \text{layering} \times \text{footwear}$ so all existing deterministic tests match to the last bit. When `!tasteWeights.isDefault`, `scoreCombo` evaluates $\exp(\beta^\top x)$, which corresponds to the log-linear (geometric) utility model centered at $\beta_0 = \mathbf{1}$.

### 3. Core-Level IIA Mitigation (Deduplication)
To avoid Independence of Irrelevant Alternatives (IIA) violations ("red bus / blue bus" problem from near-duplicate accessory/shoe permutations splitting softmax probability; Comment 1, §4) without requiring nested logit derivatives:
- For stratum $d$, the chosen core is represented by the **actual worn outfit** ($y = 1$).
- Each competing core (Top × Bottom or OnePiece, excluding any other core worn on the same date $d$) is represented by its **single highest-default-scoring completion** (best optional outerwear, footwear, accessory combination under $\beta_0$).
- Keeps the exact closed-form 1-case-per-stratum math without nested derivatives or Gail et al. recursions (Comment 3, §1).

### 4. Regularized Newton-Raphson Solver
Following Reid & Tibshirani (2014) §2.1–2.6 with an $L_2$ Gaussian prior centered at $\beta_0$:
- **Standardization & Zero-Variance Guard:**
  - Within each stratum $d$, conditioning removes the stratum mean, so compute the Pooled Within-Stratum (or global centered) sample standard deviation $\sigma_j$ for each feature $j \in \{0 \dots 7\}$ across all training candidates.
  - **Zero-Variance Guard:** If $\sigma_j < 10^{-8}$ (e.g., weather never logged so $x_6 \equiv 0$, or no hex colors tagged so $x_1 \equiv 0$), mark feature $j$ as **inactive/frozen**: set $\sigma_j = 1.0$, zero out $\tilde{x}_{d, k, j} = 0$, and keep $\hat{\beta}_j = \beta_{0, j} = 1.0$.
  - For active features ($\sigma_j \ge 10^{-8}$): standardized feature $\tilde{x}_j = x_j / \sigma_j$ and standardized prior center $\tilde{\beta}_{0,j} = \beta_{0,j} \sigma_j$.
- **Penalized Log-Likelihood Objective (Maximization):**
  $$\ell(\tilde{\beta}) = \sum_d \left( \tilde{\beta}^\top \tilde{x}_{\text{worn}, d} - \text{LSE}_{k \in C_d}(\tilde{\beta}^\top \tilde{x}_{d, k}) \right) - \frac{\lambda}{2} \|\tilde{\beta} - \tilde{\beta}_0\|_2^2$$
  where $\text{LSE}_{k \in C_d}(u_k) = m_d + \ln \sum_{k \in C_d} \exp(u_k - m_d)$ with $m_d = \max_{k \in C_d} u_k$ prevents floating-point overflow.
- **Gradient (Score Vector):**
  $$g(\tilde{\beta}) = \nabla \ell(\tilde{\beta}) = \sum_d (\tilde{x}_{\text{worn}, d} - \tilde{\mu}_d) - \lambda (\tilde{\beta} - \tilde{\beta}_0)$$
  where $p_{d, k} = \exp(u_{d, k} - \text{LSE}_d)$ and $\tilde{\mu}_d = \sum_{k \in C_d} p_{d, k} \tilde{x}_{d, k}$.
- **Negative Hessian (Fisher Information + Penalty):**
  $$A(\tilde{\beta}) = -\nabla^2 \ell(\tilde{\beta}) = \sum_d \text{Cov}_d(\tilde{x}) + \lambda I_8 = \sum_d \sum_{k \in C_d} p_{d, k} (\tilde{x}_{d, k} - \tilde{\mu}_d)(\tilde{x}_{d, k} - \tilde{\mu}_d)^\top + \lambda I_8$$
  Since $\text{Cov}_d(\tilde{x}) \succeq 0$ and $\lambda > 0$, matrix $A$ is strictly symmetric positive-definite (SPD).
- **Newton Ascent Step:** Solve $A \Delta\tilde{\beta} = g(\tilde{\beta})$ via Cholesky decomposition ($A = L L^\top$) in pure Kotlin (~25 lines). Because $A \succ 0$, $\Delta\tilde{\beta}^\top g(\tilde{\beta}) > 0$ is guaranteed to be an ascent direction whenever $g(\tilde{\beta}) \ne 0$.
- **Armijo Backtracking Line Search:** Start at step size $\alpha = 1.0$, halving $\alpha \leftarrow 0.5\alpha$ until the maximization sufficient-increase condition holds:
  $$\ell(\tilde{\beta} + \alpha \Delta\tilde{\beta}) \ge \ell(\tilde{\beta}) + c_1 \alpha \, g(\tilde{\beta})^\top \Delta\tilde{\beta} \quad (c_1 = 10^{-4})$$
  Stop iterating when $\|g(\tilde{\beta})\|_\infty < 10^{-6}$ or $\|\alpha \Delta\tilde{\beta}\|_\infty < 10^{-6}$ (typically 4–8 iterations).
- **Unstandardization & Drift Clamping:** $\hat{\beta}_j = \tilde{\beta}_j / \sigma_j$ (for active features; $\beta_{0, j}$ for frozen features). When $N_{\text{strata}} < 30$, clamp maximum drift $\|\hat{\beta} - \beta_0\|_\infty \le 0.5$.

### 5. Time-Ordered Cross-Validation & Sample Guard
- **Cold-Start Guard:** If $N_{\text{strata}} < 20$, return $\beta_0$ directly without fitting (respecting the "rule of ten" / thin-evidence regime in Comment 1, §4).
- **Path / Grid Search with Warm Starts:** Evaluate $\lambda \in \{100.0, 30.0, 10.0, 3.0, 1.0, 0.3, 0.1\}$ (walked from $\lambda_{\max} = 100.0$ down to $\lambda_{\min} = 0.1$ using warm starts; Comment 3, §2) across expanding-window time-ordered splits (e.g. initial training window of $\max(10, \lfloor 0.5 N \rfloor)$ strata, advancing in blocks across the remaining held-out validation strata).
- **Per-Stratum One-Standard-Error Rule:** For each out-of-sample validation stratum $d \in V$, record its held-out conditional log-likelihood improvement over the default prior:
  $$\Delta\ell_d(\lambda) = \ell_d(\hat{\beta}(\lambda)) - \ell_d(\beta_0)$$
  Compute the mean $\overline{\Delta\ell}(\lambda)$ and standard error $\text{SE}(\lambda) = \text{sd}(\{\Delta\ell_d(\lambda)\}_{d \in V}) / \sqrt{|V|}$ across the individual validation strata. Let $\lambda_{\text{best}}$ maximize $\overline{\Delta\ell}(\lambda)$. Select the **largest** $\lambda^* \ge \lambda_{\text{best}}$ (strongest regularization toward $\beta_0$) such that:
  $$\overline{\Delta\ell}(\lambda^*) \ge \overline{\Delta\ell}(\lambda_{\text{best}}) - \text{SE}(\lambda_{\text{best}})$$
  Finally, if $\overline{\Delta\ell}(\lambda^*) \le 0$ (no improvement over defaults on held-out strata), fall back to $\beta_0$; otherwise refit on all $N_{\text{strata}}$ using $\lambda^*$.

---

## Global Constraints

1. **Pure Kotlin in `commonMain`:** Solver, matrix math, feature extraction, and candidate reconstruction must have no Android or external linear algebra dependencies (works identically on `full`, `foss`, JVM, and iOS).
2. **Cold-Start Parity:** With `TasteWeights.DEFAULT`, existing engine tests must produce identical scores and rankings.
3. **Causality & Zero Leakage:** In historical stratum $d$, candidate items, weather signals, and co-wear/recency statistics must use only items and logs strictly prior to $d$ (`log.date < d`).
4. **Time Abstraction:** Use `AppClock` for current date/timestamps.
5. **Replay Check & Variety Gates:**
   - Learned weights must improve held-out conditional log-likelihood over `TasteWeights.DEFAULT` and maintain/exceed the hit-rate@10 floor (`0.15`) on `PlantedTasteFixture` in `ReplayCheckTest`.
   - Learned weights must not regress a fixture with no planted taste (random wear logs stay near $\beta_0$).
   - Existing echo-chamber / rotation / core-diversity session tests (`EngineBeamTest`, `RecommendationViewModelSessionTest`) must remain green.

---

## Tasks

### Task 1: Feature Extraction & `TasteWeights` Model

- [x] **1.1 Create `TasteWeights` model**
  - Create `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/learning/TasteWeights.kt`.
  - Properties: `meanItem: Double = 1.0`, `hueHarmony: Double = 1.0`, `familyHarmony: Double = 1.0`, `patternMix: Double = 1.0`, `formalityGap: Double = 1.0`, `historyLift: Double = 1.0`, `layering: Double = 1.0`, `footwear: Double = 1.0`.
  - Helpers: `toDoubleArray()`, `fromDoubleArray(DoubleArray)`, `DEFAULT = TasteWeights()`, `isDefault: Boolean`, compact JSON or comma-separated serialization/deserialization helpers with safe fallback to `DEFAULT`.
- [x] **1.2 Create `OutfitFeatureExtractor`**
  - Create `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/learning/OutfitFeatureExtractor.kt`.
  - Computes the 8-element `DoubleArray` feature vector $x$ from candidate combo items, item scores, weather, co-wear (with neighbors & subcategory backoff), and `hasFootwearInPool` flag.
  - Partition non-Neutral color pairs cleanly between `hueMultiplier` ($x_1$) and `familyMultiplier` ($x_2$) so that `hueMultiplier * familyMultiplier == colorHarmonyMultiplier(items)` identically.
- [x] **1.3 Integrate `TasteWeights` into `EngineInput` & `OutfitRecommendationEngine`**
  - Add `tasteWeights: TasteWeights = TasteWeights.DEFAULT` to `EngineInput`.
  - In `OutfitRecommendationEngine.scoreCombo`:
    - When `tasteWeights.isDefault`, preserve the exact existing arithmetic blend (`meanScore * multiplier`).
    - When `!tasteWeights.isDefault`, compute features via `OutfitFeatureExtractor.extract(...)` and return $\exp(\sum_{j=0}^7 \beta_j x_j)$.
- [x] **1.4 Tests for feature extraction and engine scoring**
  - Create `features/recommendations/src/commonTest/kotlin/com/closet/features/recommendations/engine/learning/TasteWeightsScoringTest.kt`.
  - Verify `hueMultiplier * familyMultiplier` equals `colorHarmonyMultiplier` across mixed chromatic hex and coarse family outfits.
  - Verify `TasteWeights.DEFAULT` matches legacy `recommend()` output and scores exactly, and that $\exp(\beta_0^\top x)$ matches the rules-only score when $W = 0$.
  - Verify setting individual weights (e.g. `patternMix = -1.0` or `hueHarmony = 2.0`) modifies candidate rankings predictably.

### Task 2: Pure On-Device Cholesky & Newton-Raphson Solver

- [x] **2.1 Implement `CholeskySolver`**
  - Create `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/learning/CholeskySolver.kt`.
  - Implements $A = L L^\top$ factorization for $p \times p$ SPD matrices, forward substitution ($L y = b$), and back substitution ($L^\top x = y$). Returns `null` safely if a matrix is numerically non-SPD.
- [x] **2.2 Implement `ConditionalLogitOptimizer`**
  - Create `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/learning/ConditionalLogitOptimizer.kt`.
  - Implements:
    - Feature spread standardization ($\sigma_j$) with zero-variance guard ($\sigma_j < 10^{-8} \implies$ freeze $\hat{\beta}_j = \beta_{0, j}$ and zero column $j$).
    - Prior center scaling ($\tilde{\beta}_{0, j} = \beta_{0, j} \sigma_j$).
    - Log-Sum-Exp stabilized log-likelihood $\ell(\tilde{\beta})$ and softmax probabilities $p_{d, k}$.
    - Closed-form score vector $g(\tilde{\beta})$ and penalized covariance Hessian $A(\tilde{\beta}) = \sum_d \text{Cov}_d(\tilde{x}) + \lambda I$.
    - Armijo backtracking line search on the maximization objective and warm-start support across $\lambda$ grids.
    - Unstandardization ($\hat{\beta}_j = \tilde{\beta}_j / \sigma_j$) and thin-data drift clamping ($\|\hat{\beta} - \beta_0\|_\infty \le 0.5$ when $N_{\text{strata}} < 30$).
- [x] **2.3 Unit tests for the optimizer**
  - Create `features/recommendations/src/commonTest/kotlin/com/closet/features/recommendations/engine/learning/ConditionalLogitOptimizerTest.kt`.
  - **Cold start test:** $N < 20$ returns $\beta_0$ exactly.
  - **Zero-variance feature test:** when a feature is constant within every stratum (e.g. $x_6 \equiv 0$ with no weather), its learned weight remains exactly `1.0` without `NaN`.
  - **Synthetic clash lover test:** on a dataset where clashing color/pattern combos are consistently chosen, `hueHarmony` / `familyHarmony` / `patternMix` weights drop $\le 0$.
  - **Separable data test:** weights stay finite under complete separation due to $\lambda \ge 0.1 > 0$.
  - **Oracle & convergence test:** verify gradient norm $\|g(\tilde{\beta}^*)\|_\infty < 10^{-5}$ at the optimum on a multi-stratum fixture and verify monotonic log-likelihood climb.
  - **Determinism test:** identical inputs produce bit-for-bit identical weights.

### Task 3: Historical Stratum Builder & Core-Level Deduplication

- [x] **3.1 Implement `HistoricalStratumBuilder`**
  - Create `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/learning/HistoricalStratumBuilder.kt`.
  - Reconstructs strata for historical wear logs in chronological order:
    - Item availability on date $d$: item is included if $\min(\text{createdAtDate}, \text{firstWornDate}) \le d$ (handling bulk-imported wardrobes whose `createdAt` timestamp postdates historical logs).
    - Skips logs whose worn items do not contain a valid core (Top + Bottom or OnePiece).
    - Same-day multiple logs: each valid log on date $d$ forms a 1-case stratum; any other core worn on date $d$ is excluded from that stratum's control candidates (Comment 2, §4).
    - Causality: co-wear statistics, temperature/rain/wind suitability signals, and `lastWornDates` for stratum $d$ are computed **strictly from logs with `log.date < d`**.
    - Case ($y=1$): 8-dim feature vector of the actual worn outfit on log $d$.
    - Controls ($y=0$): competing cores available on date $d$, each represented by its single highest-default-scoring completion (best optional outerwear/footwear/accessory extension under $\beta_0$), capped to the top $M$ (e.g. `CORE_BEAM = 30`) competing cores so stratum size matches the engine's candidate pool.
- [x] **3.2 Unit tests for stratum builder**
  - Create `features/recommendations/src/commonTest/kotlin/com/closet/features/recommendations/engine/learning/HistoricalStratumBuilderTest.kt`.
  - **Zero future leakage test:** adding or mutating a future log on day $d+1$ does not alter stratum $d$'s candidate set or feature vectors.
  - **Core deduplication (IIA) test:** adding near-duplicate optional accessories/shoes to the wardrobe does not add duplicate core controls to stratum $d$, leaving competing core probabilities stable.
  - **Same-day multi-log test:** two logs on the same date produce two 1-case strata where neither worn core appears as a control for the other, and neither leaks into the other's `date < d` co-wear history.

### Task 4: Time-Ordered Cross-Validation & Repository Training Plumbing

- [x] **4.1 Implement time-ordered CV in `TasteWeightTrainer`**
  - Create `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/learning/TasteWeightTrainer.kt`.
  - Evaluates $\lambda \in \{100.0, 30.0, 10.0, 3.0, 1.0, 0.3, 0.1\}$ from largest to smallest with warm starts on expanding time-ordered folds.
  - Computes per-stratum held-out conditional log-likelihood improvement $\Delta\ell_d(\lambda) = \ell_d(\hat{\beta}(\lambda)) - \ell_d(\beta_0)$ and applies the one-standard-error rule toward larger $\lambda$.
  - Falls back to `TasteWeights.DEFAULT` if $\overline{\Delta\ell}(\lambda^*) \le 0$, otherwise fits final $\hat{\beta}$ on all strata at $\lambda^*$.
- [x] **4.2 DAO and Repository queries**
  - Add historical log query in `RecommendationDao` and `RecommendationRepository` to fetch chronological wear logs (`id`, `date`, `temperature_low`, `temperature_high`, `precipitation_mm`, `wind_speed_kmh`) and their snapshot `outfit_log_items` alongside wardrobe item metadata for training when the log count watermark changes.
- [x] **4.3 Verification tests**
  - Create `features/recommendations/src/commonTest/kotlin/com/closet/features/recommendations/engine/learning/TasteWeightTrainerTest.kt`.
  - Verify end-to-end training on multi-day synthetic history:
    - Consistent preference shifts weights in the expected direction and improves held-out log-likelihood.
    - Purely random choices select high $\lambda^*$ / default fallback via the 1-SE rule, keeping weights near `TasteWeights.DEFAULT`.

### Task 5: Preferences Persistence, ViewModel Wiring, Replay Gate & Smoke Test

- [x] **5.1 Preferences storage (aligned with #67 & #81/#106 schema versioning)**
  - Store `taste_weights` (serialized `TasteWeights`) and `taste_weights_log_watermark` (format: `"v1:$totalLogCount:$maxLogId"`, where bumping the `"v1"` prefix invalidates cached weights when #81 Color v2 or #106 weather-fit priors land) in `AiPreferencesRepository` (or `PreferencesRepository`).
  - Follow #67 error-handling best practices on the new preference methods: `.catch` DataStore `IOException`/corruption failures on the Flow getter (falling back to default/null) and re-throw `CancellationException` on suspend setters.
- [x] **5.2 Wire `RecommendationViewModel` with lazy retraining**
  - In `RecommendationViewModel.fetchAndRun`:
    - Read stored `TasteWeights` and watermark.
    - When `totalLogCount < 20`, use `TasteWeights.DEFAULT`.
    - When `totalLogCount >= 20` and the current log count/watermark differs from the stored watermark, run `TasteWeightTrainer` on `computationDispatcher`, persist the updated `TasteWeights` and watermark, and pass `tasteWeights` into `EngineInput`.
- [x] **5.3 Upgrade `ReplayCheckTest` gate & no-planted-taste control**
  - In `PlantedTasteFixture.kt`: add `buildNoPlantedTaste()` (0% planted triples, 100% uniform random valid outfits) while preserving the exact baseline seed-42 planted fixture.
  - In `ReplayHarness.kt` & `ReplayCheckTest.kt`:
    - Add `evaluateReplay(...)` that trains `TasteWeights` strictly on `earlier` logs (`date < log.date`) and evaluates both **hit-rate@10** and **held-out conditional log-likelihood** against `TasteWeights.DEFAULT`.
    - Assert that learned weights strictly improve held-out conditional log-likelihood over `TasteWeights.DEFAULT`, achieve hit-rate@10 $\ge 0.15$ (and $\ge$ fixed-weight hit rate) on `PlantedTasteFixture`, and do not regress a uniform random (no-planted-taste) fixture.
- [x] **5.4 Full verification suite & device smoke test**
  - Run all JVM and Android host unit tests across modules (`./gradlew test :core:data:jvmTest :core:ui:jvmTest :features:recommendations:jvmTest :shared:jvmTest`).
  - Run `./gradlew :features:recommendations:lint :core:data:lint`.
  - Run `assembleFullDebug` and `assembleFossDebug`.
