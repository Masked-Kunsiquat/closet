# Recommendation engine v2: learned compatibility + step-by-step assembly

**Status:** approved (#98, 2026-11-17); amended 2026-11-17 (see "Amendment 1") · **Issues:** #96, under #94 (replaces its "PR 2: embedding compatibility" slot)

> **Amendment 1 (2026-11-17)** changes the following:
> - **Stochastic beam search** (seeded Gumbel-top-k sampling) replaces the ±3% ranking jitter.
> - **Diversity** is held inside the beam, per core.
> - **Already-shown outfits** are excluded during the final extension step, not after ranking.
> - **A layering-fairness rule and test.**
> - **An offline replay check**, built in PR 1 as a baseline against the current engine.
>
> The "complete" beam variants (beam stack, depth-first beam, BULB) and local beam search were considered and rejected. The search is four levels deep, and we want several good, varied outfits, not the single optimum.

## Why

The engine (as of #95) scores items one at a time (weather fit, recency) and scores an outfit with hand-written rules over 5 coarse color families, a solid/pattern flag, layering and footwear. It never asks whether two specific items go together, and it ignores the strongest evidence the app has about the owner's taste: which items they have actually worn together. Candidate generation keeps the top-K items per slot *in isolation* and tries every combination of them, which is why #95 needed a bounded cap-widening workaround to keep unseen outfits reachable.

Goal: suggestions that reflect how *this* closet is actually worn, that pair items deliberately, and that still work on a brand-new wardrobe with no history.

## Goals

1. **Pair compatibility.** A score `pair(a, b)` for any two items, blending the owner's wear history, embedding-based generalisation of that history, and rule priors (color, pattern, formality).
2. **Step-by-step assembly.** Outfits are built core-first, adding each optional slot by compatibility with what is already chosen (beam search), replacing "top-K per slot × cartesian product" and the #95 widening.
3. **Real color and formality.** Use `colors.hex` where present for hue-based harmony; derive a formality level from the item's occasion tags.
4. **Cold start = today's behaviour.** With no logs, the rules carry the score; history takes over smoothly as logs grow.
5. **Keep the frame:** the engine stays pure `commonMain` Kotlin (no I/O, no clock, injected randomness), and the session/Regenerate logic, exclusion, diversity by core, weather/recency item scores, occasion filter and AI curator all keep working.

## Non-goals

- A general "fashion knowledge" model (fashion-trained image model, e.g. on-device CLIP-style). Possible later as an optional add-on; size and setup cost not justified yet.
- Training any neural model on device. Everything here is counting, smoothing and vector lookups.
- Cloud anything (project rule: local-first, no accounts).
- Changing the recommendations UI beyond what explicit feedback (phase 4) needs.

## Anti-goals (failure even if it "works")

- **Echo chamber.** History must not lock suggestions into the same few outfits the owner already wears. The history weight is capped (never 100%), recency still applies, the beam samples rather than always taking the top (see "Stochastic selection"), and diversity by core still applies.
- **Regressing a new wardrobe.** A wardrobe with zero logs must get suggestions at least as good as #95's.
- **Slower than it feels instant.** A run on a 500-item wardrobe stays well under 100 ms of engine time on the test phone (DB reads excluded).

## Approaches considered

1. **Tune the current rules** (more rules, finer weights). Cheap, but still blind to the owner's taste and keeps the per-slot cap problem. Rejected: it's the band-aid path.
2. **Learned pairwise compatibility + beam assembly** (chosen). Rewrites only the engine's middle (outfit scoring and generation); data access, ViewModel, UI and AI curator barely change. Learns from data the app already records.
3. **Ground-up rewrite around a fashion model.** Biggest potential ceiling, but large model download, poor fit for local-first/F-Droid, and generic taste beats nothing but loses to the owner's own history for a personal closet. Deferred (see non-goals).

Owner picked 2 (2026-11-17).

## Design

### Inputs (new, all read once per run in the ViewModel and passed in `EngineInput`)

| Input | Source | Notes |
|---|---|---|
| Co-wear counts | `outfit_log_items` grouped by `outfit_log_id`: count of logs containing both `a` and `b`, plus per-item log counts and total logs | Logged wears are the strongest positive. Use the snapshot table (same as `wear_count`). |
| Saved-outfit co-membership | `outfit_items` grouped by `outfit_id` | Weaker positive: weight 0.5 of a log (owner composed it but may not have worn it). |
| Formality | item's occasion tags → level | See mapping below. Items with no tags: unknown (no penalty). |
| Hex colors | `colors.hex` via `clothing_item_colors` | Items with no hex fall back to their color family. |
| Embeddings | `item_embeddings` (current `model_version` only) | Used only for history generalisation (below); items without one skip that term. |

Only pairs among *candidate* items are needed; queries take the candidate ID list.

### Pair score

For items `a`, `b` in different roles:

```
pair(a,b) = w·hist(a,b) + (1 − w)·prior(a,b)       clamped to [PAIR_MIN, PAIR_MAX]
w = min(W_MAX, n / (n + K))                         n = evidence count for (a,b) (see backoff)
```

- **`hist(a,b)`** — smoothed lift of co-wear over chance (observed co-wears vs the count expected from each item's own wear rate), shrunk toward 1.0 when evidence is thin and mapped into `[PAIR_MIN, PAIR_MAX]`. Exact formula is the builder's call; requirements: 1.0 with no evidence, above 1 when worn together more than chance, below 1 when both are worn often but never together.
- **Backoff** when `(a,b)` themselves have little evidence: neighbours via embeddings — `hist` of the top-k most similar items to `a` paired with the top-k most similar to `b`, similarity-weighted. Then subcategory-pair co-wear. Evidence count `n` sums across the levels used, discounted per level.
- **`prior(a,b)`** — today's rules, pairwise: color harmony (hue-based when both have hex: neutral passes, analogous/complementary favoured, clashing hues penalised; else the existing family table), pattern clash, formality gap penalty (`|level_a − level_b| ≥ 2` → penalty).
- Starting constants (tunable, in the companion like today): `K = 5`, `W_MAX = 0.7`, `PAIR_MIN = 0.7`, `PAIR_MAX = 1.3`, top-k neighbours = 5.

**Outfit score (shipped implementation plan):** Rather than blending per-pair, the blend is applied at the outfit level:
`outfitScore = meanItem × [W·H + (1 − W)·R] × situational`
- `R` = color harmony × pattern mixing × geometric mean of pair formality terms
- `H` = geometric mean of pair history lifts `hist_ab`
- `W` = mean of pair weights `w_ab`
- `situational` = weather layering × footwear penalty
For example, for a three-item outfit with one history pair ($w_1 > 0$) and two zero-history pairs ($w_2 = w_3 = 0$), $W = (w_1 + 0 + 0) / 3$ and $H = (hist_1 \times 1.0 \times 1.0)^{1/3}$. When $W = 0$, the formula simplifies exactly to $meanItem \times R \times situational$. (The per-pair blend formula `pair(a, b)` above is superseded by this outfit-level formulation per the 2026-11-18 owner ruling).

### Formality mapping (from seeded occasions)

| Level | Occasions |
|---|---|
| 1 | Loungewear, Athletic |
| 2 | Casual, Outdoor/Hiking, Vacation |
| 3 | Date Night |
| 4 | Work/Business |
| 5 | Formal, Special Occasion |

An item's level is the median of its tags' levels. User-created occasions have no level and are ignored.

### Assembly (beam search)

1. **Cores.**
   - Score every Top×Bottom pair as `itemScore(top)·itemScore(bottom)·pair(top,bottom)`, and every OnePiece by its item score.
   - Cores already shown this session (cores of excluded keys, as in #95) rank after unshown ones.
   - **Select** `CORE_BEAM` cores (e.g. 30) with the stochastic selection below.
2. **Extend each core separately.**
   - Go through outerwear → footwear → accessory. Each slot offers "none" plus every candidate in that role.
   - Score each partial outfit with the full outfit formula (mean item score × geometric-mean pair score × outfit multipliers) applied to the items chosen so far.
   - **Select** `BEAM_PER_CORE` partials (e.g. 4) *per core* after each slot.

   Holding the beam per core keeps the diversity inside the search: one dominant core can't crowd the others out of the beam.
3. **Exclude during the last slot.** Combo keys include the optional slots, so a partial can only be matched against `exclude` once it is complete. Drop excluded keys from the last slot's candidates *before* that core's selection, so shown outfits never take a beam place.
4. **Final pick:** stochastic selection over the complete outfits, then **diversity by core** + fill (as today).

   Ask for `topN` (3, or 25 for the AI pool). If exclusion leaves fewer than `topN`, widen `CORE_BEAM`/`BEAM_PER_CORE` up to a bound. This replaces #95's `MAX_WIDENED_CANDIDATES_PER_SLOT`.

**Stochastic selection (replaces the ±3% jitter).** "Select k" means seeded Gumbel-top-k, which samples without replacement in proportion to score:
- Compute `key = ln(score) / T + g` per candidate, with `g = −ln(−ln(u))`, `u` uniform from the injected `Random`. Keep the k largest keys.
- **`T` (temperature)** controls exploration. A small `T` approaches strict top-k; a larger `T` gives runner-ups more chances. Start at `T = 0.15`; the builder tunes it with the replay check.
- **When `jitter`/random is null** (the deterministic path the engine tests use), selection is a plain top-k by score. The existing engine tests keep their exact values.
- **Seeding is unchanged:** the session's first run is date-seeded and Regenerate is unseeded. The reported `OutfitCombo.score` stays the un-noised score.

**Layering fairness.**
- Adding an item adds pairs to the geometric mean, so a mediocre layer can pull the mean below "none" even when the weather calls for it.
- The weather multipliers (`MULTIPLIER_COLD_NO_OUTER` and the warm-with-outer one) must stay applied to *partial* outfits at the outerwear step, so cold weather favours adding a reasonable layer.
- Tested below.

Cost: cores ≤ tops×bottoms (e.g. 60×40 = 2 400 pair scores, cheap), then `CORE_BEAM × BEAM_PER_CORE × slots × (role size + 1)` extensions — linear in wardrobe size per slot, not multiplicative.

### Explicit feedback (phase 4, optional)

- "Wore it" already logs the outfit → it becomes history automatically; nothing new needed.
- New "Not for me" on a suggestion card → a weak negative on its pairs. Needs a table (`recommendation_feedback`: combo key, date, signal) — the **first real Room migration** (v1 → v2), which must drop `one_ootd_per_day` first per `migrations/AGENTS.md`. Separate PR; may be dropped if phases 1–3 feel right.

## Delivery (one PR each, under #94)

1. **Replay check + compatibility data + pair scorer.**
   - **First**, the offline replay check (see Testing), run against the *current* engine to record a baseline.
   - **Then** the DAO queries (co-wear, saved-outfit co-membership, formality levels, hex) and `PairScorer` (pure, `commonMain`) with hist + prior, wired into the *existing* assembly as the outfit score.
   - Ships value immediately, with a measured before/after.
   - **Done in PR #115** (part of tracking issue #96), together with item 2.
2. **Beam assembly.** Replace the per-slot cap + cartesian product + widening with the per-core stochastic beam. The ±3% jitter goes too. Pure engine change, measured with the replay check.
   - **Done in PR #115** (part of tracking issue #96). Replay hit-rate@10: 0.05 before → 0.15 after (3 of 20). `CORE_BEAM = 30`, `BEAM_PER_CORE = 4`, widening to 120 / 12; `T` tuned from 0.15 to 0.01: at higher `T` a clearly stronger habit pair often missed the first page (~24% at 0.15) and an unseeded Regenerate could skip every core without the dominant items (~6% at 0.05, ~0.2% at 0.02; 0 of 120,000 at 0.01). Exploration now rotates near-ties (within a few %), about the old jitter's scale; the replay check is deterministic and does not depend on `T`.
3. **Embedding backoff.** Neighbour-based history generalisation (the old "PR 2" slot).
4. **Feedback** (optional, needs the migration).

Order 1 → 2 → 3 → 4. Scheduled after Phase 5 chat + wardrobe, so it lands on fully migrated code.

## Testing

- Engine stays pure: every scorer and assembly rule is unit-testable in `commonTest` with fixtures (`EngineTestFixtures`).
- **Cold start:** with no history, results equal (or are a documented, tested superset improvement of) #95 behaviour on the existing engine tests.
- **History works:** a fixture where items A+B are logged together often ranks A+B above an otherwise-equal A+C.
- **Echo-chamber guard:** with a dominant historical pair, Regenerate still surfaces other cores; `w` never exceeds `W_MAX`.
- **Formality:** a level-5 blazer with level-1 sweatpants scores below the same blazer with level-4 trousers.
- **Hex harmony:** complementary/analogous pairs outrank a clashing pair; missing hex falls back to families.
- **Beam reachability:** an unseen outfit that the old per-slot cap could not reach is returned.
- **Stochastic selection:**
  - the same seed gives the same results;
  - a null random gives strict top-k;
  - with a seeded random over many runs, a runner-up partial is sometimes selected and a far-worse one almost never (a statistical bound, not exact counts);
  - selection is without replacement (no duplicate partials in a beam).
- **Per-core beam:** with one core whose extensions all outscore every other core's, the final picks still have `topN` distinct cores when they exist.
- **Exclusion inside the search:** with the top-scoring complete outfit of each core excluded, a run still returns `topN` outfits without widening.
- **Layering fairness:** in cold weather (high below the cold threshold), with an ordinary jacket available, the top result includes outerwear. In warm weather (low above the warm threshold), it doesn't.
- **Offline replay check** (built in PR 1):
  - **Setup:** a jvmTest generates a synthetic wardrobe with planted taste (some top/bottom/shoe pairings worn far more often than chance) plus a log history. Hold out the last N logs (e.g. 20). For each held-out log, build `EngineInput` from only the logs before its date, with `today` = its date.
  - **Metric:** hit-rate@10 by core, meaning whether the held-out outfit's core appears among the cores of the top-10 results, with no exclusion and deterministic selection.
  - **Gate:** a PR may not lower it versus the recorded baseline. PR 1 must raise it on the planted-taste fixture.
  - **Real data (pending):** the property-gated restored-database workflow is deferred and not implemented in this PR; the replay harness currently operates on the synthetic planted-taste fixture.
- **Performance:** a jvmTest generating a 500-item wardrobe with 300 logs asserts the engine run is under a generous bound (smoke guard, not a benchmark).
- Device check on the test phone with the demo wardrobe after each PR.

## Left to the builder

- The exact smoothing formula for `hist`, as long as it meets the stated properties.
- Hue harmony thresholds (degrees for analogous/complementary, saturation/lightness cut-off for "neutral").
- `CORE_BEAM`/`BEAM_PER_CORE` values within the performance bound, and the temperature `T` (start at 0.15), tuned with the replay check.
- The replay fixture's generator: how taste is planted, and the wardrobe and log sizes.
- Query shapes (one aggregated SQL per signal vs. Kotlin aggregation), as long as reads happen once per run, in parallel like today.
