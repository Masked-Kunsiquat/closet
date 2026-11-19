# Recommendation Engine v2: Learned Taste Weights — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Issue #114 (under umbrella #94 / Engine v2). Replace static hand-tuned scoring weights with **personalized taste weights** learned on-device from the user's wear logs using a regularized conditional logit (McFadden choice model) and a pure-Kotlin Newton-Raphson solver.

**Issue:** #114 (part of #96, following Deliveries 1–3 in PR #115 and PR #116).  
**Spec Reference:** `docs/superpowers/specs/2026-11-17-recommendation-engine-v2-design.md`.

---

## Architecture & Mathematical Formulation

### 1. Choice Model (Conditional Logit)
Each logged day is a stratum $d$. The worn outfit is the case ($y = 1$), and competing clean, in-season candidate outfits available on day $d$ are the controls ($y = 0$).
The probability that outfit $j$ was chosen among candidates $C_d$ is:
$$P(\text{worn} = j \mid C_d) = \frac{\exp(u_j)}{\sum_{k \in C_d} \exp(u_k)}$$
where utility $u_j = \beta^\top x_j$.

### 2. Candidate Outfit Feature Vector ($x \in \mathbb{R}^8$)
Features correspond to the terms already computed in `scoreCombo`:
- $x_0 = \ln(\text{meanItem})$ — weather suitability and wear recency.
- $x_1 = (1 - W) \ln(\text{hueMultiplier})$ — hue-based color harmony (chromatic hex pairs).
- $x_2 = (1 - W) \ln(\text{familyMultiplier})$ — coarse color family harmony rule.
- $x_3 = (1 - W) \ln(\text{patternMultiplier})$ — single patterned bonus (+0.10) / clash penalty (-0.20).
- $x_4 = (1 - W) \ln(\text{formalityMultiplier})$ — formality gap penalty (0.85 when $|a - b| \ge 2$).
- $x_5 = W \ln(H)$ — co-wear habit lift $H$, weighted by evidence confidence $W$.
- $x_6 = \ln(\text{layeringMultiplier})$ — weather layering fit (0.75 cold-without-outer, 0.80 warm-with-outer).
- $x_7 = \ln(\text{footwearMultiplier})$ — missing shoes penalty (0.80) when footwear exists in pool.

Default weights: $\beta_0 = [1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0]^\top$.

### 3. Core-Level IIA Mitigation (Deduplication)
To avoid IIA violations ("red bus / blue bus" problem from near-duplicate accessory permutations) without requiring nested logit derivatives:
- For stratum $d$, the chosen core is represented by the **actual worn outfit**.
- Each competing core (Top × Bottom or OnePiece) is represented by its **single highest-default-scoring completion** (with optional outerwear, footwear, accessories).
- Keeps the exact closed-form 1-case-per-stratum math without nested derivatives.

### 4. Regularized Newton-Raphson Solver
- **Standardization:** Compute sample standard deviation $\sigma_j$ for each feature across all training candidates. Standardized feature $\tilde{x}_j = x_j / \sigma_j$.
- **Prior Scaling:** Standardized prior center $\tilde{\beta}_{0,j} = \beta_{0,j} \sigma_j$.
- **Objective:**
  $$\ell(\tilde{\beta}) = \sum_d \left( \tilde{\beta}^\top \tilde{x}_{\text{worn}, d} - \ln \sum_{k \in C_d} \exp(\tilde{\beta}^\top \tilde{x}_{d, k}) \right) - \frac{\lambda}{2} \|\tilde{\beta} - \tilde{\beta}_0\|_2^2$$
- **Gradient:**
  $$g(\tilde{\beta}) = \sum_d (\tilde{x}_{\text{worn}, d} - \tilde{\mu}_d) - \lambda (\tilde{\beta} - \tilde{\beta}_0)$$
  where $p_{d, k} = \frac{\exp(\tilde{\beta}^\top \tilde{x}_{d, k})}{\sum_m \exp(\tilde{\beta}^\top \tilde{x}_{d, m})}$ and $\tilde{\mu}_d = \sum_k p_{d, k} \tilde{x}_{d, k}$.
