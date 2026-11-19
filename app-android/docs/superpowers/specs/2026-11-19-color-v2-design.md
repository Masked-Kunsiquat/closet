# Color v2 — design (ROUGH DRAFT)

**Status:** rough draft, 2026-11-19. It needs the owner's reading list to settle the open questions below, then approval. Tracking issue: #81.

## Why
Color is the weakest input to everything that uses it.

**Capture keeps only a name.** `AndroidItemPhotoProcessor.extractColors` (on add or edit) and `BatchSegmentationWorker` (after background removal):
- run AndroidX Palette;
- take its dominant, vibrant and muted swatches;
- snap each one to the nearest of 18 seeded names using `ColorMatcher`, a weighted HSL distance `sqrt(2ΔH² + ΔS² + ΔL²)`;
- store only `clothing_item_colors(item_id, color_id)`.

The measured color is thrown away.

**What inherits that loss:**
- **Recommendations:** hue harmony in `PairScorer` reads `colors.hex`, one fixed hex per *name*, so every "Yellow" item counts as `#FFFF00`. #118's learned weights include `IDX_HUE_HARMONY` and `IDX_FAMILY_HARMONY` features (`OutfitFeatureExtractor`), trained on those name-level values.
- **Chat:** `search_items` color filters match tag names, with description text as the fallback (#75).
- **Stats:** the color breakdown counts names.
- **#108, the duplicate warning:** it could use color distance, but there is none to use.

**Known misses** (#81): pale yellow sneakers tagged Tan. Three causes:
- Palette's vibrant and muted swatches add colors that aren't the item's, and without a mask the background dominates.
- HSL distance isn't perceptual, so pastels land on Beige or Tan.
- There is one reference per name, and the seeds are web color names: "Brown" `#A52A2A` is brick red, and Navy is pure `#000080`.

## Goals
1. **Accurate tags:** an item's tags match what a person would call its color, including pastels, darks, denim and heathers.
2. **Keep the measurement:** store the measured shade and its share of the item, so math runs on real shades.
3. **Main color vs. accents:** each color knows its share.
4. **Manual edits always win:** re-extraction never overwrites them.
5. **Shared extraction:** the math lives in `commonMain`; only pixel access and the mask are per platform, so iOS gets it.

**Non-goals:** cloud vision; user-defined color names or palettes.

**Anti-goals:**
- more colors tagged than a person would list;
- a baseline path that only works on one flavor;
- slowing the add-item flow noticeably. A target to confirm on the test device: extraction adds under 150 ms for a 1024-px image.

## Approaches
| | What | Trade-off |
|---|---|---|
| A | Keep Palette; add OKLab matching and reference shades (the original #81 plan) | Cheapest, but keeps theme-oriented swatches and still discards the measurement |
| B | MaterialKolor's `QuantizerCelebi` for clustering, plus OKLab naming | A solid KMP quantizer, but a dependency for about 50 lines; its `Score` step is theme-oriented and must not be used |
| **C (proposed)** | Hand-rolled OKLab plus k-means++ in `commonMain` | No dependency, testable, shared with iOS. Fall back to B if clustering proves flaky |

## Design
### Data (the first real migration, v1 → v2)
**`clothing_item_colors`** gains:
- `hex TEXT NULL`: the measured shade, as sRGB `#RRGGBB`;
- `share REAL NULL`: the fraction of item pixels, 0–1;
- `source TEXT NOT NULL DEFAULT 'user'`: one of `auto` | `user` | `nano`.

**`colors`** gains:
- `wears_as_neutral INTEGER NOT NULL DEFAULT 0`: a fashion convention, so Navy, denim, olive and camel wear as neutrals even though they have chroma. It replaces "the Neutral family means neutral" in harmony scoring.
- Reference shades: a child table `color_shades(color_id, hex)`, so pale yellow, yellow and mustard all name Yellow.

**Seeds:**
- Re-pick the base hexes to look like clothing.
- Review the families (is Forest Green "Cool"?) and the 18 names themselves: are Denim, Tan/Camel, Cream, Charcoal and Khaki missing?
- No users yet, so the seed changes need no data migration beyond the schema.

**The migration** follows `migrations/AGENTS.md`: it drops `one_ootd_per_day` first, and the test covers v1 → v2.

**Junction helpers:**
- `ClothingRepository`'s delete-then-insert colors helper takes rows of (colorId, hex?, share?, source).
- A user edit writes `source = user` and clears hex and share for colors the user added.
- **Open:** should a user *keeping* an auto-tagged color keep its hex?

### Extraction pipeline (`commonMain`, pure)
`ColorExtractor.extract(pixels: IntArray /* ARGB */, mask: BooleanArray?, palette: List<ReferenceShade>): List<ExtractedColor(colorId, hex, share)>`

1. **Pixel set:**
   - mask pixels when there is a mask (full flavor, after segmentation);
   - otherwise a centre-weighted crop with the border dropped;
   - downsample to about 10k pixels.
2. **sRGB → linear → OKLab** (Björn Ottosson's matrices).
3. **k-means++** in OKLab, k ≤ 4, seeded for determinism.
4. **Cleanup:** merge clusters closer than ΔE_merge, then drop clusters with share below `MIN_SHARE` (about 0.15).
5. **Naming:** nearest reference shade by ΔE in OKLab. That gives the name, while the cluster mean is stored as `hex`. Two clusters with the same name merge and their shares add.
6. **Output:** at most 3, by share.
7. **White balance (optional):** correct the colour cast of the lighting before clustering. **Open:** decide with the reading list and the demo wardrobe.

**Platform:**
- **Android:** decodes the bitmap and supplies the mask: ML Kit when present, else none.
- **iOS:** the centre path now; a Vision foreground mask later.
- AndroidX Palette and the HSL `ColorMatcher` are deleted.

### Downstream changes
- **Engine (`PairScorer`):**
  - The hue term reads the item's measured hex (name hex as fallback) and works in OKLCh hue, not HSL.
  - Neutrality is `wears_as_neutral` or measured chroma below a threshold.
  - Pair contributions are weighted by share: main colors count fully, accents lightly. **Open:** the exact weighting.
  - The same-family `max(familyRule, hueTerm)` from #115 stays.
  - Re-check the thresholds (80–100° clash band, ±30° analogous, 150–210° complementary) on OKLCh and real shades; the seeded-palette test moves to the new references.
- **Learned weights (#118):** the hue and family feature values change, so bump `TASTE_WEIGHTS_SCHEMA_VERSION` to retrain, and keep `ReplayCheckTest` green.
- **Chat:** `search_items` matches return share, so "mostly blue" and "blue trim" become answerable. The tool description stays ≤ 180 characters.
- **Stats:** the color breakdown stays by name. Share weighting is optional and probably later.
- **#108:** measured-color ΔE becomes an extra duplicate signal next to embeddings, which #108 owns.

### Nano overlay (full flavor, supported phones; later PR)
- Photo plus the seeded names go in, and a structured-output enum comes back.
- It writes `source = nano` names, and the pipeline's measured hex attaches where the names agree.
- It falls back to the pipeline.

## Delivery
1. **PR 1:** the schema migration, seeds and shades; the `ColorExtractor` pipeline replacing Palette; the junction helpers; the demo-wardrobe accuracy check.
2. **PR 2:** engine and chat consumers, threshold re-check, weights schema bump.
3. **PR 3 (optional):** the Nano overlay.

## Done when
- **Pipeline unit tests** (known pixels → names and shares):
  - pastel yellow → Yellow; mustard → Yellow; tan → Tan/Camel; denim → Denim or Navy, per the seed decision; charcoal → Charcoal or Grey;
  - a two-color synthetic item at 70/30 gives shares within ±0.05;
  - an all-background crop returns nothing;
  - the same input gives the same output.
- **The migration test passes** (v1 → v2).
- **Demo wardrobe (#70):** auto-tag agreement with the dataset's `baseColour` is reported before and after, and the after must beat before by a margin set once we see the baseline.
- **User edits survive** re-extraction.
- **The engine's replay and variety tests stay green.**

## Open questions (for the reading list)
1. ΔE thresholds for merging and naming: OKLab Euclidean or CIEDE2000? *(Color difference; Oklab color space)*
2. Is k-means++ with k ≤ 4 enough, and how should k be picked? Fixed k plus merge, or a silhouette/elbow method? *(k-means clustering; Color quantization)*
3. Gamma: average in linear light vs. in OKLab. *(sRGB)*
4. White balance: correct or not, and how? Gray-world, or using the background? *(Color constancy; Color balance)*
5. Harmony bands in OKLCh vs. HSL hue, and whether triadic or split-complementary deserve a bonus. *(Color scheme; Color theory)*
6. Seed names: which basic and fashion color terms to add. *(Basic Color Terms)*
7. Heathers and patterns: does a striped or heathered item produce two clusters, and is that desired?
