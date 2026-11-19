# Color v2 — design

**Status:** approved for planning, 2026-11-19. Tracking issue: #81. The PR 1 plan is `docs/superpowers/plans/2026-11-19-color-v2-pr1-extraction-and-schema.md`.

**Decisions made while planning PR 1** (they amend the sections below):
- **Re-picking the base hexes moves to PR 2.** The engine reads `colors.hex` and `color_family`, so PR 1 leaves the 18 existing rows unchanged. Naming uses the new `color_shades` table instead.
- **New seed names:** Charcoal, Cream, Denim and Teal. Khaki and camel become shades of Tan, and lavender a shade of Purple.
- **Tan wears as neutral,** because it lies between the sourced neutrals beige and brown. This departs from point 12's "camel not neutral".
- **Beige's `basic_term` is brown, and Teal's is blue.**
- **A kept auto colour keeps its measured hex.** Any manual change to an item's colours marks its rows `user`, and `user` rows are never overwritten by re-extraction.

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
- `basic_term TEXT NOT NULL`: one of the 11 Berlin & Kay basic terms, for chat search, the stats roll-up and coarse logic (see Research findings 6).

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
5. **Naming:** nearest reference shade by ΔE in OKLab. That gives the name, while the cluster's mean in linear RGB, encoded to sRGB, is stored as `hex` (see Research findings 3). Two clusters with the same name merge and their shares add.
6. **Output:** at most 3, by share.
7. **White balance (optional):** correct the colour cast of the lighting before clustering. **Open:** decide with the reading list and the demo wardrobe.

**Platform:**
- **Android:** decodes the bitmap into sRGB (`inPreferredColorSpace`, see Research findings 5) and supplies the mask: ML Kit when present, else none.
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
- **Chat:** `search_items` color filters also match `basic_term`, so "blue" finds Navy and Sky Blue items as tagged matches. Matches return share, so "mostly blue" and "blue trim" become answerable. The tool description stays ≤ 180 characters.
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
  - pastel yellow (including `#FFFACD`, which is 0.029 from Beige) → Yellow; mustard → Yellow; tan → Tan/Camel; denim → Denim or Navy, per the seed decision; charcoal → Charcoal or Grey;
  - a two-color synthetic item at 70/30 gives shares within ±0.05;
  - an all-background crop returns nothing;
  - the same input gives the same output;
  - conversion vectors: `#FFFFFF` → OKLab (1, 0, 0) and `#FF0000` → (0.6280, 0.2249, 0.1258), each to ±1e-4; the sRGB encode/decode round-trip is within 1 / 255.
