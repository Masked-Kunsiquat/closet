# Outfit Recommendations Feature — Design Notes
# (v4 — refined 2026-03-25, scoring + color + pattern decisions finalised)

## Overview
An opt-in outfit suggestion feature that uses a two-layer architecture:

1. **Programmatic pipeline** — does the heavy lifting. Handles weather/temperature
   filtering, statistical suitability scoring from log history, category completeness,
   color/pattern rules, and candidate trimming. SQL only, no AI.

2. **Gemini Nano coherence scorer** — sees only a small, clean, constrained candidate
   pool. Acts as a last-mile-style coherence scorer, not a generative engine.

AI is entirely optional. The programmatic pipeline alone is a useful feature.
Coherence scoring is an enhancement layered on top when the device supports it.
**Phase 1 = programmatic pipeline only. AI coherence scoring is Phase 2.**

---

## Architecture Philosophy
- Programmatic logic does the heavy lifting — AI only sees a small, clean payload
- AI cannot hallucinate invalid categories/items because output is validated against real DB IDs
- User can always override suggestions
- Feature is entirely opt-in, hidden behind a settings toggle
- Programmatic suggestions work even on devices that don't support Gemini Nano
- Statistical suitability is derived from log history at query time — never stored as a
  column (consistent with the project's no-derived-data rule)
- Feature lives in `features/recommendations/` — a self-contained module. The app
  must not break when this module is absent or disabled.

---

## Module Structure
`features/recommendations/` — standalone module, same dependency rules as other features.
Dependencies: `core/data`, `core/ui`. No other feature module depends on it.

---

## UX Flow

### Entry point
- Outfit gallery screen is restyled to a **3-column grid** (currently 1-column list).
- The existing FAB expands into two options: **Add outfit** (existing) and **Get suggestions** (new).
- Tapping "Get suggestions" opens the pre-suggestion sheets, then navigates to the
  Suggestions screen.

### Pre-suggestion sheets (shown in order, both optional)
1. **Occasion sheet** — single-select. User picks one occasion (Casual, Formal, etc.).
   It can be dismissed or skipped; the engine runs without an occasion filter if skipped.
2. **Weather sheet** — shows today's conditions for the engine to use:
   - Auto-filled from `WeatherRepository` when a cached forecast is available. A small
     chip indicates "Pulled from location data" when auto-filled.
   - Fields: temperature range (low/high °C), condition (Sunny / Cloudy / Rainy /
     Snowy / Windy — a realistic preset list), precipitation toggle, wind toggle.
   - User can override any field freely (or fill it in manually if location is off).
   - If dismissed with no data: engine skips temperature-band season matching and
     all weather-based suitability signals; calendar-only season mode runs instead.
   - The engine receives weather conditions as plain parameters — it does not call
     `WeatherRepository` directly.

### Suggestions screen
- Displays **3 outfit combinations** in a horizontal carousel.
- Each card in the carousel shows the items in the combo (images or placeholders).
- Per-combo actions:
  - **Log it** — navigates to outfit logging flow with this combo pre-loaded (user
    can accept as-is or adjust before logging).
  - **Save for later** — saves the combo as a named outfit for future use.
  - **Regenerate** — batch-regenerates all 3 combos (not just the current card).

---

## Schema Prerequisites

### Migration 1→2 (already applied)
- `seasons.temp_low_c` / `temp_high_c` — temperature band for season matching ✅
- `categories.warmth_layer` — None / Base / Mid / Outer ✅
- `outfit_logs.precipitation_mm` / `wind_speed_kmh` — weather data collection ✅

### Migration 2→3 (needed before engine work)
- `categories.outfit_role` — TEXT NOT NULL DEFAULT 'Other'.
  Values: `Top | Bottom | OnePiece | Outerwear | Footwear | Accessory | Other`.
  Seeds: Top → Tops, Bottom → Bottoms, OnePiece → Dresses & Jumpsuits,
  Outerwear → Outerwear, Footwear → Footwear, Accessory → Accessories.
  Used by category completeness check. String-matching on `categories.name` is
  explicitly avoided — all role logic reads this column.
- `colors.color_family` — TEXT NOT NULL DEFAULT 'Neutral'.
  Values: `Neutral | Earth | Cool | Warm | Bright`.
  Seeds (representative): Neutral → black, white, gray, cream, beige, navy, camel;
  Earth → brown, tan, rust, olive, khaki, terracotta;
  Cool → blue, teal, purple, lavender, sage, mint, slate;
  Warm → red, orange, yellow, coral, pink, burgundy;
  Bright → neon / high-saturation variants.
  Used by color harmony scoring in 1e.

---

## Layer 1 — Programmatic Pre-Filter (no AI involved)

### 1a. Hard filters (binary — item either passes or doesn't)
- `wash_status = Clean` only
- `status = Active` only
- Season match — two modes run in parallel:
    - Calendar mode: item's seasons include the current calendar season
    - Temperature-band mode: current forecast temp falls within `temp_low_c`/`temp_high_c`
      on any of the item's tagged seasons (uses the `seasons` lookup table added in
      Migration 1→2). An item passes season filter if it passes EITHER mode.
    - All Season items always pass.
    - If no weather data provided (sheet skipped): calendar mode only.
