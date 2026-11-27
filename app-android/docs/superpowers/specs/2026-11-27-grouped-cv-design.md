# Taste learning: grouped cross-validation (design)

**Status:** approved 2026-11-27 (with MIN_DISTINCT_CHOICES = 20). Issue: #142. Builds on #135 (PR #143).

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

### D3. Count each choice once in the gate
Held-out deltas are first **averaged per `choiceKey`** within the validation set. The mean and SE used by the one-SE rule and the 2·SE gate are then computed **across choice keys**: one observation per distinct outfit. This is the standard cluster-level correction. With no repeats, it's identical to today.

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

Also:
- `ReplayCheckTest` floors hold, with before and after numbers reported. Its planted fixture repeats favourite triples, so this is the real check that genuine taste still passes.
- **Device:** after the retrain, the stored weights are either DEFAULT (if the demo's 25 random outfits are noise) or within β₀ ± 1 on the rule features. Hue must not sit at the floor.

## Out of scope
- recency weighting (#136), which will weight the strata, so the cluster means in D3 must take those weights when it lands;
- colouring the replay fixture (#133).