- **Hessian:**
  $$A = \sum_d \text{Cov}_d(\tilde{x}) + \lambda I_8 = \sum_d \sum_k p_{d, k} (\tilde{x}_{d, k} - \tilde{\mu}_d)(\tilde{x}_{d, k} - \tilde{\mu}_d)^\top + \lambda I_8$$
  Since $\text{Cov}_d(\tilde{x}) \succeq 0$ and $\lambda > 0$, matrix $A$ is strictly symmetric positive-definite (SPD).
- **Newton Step:** Solve $A \Delta\tilde{\beta} = g(\tilde{\beta})$ via Cholesky decomposition ($A = L L^\top$) in pure Kotlin (~25 lines).
- **Line Search:** Armijo backtracking to guarantee monotonic likelihood convergence.
- **Unstandardization & Clamping:** $\hat{\beta}_j = \tilde{\beta}_j / \sigma_j$. When $N_{\text{strata}} < 30$, clamp maximum drift $\|\hat{\beta} - \beta_0\|_\infty \le 0.5$.

### 5. Time-Ordered Cross-Validation & Sample Guard
- If $N_{\text{strata}} < 20$: return $\beta_0$ directly (cold-start safeguard).
- Grid search over $\lambda \in \{0.1, 0.3, 1.0, 3.0, 10.0, 30.0, 100.0\}$ using expanding-window time-ordered folds (train on days $< t$, score on days $\ge t$).
- Select $\lambda^*$ using the one-standard-error rule toward stronger regularization (higher $\lambda$).

---

## Global Constraints

1. **Pure Kotlin in `commonMain`:** Solver, matrix math, feature extraction, and candidate reconstruction must have no Android or external linear algebra dependencies.
2. **Cold-Start Parity:** With `TasteWeights.DEFAULT`, existing engine tests must produce identical scores and rankings.
3. **Causality & Zero Leakage:** In historical stratum $d$, candidate items and co-wear/recency statistics must use only items and logs strictly prior to $d$ (`log.date < d`).
4. **Time Abstraction:** Use `AppClock` for current date/timestamps.
5. **Replay Check Gate:** Learned weights must improve held-out log likelihood and maintain/exceed the hit-rate@10 floor (0.15) in `ReplayCheckTest`.

---

## Tasks

### Task 1: Feature Extraction & `TasteWeights` Model

- [ ] **1.1 Create `TasteWeights` model**
  - Create `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/learning/TasteWeights.kt`.
  - Properties: `meanItem: Double = 1.0`, `hueHarmony: Double = 1.0`, `familyHarmony: Double = 1.0`, `patternMix: Double = 1.0`, `formalityGap: Double = 1.0`, `historyLift: Double = 1.0`, `layering: Double = 1.0`, `footwear: Double = 1.0`.
  - Companions: `DEFAULT = TasteWeights()`, `isDefault`, serialization/deserialization helpers.
- [ ] **1.2 Create `OutfitFeatureExtractor`**
  - Create `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/learning/OutfitFeatureExtractor.kt`.
  - Computes 8-dim `DoubleArray` from candidate combo items, item scores, weather, co-wear, and footwear pool flag.
- [ ] **1.3 Integrate `TasteWeights` into `EngineInput` & `OutfitRecommendationEngine`**
  - Add `tasteWeights: TasteWeights = TasteWeights.DEFAULT` to `EngineInput`.
  - In `OutfitRecommendationEngine.scoreCombo`:
    - When `tasteWeights.isDefault`, preserve the exact existing arithmetic blend.
    - When `!tasteWeights.isDefault`, score via exponential of weighted dot product: $\exp(\sum_j \beta_j x_j)$.
- [ ] **1.4 Tests for feature extraction and engine scoring**
  - Create `features/recommendations/src/commonTest/kotlin/com/closet/features/recommendations/engine/learning/TasteWeightsScoringTest.kt`.
  - Verify default weights match legacy output exactly.
  - Verify setting individual weights (e.g. `patternMix = 0.0` or `hueHarmony = 2.0`) modifies candidate rankings predictably.

