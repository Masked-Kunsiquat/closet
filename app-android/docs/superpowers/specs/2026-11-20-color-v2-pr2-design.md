# Color v2 PR 2: engine and chat read measured color (design)

**Status:** draft for owner approval, 2026-11-20. Part of #81. The parent spec is `2026-11-19-color-v2-design.md`; this document settles that spec's PR 2 open points and does not repeat its research. The numbers below come from the spec's Oklab matrices and were recomputed by the controller.

## Scope
**In:**
- the recommendation engine's colour harmony (measured hex, OKLCh, the RYB warp, new bands, `wears_as_neutral`, share weighting);
- two new learned features with no effect until learned, plus the weights schema bump;
- clothing-realistic base hexes;
- chat colour search by basic term, with shares in the results.

**Out:**
- stats (they stay by name);
- #108 (duplicate warning);
- the Nano overlay (PR 3);
- the two-tier picker;
- the AI coherence scorer, which keeps reading families.

## Already settled by the parent spec
- **Hue** comes from OKLCh, warped onto the painter's (RYB) wheel. The warp is piecewise-linear through these anchors, wrapping at 360°:

  | Colour | OKLCh hue | Warped |
  |---|---|---|
  | red | 29° | 0° |
  | orange | 71° | 60° |
  | yellow | 110° | 120° |
  | green | 143° | 180° |
  | blue | 266° | 240° |
  | purple | 328° | 300° |

- **Bands** on the warped gap, folded to 0–180°:

  | Gap | Multiplier |
  |---|---|
  | ≤ 60° (analogous) | × 1.05 |
  | ≥ 150° (complementary) | × 1.05 |
  | anything else | × 1.0 |

  The 80–100° clash penalty is removed.
- **Neutrality** comes from `wears_as_neutral`, not from the Neutral family.
- **The #115 same-family rule stays:** when both colours share a family, the term is `max(familyRule, hueTerm)`.
- **Weights:** bump `TASTE_WEIGHTS_SCHEMA_VERSION`, and keep `ReplayCheckTest` (hit-rate floor 0.15) and the variety tests green.

## Decisions for approval

### D1. Re-picked base hexes: each one becomes one of its own reference shades
`colors.hex` is the engine's fallback when an item has no measurement (manual tags, and items never re-detected), and it is the swatch the UI shows. Ten base hexes are web colours that don't look like clothes. Each is replaced with one of that colour's existing reference shades, so no new numbers are invented:

| Colour | Now | New | Why |
|---|---|---|---|
| Brown | `#A52A2A` | `#6B4423` | the current value is brick red |
| Navy | `#000080` | `#1F2A44` | pure screen blue |
| Red | `#FF0000` | `#C41E3A` | |
| Orange | `#FFA500` | `#F28C28` | see note |
| Yellow | `#FFFF00` | `#F2CF3A` | |
| Purple | `#800080` | `#6A3D8F` | |
| Olive | `#808000` | `#708238` | |
| Mint | `#98FF98` | `#A8DCC0` | neon |
| Forest Green | `#228B22` | `#355E3B` | |
| Beige | `#F5F5DC` | `#E8DCC4` | near-white |

The other twelve keep their hex.

**Navy's `color_family` changes from Neutral to Cool.** Navy no longer wears as neutral (parent point 12), so it takes part in harmony, and the family fallback must agree.

**Checks on the new hexes** (warped gaps):
- red + forest green 168°;
- yellow + purple 177°;
- orange + royal blue 162°;
- orange + navy 163°;
- orange + teal 163°.

All of these land in the complementary band.

**Note on orange:** my first pick, `#E8742A`, put orange + blue at 148° and missed the band, which is why I chose `#F28C28` instead.

**How it ships:** `MIGRATION_3_4`, which updates the 10 hexes and Navy's family by name, together with the seeds, so existing installs (both phones) match fresh ones. Schema v4 adds no columns; `MigrationTest` checks the v3 → v4 path and that the migration's values agree with the seeds.

**Alternative:** change the seeds only. That's fresh-install-only and has no migration, but both phones would keep brick-red Brown.

**Recommended:** the migration.

### D2. What the engine reads per colour tag
For each tagged colour, the engine reads:
- **hex:** the measured hex, or the name hex when there is no measurement;
- **share:** the measured share;
- `family`;
- `wears_as_neutral`.

When any of an item's shares is missing (manual tags), every tag on it counts as `1 / n`.