- Occasion match — user-selects via occasion sheet (skippable; omit filter if skipped)

### 1b. Statistical suitability scores (derived from log history, not stored)
These are SQL aggregations over `outfit_log_items` (historical snapshot table) joined
to `outfit_logs` weather columns. Items with no history are treated as neutral (score = 1.0).

- **Comfortable temp range** — collect all logs where this item was worn with a
  non-null temperature range. Comfortable range = 10th–90th percentile of logged
  `temperature_low`/`temperature_high` values. If today's forecast is outside that
  range, down-score. Sparse data (< 5 logs with temps): skip this signal.
- **Rain suitability** — % of this item's logs where `precipitation_mm` > 1.0 mm.
  If raining today and rain% < 20%, down-score. Sparse data: skip.
- **Wind suitability** — % of logs where `wind_speed_kmh` > 30 km/h. If windy today
  and wind% < 20%, down-score. Sparse data: skip.
No worn-recently penalty. The app promotes a small, high-CPW wardrobe — repeat
wearing is expected and encouraged, not penalised.

**Scoring formula — multiplicative per-item score starting at 1.0:**

| Signal | Condition | Multiplier |
|--------|-----------|------------|
| Temp range | Today's forecast outside comfortable range | × 0.55 |
| Rain suitability | Raining today and item's rain% < 20% | × 0.60 |
| Wind suitability | Windy today and item's wind% < 20% | × 0.70 |
| Sparse data | < 5 logs for a given signal | skip that multiplier |

Multipliers stack independently. Outfit-level multipliers applied after item scoring:

| Condition | Multiplier |
|-----------|------------|
| Cold day (< 5°C) + no Outer item in combo | × 0.50 |
| Warm day (> 20°C) + Outer item in combo | × 0.75 |

**Outfit score** = arithmetic mean of item scores × outfit-level multipliers.
Ties broken by longest-since-last-worn (prefer items not worn recently, without penalising them).

> Note: these scores inform ranking and trimming, not hard exclusion. An item with a
> low suitability score is still a candidate — it's just ranked lower and trimmed first
> when the pool needs to shrink.

### 1c. Layering validation (outfit-level, not item-level)
Uses `warmth_layer` on `categories` (None / Base / Mid / Outer — Migration 1→2).

- Cold day (below 5°C): candidate pool must include at least one Outer item or the
  engine adds the highest-ranked Outer item from the full active/clean set.
- Moderate day (5–15°C): Mid layer encouraged but not required.
- Warm day (above 20°C): Outer items down-ranked (still available for user override).
- Layering score is applied at the OUTFIT level after item selection — a t-shirt alone
  scores low on a cold day; same t-shirt with a jacket scores fine.
- If no weather data: layering validation is skipped.

### 1d. Category completeness
Uses `categories.outfit_role` (Migration 2→3).
Valid outfit = (role:Top + role:Bottom) OR (role:OnePiece), with optional
Outerwear / Footwear / Accessory slots.
Engine builds combinations satisfying completeness before scoring.

### 1e. Presentation-layer trimming (before AI payload)
- **Color harmony** — uses `colors.color_family` (Migration 2→3). Outfit-level adjustments:
  - Neutral + anything → compatible (no effect)
  - Same family + same family → compatible (+0.10 bonus)
  - Earth + Warm → compatible (+0.05)
  - Bright + Bright → clash (× 0.70)
  - Warm + Cool → slight clash (× 0.85)
  - All other pairings → neutral (no adjustment)
  Multi-color items: use the highest-saturation color's family. Items with no color
  tagged: treated as Neutral.
