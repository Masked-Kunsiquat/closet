# Engine v2 Delivery 3: Embedding Backoff & History Generalization — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Delivery 3 of recommendation engine v2 (issue #96 under #94), building directly on top of PR #115 which merged Deliveries 1 & 2.
For item pairs $(a, b)$ with thin or zero direct wear evidence ($n_{direct} < K = 5.0$), generalize the owner's wear history instead of defaulting immediately to rule priors:
1. **Neighbor Generalization (Level 1):** Use pre-computed 384-dim item embeddings (from ONNX `arctic-embed-xs-q8` stored in `item_embeddings`) to find top-k ($k = 5$) cosine neighbors ($s \ge S_{min} = 0.75$) to candidate item $a$ paired with top-k neighbors to candidate item $b$. Compute similarity-weighted co-wear lift and discount evidence ($\alpha = 0.6$).
2. **Subcategory Fallback (Level 2):** Fall back to aggregate subcategory-pair co-wear rates across the wardrobe, discounted ($\beta = 0.25$, capped at $K$).
3. **Plumbing & Purity:** Keep `OutfitRecommendationEngine` and `PairScorer` pure in `commonMain` (no I/O, no vector DB inside the engine; embeddings and neighbor structures fetched once per run in `RecommendationViewModel` and passed via `EngineInput`).
4. **Verification Gate:** Pass `ReplayCheckTest` in `features/recommendations` maintaining the hit-rate@10 floor (currently $\ge 0.15$), add unit tests for history generalization, and pass test suites on `:core:data` and `:features:recommendations`.

**Spec & Context References:**
- Spec: `docs/superpowers/specs/2026-11-17-recommendation-engine-v2-design.md` (sections "Backoff" and "Delivery 3").
- Design finding: `2026-11-18-engine-v2-delivery-3-embedding-backoff.md`.
- PR #115 plan reference: `docs/superpowers/plans/2026-11-18-engine-v2-pr1-pair-scorer.md`.

---

## Architecture & Mathematical Formulation

### 1. Two-Level Backoff in `PairScorer`

For candidate items $a$ and $b$:
- **Level 0 (Direct co-wear):**
  $c_{direct} = \text{coWear.pairCounts}[a \text{ to } b] ?: 0.0$
  $nA = \text{coWear.itemCounts}[a] ?: 0.0, \quad nB = \text{coWear.itemCounts}[b] ?: 0.0$
  $E_{direct} = \text{if (total} > 0) \frac{nA \cdot nB}{\text{total}} \text{ else } 0.0$
  $n_0 = n_{direct} = \max(c_{direct}, E_{direct})$
  $\text{lift}_0 = \text{if } (n_0 > 0) \text{ clamp}\left(\frac{c_{direct} + 1.0}{E_{direct} + 1.0}, \text{PAIR\_MIN}, \text{PAIR\_MAX}\right) \text{ else } 1.0$

- **Sufficient Direct Evidence ($n_0 \ge K = 5.0$):**
  No backoff is needed. Direct habit is established:
  $w = \min(W_{MAX}, n_0 / (n_0 + K))$
  $\text{lift} = \text{lift}_0$

