# Recommendation scoreboard (design)

**Status:** approved by the owner 2026-11-27.
**Issue:** #148. Follow-up: #149 (embeddings).
**Related:**
- supersedes #133;
- the baseline for #147 (the two-stage engine);
- the gate for #145 (the learner redesign).

## Why
Learned taste was paused in #142 after six successive flaws got past our tests. Every one of them got through for the same reason: nothing measured suggestion quality on histories that look like real life.
- **The fixtures were made up:** the planted-taste replay fixture and the random demo logs.
- **The real checks were by eye:** I looked at the test phone by hand.

The scoreboard fixes that. It is a repeatable measurement of how sensible the app's suggestions are across several simulated people over a simulated year. Every engine change from now on, starting with #147, is judged against it.

## Goals
- **Run the real pipeline.** Suggestions come from the same code the phone runs:
  - the database queries for candidates (clean, season, occasion);
  - the per-item weather history (temperature percentiles, rain and wind suitability);
  - the co-wear statistics;
  - the engine.
- **Use realistic histories.** Five simulated people, each with a coherent wardrobe and an outfit catalogue curated by taste, not by our rules. They get dressed through about a year of generated weather, with occasions, laundry cycles and occasional noise.
- **Run common-sense checks** on the suggestions actually shown, plus the existing accuracy measure (is the outfit the person wore in the top 10?).
- **Make it deterministic and reportable.**
  - The same inputs give the same numbers.
  - Per-persona rates go to a report file.
  - The first run becomes a committed baseline, and later changes can't fall below it without the owner deciding so.

## Non-goals
- Improving suggestions. This sprint only measures today's engine, and fixing what it finds is #147. The one exception is the test-only harmony hook in §7, which can't change production behaviour.
- Learning anyone's taste. The personas are generic archetypes, never the owner (no-personal-details rule).
- UI changes.

## Anti-goals (a failure even if it works)
- **Circular checks.** A check that imports engine constants or rules, such as `MULTIPLIER_NO_FOOTWEAR` or `COLD_THRESHOLD_C`, just grades the engine with its own rubric. Checks are defined from the fixture's own tags and plain thresholds written in the scoreboard.
- **Re-implementing production logic in test code.** For example, rewriting the candidate SQL filter in Kotlin. Drift between a copy and the original is exactly the train/serve-mismatch failure.
- **Committing dataset content.** No Kaggle item names, ids or images go in the repo (`tools/demo-backup/README.md`).
- **Flaky numbers.** Every random choice is seeded.

## Approaches (owner picked B, 2026-11-27)
| | A. Engine-only harness | **B. Real pipeline on a test database (picked)** |
|---|---|---|
| How | Test code re-creates the clean, season and occasion filters and the weather suitabilities, then calls the engine | Persona wardrobes and logs go into a real Room database on the JVM, and the app's repository queries and engine produce the suggestions |
| Fidelity | Hand-copied logic can drift | Identical to the phone |
| Speed | Seconds | About 1–2 minutes |
| Follows #137/#141 | Must be updated by hand | Automatically |

The personas' outfits come from a curation step, not from our engine. The owner chose simulated people with taste-curated catalogues over the demo's 120 random logs, which are noise by construction, and over a handful of hand-written fixtures, which are too few to measure rates.

## Design

### 1. A shared recommendation pipeline (a small production refactor)
`RecommendationViewModel.fetchAndRun` is where candidates, weather maps, co-wear and taste weights are assembled before the engine runs. It is private.
- **Extract it** into an `internal` class, for example `RecommendationPipeline`, in `features/recommendations` commonMain.
- **Its inputs:** the repositories, the engine, the clock and the dispatcher.
- **What it does:** runs one request and returns the ranked combos.
  - **Request:** occasion, weather, `topN`, exclusions and a `Random` for jitter.
  - **Building `EngineInput`:** candidates, weather maps, co-wear including saved outfits, neighbours and taste weights.
  - **Running the engine:** one `engine.recommend` call.
