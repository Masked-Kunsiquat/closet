# Sizing & Settings Roadmap

Two deferred features with clear implementation paths. Both are self-contained and can ship independently.

---

## Feature 5 — Size Tracking on Items

### Current state

The backend is **complete**. Nothing needs to be added to the database layer.

| Layer | What exists |
|-------|------------|
| `size_systems` table | `SizeSystemEntity` — `id`, `name` |
| `size_values` table | `SizeValueEntity` — `id`, `size_system_id` (FK), `value`, `sort_order` |
| `clothing_items.size_value_id` | Nullable FK → `size_values(id)` ON DELETE SET NULL |
| `ClothingItemDetail.sizeValue` | `@Relation` already joined |
| `LookupDao` | `getSizeSystems()` + `getSizeValues(systemId)` both return `Flow` |
| `LookupRepository` | Wraps both DAO methods |
| Seeds | 4 systems seeded on `onCreate`: Letter (XS–XXXL), Women's Numeric (00–16), Shoes US Men's (6–15 incl. half sizes), One Size |

**Gap:** the UI layer is entirely missing. The form has no size picker, the detail screen shows nothing, and `ClothingFormViewModel` carries no size state.

### Auto-selection: subcategory → size system

When the user picks a subcategory, the ViewModel auto-selects the most appropriate size system. The user can still override it via the system dropdown.

```text
Footwear (any subcategory)              → Shoes (US Men's)
  Sneakers, Boots, Sandals, Dress Shoes, Slippers

Accessories, Bags                       → One Size
  Belt, Hat/Cap, Scarf, Sunglasses, Watch, Jewelry,
  Tie, Cufflinks, Backpack, Tote, Crossbody, Duffel

Everything else (Tops, Bottoms,         → Letter
  Outerwear, Dresses & Jumpsuits,
  Activewear, Underwear & Intimates,
  Swimwear)
```

Women's Numeric is intentionally **not** auto-selected — we can't infer gender from subcategory, and Letter covers unisex/men's. The user switches to Women's Numeric manually when needed.

The mapping lives in a pure function in the ViewModel (no DB changes, no new seeds):

```kotlin
private fun defaultSizeSystemName(subcategoryName: String?): String? = when (subcategoryName) {
    "Sneakers", "Boots", "Sandals", "Dress Shoes", "Slippers" -> "Shoes (US Men's)"
    "Belt", "Hat/Cap", "Scarf", "Sunglasses", "Watch",
    "Jewelry", "Tie", "Cufflinks",
    "Backpack", "Tote", "Crossbody", "Duffel"              -> "One Size"
    null                                                     -> null
    else                                                     -> "Letter"
}
```

Match against `sizeSystem.name` from the already-loaded `sizeSystems` list; no extra query.

### What to build

#### Step 1 — ViewModel state (ClothingFormViewModel)

Add to `ClothingFormUiState`:
```kotlin
val sizeSystems: List<SizeSystemEntity> = emptyList()
val sizeValues: List<SizeValueEntity> = emptyList()   // filtered by selected system
val selectedSizeSystemId: Long? = null
val selectedSizeValueId: Long? = null
```

In `ClothingFormViewModel.init`, collect `lookupRepository.getSizeSystems()` into state.

When `selectedSizeSystemId` changes, collect `getSizeValues(systemId)` and clear `selectedSizeValueId` (unless hydrating for edit, where the saved value should be restored instead).

When `selectSubcategory(subcategory)` is called, call `defaultSizeSystemName(subcategory?.name)`, find the matching `SizeSystemEntity` in the loaded list, and call `selectSizeSystem(it)` — **but only if the user has not already manually picked a system**. Track a boolean `sizeSystemUserOverridden` in private ViewModel state; set it to `true` when the user explicitly changes the system dropdown, reset it to `false` when the category changes.

On save, pass `selectedSizeValueId` into `ClothingItemEntity.sizeValueId`.

#### Step 2 — Form UI (ClothingFormScreen)

Two-step cascade picker, placed after the brand field:

1. **System dropdown** — `ExposedDropdownMenuBox` listing all size systems. Auto-populated by subcategory selection; user can override. Changing the system resets the value.
2. **Value dropdown** — `ExposedDropdownMenuBox` listing `sizeValues` sorted by `sort_order`. Disabled until a system is selected.

Both fields are optional — the "no size" state is `null` for both IDs. A "Clear" icon button next to the system dropdown resets both fields and clears `sizeSystemUserOverridden`.

#### Step 3 — Detail screen display (ClothingDetailScreen)

Show a "Size" row using `item.sizeValue?.value`. Append the system name in muted text when it adds clarity: `M  ·  Letter`, `9.5  ·  Shoes`, `One Size`. Hide the row entirely when `sizeValue` is null.

#### Step 4 — Filter chip (optional, Phase 2)

Size is a single-select FK, not a junction, so filtering is straightforward: add a size-system group to `FilterPanel` when items in the closet have sizes set. No schema changes — just a `WHERE size_value_id IN (...)` clause in `ClothingDao`.

### Effort

- Steps 1–3 (core feature): ~1 day
- Step 4 (filter): ~half day, can ship later

---

## Feature 7 — Settings Screen & Accent Theming

### Current state

Accent palettes are defined and the theme is parameterised, but nothing persists or switches at runtime.

| Layer | What exists |
|-------|------------|
| `ClosetAccent` enum | 6 values: Amber, Coral, Sage, Sky, Lavender, Rose — each with `primary` + `muted` `Color` |
| `getDarkColorScheme(accent)` / `getLightColorScheme(accent)` | Both accept accent; manually assign `primary`/`secondary`/etc. |
| `ClosetTheme(accent)` | Composable accepts accent param, defaults to `Amber` |
| `MainActivity` | Calls `ClosetTheme()` with no argument — hard-coded Amber |