- **Sparse Direct Evidence ($n_0 < K$):**
  - **Level 1 (Neighbor Generalization):**
    For item $a$ and item $b$, take their candidate neighbors $N(a)$ and $N(b)$ with cosine similarity $s \ge S_{min} = 0.75$, top-$k = 5$.
    Let extended sets $N^+(a) = \{ (a, 1.0) \} \cup N(a)$ and $N^+(b) = \{ (b, 1.0) \} \cup N(b)$.
    For all pairs $(a', b') \in N^+(a) \times N^+(b)$ where $(a', b') \ne (a, b)$ and $a' \ne b'$:
    Look up direct co-wear between $a'$ and $b'$ in `coWear`:
    $c_{a'b'}, E_{a'b'}, n_{a'b'} = \max(c_{a'b'}, E_{a'b'})$.
    If $n_{a'b'} > 0$:
    $s_{a'b'} = s(a, a') \cdot s(b, b')$
    $\text{lift}_{a'b'} = \text{clamp}\left(\frac{c_{a'b'} + 1.0}{E_{a'b'} + 1.0}, \text{PAIR\_MIN}, \text{PAIR\_MAX}\right)$
    Accumulate:
    $\text{sumSimLift} += s_{a'b'} \cdot \text{lift}_{a'b'}$
    $\text{sumSim} += s_{a'b'}$
    $n_{neighborsRaw} += s_{a'b'} \cdot n_{a'b'}$

    If $\text{sumSim} > 0$:
    $\text{lift}_1 = \text{sumSimLift} / \text{sumSim}$
    $n_1 = \alpha \cdot n_{neighborsRaw} \quad (\alpha = 0.6)$
    Else: $\text{lift}_1 = 1.0, n_1 = 0.0$.

  - **Level 2 (Subcategory Fallback):**
    If $a.\text{subcategoryId} \ne \text{null}$ and $b.\text{subcategoryId} \ne \text{null}$ and $a.\text{subcategoryId} \ne b.\text{subcategoryId}$:
    $sA = a.\text{subcategoryId}, sB = b.\text{subcategoryId}$
    Look up co-wear for $(sA, sB)$ in `subcategoryCoWear`:
    $c_{sub}, E_{sub}, n_{sub} = \max(c_{sub}, E_{sub})$.
    If $n_{sub} > 0$:
    $\text{lift}_2 = \text{clamp}\left(\frac{c_{sub} + 1.0}{E_{sub} + 1.0}, \text{PAIR\_MIN}, \text{PAIR\_MAX}\right)$
    $n_2 = \min(K, \beta \cdot n_{sub}) \quad (\beta = 0.25)$
    Else: $\text{lift}_2 = 1.0, n_2 = 0.0$.
    Else: $\text{lift}_2 = 1.0, n_2 = 0.0$.

  - **Combined Evidence and Blend:**
    $n_{eff} = n_0 + n_1 + n_2$
    If $n_{eff} \le 0.0$: return `PairHistory(lift = 1.0, weight = 0.0)`.
    $\text{lift}_{combined} = \text{clamp}\left(\frac{n_0 \cdot \text{lift}_0 + n_1 \cdot \text{lift}_1 + n_2 \cdot \text{lift}_2}{n_{eff}}, \text{PAIR\_MIN}, \text{PAIR\_MAX}\right)$
    $w = \min(W_{MAX}, n_{eff} / (n_{eff} + K))$
    Return `PairHistory(lift = lift_{combined}, weight = w)`.

### 2. Constants in `OutfitRecommendationEngine.Companion`
- `ALPHA_NEIGHBORS = 0.6`
- `BETA_SUBCATEGORY = 0.25`
- `MIN_NEIGHBOR_SIMILARITY = 0.75`
- `TOP_K_NEIGHBORS = 5`

---

## Review Focus
1. **Cold-start parity:** When neither neighbors nor subcategories have wear history, the pair score produces `PairHistory(lift = 1.0, weight = 0.0)`. All existing engine tests pass unchanged.
2. **Robustness of direct evidence:** When $n_0 \ge K = 5.0$, backoff is bypassed and direct history governs.
3. **Generalization effectiveness:** A new or rarely worn item paired with another item gains compatibility signal when similar items were worn together.
4. **Subcategory baseline:** When items and neighbors are unseen, aggregate subcategory pair wear rates provide a subtle, discounted prior.
5. **Replay gate:** `ReplayCheckTest` must maintain or exceed the $\ge 0.15$ hit-rate@10 floor.
6. **Engine purity:** No SQLite, no Android, no ONNX runtime in the engine; inputs arrive cleanly via `EngineInput`.

---

## Tasks

### Task 1: Level 1 & Level 2 Backoff in `PairScorer` + Unit Tests
- [x] **Step 1:** Define `ItemNeighbor` in `EngineModels.kt` and add `itemNeighbors: Map<Long, List<ItemNeighbor>> = emptyMap()` and `subcategoryCoWear: CoWearStats = CoWearStats.EMPTY` to `EngineInput`.
- [x] **Step 2:** Implement `buildItemNeighbors` in `features/recommendations/.../engine/EmbeddingNeighbors.kt` (pure vector cosine similarity calculation from `Map<Long, FloatArray>`).
- [x] **Step 3:** Add backoff constants to `OutfitRecommendationEngine.Companion` (`ALPHA_NEIGHBORS`, `BETA_SUBCATEGORY`, `MIN_NEIGHBOR_SIMILARITY`, `TOP_K_NEIGHBORS`).
- [x] **Step 4:** Implement two-level backoff in `PairScorer.kt`:
  - `history(a: EngineItem, b: EngineItem, coWear: CoWearStats, itemNeighbors: Map<Long, List<ItemNeighbor>>, subcategoryCoWear: CoWearStats): PairHistory`
  - Maintain `history(a: Long, b: Long, coWear: CoWearStats)` delegator for backwards compatibility.