- **The ViewModel calls it.** The split runs through the middle of today's `fetchAndRun`. These stay in the ViewModel, around the pipeline call:
  - the shown and last-page exclusion wrap-around;
  - the `topN = 25`-when-AI-is-ready choice;
  - the `firstOfSession` choice between a date-seeded and an unseeded `Random`;
  - the AI scorer.
- **The refactor preserves behaviour.** The existing ViewModel session tests must pass unchanged.

The scoreboard calls the pipeline directly. That gives it production fidelity without driving UI state.

### 2. Test database
- **The suite** lives in `features/recommendations` `jvmTest`, which already depends on `:core:data`.
- **The database** is a temp-file `ClothingDatabase` from the production `getDatabaseBuilder(file)`, so the real seeding of lookups, callbacks and migrations runs.
- **A small local helper** opens it, since `core/data`'s `TestDatabase` is test-only and not visible.
- **Writes go through the real DAOs and repositories:**
  - items with their category, subcategory, colours (hex + share), materials, seasons and occasions;
  - wear logs, with the day's weather (`temperature_low/high`, precipitation, wind):
    - a wear of a saved outfit goes through `LogDao.insertLogAndSnapshot`, which snapshots the saved outfit's items;
    - every other wear, including swaps and no-footwear days, goes through `insertAdHocLogAndSnapshot`;
  - laundry through `wash_status`.

  Wears don't mark items dirty yet (#105), so the simulator sets `wash_status` itself.