- **The migration test passes** (v1 → v2).
- **Demo wardrobe (#70):** auto-tag agreement with the dataset's `baseColour` is reported before and after, and the after must beat before by a margin set once we see the baseline.
- **User edits survive** re-extraction.
- **The engine's replay and variety tests stay green.**

## Research findings (2026-11-19)
Sources: Wikipedia *Color quantization*, *Oklab color space*, *sRGB*, *Color term*, and Wikibooks *Color Theory/Color gradient*.

**Settled:**
1. **Distance is plain Euclidean in OKLab.** The Oklab page: "The perceptual color difference in Oklab is calculated as the Euclidean distance". No CIEDE2000 is needed.
   - Neither page gives a just-noticeable-difference value, so `ΔE_merge` and the naming cut-off are **calibrated on the demo wardrobe (#70)**, not taken from a source.
   - The thresholds live as named constants, with the calibration numbers recorded in the PR.
2. **Clustering is k-means in a perceptual space.**
   - *Color quantization* cites Celebi (2011): "an efficient implementation of k-means outperforms a large number of color quantization methods". It also calls Lab "another popular choice" because "Euclidean distance is more consistent with perceptual difference".
   - The page gives no rule for choosing k, so we keep **fixed k = 4 + merge + `MIN_SHARE` drop**, which is deterministic and simple.
   - Dithering is irrelevant to us, because we never render the quantized image.
3. **Assign clusters in OKLab, but average in linear RGB.**
   - The Oklab page warns it is "not suitable for image blending or processing, for which the gamma-expanded linear RGB color space is more appropriate". The Wikibooks gradient page agrees: average in *linear* RGB.
   - So:
     - pixels are assigned to clusters by OKLab distance;
     - each cluster's stored representative is the **mean of its members in linear sRGB**, encoded back to sRGB for `hex`;
     - k-means centroid *updates* still use OKLab means, so assignment stays perceptual.
   - **Never average gamma-encoded sRGB bytes.**
4. **Exact constants.** These were verified by the controller: white `#FFFFFF` → (L, a, b) = (1, 0, 0) and red `#FF0000` → (0.6280, 0.2249, 0.1258), C = 0.2577, h = 29.2°. A subagent's transcription of the page had lost the minus signs and failed this check, so **the implementation's tests must include these two vectors.**
   - **sRGB decode:** `c ≤ 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055)^2.4`.
   - **sRGB encode:** `c ≤ 0.0031308 ? 12.92·c : 1.055·c^(1/2.4) − 0.055`.
   - **Linear sRGB → LMS:**
     ```
     l = 0.4122214708 r + 0.5363325363 g + 0.0514459929 b
     m = 0.2119034982 r + 0.6806995451 g + 0.1073969566 b
     s = 0.0883024619 r + 0.2817188376 g + 0.6299787005 b
     ```
   - **Cube root**, then **LMS′ → Lab:**
     ```
     L = 0.2104542553 l′ + 0.7936177850 m′ − 0.0040720468 s′
     a = 1.9779984951 l′ − 2.4285922050 m′ + 0.4505937099 s′
     b = 0.0259040371 l′ + 0.7827717662 m′ − 0.8086757660 s′
     ```
   - **OKLCh:** C = √(a² + b²), h = atan2(b, a). Hue is undefined at C = 0 and noisy at low C, so **gate harmony on a chroma floor.** In practice C ≤ about 0.4 for sRGB colors.
   - **Gamut:** converting a linear mean back to sRGB stays in gamut, because it is a convex combination of in-gamut colors. Clamp to [0, 1] before the 8-bit encode anyway.
5. **Decode photos into sRGB.**
   - The sRGB page says sRGB is "usually the assumed colorspace for images that do not have an embedded color profile". But phones can write Display P3.
   - On Android, decode with `BitmapFactory.Options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)` (API 26 = our `minSdk`), so pixel math always sees sRGB. Verify on the test device with one of its own photos.
6. **Two-level names** (*Color term*, Berlin & Kay). English has 11 basic terms: black, white, red, green, yellow, blue, brown, orange, pink, purple, gray.
   - Basic terms are "monolexemic … high-frequency, and agreed upon by speakers". Terms like maroon and turquoise are not basic. Brown is set apart by *lightness* (dark orange), which OKLab naming handles naturally, because L is in the distance.
   - **Our 18 seeds have no plain "Blue" or "Green"**, only Navy, Royal Blue, Sky Blue, Forest Green, Mint and Olive. Chat's color filter resolves names against the lookup table, so "blue" doesn't match a Navy item as a tag (only through description text).
   - **Proposal:** each seeded color gets a `basic_term` column (Navy, Royal Blue and Sky Blue map to blue; Burgundy maps to red; Tan maps to brown; Beige maps to white or brown, *to decide*).
     - The fashion name stays the tag the user sees and edits.
     - The basic term powers chat search ("blue"), the stats roll-up and coarse family logic.
     - Languages split blue into light and dark (Russian, Italian), which supports keeping Navy and Sky Blue as separate fashion names under one basic term.

**Second round** (Wikipedia *Color balance*, *CIE 1931 color space*, *Color wheel*, *Color theory*, *Hue*, *Comparison of color models in computer graphics*, Wikibooks *Color Models: RGB, HSV, HSL*, and W3C *CSS Color Module Level 4*; the controller recomputed every number below):

7. **Thresholds have a scale, and naming needs reference shades.**
   - CSS Color 4 §14.2.1: "For the OkLCh color space, one JND is an OkLCh difference of 0.02." That value is a conversion (ΔE2000 JND ≈ 2, ÷ 100), used for gamut mapping, not an observer measurement. The "< 0.01 imperceptible / > 0.05 clearly distinct" figures from a search-engine AI summary are **not** in the spec, so we don't use them.
   - **Controller-computed reference distances:**

     | Pair | ΔE_OK |
     |---|---|
     | Adjacent 8-bit greys | 0.003 |
     | Grey + 5 steps | 0.017 |
     | Beige vs. Tan | 0.182 |
     | Yellow vs. mustard `#E1AD01` | 0.214 |
     | Navy vs. Royal Blue | 0.289 |
     | Black vs. charcoal `#36454F` | 0.382 |

     Fabric shading spans many JNDs, so `ΔE_merge` starts around 0.06–0.08 (3–4 JND), to be calibrated on the demo wardrobe.
   - **Oklab alone does not fix #81's bug.** Pale yellow `#FFFACD` is **0.029 from Beige** but 0.154 from Yellow, so nearest-name naming in Oklab would still call it Beige. Reference shades (pale yellow as a Yellow shade) are what fix it, and the pale-yellow → Yellow test is the guard.
   - Beige and pale yellow are only about 1.5 JND apart, so this is a naming-convention choice, not perception. The Beige reference shades must sit at lower chroma than Yellow's pale shades.
8. **No white-balance correction in PR 1.**
   - *Color balance*: balancing works best in camera RGB ("most camera RGB spaces performed better than either monitor RGB or XYZ"), and we only get processed sRGB.
   - Phone JPEGs have already been through the camera's auto white balance, so correcting again risks a double correction. (This is inference: the page doesn't discuss pre-balanced images.)
   - Gray-world isn't on the page. Its known failure is a scene dominated by one colour, which is exactly a photo of one garment (inference).
   - **If measurement on warm-light test photos shows casts:** a later option is von Kries diagonal scaling in Oklab's own LMS stage, using the known background pixels from the mask as the neutral estimate. Not built now.
9. **Harmony is a weak prior, on the painter's wheel.**
   - *Color wheel*: "a painter's color wheel is indicative rather than predictive". *Color theory* (summary-level read) says harmony responses vary with culture and context. So harmony stays a weak prior whose weight #118 learns from your logs.
   - But learning only scales the bands, it can't move them, so **which wheel the bands sit on matters.**
   - **Controller-computed hue gaps** for the painter's (RYB) complements:

     | Pair | HSL hue gap | OKLCh hue gap |
     |---|---|---|
     | Red + Forest Green | 120° | 114° |
     | Yellow + Purple | 120° | 141° |
     | Orange + Royal Blue | 174° | 164° |

     Today's 150–210° "complementary" band misses red–green and yellow–purple on *both* wheels. Fashion advice ("red and green are complementary") follows the RYB wheel.
   - **Proposal (owner to approve):** warp the OKLCh hue onto an RYB-like wheel with a piecewise-linear map through anchors taken from the seeded hues:

     | Color | OKLCh hue | RYB wheel |
     |---|---|---|
     | red | 29° | 0° |
     | orange | 71° | 60° |
     | yellow | 110° | 120° |
     | green | 143° | 180° |
     | blue | 266° | 240° |
     | purple | 328° | 300° |

     The analogous, complementary and clash bands then apply on the warped wheel, so the painter's complements land at about 180°.
   - Hue differences wrap around 360° (*Hue*: "the simple difference between the two hue angles", wrapped).
   - No triadic or split-complementary bonus by default, because the rules are weak. #118 can learn if one matters.
   - Harmony stays gated on the chroma floor. *Hue* notes that HSL hue "does not appear to all be of the same lightness", which is another reason to leave HSL.
   - **Alternative:** keep the bands on the raw OKLCh hue. That's simpler, but it contradicts stylist convention for red–green and yellow–purple.

**Third round: fashion sources.**
- **Read:** Karen Tarver, "How to combine colors"; Carlo Pham, "Color wheel for clothes"; Ivy & Pearl, "Matching clothing colors using the color wheel"; The Concept Wardrobe, "What is color analysis".
- **Failed to load:** Vogue (fetch refused) and a Medium article (HTTP 403).
- The controller had the key Tarver sentences re-fetched verbatim.

10. **The painter's wheel is confirmed, so we adopt the RYB warp from point 9.**
    - Ivy & Pearl: "The primary colors are red, yellow, and blue", with complements "red/green, yellow/purple, and blue/orange".
    - Tarver: complements "yellow and violet; blue and orange; red and green".
    - Pham's 12-segment primary/secondary/tertiary wheel is RYB-style.
    - All three readable outfit guides agree.
11. **Drop the 80–100° clash penalty.** No source names a clashing hue angle.
    - Tarver: "Colors which are at an angle of 90 degrees from each other are a good match when combined in an outfit". Her page never says any combination should be avoided.
    - Ivy & Pearl: "Complementary colors always match."
    - Pham and Ivy list triadic, split-complementary and tetradic as workable, with "one color to dominate" for four-colour schemes.
    - **New bands on the warped wheel:**

      | Hue gap | Multiplier |
      |---|---|
      | ≤ 60° (analogous; widened from 30° by point 16) | × 1.05 |
      | 150–210° (complementary) | × 1.05 |
      | everything else | × 1.0 |

    - The #115 seeded-pair test is rewritten to match. The Bright+Bright family penalty is a separate question for PR 2.
12. **The neutral set comes from the sources, not my earlier guess.**
    - Tarver: "Black, brown, gray and white are neutral colors … Denim is considered a neutral color in most cases as well."
    - Ivy & Pearl: "black, gray, white, off-white, brown, charcoal, and beige". Navy is listed there as a *cool colour* ("navy, blue, and shades of green").
    - **So `wears_as_neutral`** = black, white/off-white/cream, grey/charcoal, beige, brown, denim.
    - **Navy, olive and camel are *not* neutral.** This reverses the earlier draft and the current seed, where Navy's family is Neutral. Navy then takes part in hue harmony, so navy + orange gets the classic blue–orange complement.
    - Brown becomes neutral; today it's Earth.
13. **Intensity matching is a candidate, not a rule.**
    - Ivy & Pearl: "match pastel colors with other pastels, jewel tones with jewel tones". Only one source says this.
    - So PR 2 adds a **chroma-match feature** (the OKLCh chroma gap between the main colours) to #118's feature set with a **prior weight of 0**. It only matters if your logs show it.
    - Adding it means bumping `TASTE_WEIGHTS_SCHEMA_VERSION`.
14. **Dominance:** the multi-colour advice ("one or two colors as the dominant ones", Pham) supports the share weighting already in the design: main colours count fully, accents lightly.
16. **Vogue ("How to Master Color Theory for Clothing Like a Vogue Fashion Editor") and Maggie Carroll ("Color Theory in Fashion", Medium).** Both were read in full from owner-supplied PDFs.
    - **Four of four guides now use the painter's wheel.** Vogue: "primary colors: red, yellow, and blue"; complements "red and green, orange and blue, yellow and purple". Carroll calls orange and blue "on the exact opposite sides of the color wheel".
    - **Navy is a blue, not a neutral.** Vogue describes a monochromatic blue scheme that "can span deep navy, bright sky, and pale baby blue". Its neutrals are "black or white", plus "off-whites and rich espresso". That supports point 12.
    - **Dominance:** "one color is usually the star of the show in complementary looks", which supports share weighting (point 14).
    - **The warp checked against Vogue's real outfits.** The hexes are the controller's estimates of the named colours:

      | Outfit | Vogue calls it | Raw OKLCh gap | Warped gap |
      |---|---|---|---|
      | Forest green sweater + vivid red skirt | complementary | 117° | **176°** |
      | Blue dress over an orange tank | complementary | 159° | **166°** |
      | Dusty purple sweater + ochre skirt | complementary | 89° | **102°** |
      | Red + orange | analogous | — | 46° |
      | Lime + olive | analogous | — | 31° |
      | Carroll's pink + purple | analogous | — | 37° |
      | Carroll's purple + blue | analogous | — | 63° |

      - The warp turns red–green and blue–orange into complements, as stylists describe them.
      - Dusty purple + ochre still misses, at 102°. Dusty purple is low-chroma, where hue is unreliable, and ochre is an orange-yellow, so the estimated hexes are the weak link. This is why harmony stays gated on a chroma floor and stays a weak, learned prior.
      - Tarver's 90° example (yellow + red-orange) lands at 86° on the warped wheel, as she describes.
    - **Change: widen analogous to ≤ 60° on the warped wheel**, replacing ≤ 30°. Real analogous examples sit at 31–63°, because a three-colour run on a 12-step wheel spans 60°.
    - **Monochrome needs lightness variety.** Carroll: "green with varying saturation and value (especially value)". Vogue: "plenty of room for dimensionality". A same-hue outfit looks deliberate when value varies. This is the second **candidate learned feature (prior weight 0)**, the lightness spread among same-hue main colours, alongside chroma-match (point 13).
    - **Patterns in tonal dressing:** Vogue says "patterns are your friend in tonal dressing" (a striped green maxi skirt). So a two-tone striped item producing two same-hue clusters is fine and expected, which answers part of the heathers/patterns question.
    - **Preference over rules:** Vogue says to pick "a color that matches your season and—more importantly—that you like". That backs leaving the weights to #118.

17. **The manual colour picker** (from a search-engine AI answer about UI palettes; claims checked by the controller):
    - **Adopt the two-tier picker for manual edits.** Show a row of basic terms, and tapping one reveals that term's fashion shades (blue → Navy, Royal Blue, Sky Blue). This maps directly onto `basic_term` (point 6). It's a UI note for PR 1's edit form; whether to build it is the owner's call.
    - **Reject evenly spaced hue steps and "avoid pure black/white".** Those advise building a pleasant *UI* palette. Our seeds must look like real garments (the `#A52A2A` "Brown" lesson), and black and white garments exist.
    - **Two of its claims are wrong. Don't reuse them:**
      - **"OKLCH guarantees the exact same visual weight at a given lightness."** Many lightness–chroma–hue cells are out of the sRGB gamut, and the peak chroma depends on the hue:

        | L | Max chroma, yellow (h 110°) | Max chroma, blue (h 264°) |
        |---|---|---|
        | 0.3 | 0.065 | 0.173 |
        | 0.9 | 0.196 | 0.048 |

        So "peak chroma in the mid-tones" doesn't hold for yellow or blue. Its HSL claim *is* right: `#FFFF00` is L 0.968 and `#0000FF` is L 0.452.
      - **"L > 0.65 needs dark text, below needs light text."** For greys, WCAG contrast crosses over near L ≈ 0.56. At L 0.60, white text is only 3.9:1, which fails AA's 4.5:1, while black text is 5.3:1. Use WCAG contrast on relative luminance, not an Oklab L cut-off.

15. **Seasonal colour analysis is out of scope.**
    - The Concept Wardrobe: a colour is harmonious "if it emphasises certain aspects of your own coloring". It is about the wearer's skin, hair and eyes, not garment-to-garment pairing.
    - The app has no photo of the wearer. A user-chosen "season" palette preference is a possible later feature and a non-goal here.

**Still open:**
- **Heathers and patterns:** does a striped or heathered item produce two clusters, and is that desired? To be decided on demo-wardrobe examples; `MIN_SHARE` and `ΔE_merge` govern it.
- **The seed list itself:** which of Denim, Camel/Khaki, Cream, Charcoal and Teal to add, and the `basic_term` of the ambiguous ones (Beige, Tan, Olive).