### D3. Neutral colours and the item's harmony colour
A colour is **neutral** when it has `wears_as_neutral`, or when its chroma is below **C_FLOOR = 0.03** (1.5 JND). Hue is meaningless at that chroma.

Where the floor falls:

| Colour | Chroma | Result |
|---|---|---|
| lavender `#E6E6FA` | 0.027 | neutral (it's a Purple shade) |
| Charcoal | 0.026 | neutral (it already wears as neutral) |
| beige | 0.033–0.046 | wears as neutral anyway |
| re-picked navy | 0.050 | chromatic |
| pale yellow `#FFFACD` | 0.058 | chromatic |

An item's **harmony colour** is its largest-share non-neutral colour; ties go by colour id. An item with no non-neutral colour is **neutral** and sits out of harmony, as Neutral-family items do today.

The family rule uses the harmony colour's family, instead of the "dominant family" priority over every tag.

### D4. Share weighting
A pair's colour term is weighted by the shares of the two harmony colours: `ln(term) × sA × sB`. A solid item counts fully.

**Example:** a grey tee with a 30% red print, paired with green chinos. Red + green is complementary, but the effect is `1.05^0.3 ≈ × 1.015`.

The same weighting applies to whichever term is used, hue or family.

**Alternative:** score every colour pair across the two items, weighted by share products. That's more complete, but it's harder to reason about and would let accents pile up.

**Recommended:** main colour only.

### D5. Two learned features, with no effect until learned
These are parent points 13 and 16, computed over harmony-colour pairs:
- **x8, chroma match:** `(1 − W) · mean |ΔC| / 0.1`;
- **x9, lightness spread** among same-hue pairs (warped gap ≤ 60°): `(1 − W) · mean |ΔL| / 0.1`. It is 0 when there are no such pairs.

**What changes in the code:**
- `FEATURE_COUNT` goes from 8 to 10.
- `TasteWeights` keeps its β₀ = 1, so `(β − β₀)·x` is 0 for both features until the optimizer learns from your logs. The deployed score is unchanged.
- The schema goes from `"v2"` to `"v3"`. Stored weights reset to the default and retrain on the next learning pass.

### D6. Chat colour search
**Basic terms become valid filter values.** "blue" matches every colour whose `basic_term` is blue (Navy, Royal Blue, Sky Blue, Denim, Teal), and those count as *tagged* matches. Name matching is unchanged. The British spelling "grey" also resolves to the basic term gray, so it finds Charcoal as well as Grey.

**Item JSON** (`search_items`, `get_items`): `colors` becomes a list of `{name, share}`, ordered by share, with `share` omitted when it isn't known. That lets "mostly blue" and "blue trim" be answered. Item lists keep their cap of 20.

The `colors` parameter's description is updated, and the tool description stays at 180 characters or fewer.

### D7. Left alone
- `MULTIPLIER_BRIGHT_BRIGHT`: no seeded colour has the Bright family, so it never fires.
- Stats.
- The AI coherence scorer.
- Extraction thresholds.

## Delivery (one PR, 4 tasks)
1. **Data:** `MIGRATION_3_4` plus the seeds (D1); the DAO returns the measured hex, share and `wears_as_neutral` per tag; a batched colour-rows query for chat.
2. **Engine:** an OKLCh and warp helper (reusing `core.data.color.Oklab`); D2–D4 in `PairScorer` and `colorHarmonyFactors`; `PairScorerTest` rewritten on the new seeds.
3. **Learned weights:** D5.
4. **Chat:** D6.

The whole-branch verification, the replay hit rate before and after (reported in the PR), and doc updates (`CLAUDE.md`'s chat colour note, the parent spec's status) close it out.

## Done when
- **Warp:** the anchor hues map exactly to their wheel positions, and the warp is continuous across the 328° → 29° wrap.
- **The new hexes:** red + forest green, yellow + purple and orange + royal blue each get × 1.05.
- **Navy** + orange is complementary, while grey + anything is skipped.
- **Shares:** a 30% accent's effect is the full effect to the power 0.3.
- **C_FLOOR:** lavender `#E6E6FA` counts as neutral.
- **Weights:** at the default weights, x8 and x9 change no score: the score equals the same combo's score with those two features zeroed. The schema is `"v3"`.
- **Chat:** "blue" finds a Navy item as a tagged match; "grey" finds Charcoal; shares appear in the item JSON.
- **The migration:** v3 → v4 keeps the data, applies the 10 hexes and Navy's family, and agrees with the seeds.
- **Tests:** `ReplayCheckTest` stays at or above 0.15, and the variety tests pass.