### Task 2: Pure On-Device Cholesky & Newton-Raphson Solver

- [ ] **2.1 Implement `CholeskySolver`**
  - Create `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/learning/CholeskySolver.kt`.
  - Implements $L L^\top$ factorization for SPD matrices, forward substitution, and back substitution.
- [ ] **2.2 Implement `ConditionalLogitOptimizer`**
  - Create `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/learning/ConditionalLogitOptimizer.kt`.
  - Implements feature standardization, prior center scaling, softmax probabilities, gradient, Hessian with L2 penalty, Armijo line search, and drift clamping.
- [ ] **2.3 Unit tests for the optimizer**
  - Create `features/recommendations/src/commonTest/kotlin/com/closet/features/recommendations/engine/learning/ConditionalLogitOptimizerTest.kt`.
  - **Cold start test:** $N < 20$ returns $\beta_0$.
  - **Synthetic clash lover test:** on a dataset where clashing color combos are consistently chosen, `hueHarmony` and `familyHarmony` weights drop $\le 0$.
  - **Separable data test:** weights stay finite under complete separation due to $\lambda > 0$.
  - **Determinism test:** identical inputs produce identical weights.

### Task 3: Historical Stratum Builder & Core-Level Deduplication

- [ ] **3.1 Implement `HistoricalStratumBuilder`**
  - Create `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/learning/HistoricalStratumBuilder.kt`.
  - Reconstructs strata for logged dates $d_1 \dots d_N$:
    - Filters items active on date $d$ (`createdAt <= d` and not retired).
    - Computes co-wear and recency strictly from logs with `date < d`.
    - Case ($y=1$): features of actual worn outfit.
    - Controls ($y=0$): top competing cores, deduplicated to best single completion per core.
- [ ] **3.2 Unit tests for stratum builder**
  - Create `features/recommendations/src/commonTest/kotlin/com/closet/features/recommendations/engine/learning/HistoricalStratumBuilderTest.kt`.
  - Assert zero future leakage: adding a future log on day $d+1$ does not alter stratum $d$'s features.
  - Assert exactly one case per stratum and distinct cores among controls.

### Task 4: Time-Ordered Cross-Validation & Repository Training Plumbing

- [ ] **4.1 Implement time-ordered CV in `TasteWeightTrainer`**
  - Create `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/learning/TasteWeightTrainer.kt`.
  - Evaluates log-spaced $\lambda \in [0.1, 100.0]$ on expanding time windows.
  - Applies one-SE rule to choose $\lambda^*$, then fits final $\hat{\beta}$ on all strata.
- [ ] **4.2 DAO and Repository queries**
  - Add queries in `RecommendationDao` and `RecommendationRepository` to fetch wear logs with snapshot items and candidate clothes for training.
- [ ] **4.3 Verification tests**
  - Unit tests verifying end-to-end training pipeline on multi-day synthetic history.

### Task 5: Preferences Persistence, Replay Check Gate & Smoke Test

- [ ] **5.1 Preferences storage in `PreferencesRepository`**
  - Add `taste_weights` preference string in `core:ui` / `core:data`.
  - Expose `getTasteWeights()` and `setTasteWeights()`.
- [ ] **5.2 Wire `RecommendationViewModel`**
  - Load `TasteWeights` and pass into `EngineInput`.
- [ ] **5.3 Offline replay check gate**
  - Run `ReplayCheckTest` with learned weights on the planted taste fixture.
  - Assert hit-rate@10 $\ge 0.15$ and improved held-out log likelihood.
- [ ] **5.4 Full verification suite & device smoke test**
  - Run all JVM unit tests across modules (`./gradlew test :core:data:jvmTest :core:ui:jvmTest :features:recommendations:jvmTest :shared:jvmTest`).
  - Run `./gradlew lint`.
  - Run `assembleFullDebug` and `assembleFossDebug`.
  - Live smoke test on connected device.
