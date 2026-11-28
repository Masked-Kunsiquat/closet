# Two-stage recommendation engine (design)

**Status:** approved by the owner 2026-11-28.
**Issue:** #147. Folds in #137 (season and occasion as filters), #138 (outfit-level weather) and #141 (include items in the wash).
**Judged by:** the scoreboard baseline (#148, `scoreboard/Baseline.kt`).

## Why
Today the engine scores an outfit as one product of about 25 hand-set multipliers, so "is this wearable today?" and "how good is it?" are mixed into a single number. That has two consequences:
- **Strong signals outvote common sense.** "Has shoes" is a ×0.85 nudge, and it was outvoted twice (#135, #142).
- **The engine can't plan for weather.** Weather is judged per item and outerwear is a nudge, so the engine can't decide a day needs a coat, or a sweater over a shirt.

The scoreboard baseline shows the cost:
- **Not warm enough:** 22–67% of suggestions.
- **Rain gear skipped** when the person owns some: 33–81%.
- **The three shown outfits are near-duplicates:** 35–64%.

The owner wants a rebuild, not another patch: "patchwork rarely lasts".

## Goals
- **Stage 1, "wearable today?":** only outfits that meet today's requirements are built.
  - **Complete:** a core, plus shoes when suitable clean shoes exist.
  - **Warm enough:** across all layers.
  - **Rain-ready** when it's raining and rain gear is available.
  - **Clean,** unless the user allows items in the wash.
  - **Fits the occasion.**
- **Stage 2, "how good is it?":** the existing quality signals rank the outfits that pass stage 1. These are colour harmony, co-wear habit, formality coherence, freshness and season preference, plus learned taste later (#145).
- **When nothing passes,** relax the rules in a fixed order and tell the user what was relaxed.
- **Item warmth and rain-readiness** are worked out automatically for every item, and the user can override them.
- **The scoreboard improves** on WARM_ENOUGH, RAIN_READY and THREE_DIFFER, and no metric falls below the baseline without the owner deciding so with before and after numbers.

## Non-goals
- Learned taste (paused; #145). Stage 2 keeps a hook for it.
- New photo or colour work (#151, #139).
- Embeddings and neighbour backoff (#149).

## Anti-goals
- **Stage 2 must never be able to resurrect an outfit stage 1 rejected.** That is the whole point of the split.
- **Silent degradation:** relaxing a rule without telling the user.
- **Sharing scoring logic with the scoreboard.** The app's warmth model and the scoreboard's rubric must stay independent, or the scoreboard would grade the engine with its own answer key. Both may encode the same common sense, but separately. The scoreboard already uses two non-scoring app values, the page size (`TOP_N`) and the calendar-season mapping (`seasonFor`); that stays allowed.
- **Asking the user to fill in two new fields on every item.** "Auto" must be right most of the time.

## Owner decisions (2026-11-28)
- **Item warmth and rain-readiness:** derived automatically, with an optional override per item (option C).
- **When nothing passes:** relax in the fixed order rain gear → warmth → shoes → occasion. Never fall back to dirty clothes unless the user allowed it. Show a short note saying what was relaxed.
- **Occasion** stays a requirement but relaxes last. **Season** becomes a stage-2 preference, not a filter (#137).
- **"Include items in the wash"** is a switch on the weather sheet, off by default, for that session only. Dirty items rank below clean ones and are marked "needs a quick wash" (#141).
- **Approach: plan the day, then fill it** (option C below), in three PRs.

## Approaches (owner picked C, 2026-11-28)
| | Approach | Trade-off |
|---|---|---|
| A | Filter afterwards: today's engine plus a post-filter | Smallest change, but a filter can't add a coat the search never considered. It's the patchwork the owner rejected. |
| B | Requirements inside today's beam search | Medium-sized, but still one-top-one-bottom cores, so no sweater over a shirt (17–35% of persona days were layered). |
| **C** | **Plan the day (required slots, warmth target, rain), add a mid-layer slot, then fill the slots ranked by stage 2** | **The largest change, but outfits meet the requirements by construction and layering becomes possible** |

## Design

### 1. Item warmth and rain-readiness (PR 1)
**Scale.** Warmth has five levels: none, light, medium, warm and heavy. Inside the engine each level stands for an insulation contribution, grounded in ASHRAE garment insulation (clo) values, which add up across layers. Reference values:
- T-shirt 0.08, long-sleeve shirt 0.25, thick sweater 0.36, lined suit jacket about 0.44;
- shoes 0.02, boots 0.10;
- down and padded outerwear far higher.

The builder fixes one number per level, documents it in the code with its source, and keeps the ordering ASHRAE implies.

**Automatic value.** Pure Kotlin rules in commonMain, from each item's category, subcategory and materials. Indicative defaults (the builder finalises the table, documented):

| Item | Warmth | Rain-ready |
|---|---|---|
| T-Shirt, Tank Top, Blouse, Shirt, Polo, Bodysuit | none | no |
| Sweatshirt, Hoodie, Cardigan | light | no |
| Sweater | medium | no |
| Blazer, Vest | light | no |
| Jacket | medium | yes if the material is Nylon, Polyester or Faux Leather (a shell) |
| Coat | warm | no, unless Nylon or Polyester |
| Raincoat | light | yes |
| Boots | none | yes |
| Other footwear | none | no |
| Scarf, Hat/Cap | light, but only in cold weather | no |
| Wool, Cashmere, Fleece | one level warmer, capped | — |
| Linen, Mesh | one level cooler | — |

Activewear, Underwear, Swimwear and Bags are none and no. Bottoms and dresses are none (their insulation sits in the day-need table's baseline).

**Refined by wear history.** When an item has enough logs carrying weather (at least 5, reusing the existing per-item temperature percentiles), and it is regularly worn on days colder than its level suits, its effective warmth moves up one level. It never moves down, and never by more than one level. That keeps it safe, and it is the "your coat is warmer than its type suggests" signal.

**Override.** Two nullable columns on `clothing_items`: `warmth_override` (0–4) and `rain_ready_override` (0 or 1). Null means Auto.
- **Migration:** MIGRATION_4_5, version 5, following the migration AGENTS.md (drop and recreate `one_ootd_per_day`), with a migration test.
- **Item form:** two dropdowns. Auto shows the derived value, for example "Auto (medium)". The override is used when set.
- **Backup and restore:** the backup carries the columns.
- **Scoreboard:** fixtures leave both columns null, so it measures the automatic values, as most users would experience them.

**Engine input.** `EngineItem` gains `warmthLevel` (effective: override, else history-refined, else derived) and `rainReady`. Both are computed in the pipeline (the repository plus the pure derivation function) before the engine runs.

### 2. The engine: plan the day, then fill it (PR 2)
**Roles.** A new mid-layer role is a property of the subcategory, not the category. Sweater, Cardigan, Hoodie and Sweatshirt in Tops can be worn as a mid-layer over a base top (T-Shirt, Shirt, Polo, Blouse, Tank Top, Bodysuit), or as the top itself.
- **The core** is still Top + Bottom, or a OnePiece, with the top being any Tops item.
- **The optional slots** become mid-layer (only when the core's top is a base top), outerwear, footwear, the one general accessory (as today) and a **new, separate warmth-accessory slot** (scarf or hat, offered only when warmth is needed). Today an outfit has a single accessory slot (`OutfitRecommendationEngine.kt:276-277`), so the warmth accessory must not compete with a watch or belt.
- **For the scoreboard,** layered outfits become reachable, so `layeredCoreDays` should fall.

**The day plan.** A pure function from the day's weather (low, high, raining, windy), the requested occasion, the "include items in the wash" switch and the candidate pool. It produces:
- **Requirements:**
  - a core;
  - footwear, when the pool has eligible footwear and the occasion isn't Loungewear;
  - a warmth target, from the day's mean temperature (and wind) via the **app's own** need table, documented, independent of the scoreboard, and grounded in the clo scale;
  - a rain-ready item (outerwear or footwear), when it's raining, the mean is below about 20 °C and the pool has one;
  - the occasion, as today.
- **A relaxation ladder:** the same requirements, minus rain, then minus warmth, then minus footwear, then minus occasion, in that order (owner decision). Each rung carries a reason.

**When weather is skipped** (the user skipped the weather sheet), there are no warmth or rain requirements.

**Assembly.**
- For the first rung of the ladder that can produce a page:
  1. Build cores as today (Top × Bottom pairs and OnePieces), ranked by stage 2.
  2. Extend each core through the slots, keeping only extensions that can still meet the requirements. For example, if the core plus the best available layers can't reach the warmth target, drop that core at this rung.
  3. A finished outfit must meet every requirement of the rung.
- Ranking inside a rung is stage 2's score.
- The engine returns the outfits plus the rung used and its reasons, for example `RelaxedRain`, `RelaxedWarmth`, `RelaxedFootwear`, `RelaxedOccasion`, or none.
- Beam widening, exclusion and jitter work as today, within a rung.

**Stage 2 score.** Today's score minus the terms stage 1 now owns:
- **Removed:** the shoe-less ×0.85, the cold-with-no-outerwear ×0.50, the warm-with-outerwear ×0.75, and the per-item temperature, rain and wind multipliers, as hard factors.
- **Kept:** harmony, pattern mixing, formality coherence, the co-wear habit and recency.
- **Weather fit:** within the outfits that pass, it may stay as a mild preference, for example closer to the warmth target is better (overshoot is mildly penalised). The builder documents it, with no hard multipliers.
- **Season** becomes a mild preference: an item tagged for the calendar season or All Season scores ×1.0, otherwise a small penalty, documented.

**The candidate query.** `RecommendationDao.getCandidates` drops the season filter (it becomes stage 2). The occasion filter moves into the day plan, so it can relax: the query returns all active items, and the plan applies occasion per rung. The wash filter moves into the plan too: dirty items enter only when the switch is on, ranked below clean ones by a documented stage-2 factor, and marked on the combo.

**Page diversity** (my call, in PR 2). The page shows TOP_N (3), chosen greedily: each next outfit is the best-scored one that differs from every already-picked outfit in at least two items, otherwise the best remaining. This is maximal marginal relevance in its simplest form, and it addresses THREE_DIFFER. Today `selectDiverse` (by core) runs for every `topN`, including the scoreboard's hit@10 call and the AI path's 25. So the request gains an explicit flag saying the call is the shown page. Item-level diversity applies only then, and the existing core-level diversity stays for every call.

**The scoreboard.** Stage 1 never imports scoreboard code, and the scoreboard never imports engine constants. The fixture writer leaves the override columns null. A PR that moves a metric below the baseline must show before and after numbers, and the owner decides whether to re-record. A drop in hit@10 is expected to be possible, because personas sometimes wore non-weather-ready outfits.

### 3. What the user sees (PR 3)
- **The relaxation note** above the results, one line per reason in plain language. For example: "Your rain-ready items are in the wash. These outfits skip rain gear." "Nothing warm enough is clean. These outfits may be cold." "No suitable shoes are clean." "Nothing clean matches Work. Showing other occasions."
- **The "Include items in the wash" switch** on the weather sheet, off by default, for that session only. Outfits containing dirty items show a "needs a quick wash" label.
- **Strings** go in compose resources (features/recommendations).
- **Item form:** the two dropdowns from §1 (PR 1).

## Delivery (three PRs)
1. **Warmth and rain-readiness:** derivation, history refinement, the override columns and migration, the item form, and the pipeline passing `warmthLevel` and `rainReady` to the engine. No engine behaviour change, so the scoreboard is unchanged.
2. **The engine:** the day plan, the mid-layer slot, assembly by rung, stage-2 score cleanup, season as a preference, the candidate query change, and page diversity. Scoreboard before and after numbers go in the PR.
3. **The UI:** the relaxation note, the wash switch, and the needs-a-wash label.

## Decided by Claude (owner can override)
- The five-level warmth scale, grounded in ASHRAE clo values.
- History refinement only raises warmth, by at most one level.
- The mid-layer as a subcategory property.
- A warmth accessory slot (scarf or hat) only when warmth is needed.
- Page diversity by greedy "differs in at least two items".
- Season as a mild preference.
- Weather fit as a mild preference within stage 1's passing set.

## Left to the builder
- The exact clo number per level and the day-need table (documented with sources).
- The exact derivation table beyond the indicative rows above.
- The stage-2 weather-fit, season and dirty-item factors.
- Data structures for the plan and the rung reasons.
- How the beam prunes cores that can't meet the warmth target.

## Resolutions after the builder check (2026-11-28)
Each of these is Claude's call unless it says otherwise. The owner can override any of them.

1. **Relaxation drops the fewest requirements, not cumulatively.** The ladder order (rain → warmth → footwear → occasion) is a *priority*: relax the lowest-priority requirement that unblocks the page, and keep every other requirement that can still be met. A day blocked only by occasion therefore keeps warmth, rain and shoes. Algorithm: keep requirements greedily from the most important (occasion, then footwear, warmth, rain), each one kept only if a page remains possible with it plus those already kept. Show a note line for each relaxed requirement.
2. **Requirements are triggered by what the user owns, not by what's clean today.** "Rain-ready required" fires when it's raining and the user owns rain-ready gear; that gear may be dirty. So the note can say why ("Your rain-ready items are in the wash" vs nothing owned, which is silent because there's nothing to relax). Consequences:
   - the candidate query returns dirty items too, carrying a wash status;
   - `CandidateItem` and `EngineItem` gain `washStatus`;
   - the engine's always-true clean check becomes real;
   - dirty items are usable only when the wash switch is on.

   The same applies to footwear (owned and eligible) and warmth (owned warm items).
3. **Occasion applies to core items only** (Top, Bottom, OnePiece, and the mid-layer), consistent with the calibrated scoreboard. A core item with no occasion tags counts as matching any occasion (today the DAO drops untagged items: `RecommendationDao.kt:338-346`). Footwear, outerwear and accessories are not occasion-filtered: someone whose only warm coat isn't tagged Work still gets the coat.
4. **A relaxed requirement becomes a preference, not nothing.** "Minus warmth" means the closest warmth to the target that the pool allows, so a sweater beats a T-shirt at −5 °C when nothing reaches the target. "Minus footwear" and "minus rain" mean preferred when available. Stage 2 adds the documented best-effort terms only on relaxed rungs.
5. **"Not too warm" is a stage-1 requirement,** relaxed first, before rain. The outfit's total warmth may not exceed the day's target by more than one level, and heavy items (warmth level heavy) are excluded when the day's mean is at least 22 °C. This replaces the removed warm-with-outerwear and per-item temperature multipliers, so NOT_TOO_WARM shouldn't regress. It adds a rung at the front of the owner-approved ladder; overheating is the least harmful thing to relax.
6. **A page is TOP_N (3) outfits.** The rung is chosen by whether it can fill the shown page. The AI path's larger pool (25) is then filled from that same rung, and fewer than 25 is fine. **Regenerate** stays on the chosen rung: when exclusion exhausts it, the cycle restarts on that rung (today's wrap-around), and it never silently drops to a worse rung.
7. **The baseline is re-recorded in PR 2,** with before and after numbers in the PR, for the owner to accept at review (the owner merges). This is the rule the owner set in #148.

**Code facts the design now accounts for:**
- **`WeatherFitPrior` already exists** (`engine/WeatherFitPrior.kt`, #106). It derives each item's comfortable temperature band and its rain and wind suitability from subcategory, material and `warmthLayer`, ordered by ASHRAE clo, and blends them with wear history (Bühlmann, K = 5). PR 1 **builds the warmth level and rain-ready values on it**:
  - reuse its subcategory and material tables, and its credibility blending, for the history refinement;
  - rain-ready comes from the blended rain suitability, so a user's rainy-day wears count.
  - In PR 2 its per-item score multipliers retire, because stage 1 owns weather. The prior stays as the derivation source.
- **`EngineItem.warmthLayer`** (from `categories.warmth_layer`; no category is "Mid") is superseded by `warmthLevel` and the mid-layer subcategory property. PR 2 removes its engine uses. The DB column stays (no compat shims, and the seeder is untouched).
- **Dropping the season filter** also drops its temperature-band mode and its exclusion of untagged items (`RecommendationDao.kt:296-336`), so untagged items newly enter the pool. Season preference treats untagged items as neutral. The scoreboard numbers will show the effect.
- **Backup:** it copies `closet.db` as a file, so the new columns travel automatically. The only work is the existing schema-version guard on restore.
- **Learned taste** (paused): `OutfitFeatureExtractor` still uses the weather and footwear terms that stage 2 drops. PR 2 keeps the extractor consistent with stage 2 and leaves `LEARNED_TASTE_ENABLED` false.
