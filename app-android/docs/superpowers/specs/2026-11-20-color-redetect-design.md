# Re-detect colors and the "colours curated" marker — design

**Status:** approved by the owner on 2026-11-20 (Option A). **Issue:** #128, which also covers the clear-all finding from #127's review. **Parent:** #81 (color v2).

## Why
- **Pre-v2 items can't get measured shades.** Color v2 (#127) never lets automatic extraction overwrite colours the user chose. The v1→v2 upgrade had to mark every existing item's colours as `user`, so the demo wardrobe (298 items) has no way to get measured shades: batch background removal logged "left unchanged" for all of them.
- **A cleared selection isn't remembered.** If a user clears every colour, zero rows looks the same as "never tagged", so `replaceAutoColors` can re-add colours.

## Goals
1. **Item-level marker:** a flag that records "the user set this item's colours by hand", including clearing them all. Automatic re-tagging respects it.
2. **Per-item "Re-detect colors"** with a preview. Applying it gives the item measured `auto` rows.
3. **Bulk "Re-detect colors"** in Settings. An option limits it to items the user never edited.
4. **Display P3 check:** an on-device test proves that photo decoding converts Display P3 to sRGB.

**Non-goals:**
- engine or chat changes (color v2 PR 2);
- Nano;
- changing how extraction works.

**Anti-goals:**
- any automatic overwrite of user-set colours;
- a bulk action that runs without a confirmation that states its count.

## Approaches (owner chose A)
- **A. A `clothing_items.colors_curated` column.** Chosen. It can represent "cleared on purpose", and it's one cheap check.
- **B. Derive the marker from the junction rows.** Rejected, because it can't represent an empty selection.
- **C. A separate table of curated items.** Rejected: more joins for no gain.

## Design
### Data: migration v2 → v3
- **Column:** `clothing_items.colors_curated INTEGER NOT NULL DEFAULT 0`, with entity field `colorsCurated: Boolean = false`.
- **Backfill:** set it to `1` for every item with any `clothing_item_colors.source = 'user'` row. After the upgrade every migrated item is curated, so behaviour is unchanged.
- **Migration:** manual, starting with the `one_ootd_per_day` drop, per `migrations/AGENTS.md`.

### When the flag changes
| Event | Flag afterwards |
|---|---|
| Form save after any manual colour toggle (including clear-all) | `true` |
| Form save where a new photo's extraction set the colours and the user didn't touch them | `false` |
| Form save with colours untouched and no extraction applied | unchanged (the stored value) |
| Detail colour picker changes the set (`updateColorsAsUser`, changed case) | `true` |
| Detail picker confirmed unchanged | unchanged (no write) |
| Re-detect applied (per item or bulk) | `false` |
| `replaceAutoColors` writes (worker / background removal on untouched auto) | stays `false` |

- **`replaceAutoColors`** writes nothing when `colors_curated = 1`, **or** any row is `user`, **or** the extraction is empty.
- **Demo-backup tool:** it writes `user` rows, so it sets `colorsCurated = true`.

### Re-detect on one item (detail screen)
- **Entry point:** a "Re-detect colors" action in the Colors section, shown when the item has an image.
- **Measuring:** the current image, through `ItemPhotoProcessor.extractColors` with the reference shades. A cut-out keeps its alpha mask.
- **Preview:** a sheet showing the current colours next to the proposed ones (name, swatch, share). **Apply** writes `auto` rows with hex and share and sets the flag to `false`. **Cancel** changes nothing. The preview is the confirmation.
- **Nothing detected:** a snackbar says "Couldn't detect colours", and nothing changes.

### Re-detect for all items (Settings)
- **Entry point:** next to batch background removal, on **both flavors**. Extraction needs no GMS.
- **Confirmation dialog:**
  - a checkbox **"Only items whose colours you haven't edited"**, on by default;
  - the item count for the current choice;
  - for "all items", it states plainly that it replaces colours the user set.
- **Background job:** a WorkManager job, `ColorRedetectWork`, with progress and a result message (re-tagged / unchanged / failed). It follows the `BatchSegmentation*` pattern: a scheduler interface in core/data commonMain, no-op on JVM and iOS, and the Android worker in features/wardrobe.
- **Per item:** an empty extraction leaves the item unchanged. Otherwise it applies, exactly as the per-item Apply does.
- **Shared code:** the per-item logic lives in a commonMain `ColorRedetector` used by both the ViewModel and the worker, so it can be tested on the JVM.

### Display P3 check
- **Test:** an `androidDeviceTest` in core/data.
  - It creates a `Bitmap` in `ColorSpace.Named.DISPLAY_P3` filled with a known in-gamut colour, writes it as PNG (Android embeds the profile), and decodes it with `BitmapUtils.decodeSampledBitmap`.
  - It asserts that the result's colour space is sRGB, and that the pixel equals `ColorSpace.connect(DISPLAY_P3, SRGB)` applied to the source colour, within ±2 per channel.
- **Runs on:** the test device, via `./gradlew :core:data:connectedAndroidDeviceTest`.

## Done when
- **Migration:** test for v2→v3, covering the backfill and fresh-vs-migrated parity. `FreshInstallSchemaTest` is updated to v3.
- **Flag tests:** one per row of the table above. The clear-all case is: clear every colour, then run `replaceAutoColors`, and the item stays colourless.
- **Per-item and bulk:** tests for preview / Apply / Cancel / empty, eligible counts, and the worker decision logic (`ColorRedetector`).
- **The P3 device test** passes on the test device.
- **Device smoke:**
  - re-detect one demo item, and its shades look right;
  - run bulk "only unedited", which leaves the demo wardrobe unchanged (every item is curated);
  - running bulk "all items" is the owner's call, since it re-tags the demo wardrobe.
