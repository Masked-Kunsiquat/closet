# Brand System — Implementation Roadmap

## Current State Summary

| Item | Status |
|---|---|
| `clothing_items.brand` column | **Exists** — nullable `TEXT`, free-form string |
| `LookupRepository.getDistinctBrands()` | **Exists** — queries distinct strings from the column |
| Brand in `ClothingFormScreen` | **Exists** — plain `OutlinedTextField` |
| `brands` lookup table | **Does not exist** |
| Brand management screen | **Does not exist** |
| Autocomplete widget | **Does not exist** |

The pivot: `brand` is currently denormalized free text. The feature requires normalizing it into a proper FK relationship. This touches the schema, entity, all DAOs that select brand, the repository layer, and the UI.

---

## Phase 1 — Schema Migration

**Migration 3 → 4**

1. Create `brands` table:
   ```sql
   CREATE TABLE brands (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       name TEXT NOT NULL UNIQUE
   )
   ```
2. Backfill brands from existing data:
   ```sql
   INSERT OR IGNORE INTO brands (name)
   SELECT DISTINCT brand FROM clothing_items
   WHERE brand IS NOT NULL AND brand != ''
   ```
3. Add `brand_id` column to `clothing_items`:
   ```sql
   ALTER TABLE clothing_items ADD COLUMN brand_id INTEGER
       REFERENCES brands(id) ON DELETE SET NULL
   ```
4. Populate `brand_id` from the backfill:
   ```sql
   UPDATE clothing_items
   SET brand_id = (SELECT id FROM brands WHERE brands.name = clothing_items.brand)
   WHERE brand IS NOT NULL AND brand != ''
   ```
5. SQLite cannot drop columns before API 35, so `brand` (the old text column) stays but is deprecated — stop reading it in all new queries. Add a comment in the migration noting it is vestigial.

**Seed data** (called from the migration, not `seedAll`): insert 5–10 common brands (`Zara`, `H&M`, `Nike`, `Adidas`, `Levi's`, `Uniqlo`, `Gap`, `Mango`) using `INSERT OR IGNORE` so they don't overwrite backfilled data and are idempotent.

**Schema export:** Room will auto-export `4.json` when `kspDebugKotlin` runs.

---

## Phase 2 — Data Layer

### 2a. `BrandEntity`
New file in `core/data/model/` (alongside `LookupEntities.kt` — can live inside it or as a separate file):
```kotlin
@Entity(tableName = "brands")
data class BrandEntity(
    id: Long,       // PrimaryKey autoGenerate
    name: String    // NOT NULL UNIQUE
)
```

Update `ClothingDatabase` entities list and bump version to 4.

### 2b. `BrandDao`
New `@Dao` interface (separate from `LookupDao` since brands are user-managed, not seed-only lookups):

| Method | Query |
|---|---|
| `getAllBrands(): Flow<List<BrandEntity>>` | `SELECT * FROM brands ORDER BY name` |
| `getBrandById(id): BrandEntity?` | `SELECT * WHERE id = :id` |
| `insertBrand(brand): Long` | `INSERT INTO brands (name) VALUES (:name)` |
| `updateBrand(brand)` | `@Update` |
| `deleteBrand(id)` | `DELETE WHERE id = :id` |
| `getItemCountForBrand(brandId): Int` | `SELECT COUNT(*) FROM clothing_items WHERE brand_id = :brandId` |

### 2c. Update `ClothingItemEntity`
- Add `brandId: Long?` with `ForeignKey(entity = BrandEntity::class, onDelete = SET_NULL)`
- Keep `brand: String?` column present (legacy, no longer written to)
- Add index on `brand_id`

### 2d. Update `ClothingDao` queries
Every `SELECT` that emits brand data should change from `ci.brand` to `brands.name` via a LEFT JOIN:
```sql
LEFT JOIN brands ON brands.id = ci.brand_id
```
Update `ClothingItemWithMeta.brand: String?` to be populated from the join. No structural changes to the data class — just the query source changes.

### 2e. `BrandRepository`
New repository (separate from `LookupRepository` since brands are user-editable):

| Method | Returns | Notes |
|---|---|---|
| `getAllBrands()` | `Flow<List<BrandEntity>>` | For management screen and form autocomplete |
| `insertBrand(name)` | `DataResult<Long>` | Returns new ID; wrap in `wrapInTransaction` |
| `updateBrand(id, name)` | `DataResult<Unit>` | |
| `deleteBrand(id)` | `DataResult<Unit>` | Repository layer does NOT check count — DAO constraint handles it |
| `getItemCountForBrand(id)` | `DataResult<Int>` | Used by management screen before showing delete |

Wrap all ops in the same `wrapInTransaction` / `DataResult` pattern as `ClothingRepository`. Provide via `@Singleton` in `DataModule`.

### 2f. Update `ClothingRepository`
- `createClothingItem` / `updateClothingItem` signatures gain `brandId: Long?` in place of `brand: String?`
- Remove any `brand: String?` write path

### 2g. Remove `LookupRepository.getDistinctBrands()`
This method and the corresponding `LookupDao` query become dead code once `BrandRepository` exists. Delete them.