- **Saved outfits mirror real use** (Claude's call):
  - each persona saves only its favourite outfits (catalogue outfits with a high favourite weight) as saved outfits, and logs them through the saved-outfit path;
  - everything else is logged ad hoc;
  - **how much each persona saves:** the creature of habit saves nearly all of its outfits, the mix-and-matcher almost none, and the others save their top 5–10.

  The pipeline gives saved outfits a co-wear weight of 0.5, so the share saved shapes hit@10. That's real behaviour, not a leak: real users save favourites. The report shows each persona's saved share next to its numbers.
- **Fidelity gap: embeddings.**
  - **The gap:** the app's neighbour backoff uses ONNX item embeddings, and those can only be computed on Android. The JVM binds `UnavailableEmbeddingEncoder`, and the demo tool leaves embeddings empty, so the scoreboard runs without neighbours.
  - **Effect:** that understates how the app handles new or rarely worn items. The report states it, and a follow-up issue covers computing and committing fixture embeddings (derived from our own descriptions, not dataset content).

### 3. Personas and their catalogues (committed fixture data)
**Five archetypes:**

| Persona | Wardrobe | Pattern | Climate |
|---|---|---|---|
| Office commuter (menswear) | mid-size | Work/smart-casual weekdays, casual weekends, about 10 favourites | four seasons, cold winter |
| Office commuter (womenswear) | mid-size, includes dresses and heels (filed as Dress Shoes, since the app has no heels subcategory) | same weekly pattern; one-pieces on some workdays | four seasons |
| Creature of habit (menswear) | small | 6–8 outfits on repeat, two pairs of shoes | mild, rainy |
| Mix-and-matcher (womenswear) | large, accessory-heavy | rarely repeats an exact outfit | warm, hot summers |
| Active student | mostly athletic and casual | gym, campus, hiking weekends | cool, wet, windy |

**Each persona's committed fixture** holds:
- **A wardrobe.** Every item has:
  - a generic description written by us (for example "navy oxford shirt");
  - the app's seeded category and subcategory.

    **Outfit role and warmth layer come from the category, not the item.** Activewear, Swimwear and Bags have role `Other`, so they never enter an outfit. Every category except Outerwear and Underwear has warmth layer `None`, so a sweater and a tee look the same to the engine.
  - **Items are filed the way a user would file them** (Claude's call). Gym shorts go under Activewear even though the engine then ignores them. The scoreboard should expose those gaps, as input for #147, not hide them by filing for the engine. The active student's numbers will likely be poor for this reason, and the report says why.
  - materials, seasons and occasions;
  - colours (hex + share), measured with the app's `ColorExtractor` on a garment crop of the photo, padded with white (see the calibration addendum);
  - **scoreboard-only tags:** `warmth` (0–3), `rainReady` and `formality` (1–5).
- **An outfit catalogue** of 20–40 outfits. Each is a list of item references, tagged with the occasions and the temperature band it suits, whether it works in rain, and a favourite weight.

**How the catalogues are made:** a one-off curation by Claude, in session, from the full local Kaggle "Fashion Product Images" dataset. Each persona's wardrobe is drawn only from coherent sections, so there are no mixed men's and women's closets unless a persona mixes on purpose.
- **Kept locally:** a mapping from fixture items to dataset ids, outside the repo, so a photo contact sheet can be built.
- **Owner check:** the owner reviews a sample of outfits per persona as photos and vetoes anything implausible. Vetoed outfits are replaced before commit.

### 4. The simulator (deterministic)
For each persona, seeded:
- **Weather:** monthly mean high and low temperatures for its climate, plus seeded noise, rain probability per month and wind.
- **The day's occasion** comes from the persona's weekly schedule, mapped to the seeded occasions:

  | Day | Occasion |
  |---|---|
  | office | Work/Business |
  | campus, errands, weekend | Casual |
  | home | Loungewear |
  | gym | Athletic |
  | hike | Outdoor/Hiking |
  | evening out | Date Night |
- **What the simulated user asks the app for** (Claude's call): the day's occasion on about 70% of evaluation days, and no occasion on the rest, the way users skip the picker.
- **Logging rate:** about 70% of days are logged, since real users skip days.
- **Choosing the outfit:**
  - candidates are the catalogue outfits that suit the occasion and temperature band, need rain-readiness if it's raining, and have every item clean;
  - choose by favourite weight;
  - with small seeded probabilities, swap one item for a same-role item in the wardrobe, or, on a home day, log no footwear;
  - if nothing qualifies, log the closest outfit, ignoring rain and then temperature (real people make do).
- **Laundry:** worn items are dirty for a persona-specific number of wears or days, then clean on its laundry day.
- **The length:** about 12 months, starting on a fixed date.

### 5. The evaluation loop
The simulation runs day by day, writing each day's log after that day is evaluated, so the database only ever holds strictly earlier history.
- **When to evaluate:** on every 3rd **logged** day after a 60-day warm-up, so a cold start doesn't dominate. Hit@10 and every check share that denominator, because only a logged day has an actual outfit to compare against.
- **The clock:** a fresh `FixedAppClock` per evaluation day.
- **Two pipeline calls per evaluation day,** each with its own `Random` seeded by the date, and with that day's weather, the requested occasion and that morning's laundry state:
  - **`topN = 3`:** exactly the page the app shows. The checks run on it.
  - **`topN = 10`:** for hit@10.

  They're separate calls because when fewer combos come back than asked for, `recommend` widens its search and reuses a partly used `Random`. So the app's 3-item page isn't always the first 3 of a 10-item call.
- **The taste weights** are whatever production resolves. Today that is DEFAULT, since `LEARNED_TASTE_ENABLED = false`.
- **What gets recorded:**
  - **Hit@10:** the persona's actual core for the day is among the top 10 cores.
  - **No results:** the pipeline returned nothing.
  - **The checks** below, on the 3 suggestions the app shows.

### 6. The checks (defined from fixture tags, never engine constants)
| # | Check | Pass when |
|---|---|---|
| 1 | Complete | has a core (Top+Bottom or OnePiece), and has footwear when the wardrobe has clean footwear suiting the day; footwear not required on home days |
| 2 | Warm enough | the summed item `warmth` meets the day's need from the day's **mean** temperature (table: ≥18 °C 0, ≥12 1, ≥6 2, ≥0 3, ≥−6 4, below 5); not applicable at home or the gym |
| 3 | Not too warm | no `warmth` 3 item when the high is ≥ 24 °C |
| 4 | Rain-ready | when it's raining and the wardrobe has a clean, in-season `rainReady` footwear or outerwear item, the outfit includes one; not applicable at home, at the gym, or when the day's mean is ≥ 20 °C (warm rain) |
| 5 | Formality coherent | max − min `formality` ≤ 2 over core items and footwear, and core items fit the requested occasion's band (Work/Business 2–5); not applicable when there is nothing to judge |
| 6 | Clean | no item that is dirty that morning |
| 7 | No doubled slots | at most one footwear and one belt (layering outerwear, such as a blazer under a coat, is allowed) |
| 8 | The three differ | each pair of shown suggestions differs in at least two items |
| 9 | Not on repeat | not the exact outfit worn yesterday, unless it's a catalogue favourite |

A check that can't apply on a day, such as "rain-ready" on a dry day, is counted as not applicable, not as a pass.

Check 6 (Clean) can't fail on today's pipeline, because the candidate query already filters on `wash_status`. It's kept as a regression guard, so that #141's "include items in the wash" toggle can't leak dirty items when it's off.

### 7. Output and the baseline
- **The report.** Per persona and in total: each check's failure rate over the applicable days, hit@10, the no-results rate and the number of evaluated days. It goes to `features/recommendations/build/reports/scoreboard/scoreboard.md` and to stdout.
- **The baseline.** The first run on today's engine is committed in the test as constants, with the date and engine commit in a comment.
- **The assertion:** no metric is worse than its baseline by more than a small tolerance (in failure-rate points, set from the run-to-run spread, which should be 0 when deterministic). When a change is meant to move a metric, the PR re-baselines with before and after numbers, and the owner decides.
- **Sensitivity check (what #133 asked for):** a test variant forces colour harmony to ×1.0 and must move hit@10 or the harmony-related numbers, which proves the scoreboard can see colour. If it can't, that is reported, not hidden.
  - **It needs a small engine hook** (Claude's call), because harmony is a private, hard-wired multiplier: an `internal` engine constructor parameter that defaults to production behaviour and is set only by tests. It's added in PR 2, with the sensitivity check. It's the one engine change in this sprint, and it can't change production behaviour.

### 8. Where it runs
- **Its own Gradle test task** (for example `:features:recommendations:scoreboard`), excluded from the default `jvmTest`, so day-to-day test runs stay fast.
- **It's added** to the `app-android/CLAUDE.md` pre-PR command and to CI.
- **If it's too slow,** evaluate every 3rd day and sample personas. The target is under 3 minutes.

## Delivery (two PRs)
1. **Machinery,** which can start now:
   - the pipeline extraction;
   - the test-database helper;
   - the simulator;
   - the checks;
   - the report, run on one small hand-written persona (a few items and outfits written by us, no dataset needed);
   - unit tests for each check and for simulator determinism.
2. **The real personas:**
   - curate the 5 catalogues from the full dataset;
   - the owner's photo veto;
   - commit the fixtures;
   - run the baseline and commit it;
   - close #133 as superseded.

## Decided by Claude (owner can override)
- Two PRs as above.
- Evaluate every 3rd logged day after a 60-day warm-up; a 70% logging rate.
- Only favourite outfits are saved, and how many varies by persona.
- Items are filed the way a user would file them, even where the engine then ignores them.
- The occasion mapping; no occasion requested on about 30% of days.
- Two pipeline calls per evaluation day (`topN = 3` for the checks, `topN = 10` for hit@10).
- Embeddings stay a documented gap, with a follow-up issue.
- An internal, test-only harmony hook on the engine, in PR 2.
- The scoreboard-only tags (`warmth`, `rainReady`, `formality`) live in the fixture, not the app schema.
- ReplayCheckTest stays as it is, since it still guards the planted-learning checks for #145.

## Left to the builder
- The names of the pipeline class and the Gradle task.
- The fixture file format (JSON or Kotlin data).
- The exact temperature table for "warm enough", written down in the scoreboard.
- Laundry parameters per persona.

## Calibration addendum (2026-11-27, PR 2)
The first run of the curated personas showed the checks failing the personas' **own** outfits far too often. For example, WARM_ENOUGH failed 60–90% of what they actually wore. A rubric that fails sensible people's choices would make the scoreboard misleading, which is this spec's main anti-goal. So the rubric was calibrated against a **self-check**: the checks applied to what each persona actually wore. Only the rubric and the simulator changed, never the engine.

**Rule changes** (§4 and the §6 table above):
1. **The simulator** suits temperature by the day's mean being inside the outfit's band.
2. **WARM_ENOUGH** uses the day's mean with the table above, and is not applicable at home or the gym.
3. **COMPLETE** doesn't require footwear on home days.
4. **RAIN_READY** is not applicable at home, at the gym, or in warm rain (mean ≥ 20 °C).
5. **FORMALITY** judges the occasion band on core items only (Work/Business 2–5), and coherence over core items plus footwear. It is not applicable when there is nothing to judge.
6. **NO_DOUBLED_SLOTS** counts footwear and belts only.
7. **Only washable categories go through the simulated laundry:** Tops, Bottoms, Dresses & Jumpsuits, Activewear, Underwear & Intimates and Swimwear.

**Fixture choices that differ from §3:**
- **Colours** were measured on garment crops of the reference photos, padded with white. Almost every photo was shot on a model, and a user photographs the item alone. A minor tan, beige or brown share (under 50%) on a model shot was dropped when the item's own label didn't explain it (21 corrections).
- **Hand-described items:** the reference catalogue has no boots or coats and almost no rain gear, so those 13 gap items are hand-described, with no photo and a hand-set colour (owner decision).
- **Outdoor outfits' minimum temperatures** were aligned with the warmth table: an outfit's minimum is never below the temperature its total warmth covers. The engine never sees the scoreboard's tags or bands, so this aligns persona truth with the rubric without being circular for the engine. Its cost is that WARM_ENOUGH and hit@10 now partly measure the same thing.
- **Catalogue sizes:** the creature of habit has 11 outfits and three pairs of shoes, the active student 16 outfits with 4 saved, and the others 23–29.

**The self-check after calibration** (persona's own fails / applicable; the report prints these next to the engine's rates):

| Persona | WARM_ENOUGH | RAIN_READY | FORMALITY | COMPLETE |
|---|---|---|---|---|
| office commuter, menswear | 29/217 | 16/40 | 7/251 | 0/251 |
| office commuter, womenswear | 25/213 | 8/40 | 2/251 | 0/251 |
| creature of habit | 71/238 | 40/94 | 18/238 | 0/238 |
| mix-and-matcher | 40/265 | 10/20 | 5/265 | 0/265 |
| active student | 39/144 | 34/76 | 0/254 | 31/254 |

The remaining self-fails come from forced fallbacks: when laundry leaves no suitable clean outfit, a persona wears what's left (the creature of habit owns two work shirts). The student's COMPLETE fails are gym tees with athletic shorts. The app files those shorts as Activewear, which it never suggests, and that is a finding for #147. Read an engine fail rate against the persona's own rate, not against zero.

**Known leniencies and blind spots:**
- **CLEAN** can't catch dirty outerwear, footwear or accessories, because they never go into the simulated wash.
- **Accessories** are outside the formality checks, so a tie with gym kit can't fail.
- **Warmth and rain** are not judged at home or the gym.
- **Small denominators:** some baseline rates rest on a handful of days (the mix-and-matcher's RAIN_READY 1/3, the creature's NOT_TOO_WARM 5/18), so one flipped suggestion trips tolerance 0.
- **The colour-harmony sensitivity is weak but non-zero.** Turning harmony off moves hit@10 by one day for three of the five personas, consistent with #144. The harmony switch doesn't reach learned colour features, which matters for #145.
