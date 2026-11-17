# Recommendation engine v2: learned compatibility + step-by-step assembly

**Status:** draft, awaiting owner approval · **Issue:** #94 (replaces its "PR 2: embedding compatibility" slot)

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

- **Echo chamber.** History must not lock suggestions into the same few outfits the owner already wears. The history weight is capped (never 100%), recency and jitter still apply, and diversity by core still applies.
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

**Outfit score** = mean item score (weather + recency, unchanged) × geometric mean of `pair` over all item pairs in the outfit × the existing outfit-level multipliers (layering vs weather, footwear). Geometric mean keeps outfit size from inflating the score (same reasoning as #95's harmony fix).

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

1. **Cores.** Score every Top×Bottom pair and every OnePiece as `itemScore(top)·itemScore(bottom)·pair(top,bottom)` (OnePiece: its item score). Rank cores with already-shown cores after unshown ones (same demotion idea as #95) and keep the best `CORE_BEAM` (e.g. 30).
2. **Extend** each kept partial outfit through outerwear → footwear → accessory, each slot offering "none" plus every candidate in that role, scored by the outfit score so far. Keep the best `BEAM` (e.g. 12) partials per core after each slot.
3. **Exclude** shown combo keys, **jitter** (ranking only, ±3%), **diversity by core** + fill — all as today.
4. Ask for `topN` (3, or 25 for the AI pool). If exclusion leaves fewer than `topN`, widen the beam (not a per-slot cap) up to a bound; this replaces #95's `MAX_WIDENED_CANDIDATES_PER_SLOT`.

Cost: cores ≤ tops×bottoms (e.g. 60×40 = 2 400 pair scores, cheap), then `CORE_BEAM × slots × (role size + 1) × BEAM` extensions — linear in wardrobe size per slot, not multiplicative.

### Explicit feedback (phase 4, optional)

- "Wore it" already logs the outfit → it becomes history automatically; nothing new needed.
- New "Not for me" on a suggestion card → a weak negative on its pairs. Needs a table (`recommendation_feedback`: combo key, date, signal) — the **first real Room migration** (v1 → v2), which must drop `one_ootd_per_day` first per `migrations/AGENTS.md`. Separate PR; may be dropped if phases 1–3 feel right.

## Delivery (one PR each, under #94)

1. **Compatibility data + pair scorer.** DAO queries (co-wear, saved-outfit co-membership, formality levels, hex), `PairScorer` (pure, `commonMain`) with hist + prior, wired into the *existing* assembly as the outfit score. Ships value immediately.
2. **Beam assembly.** Replace per-slot cap + cartesian product + widening with beam search. Pure engine change.
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
- **Performance:** a jvmTest generating a 500-item wardrobe with 300 logs asserts the engine run is under a generous bound (smoke guard, not a benchmark).
- Device check on the test phone with the demo wardrobe after each PR.

## Left to the builder

- The exact smoothing formula for `hist`, as long as it meets the stated properties.
- Hue harmony thresholds (degrees for analogous/complementary, saturation/lightness cut-off for "neutral").
- `CORE_BEAM`/`BEAM` values within the performance bound.
- Query shapes (one aggregated SQL per signal vs. Kotlin aggregation), as long as reads happen once per run, in parallel like today.