- **Pattern mixing** — item is "solid" if its patterns junction is empty or all tagged
  patterns are named `"Solid"`. Outfit-level adjustments:
  - 0 patterned items → no effect
  - Exactly 1 patterned item → +0.10 bonus (mixed solid + pattern is encouraged)
  - 2+ patterned items → × 0.75 penalty
- Color deduplication: one representative per color/subcategory combo — no 8 black t-shirts.
- Per-category cap: 1–2 candidates per category slot max.

---

## Layer 2 — Gemini Nano Coherence Scorer (opt-in, device-dependent)
**Phase 2 only — do not build until the programmatic pipeline is solid.**

### Provider abstraction
Modelled on the weather feature's multi-provider pattern. A `AiInferenceClient`
interface (or equivalent) abstracts the underlying provider so an alternate
implementation can be substituted without touching the engine.

### Activation conditions
Only runs if:
- AI features toggle is on in settings
- Model is downloaded and `aiReady` flag is true in DataStore
- Candidate pool is non-empty after Layer 1

If any condition is false, the top 3 Layer-1-ranked combinations are returned directly.

### Token Management
- On feature init: `getTokenLimit()` — store in DataStore, never hardcode
- Before every inference: serialize candidate payload → `countTokens()` → trim if over
- Treat `FinishReason.MAX_TOKENS` as failed inference, not partial result
- `countTokens()` gates only the dynamic candidate payload — system instructions are
  cached via `PromptPrefix`

### PromptPrefix (Cached System Instructions)
**Prefix (cached once):**
- Full output format instructions (JSON only, no preamble)
- Behavioral constraints ("only select from the IDs provided")
- Output schema definition

**Dynamic suffix (per request):**
- Serialized JSON of the pre-filtered + ranked candidate items for this specific request
- Include statistical suitability scores as context hints so Nano has signal beyond raw item data

### Prompt Design
Constrained output — AI selects from IDs provided, never generates free-text clothing data:
```json
{ "selected_ids": [1, 4, 7], "reason": "..." }
```
- `selected_ids` validated against the candidate list before any DB interaction
- `reason` field surfaced behind a "why?" tap, not shown by default
- Any response that fails JSON parsing or contains unknown IDs is discarded silently
  and the top programmatic result is returned instead

---

## Category & Subcategory Auto-Fill (deferred — separate AI-phase ticket)
When user adds a clothing image, send image to Gemini Nano to pre-fill category +
subcategory dropdowns. Separate from outfit recommendations. Deferred until Phase 2
AI infrastructure is in place.

---

## Settings & Opt-In Flow
- AI section added to the existing Settings screen — off by default
- On toggle on, background worker runs in sequence:
    1. `checkStatus()` — not supported? surface error, flip toggle back off
    2. Supported but not downloaded? `download()` — stream progress to UI
    3. Downloaded? `getTokenLimit()` — store in DataStore
    4. Mark `aiReady = true` in DataStore
- UI surfaces status: Checking → Downloading (n%) → Ready / Failed / Not Supported
- All AI-dependent features check `aiReady` flag before running
- If not ready: show programmatic result with no "AI" label; hide "why?" affordance
- `aiReady`, `tokenLimit` stored in DataStore (not DB, not SharedPreferences)
- Programmatic suggestions work regardless of `aiReady`

---


## Known Caveats
- GenAI Prompt API (MLKit) is in beta — API surface may change before release
- Gemini Nano minimum requirements: Pixel 6 or equivalent (≥ Android 10); ~1.5 GB
  free storage for model download. Unavailable on most mid-range/budget devices.
- Nano is a small model — occasional poor picks are expected; override is always available
- Statistical suitability needs log history to build up. New users see programmatic
  suggestions without suitability scoring until enough logs accumulate (5+ per item).
- Layering scores require `warmth_layer` to be set on categories. Only Outerwear and
  Underwear & Intimates are seeded. Other categories default to "None" — users will
  need to assign Base/Mid as that UI is built.
- `outfit_role` and `color_family` must be seeded correctly in Migration 2→3 or
  category completeness checks and color harmony will silently produce wrong results.

---

## Future Considerations
- Thumbs up/down feedback loop to weight future suggestions (requires new table)
- `general notes` field on clothing items fed as context to Nano for user-defined rules
- Expose per-item suitability scores in item detail view ("you usually wear this in
  15–25°C, sunny conditions")