**Gap:** no DataStore, no SettingsViewModel, no Settings screen, no nav route, no runtime switching.

### The theming problem worth solving first

The current `ClosetAccent` only stores two hand-picked hex values (`primary`, `muted`). The M3 color system expects a full tonal palette — `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`, `secondary`, `tertiary`, and ~20 more roles — and the current `getDarkColorScheme` fills most of those slots with hard-coded `Color.kt` constants, not values derived from the accent.

This means switching accent today would only change a few surfaces; the rest of the UI stays Amber.

**The fix: use Material Theme Builder (MTB) to generate proper per-accent ColorSchemes.**

### How MTB works

MTB takes one "seed" hex color and uses the CAM16/HCT color space algorithm to generate a full tonal palette across all M3 color roles — including contrast-safe `on*` colors and tonal containers. You export a `Theme.kt` + `Color.kt` pair from the web UI.

The workflow for hangr:

1. Go to [https://m3.material.io/theme-builder](https://m3.material.io/theme-builder) (web app)
2. Enter the primary hex for each accent as the seed color (see table below)
3. Click **Export → Jetpack Compose (Theme.kt)**
4. You get a `Color.kt` (all tonal values) and a `Theme.kt` (`lightColorScheme()` + `darkColorScheme()`)
5. Rename and drop into `core/ui/theme/` as `ColorAmber.kt` / `SchemeAmber.kt`, etc.

**Recommended seed colors** (aligned with current `ClosetAccent.primary` values):

| Accent | Current primary hex | Suggested MTB seed |
|--------|--------------------|--------------------|
| Amber | `#F59E0B` | `#F59E0B` |
| Coral | `#F97316` | `#F97316` |
| Sage | `#84CC16` | `#84CC16` |
| Sky | `#38BDF8` | `#38BDF8` |
| Lavender | `#A78BFA` | `#A78BFA` |
| Rose | `#FB7185` | `#FB7185` |

MTB will auto-derive secondary, tertiary, and all neutrals from each seed — you don't need to pick them. The result is a coherent, contrast-verified palette for each accent, not just two hand-chosen hex values.

**Alternative (fully in-code, no web tool):** the [MaterialKolor](https://github.com/jordond/MaterialKolor) library generates a full `ColorScheme` from a seed `Color` at runtime in one line. Adds ~50 KB to the APK. Suitable if you want to add user-defined colors later.

### What to build

#### Step 1 — Generate 6 ColorSchemes via MTB

For each accent, export from MTB and add to `core/ui/theme/`:
- `ColorAmber.kt`, `ColorCoral.kt`, etc. — tonal values (generated, don't hand-edit)
- Replace the body of `getDarkColorScheme(accent)` / `getLightColorScheme(accent)` in `Theme.kt` to switch on `accent` and return the corresponding pre-baked `darkColorScheme(...)` / `lightColorScheme(...)`.

The `ClosetAccent` enum can keep its `primary` and `muted` fields — they're still useful for the swatch picker UI.

#### Step 2 — Preferences DataStore (core/data)

Add `androidx.datastore:datastore-preferences` to `libs.versions.toml` + `core/data/build.gradle.kts`.

Create `PreferencesRepository` (`@Singleton`):
- `getAccent(): Flow<ClosetAccent>` — reads stored string, maps to enum, falls back to `Amber`
- `setAccent(accent: ClosetAccent): suspend Unit`

Provide via `DataModule`.

#### Step 3 — Thread accent through MainActivity

Inject `PreferencesRepository` into `MainActivity`. Collect `getAccent()` as `State<ClosetAccent>` via `collectAsStateWithLifecycle`. Pass into `ClosetTheme(accent = accent)`. That's all — the full `ColorScheme` swap propagates everywhere automatically.

#### Step 4 — SettingsViewModel

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepo: PreferencesRepository
) : ViewModel() {
    val accent: StateFlow<ClosetAccent> = prefsRepo.getAccent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClosetAccent.Amber)

    fun setAccent(accent: ClosetAccent) {
        viewModelScope.launch { prefsRepo.setAccent(accent) }
    }
}
```

#### Step 5 — Settings screen

One section for accent, one for app info:

**Accent color** — a 6-swatch row or 2×3 grid. Each swatch is a 48 dp filled circle using `ClosetAccent.primary`. Active accent gets a 2 dp selection ring in `accent.muted`. Tapping calls `setAccent(it)`. The theme recomposes immediately on the same screen (live preview effect).

**App info** (static): `BuildConfig.VERSION_NAME`, a link to the GitHub repo if desired.

#### Step 6 — Nav route + entry point

Add `SettingsDestination` (`@Serializable object`) to `ClosetNavGraph.kt`.

Add a gear `IconButton` to the `TopAppBar` in `ClosetScreen`. No bottom nav tab needed.

### Effort

- Step 1 (MTB export + plumbing): ~2–3 hours. MTB export itself is 10 minutes per accent; wiring takes the rest.
- Steps 2–6 (persistence + UI): ~1 day

---

## Sequencing suggestion

```text
Feature 7 Step 1 (MTB export)  →  Feature 5 (size tracking)  →  Feature 7 Steps 2–6 (settings screen)
```

The MTB step is quick and unblocks proper theming before any more UI is built. Size tracking ships next because the backend is already done. Settings screen (persistence + UI) closes Feature 7.
