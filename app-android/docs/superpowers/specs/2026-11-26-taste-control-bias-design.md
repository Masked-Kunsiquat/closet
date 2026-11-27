# Taste learning: unbiased control outfits (design)

**Status:** done in PR #143. Approved 2026-11-26; the drift bound was revised to [β₀−3, β₀+6], with a 2·SE gate and a replay tolerance. Issue: #135. Follow-ups: #136 (recency weighting), #142 (grouped CV).

## Problem
Taste learning (#118) fits a conditional logit. Each logged outfit, the *chosen* one, is compared against *controls*: outfits the user could have worn that day instead. `HistoricalStratumBuilder` builds those controls by **maximising the same default rules whose weights are being learned**, at three levels:

1. **Role candidates:** each core role is capped at 20 items, stratified by `itemScore` (half from the top).
2. **Cores:** half of the 30 controls are the top-scoring cores under the default rules ("hard negatives"); the other half are spread across the rest.
3. **Completions:** each control core gets its single best set of extras under the default rules (`BEAM_PER_CORE_COMPLETION = 1`). That almost always includes shoes, plus the best colour, pattern and formality terms.

Real outfits are messier than rule-optimal controls on every rule. The likelihood explains that with **negative weights on every rule**. On the test device (120 logs, 96 of them with shoes), the result was:

| Feature | Learned weight |
|---|---|
| footwear | −24.4 |
| hue harmony | −28.4 |
| family harmony | −18.0 |
| item suitability (`meanItem`) | −23.4 |

At −24.4, footwear multiplies a shoe-less outfit's score by about 60, so the top suggestion had no shoes. #118 already saw the core-level version of this (`historyLift` driven negative) and softened it by stratifying. That reduced the bias but didn't remove it.

**Why the replay check missed it:**
- every planted log has shoes;
- the learned-vs-default held-out likelihood is scored against the same biased controls.

## Principle
For a conditional logit fitted on a **sample** of alternatives, sampling the alternatives **uniformly** gives consistent estimates without any correction term (McFadden's uniform-conditioning property). Sampling by score does not, unless each alternative's log sampling probability is added, which we don't do. So controls must be **uniform random draws** (seeded, so training stays deterministic). They must never be ranked by the rules being learned.

## Decisions

### D1. Uniform, seeded control sampling at every level
- **Seed:** `Random(seed = logId)`. It's per stratum and independent of the date and the clock, so the same logs always give the same weights, and backup or restore still rebuilds identically.
- **Role candidates:** cap each core role at 20 items by **uniform sampling** without replacement from the available pool. This replaces the `itemScore`-stratified cap. The worn items are always present in the pool; a control may reuse one of them, as long as the core isn't the worn core.
- **Cores:** draw up to `MAX_CONTROLS_PER_STRATUM = 30` competing cores **uniformly without replacement** from all competing cores. The worn core and the other cores worn that day are still excluded. This replaces `selectStratifiedControls`, the scoring-and-ranking of cores, and the hard-negative half.

### D2. Slot-matched completions: whether to wear shoes is not a learned taste
- **Matched slots:** each control gets **the same number of items per optional role as the chosen outfit**, for outerwear, footwear and accessories. If the worn outfit had shoes, every control has shoes; if not, none do.
- **Item choice:** each slot's item is drawn uniformly (with the same seeded generator) from that role's full available pool. This replaces the top-6 deduplicated cap and the greedy best completion.
- **Consequence:** the footwear and layering features become **constant within each stratum**, so they have zero within-stratum variance. The existing guard then freezes them at β₀ = 1. They stay fixed rules, owned by the engine (and by #138 for weather), and are never learned. This removes the shoe-less artefact by construction.
- **Why accessories are matched too:** how many accessories someone wears is a weak signal, and matching keeps the completion step uniform.
- `findBestCompletion` and `BEAM_PER_CORE_COMPLETION` are deleted. **Training gets cheaper,** because no completion search is scored.

### D3. A drift bound at every data size (safety net)
- **The bound (revised by the owner, 2026-11-26):** every learned weight is clamped to the **asymmetric** range `[β₀ − 3, β₀ + 6]`, which is `[−2, 7]`, at any data size. The existing ±0.5 clamp for fewer than 30 strata stays.
- **Why asymmetric:** the failure that hurt users was a weight swinging hard **negative**, turning a rule into an anti-rule. So inversions are capped tightly at −3, while genuine preferences may strengthen up to +6.
- **Measured on the planted replay fixture**, where the default scores hit 0.15 and LL −66.7 (learned hit / held-out LL):

  | Bound | Hit | LL |
  |---|---|---|
  | ±3 | 0.20 | −59.6 |
  | ±6 | 0.20 | −52.9 |
  | **[−3, +6]** | 0.20 | −53.3 |
  | none | 0.25 | −28.7 |

  The unbounded fit needs `historyLift` around 31, which means "only re-suggest favourites". That runaway magnitude is exactly what the bound exists to stop.
- **Why a bound at all:** it guards against model misspecification left over after D1–D2. It's not a fix for bias.

### D3b. A two-SE acceptance gate (owner decision, 2026-11-27)
`TasteWeightTrainer` accepts learned weights only when the chosen λ's mean held-out Δℓ is greater than **twice** its standard error. Otherwise it returns `DEFAULT`. That is roughly a one-sided 97.7% bar.

**Measured over 20 pure-noise histories:**

| Gate | Fits accepted | Weight range of accepted fits |
|---|---|---|
| 1·SE (first version) | 3 of 20 | down to −2.0, up to 4.75 |
| 2·SE | 0 of 20 | none accepted |

Planted taste is unaffected (learned hit 0.20 vs 0.15 fixed). The colour-matching and shoe-less fixtures are still accepted.

A no-flip floor (`β ≥ 0`) was measured and rejected. On its own, noise was still accepted and amplified upwards. Combined with the gate, it added nothing.

**Cost:** a user with a faint but real taste needs more logs before learning switches on.

### D4. Retrain everyone
- `TASTE_WEIGHTS_SCHEMA_VERSION` goes from `"v3"` to `"v4"`, so the inverted stored weights are discarded and retrained on next use.
- The feature vector is unchanged (10 features), so `FEATURE_COUNT` stays 10.

### D5. Tests that would have caught it
Each test builds its fixture in a learning test, not in the replay fixture. #133 owns colouring the replay fixture.

| Test | Planted data | Expected result |
|---|---|---|
| **Shoe-less artefact** | 20% of logs without shoes, shoes otherwise, choices random | footwear and layering weights are exactly 1.0 (frozen) |
| **No inversion** | choices uniformly random | every learned weight within ±1 of β₀ |
| **Rule-following user** | complementary or analogous colour pairs chosen far more often than chance | hue harmony ≥ 1, never inverted |
| **Determinism** | the same logs twice, and with a different `today` | identical weights |

- **Controls are uniform:** across many strata, the colour-harmony distribution of the controls matches that of all competing cores. Sample means agree within tolerance; under the old builder they were clearly above.
- **Replay, no planted taste (revised by the owner):** the old strict check (learned LL ≥ fixed LL on random choices) is a coin flip once the controls are unbiased: both models sit at chance. It becomes **learned ≥ fixed − 0.05 × the number of held-out strata**, about 1 nat for 20, which is the expected optimism of a 10-parameter fit. The "no inversion" and "shoe-less" tests carry the real protection.
- **Replay:** `ReplayCheckTest` floors hold. Report fixed and learned hit rate, and held-out log-likelihood, before and after. The likelihood comparison is now scored on unbiased controls, so its absolute value changes. That's expected.
- **Performance:** training time on the large-wardrobe performance fixture must not regress.

## Out of scope
- recency weighting (#136);
- colouring the replay fixture (#133);
- the soft filters (#137, #141);
- outfit-level weather (#138).

## Done when
- D1–D5 are implemented and tested.
- On the test device after a retrain:
  - no learned weight is inverted beyond the D3 bound;
  - footwear and layering are 1.0;
  - the top suggestion includes shoes when eligible shoes exist.
