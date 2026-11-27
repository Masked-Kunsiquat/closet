# Taste learning: grouped cross-validation (design)

**Status:** approved 2026-11-27 with `MIN_DISTINCT_CHOICES = 20`; D3 revised and D3c (a two-stage gate) added after implementation measurements (owner-approved A+B+C). After the device smoke test, D7 (core-only learned features) was tried and rejected, and **learned taste is paused (D8, owner decision 2026-11-27)**. Issue: #142. Builds on #135 (PR #143).

## Problem
`TasteWeightTrainer` picks λ, and decides whether to keep learned weights at all, from **expanding time-ordered cross-validation**. It fits on earlier strata, scores later ones, and checks the mean held-out gain against twice its standard error. When outfits repeat, that check leaks in two ways:

1. **Fold leakage.** A validation stratum's worn outfit (its core) usually also appears in the training fold. A fit that memorises the quirks of a few repeated outfits then "validates".
2. **Counting leakage.** Repeats inside the validation block are scored as independent observations. Five wears of one outfit contribute five near-identical deltas, which shrinks the standard error by about √5. The 2·SE gate then passes noise.

**Observed on the test device (after #143).** The demo history is 120 logs that reuse 25 randomly assembled outfits. The gate accepted the fit, and hue harmony landed on the floor (−2.0), family on −1.2 and formality on −1.0. Real users re-wear saved outfits too, so this isn't only a demo artefact.

## Decisions

### D1. Each stratum knows its choice
`ChoiceStratum` gains `choiceKey: Set<Long>`, the ids of the worn core: Top + Bottom, or the OnePiece. `HistoricalStratumBuilder` already computes this core, so it just sets the field. Two logs of the same core share a key, whatever the optional items are.

### D2. Leave the validation block's choices out of training (grouped, still time-ordered)
- **Fold construction:** for each expanding fold (train = strata `[0, trainEnd)`, validation = `[trainEnd, valEnd)`), the training fold **drops every stratum whose `choiceKey` appears in that fold's validation block**.
- **What stays the same:** time order is kept, and so is the number of fits.
- **Small folds:** if a trimmed training fold has fewer than `MIN_INITIAL_TRAIN_STRATA` (10) strata, the fold is skipped and its validation strata get no score. If every fold is skipped, the result is `DEFAULT`.
- **Why the habit can still be learned:** a stratum's *features* still include its causal co-wear history (`historyLift` from earlier logs, computed in the builder). So the habit weight is still learned and validated. It's tested on whether re-wearing *other* favourites predicts choices, not on memorising the same one.

### D3. Cluster-robust gate statistics, with the mean still weighted by wears (revised 2026-11-27)
*This replaces the first version, which averaged per outfit. That gave a one-off outfit the same weight as a favourite worn 30 times, and pushed real-taste fixtures below the gate: the colour-matching and favourites fixtures sat at z 0.34 and 0.22, and planted replay learned hit fell to 0.15.*

**The statistic.** Take the per-stratum held-out deltas `dᵢ` over the `N` scored validation strata.
- **Mean:** `d̄ = Σdᵢ / N`, so it is still weighted by wear count.
- **Cluster-robust SE**, clustered by `choiceKey` over `G` scored keys: `SE = sqrt( G/(G−1) · Σ_g (Σ_{i∈g} (dᵢ − d̄))² ) / N`.
- **Why it works:** repeats of one outfit can no longer pass as independent evidence. With no repeats, `SE` is the usual sample SE up to a `√(G/(G−1))` versus `√(N/(N−1))` factor, which is identical when `G = N`.
- **Floor:** at least `MIN_SCORED_CHOICES = 2` distinct keys must be scored, otherwise the result is DEFAULT. This comes from the implementer.
- **Uses:** the one-SE rule, both gates in D3c, and the reported `meanLogLikDelta` and SE.

### D3c. A two-stage gate: habit first, rules only if they add something (added 2026-11-27)
**Why:** the first implementation showed that one genuine signal carries noise through an all-or-nothing gate. With 25 re-worn outfits, the co-wear habit is real, so the fit passes the gate. The rule weights then fit noise, and hue lands on its floor (19 of 20 repeated-noise seeds accepted, max |β−1| = 6).

**Stage 1 (habit):** CV and the one-SE rule with **only `historyLift` free**. Every other weight is fixed at β₀. The fit is accepted when its gain over DEFAULT is greater than 2·SE (D3).

**Stage 2 (rules):** CV and the one-SE rule with every non-frozen weight free.
- **The reference** is the stage-1 model if it was accepted, otherwise DEFAULT. Rules can still be learned for a user with no habit.
- **The gate is paired:** per stratum, `dᵢ = LL_full − LL_reference`, using the D3 statistic, and the fit is accepted when the gain is greater than 2·SE.

**Result:**
- stage 2 accepted: the full fit at its λ;
- otherwise, stage 1 accepted: the habit-only fit at its λ;
- otherwise: DEFAULT.

**Cost:** about twice the fits. That's fine, since training is around 0.5 s on 500 items and 300 logs.

**The optimizer needs a free-feature mask.** Fixed features stay exactly at β₀.

**The rule group:** `meanItem`, hue, family, pattern, formality, chroma match and lightness spread. Footwear and layering stay frozen by construction (#135).

### D4. Require enough distinct choices before learning
Learning also requires at least **`MIN_DISTINCT_CHOICES = 20`** distinct choice keys, besides the existing 20-strata minimum. Rule weights can't be identified from a handful of outfits, however often they're worn.
- **What a user with few outfits loses:** someone with 15 saved outfits worn 100 times stays on the default weights. The default score already includes the co-wear habit blend, so they still get favourite-aware suggestions. Only the *rule* weights stay unlearned.
- **Alternative:** no distinct-choice minimum, relying on D2 and D3 alone.
- **Recommended:** the minimum.

### D5. Retrain
`TASTE_WEIGHTS_SCHEMA_VERSION` goes from `"v4"` to `"v5"`.

### D6. Tests
| Test | Fixture | Expected |
|---|---|---|
| **Repeated noise** | 25 random outfits (no colour or rule preference), each logged about 5 times, 120 logs, over 20 seeds | at most 1 fit accepted, and every accepted fit within β₀ ± 1 |
| **Repeated favourites** | planted taste, with favourites re-worn | still accepted, with `historyLift > 1` |
| **Fold hygiene** | — | no validation stratum's `choiceKey` appears in its training fold; a fold trimmed below 10 is skipped |
| **Cluster SE** | the same validation choice duplicated k times | same mean and SE as appearing once; SE doesn't shrink with duplicates |
| **Distinct-choice minimum** | 19 distinct outfits × many logs | DEFAULT |
| **Shoe-less** (re-derived, owner 2026-11-27) | **6** favourite outfits, plus about 20% shoe-less logs | accepted (`acceptedStage` ≠ NONE); footwear and layering exactly 1.0; the top suggestion has shoes |
| **Colour preference** (re-derived, owner 2026-11-27) | planted 80/50 harmonious preference | at 120 logs, not accepted at the FULL stage. Also record the smallest history size within a realistic range (up to about 500 logs) where FULL is accepted with hue > 1; if there's none, assert non-acceptance, and #144 tracks it |

**Why the shoe-less fixture changes:** with only 3 favourites, they land in nearly every validation block, so grouped CV can't learn the habit within a fold. That's an inherent property of grouped CV. The test's subject is footwear freezing, not few-favourite habit learning.

**Why the colour expectation changes:** without leakage, the planted preference isn't detectable at 120 logs (z ≈ 1.35, and 0.65 with a 3× wardrobe). The old pass relied partly on repeated outfits leaking across folds. The colour features' small range is the likely cause (#144).

Also:
- `ReplayCheckTest` floors hold, with before and after numbers reported. Its planted fixture repeats favourite triples, so this is the real check that genuine taste still passes.
- **Device:** after the retrain, the stored weights are either DEFAULT (if the demo's 25 random outfits are noise) or within β₀ ± 1 on the rule features. Hue must not sit at the floor.

### D7. Learned weights act on the core only (tried 2026-11-27, rejected)
**Found on the test device.** After the v5 retrain the stored weights were `[1,1,1,1,1,7,1,1,1,1]`, as designed. But the top suggestion was a shirt and trousers with **no shoes**. On the default weights, all 10 of the top 10 suggestions have footwear; on the learned weights, only 2 do. The full numbers are in the investigation notes (`scratchpad/shoes-rootcause.md`, not committed).

**Cause: training and serving use the learned features differently.**
- **Training never weighs extras against no extras.** Controls are slot-matched (#135 D2), so the learned weights never compare "this core with shoes" against "this core without".
- **Serving does weigh them.** It applies `exp((β − β₀)·x)` to features of the *whole* outfit, including the decision whether to fill each optional slot.
- **The habit feature punishes extras.** `x5` is a mean of `ln lift` over item pairs, and adding shoes adds pairs with negative fallback evidence (subcategory and neighbour lifts of 0.72–0.91). With `historyLift` at 7, that swing (−1.02) is about 6 times the shoe-less penalty (`ln 0.85` = −0.16).

**Decision: compute the learned features on the core only, in training and in serving.**
- **Serving:** the score is `defaultScore(full outfit) · exp((β − β₀) · x(core items))`. Here `x(core)` is `OutfitFeatureExtractor.extract` on the outfit's core items only, meaning Top + Bottom, or the OnePiece. So adding or changing outerwear, footwear or accessories never changes the learned factor. The default rules decide the extras, at full strength.
- **Training:** every stratum's chosen and control features are computed the same way, on core items only. The baseline offset still uses the full-outfit default score: `offset = ln(defaultScore(full)) − β₀·x(core)`. The utility identity is unchanged: `offset + β·x = ln(defaultScore) + (β − β₀)·x`.
- **Footwear and layering:** on core-only features they become constant, so they stay frozen at 1.0, which is now true by construction everywhere.

**Cost:** learned taste no longer picks *which* shoes or jacket, only which core. Extras follow the default rules: weather, formality, harmony and co-wear habit at their default strength.

**Tests:**
- **The learned factor ignores extras:** for any learned weights, `scoreCombo(core + any optional items) / scoreCombo(core)` equals the same ratio under DEFAULT, to within floating-point error.
- **Device regression fixture:** a core never worn together, sneakers with negative subcategory and neighbour lift against the core's subcategories, and learned `historyLift = 7`. The top suggestion must include footwear when eligible footwear exists.
- **Training consistency:** a stratum's features equal the core-only extraction, and its offset uses the full default score.
- **Replay floors hold.** Report the numbers before and after.

**Result: rejected, nothing committed.** The device regression fixture went from failing to passing, but two existing tests broke:
- **Repeated favourites stop being learned:** stage NONE, gain 0.029 with SE 0.050 (it was HABIT, 0.186 with SE 0.086). Favourites always share the same shoes, so much of the habit evidence lives in the shoe pairs that core-only features drop.
- **Repeated noise gets through again:** seed 8 was accepted at FULL, with `meanItem` and hue at −2.0. On core-only features, the re-wear signal leaks into item suitability.

The attempt is kept outside the repo as a patch.

### D8. Pause learned taste (owner decision 2026-11-27)
**Why:** this was the sixth successive flaw found in learned taste:
1. rigged controls (#135);
2. noise accepted;
3. leakage between CV folds;
4. one real signal carrying noise through the gate;
5. the train/serve mismatch on extras;
6. D7's side effects.

Each fix was real, but the design problems are coupled and patching them on a live feature isn't converging. The measured benefit exists only on synthetic histories (planted replay hit 0.20 vs 0.15). On the real device history, learned weights made suggestions worse twice, while DEFAULT was sane both times (10 of 10 top suggestions with footwear).

**Decision:**
- `RecommendationViewModel.LEARNED_TASTE_ENABLED = false`. `resolveTasteWeights` returns `TasteWeights.DEFAULT` without reading stored weights or training. DEFAULT already includes the co-wear habit blend, so favourites still count.
- **D1–D6 stay.** Grouped CV, the cluster-robust SE, the two-stage gate, the distinct-choice minimum and their tests remain, so the trainer is still correct and tested for a redesign.
- **Re-enabling** needs a redesign that is judged first on a realistic-history scoreboard (#133, realistic test histories and common-sense checks such as "top suggestions include shoes"). It is tracked in its own issue.

**Tests:** with cached weights under a matching watermark, and with a stale watermark, the engine receives DEFAULT, and nothing is trained or persisted.

## Out of scope
- recency weighting (#136), which will weight the strata, so the cluster means in D3 must take those weights when it lands;
- colouring the replay fixture (#133).