- [x] **Step 5:** Wire `PairScorer.history` call in `OutfitRecommendationEngine.scoreCombo` with `itemNeighbors` and `subcategoryCoWear`.
- [x] **Step 6:** Write unit tests in `PairScorerTest.kt` and `EnginePairScoringTest.kt`:
  - Level 1 neighbor lift & weight calculation with cosine discounting.
  - Level 2 subcategory fallback when direct + neighbor evidence is absent.
  - Blend of direct + neighbor + subcategory when direct evidence is sparse ($n < K$).
  - Bypassing backoff when direct evidence $\ge K$.
  - Cold-start parity assertion.
  - `buildItemNeighbors` top-k and threshold validation.
- [x] **Step 7:** Run `./gradlew :features:recommendations:jvmTest` and verify all tests pass.

### Task 2: Data Plumbing in `core:data` and `RecommendationViewModel`
- [x] **Step 1:** Add queries to `RecommendationDao.kt`:
  - `getItemEmbeddings(itemIds: List<Long>, modelVersion: String)` returning `List<ItemEmbeddingBlob>`.
  - `getSubcategoryCoWearPairs(): List<ItemPair>` from `outfit_log_items` joined with `clothing_items`.
  - `getSubcategorySavedOutfitPairs(): List<ItemPair>` from `outfit_items` joined with `clothing_items`.
  - `getSubcategoryLogCounts(): List<ItemCount>`.
  - `getSubcategorySavedItemCounts(): List<ItemCount>`.
- [x] **Step 2:** Add repository methods in `RecommendationRepository.kt`:
  - `getItemEmbeddings(itemIds: List<Long>): DataResult<Map<Long, FloatArray>>` (decoding blobs via `toFloatArrayLE()`).
  - `getSubcategoryCoWearPairs()`, `getSubcategorySavedOutfitPairs()`, `getSubcategoryLogCounts()`, `getSubcategorySavedItemCounts()`.
- [x] **Step 3:** Add DAO tests in `core/data/src/jvmTest/.../dao/RecommendationDaoCoWearTest.kt` testing embeddings and subcategory co-wear queries.
- [x] **Step 4:** Wire `RecommendationViewModel.fetchAndRun`:
  - Fetch embeddings and subcategory stats concurrently via `async`.
  - Build `subcategoryCoWear: CoWearStats`.
  - Build `itemNeighbors: Map<Long, List<ItemNeighbor>> = buildItemNeighbors(embeddings)`.
  - Pass into `EngineInput`.
- [x] **Step 5:** Run `./gradlew :core:data:jvmTest :features:recommendations:jvmTest` and verify.

### Task 3: Offline Replay Verification, Engine Integration Tests & Review Gate
- [x] **Step 1:** Run `ReplayCheckTest` in `features/recommendations` to assert the offline replay hit-rate@10 remains $\ge 0.15$.
- [x] **Step 2:** Add a unit test in `EnginePairScoringTest` demonstrating an unseen outfit pair outranking another because its neighbors have logged co-wear history.
- [x] **Step 3:** Update KDoc in `OutfitRecommendationEngine.kt` and `PairScorer.kt` explaining Delivery 3 embedding backoff.
- [x] **Step 4:** Run full CLAUDE.md verification suite:
  - `:core:data:jvmTest`
  - `:features:recommendations:jvmTest`
  - `:core:data:compileCommonMainKotlinMetadata`
  - `:features:recommendations:compileCommonMainKotlinMetadata`
- [x] **Step 5:** Update `CHANGELOG.md` with Delivery 3 embedding backoff notes.
- [x] **Step 6:** Prepare git commit on `feat/engine-v2-embedding-backoff`.