---

## Phase 3 — Form Autocomplete

### 3a. `ClothingFormViewModel` changes
- Replace `_brand: MutableStateFlow<String>` with `_selectedBrandId: MutableStateFlow<Long?>` and `_brandQuery: MutableStateFlow<String>` (the text the user is typing)
- Inject `BrandRepository`; expose `allBrands: StateFlow<List<BrandEntity>>` from `BrandRepository.getAllBrands()`
- Add `fun onBrandQueryChange(text: String)` — updates `_brandQuery`
- Add `fun onBrandSelect(brand: BrandEntity)` — sets `_selectedBrandId`, clears the text query to the brand name
- Add `suspend fun onAddNewBrand(name: String): Long` — calls `brandRepository.insertBrand(name)`, then calls `onBrandSelect` with the returned entity
- On save: pass `_selectedBrandId.value` instead of brand string

### 3b. `BrandAutocompleteField` composable
New private composable in `ClothingFormScreen.kt`:

```kotlin
ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it }
) {
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChange(it); expanded = true },
        label = { Text("Brand") },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)
    )

    ExposedDropdownMenu(expanded = expanded && (filteredBrands.isNotEmpty() || showAddOption)) {
        filteredBrands.forEach { brand ->
            DropdownMenuItem(
                text = { Text(brand.name) },
                onClick = { onBrandSelect(brand); expanded = false }
            )
        }
        if (showAddOption) {  // query non-empty and no exact match
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Add \"$query\" as new brand") },
                leadingIcon = { Icon(Icons.Default.Add, null) },
                onClick = { onAddNewBrand(query); expanded = false }
            )
        }
    }
}
```

Filtering logic:
```kotlin
val filteredBrands = allBrands.filter { it.name.contains(query, ignoreCase = true) }
val showAddOption = query.isNotBlank() && filteredBrands.none { it.name.equals(query, ignoreCase = true) }
```

Replace the current `OutlinedTextField` brand field with `BrandAutocompleteField`.

---

## Phase 4 — Brand Management Screen

### 4a. New destination
In `WardrobeNavigation.kt`:
```kotlin
@Serializable
object BrandManagementDestination
```

### 4b. `BrandManagementViewModel`
- Inject `BrandRepository`
- `brands: StateFlow<List<BrandEntity>>` from `getAllBrands()`
- `uiState: StateFlow<BrandManagementUiState>` — wraps loading/error/confirmation dialog state
- `fun requestDelete(brand: BrandEntity)` — calls `getItemCountForBrand`, then:
  - If count > 0: sets state to show blocked dialog: "X items use this brand. Reassign them before deleting."
  - If count == 0: sets state to show confirm dialog
- `fun confirmDelete(id: Long)` — calls `deleteBrand`, dismisses dialog
- `fun saveBrand(id: Long?, name: String)` — insert if `id == null`, update otherwise

### 4c. `BrandManagementScreen`
- `LazyColumn` of brands, each row:
  - Brand name (primary text)
  - Item count (secondary text, e.g. "3 items")
  - Edit icon → toggle row to inline editable state (`OutlinedTextField` in place)
  - Delete icon → calls `requestDelete`
- Confirm/blocked `AlertDialog` driven by `uiState`
- FAB or top-bar action to add a new brand (inline text field + confirm)

Prefer inline edit (toggle the row to an editable state) over a separate edit screen to avoid extra navigation boilerplate.

### 4d. Access point
Add a settings/manage icon next to the brand field in `ClothingFormScreen` that navigates to `BrandManagementDestination`. Register the route in `ClosetNavGraph.kt` alongside the existing wardrobe routes.

---

## Phase 5 — Detail Screen Verification

`ClothingDetailScreen` reads `uiState.item.brand` (the string). After migration this is populated via the LEFT JOIN in `ClothingDao` — no structural change needed in the screen. Verify the DAO query and ViewModel still populate the field correctly after the join change.

---

## Execution Order

```
Phase 1  →  Phase 2a–2g  →  Phase 3a–3b  →  Phase 4a–4d  →  Phase 5
```

Phases 1 and 2 must be sequential (schema before entity). Phases 3 and 4 can be developed in parallel once Phase 2 is complete. Phase 5 is a verification step, not new work.

---

## Risk Notes

- **Migration backfill:** The `UPDATE clothing_items SET brand_id = ...` step depends on exact string matching. Case differences in existing data (e.g. "Nike" vs "nike") will not backfill correctly. Accept this — free-text data has no canonical casing guarantee. Users can reassign from the management screen.
- **`brand` column retention:** Leaving the old column avoids a costly table recreation. Mark it clearly as deprecated in the migration and never write to it in new code.
- **Unique constraint on `brands.name`:** `insertBrand` will fail if the user tries to add a brand that already exists case-sensitively. The `showAddOption` logic must filter with `ignoreCase = true` to avoid surfacing "Add as new brand" when a case-insensitive match already exists.
- **`ExposedDropdownMenuBox` with editable anchor:** Requires `MenuAnchorType.PrimaryEditable` (Material3 1.3+). Verify `libs.versions.toml` has a compatible `material3` version before starting Phase 3.
