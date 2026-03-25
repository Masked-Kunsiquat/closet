# Outfit Recommendations Feature — Design Notes
# (v2 — refined 2026-03-24 after weather feature design sessions)

## Overview
An opt-in outfit suggestion feature that uses a two-layer architecture:

1. **Programmatic pipeline** — does the heavy lifting. Handles weather/temperature
   filtering, statistical suitability scoring from log history, category completeness,
   color/pattern rules, and candidate trimming. SQL only, no AI.

2. **Gemini Nano coherence scorer** — sees only a small, clean, constrained candidate
   pool. Acts as a last-mile style coherence scorer, not a generative engine.

AI is entirely optional. The programmatic pipeline alone is a useful feature.
Coherence scoring is an enhancement layered on top when the device supports it.

---

## Architecture Philosophy
- Programmatic logic does the heavy lifting — AI only sees a small, clean payload
- AI cannot hallucinate invalid categories/items because output is validated against real DB IDs
- User can always override suggestions
- Feature is entirely opt-in, hidden behind a settings toggle
- Programmatic suggestions work even on devices that don't support Gemini Nano
- Statistical suitability is derived from log history at query time — never stored as a
  column (consistent with the project's no-derived-data rule)

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
- Occasion match — user-selects at suggestion time

### 1b. Statistical suitability scores (derived from log history, not stored)
These are SQL aggregations over `outfit_logs` + `outfit_log_items` + `outfit_logs`
weather columns. Items with no history are treated as neutral (score = 0.5).

- **Comfortable temp range** — collect all logs where this item was worn with a
  non-null temperature range. Comfortable range = 10th–90th percentile of logged
  `temperature_low`/`temperature_high` values. If today's forecast is outside that
  range, down-score. Sparse data (< 5 logs with temps): skip this filter, don't penalise.
- **Rain suitability** — % of this item's logs where `precipitation_mm` > 1.0 mm.
  If raining today and rain% < 20%, down-score. Sparse data: skip.
- **Wind suitability** — % of logs where `wind_speed_kmh` > 30 km/h. If windy today
  and wind% < 20%, down-score. Sparse data: skip.
- **Worn-recently penalty** — if the item appears in any log within the past 3 days,
  apply a soft penalty (not a hard filter). User preference could relax this later.

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

### 1d. Category completeness
Valid outfit = (top + bottom) OR (dress/jumpsuit), with optional outerwear/footwear/accessories.
Engine builds combinations satisfying completeness before passing to the AI.

### 1e. Presentation-layer trimming (before AI payload)
- Color harmony: complementary, analogous, neutral pairing. Clashing combos ranked lower.
- Pattern mixing: solid + pattern = OK; pattern + pattern = flagged (lower rank, not excluded).
- Color deduplication: one representative per color/subcategory combo — no 8 black t-shirts.
- Per-category cap: 1–2 candidates per category slot max.

---

## Layer 2 — Gemini Nano Coherence Scorer (opt-in, device-dependent)

Only runs if:
- AI features toggle is on in settings
- Model is downloaded and `aiReady` flag is true in DataStore
- Candidate pool is non-empty after Layer 1

If any condition is false, the top Layer-1-ranked combination is returned directly.

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

## Category & Subcategory Auto-Fill (separate but related)
When user adds a clothing image:
- Send image + constrained prompt to Gemini Nano (ImagePart + TextPart)
- Prompt includes full category/subcategory taxonomy, instructs JSON-only response
- Response validated against actual lookup table values before populating form
- Pre-fills category and subcategory dropdowns — user can override freely
- Gated on `aiReady` flag, same as outfit suggestions

---

## Settings & Opt-In Flow
- "AI features" toggle in settings — off by default
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

---

## Future Considerations
- Thumbs up/down feedback loop to weight future suggestions (requires new table)
- `general notes` field on clothing items fed as context to Nano for user-defined rules
- Expose per-item suitability scores in item detail view ("you usually wear this in
  15–25°C, sunny conditions")
- User-configurable worn-recently window (3 days default)
