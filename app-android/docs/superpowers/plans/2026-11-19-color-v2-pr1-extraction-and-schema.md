# Color v2 PR 1: measured colours, shared extraction and the v1→v2 schema

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every auto-tagged colour stores its measured shade and its share of the item. Extraction runs in shared code (Oklab, k-means) instead of AndroidX Palette, and colours are named by clothing-realistic reference shades.

**Architecture:**
- A pure `com.closet.core.data.color` package in `core/data` commonMain holds the colour math, the reference-shade naming and `ColorExtractor`.
- Android only decodes pixels (into sRGB) and calls it.
- Schema v2 adds `hex`/`share`/`source` to `clothing_item_colors`, `basic_term`/`wears_as_neutral` to `colors`, and a `color_shades` table, through the first real migration.
- The demo-backup tool gets a `color-check` command that measures agreement with the dataset's labelled colours.

**Tech Stack:** Kotlin Multiplatform (commonMain), Room KMP 2.x with the bundled SQLite driver, `MigrationTestHelper` (`room-testing`, already in `core/data` jvmTest), Android `BitmapFactory`, and the JVM `javax.imageio` in the dev tool.

**Spec:** `app-android/docs/superpowers/specs/2026-11-19-color-v2-design.md`. Delivery item 1 is this PR. The engine, chat and the weights schema bump are PR 2. The Nano overlay is PR 3.

## Global Constraints
- **Pure Kotlin in commonMain.** No `android.*` or `java.*` imports and no new libraries, including in `com.closet.core.data.color`. AndroidX Palette (`libs.androidx.palette.ktx`) is **removed** from `core/data` and `features/wardrobe`.
- **Use these exact constants** (verified by the controller; spec "Research findings" 4):
  - sRGB decode is `c ≤ 0.04045 ? c/12.92 : ((c+0.055)/1.055)^2.4`.
  - sRGB encode is `c ≤ 0.0031308 ? 12.92c : 1.055·c^(1/2.4) − 0.055`.
  - The linear sRGB→LMS and LMS′→Lab matrices are exactly as printed in the spec, with **signs as printed**.
- **Distance:** ΔE is Euclidean distance in Oklab (L in 0–1). **Clustering** assigns in Oklab, but each cluster's stored representative is the **mean of its members in linear sRGB**, encoded to sRGB. Never average gamma-encoded bytes.
- **Determinism:** the same pixels always give the same output. k-means++ uses `Random(COLOR_SEED = 0x5EED)` created per call.
- **Schema rules** (`core/data/src/commonMain/.../migrations/AGENTS.md`):
  - a manual `MIGRATION_1_2` with self-contained literal SQL;
  - `DROP INDEX IF EXISTS one_ootd_per_day` first;
  - bump `version = 2`, register it in `configureClosetDatabase()`, run `./gradlew :core:data:kspAndroidMain` and commit `2.json`;
  - add a `MigrationTest`, and update `FreshInstallSchemaTest` to v2;
  - never call `DatabaseSeeder` from the migration.
- **No engine behaviour change in this PR.** The 18 existing `colors` rows keep their `name`, `hex` and `color_family` exactly, because the engine reads `hex` and `color_family`. New rows get the legacy family given below. Spec Design "Seeds: re-pick the base hexes" **moves to PR 2** (ruling: re-picking changes engine hue scores, and that belongs with the engine re-tune).
- **Repositories** return `DataResult` and rethrow `CancellationException`.
- **Kotlin/Native test names:** backtick test names in commonTest avoid `()`, `.`, `:`, `;`, `/`, `<>`, `[]` and `\`.
- **Memory is tight.** Run module-scoped Gradle tasks one at a time, in the foreground. Never use `--rerun-tasks`, and never kill daemons.

## Review Focus
1. **A user's hand-curated colours get overwritten by background re-extraction.** After a user edits an item's colours, `BatchSegmentationWorker` must never replace them. (Task 4: a `ClothingRepository.replaceAutoColors` test.)
2. **A white item on a white backdrop.** Background removal must not erase the item. It falls back to the central box and still returns White. (Task 2 test.)
3. **Older data on upgrade.** A pre-v2 database, or a restored pre-v2 backup, keeps every item's colours after migration. Its rows become `source = 'user'` with null hex and share, so the worker never overwrites them. (Task 3 `MigrationTest` with realistic rows.)
4. **Seed drift.** A fresh v2 install and a migrated v1→v2 database must end with identical `colors` and `color_shades` contents. (Task 3 parity test.)
5. **A large photo on the add-item path must stay fast.** A 12 MP photo is decoded sampled (`maxDim = 512`) before extraction, and extraction of a 512×512 image completes in under 500 ms on the JVM. (Task 2 performance test; Task 4 uses `BitmapUtils.decodeSampledBitmap`.)

---

### Task 1: Colour math and reference-shade naming

**Files:**
- Create: `core/data/src/commonMain/kotlin/com/closet/core/data/color/Oklab.kt`
- Create: `core/data/src/commonMain/kotlin/com/closet/core/data/color/ColorNaming.kt`
- Test: `core/data/src/commonTest/kotlin/com/closet/core/data/color/OklabTest.kt` and `ColorNamingTest.kt`

**Interfaces (produced):**
- `data class Lab(val l: Double, val a: Double, val b: Double)`
- `object Oklab`:
  - `fun srgbToLinear(c: Double): Double`
  - `fun linearToSrgb(c: Double): Double`
  - `fun fromArgb(argb: Int): Lab`
  - `fun fromHex(hex: String): Lab`. It accepts `#RRGGBB` (case-insensitive) and throws `IllegalArgumentException` otherwise.
  - `fun linearRgb(argb: Int): DoubleArray`
  - `fun hexFromLinear(r: Double, g: Double, b: Double): String`. It clamps to [0, 1] and returns uppercase `#RRGGBB`.
  - `fun deltaE(x: Lab, y: Lab): Double`
  - `fun chroma(x: Lab): Double`
  - `fun hueDegrees(x: Lab): Double`. It returns a value in [0, 360).
- `data class ReferenceShade(val colorId: Long, val hex: String)`
- `class ColorNamer(shades: List<ReferenceShade>)` with `fun nearest(lab: Lab): Long?`. It returns the `colorId` of the closest shade by ΔE, or null when `shades` is empty. Ties go to the lower `colorId`.

- [ ] **Step 1: Write the failing tests.**
  - `OklabTest`:
    - `white maps to L 1`: `fromHex("#FFFFFF")` gives (1, 0, 0) within ±1e-4.
    - `red maps to the published vector`: `#FF0000` gives (0.6280, 0.2249, 0.1258) within ±1e-4, chroma 0.2577 ± 1e-4 and hue 29.2 ± 0.1.
    - `srgb round trip`: for every byte 0..255, `linearToSrgb(srgbToLinear(v / 255.0)) * 255` is within 0.5 of v.
    - `linear mean of black and white`: `hexFromLinear` of the averaged `linearRgb` of `0xFF000000` and `0xFFFFFFFF` is `#BCBCBC`.
    - `rejects malformed hex`: `#FFF`, `FF0000` and `#GG0000` throw.
  - `ColorNamingTest` builds a namer from **the Task 3 shade table**. Copy the table from the Task 3 section into a test fixture `SeedShadesFixture.kt` until Task 3 makes it the real seed list; Task 3 replaces the fixture with `DatabaseSeeder.SEEDED_COLORS`.
    - `held out colours get the expected names`: each held-out hex below names its expected colour. None of these hexes is in the shade table, so don't add them to it.
    - `empty namer returns null`.

    | Expected name | Held-out hexes |
    |---|---|
    | Yellow | `#FAF0B4`, `#F9E79F`, `#F3E5AB`, `#D4A017`, `#C9A227` |
    | Tan | `#C9A97E`, `#B5895A` |
    | Denim | `#5677A0`, `#3E5F8A` |
    | Charcoal | `#333B40`, `#404040` |
    | Navy | `#23304A` |
    | Grey | `#C8C8C8`, `#9A9A9A` |
    | Burgundy | `#722F37` |
    | Teal | `#367588` |
    | Olive | `#6B8E23` |
    | White | `#FAF9F6` |
    | Cream | `#FFF8E7` |
    | Beige | `#E1D3B8` |
    | Brown | `#4B3621` |
    | Purple | `#C3A6D9` |
    | Orange | `#F88379` |
    | Pink | `#FF69B4` |
    | Black | `#141414` |

- [ ] **Step 2:** Run `./gradlew :core:data:jvmTest --console=plain -q`. It should fail with unresolved references.
- [ ] **Step 3:** Implement `Oklab` and `ColorNamer` per the interfaces. `ColorNamer` precomputes each shade's `Lab` once.
- [ ] **Step 4:** Run `./gradlew :core:data:jvmTest --console=plain -q` and expect PASS. Then run `./gradlew :core:data:compileCommonMainKotlinMetadata --console=plain -q`.
- [ ] **Step 5:** Commit `feat(color): Oklab conversions and reference-shade naming in core/data commonMain (#81)`.

---

### Task 2: `ColorExtractor` (pixel selection, k-means, shares)

**Files:**
- Create: `core/data/src/commonMain/kotlin/com/closet/core/data/color/ColorExtractor.kt`
- Test: `core/data/src/commonTest/kotlin/com/closet/core/data/color/ColorExtractorTest.kt` (synthetic ARGB arrays built in the test)

**Interfaces:**
- **Consumes** Task 1's `Oklab`, `ColorNamer` and `ReferenceShade`.
- **Produces:**
  - `data class ExtractedColor(val colorId: Long, val hex: String, val share: Double)`
  - `class ColorExtractor(shades: List<ReferenceShade>)` with `fun extract(argb: IntArray, width: Int, height: Int): List<ExtractedColor>`. It returns at most 3 colours, sorted by share descending, and an empty list when nothing qualifies.

**Constants** (in the companion, `internal const`):

| Constant | Value |
|---|---|
| `MAX_SAMPLES` | 10_000 |
| `K` | 4 |
| `MAX_ITERATIONS` | 20 |
| `DELTA_E_MERGE` | 0.07 |
| `MIN_SHARE` | 0.15 |
| `MAX_COLORS` | 3 |
| `MIN_ITEM_PIXELS` | 50 |
| `ALPHA_ITEM` | 128 |
| `MASK_MIN_TRANSPARENT` | 0.02 |
| `BORDER_FRACTION` | 0.03 |
| `BORDER_UNIFORM` | 0.80 |
| `BG_DELTA_E` | 0.05 |
| `CENTER_FRACTION` | 0.60 |
| `COLOR_SEED` | 0x5EED |

`DELTA_E_MERGE` and `MIN_SHARE` are starting values that Task 5 calibrates.

**The algorithm** (this is what the tests don't fully determine):
1. **Item pixels.**
   - If at least `MASK_MIN_TRANSPARENT` of the pixels have alpha < `ALPHA_ITEM`, the alpha channel is the mask: the item pixels are those with alpha ≥ `ALPHA_ITEM`.
   - Otherwise:
     - Take the border ring (`BORDER_FRACTION` of width and height on each side) and its per-channel median `Lab`.
     - If at least `BORDER_UNIFORM` of the border pixels are within `BG_DELTA_E` of that median, the item pixels are all pixels farther than `BG_DELTA_E` from it.
     - If that leaves fewer than `MIN_ITEM_PIXELS`, or the border isn't uniform, the item pixels are the central `CENTER_FRACTION` × `CENTER_FRACTION` box.
   - Fewer than `MIN_ITEM_PIXELS` item pixels gives an empty list.
2. **Sample:** take at most `MAX_SAMPLES` item pixels by fixed stride.
3. **Cluster:** k-means++ seeding in `Lab`, with k = min(`K`, distinct samples). Assign by ΔE and update centroids as `Lab` means. Stop at `MAX_ITERATIONS` or when no assignment changes.
4. **Merge:** repeatedly merge the closest pair of clusters while their centroid ΔE < `DELTA_E_MERGE`. The merged centroid is the size-weighted mean.
5. **Represent each cluster:** the mean of its members' `linearRgb` gives `hex` via `hexFromLinear`, and `Lab` of that hex is used for naming. The share is the cluster's size over the sample count.
6. **Name** each cluster with `ColorNamer.nearest`. Clusters with the same `colorId` combine: shares add, and the hex is the share-weighted linear mean.
7. **Filter:** drop shares < `MIN_SHARE`, then keep the top `MAX_COLORS` by share. Shares are **not** renormalized.

- [ ] **Step 1: Write the failing tests.** Use `ColorExtractor(SeedShadesFixture.shades)`. Names are checked through the fixture's ids.
  - `solid image returns its colour`: 64×64 opaque `#B3202A` gives one colour, Red, with share ≥ 0.99. Here the border-equals-item case falls back to the centre box.
  - `masked two colour item gives shares`: 100×100 with the left 30% transparent; the opaque area is 70% `#1F2A44` and 30% `#F4F4F2`. Expect Navy share 0.70 ± 0.05 first and White 0.30 ± 0.05 second.
  - `product photo drops a uniform white background`: a white `#FFFFFF` 100×100 with a centred 40×40 `#E8742A` square gives only Orange.
  - `white item on white backdrop falls back to the centre`: an all-`#FAFAFA` image with a 60×60 `#F4F4F2` centre gives one colour, White.
  - `small accent below the share floor is dropped`: masked item, 92% `#2E50A8` and 8% `#FFD300`, gives only Royal Blue.
  - `noisy fabric stays one colour`: `#B3202A` with deterministic per-pixel ±6 channel noise gives one colour, Red.
  - `fully transparent image returns nothing`.
  - `same input gives the same output`: run the two-colour case twice and get equal lists.
  - `hex is uppercase and linear averaged`: the solid case's hex is `#B3202A` exactly. A masked checkerboard of `#000000`/`#FFFFFF` returns two colours (Black and White). The checkerboard proves no gamma-space averaging inside clusters.
  - `large image stays fast`: 512×512 random-seeded noise extracts in under 500 ms (measured with `TimeSource.Monotonic`, after one warm-up run).
- [ ] **Step 2:** Run `./gradlew :core:data:jvmTest --console=plain -q` and expect FAIL.
- [ ] **Step 3:** Implement per the algorithm above.
- [ ] **Step 4:** Run `./gradlew :core:data:jvmTest --console=plain -q`, then `./gradlew :core:data:compileCommonMainKotlinMetadata --console=plain -q`. Expect PASS.
- [ ] **Step 5:** Commit `feat(color): shared ColorExtractor — mask/background selection, Oklab k-means, linear-RGB representatives (#81)`.

---

### Task 3: Schema v2, seeds, shades and the junction API

**Files:**
- **Modify:** `core/data/src/commonMain/.../model/LookupEntities.kt` (`ColorEntity` gains `@ColumnInfo(name = "basic_term", defaultValue = "") val basicTerm: String = ""` and `@ColumnInfo(name = "wears_as_neutral", defaultValue = "0") val wearsAsNeutral: Boolean = false`) and add `ColorShadeEntity`:
  - table `color_shades`;
  - columns `id INTEGER PK autoGenerate`, `color_id INTEGER NOT NULL`, `hex TEXT NOT NULL`;
  - an FK to `colors(id)` with `ON DELETE CASCADE`;
  - `Index("color_id")`.
- **Modify:** `model/JunctionEntities.kt`. `ClothingItemColorEntity` gains `@ColumnInfo(name = "hex") val hex: String? = null`, `@ColumnInfo(name = "share") val share: Double? = null` and `@ColumnInfo(name = "source", defaultValue = "'user'") val source: String = ColorSource.USER`.
- **Create:** `model/ColorSource.kt`: `object ColorSource { const val AUTO = "auto"; const val USER = "user"; const val NANO = "nano" }`.
- **Create:** `model/TaggedColor.kt`: `data class TaggedColor(val colorId: Long, val hex: String? = null, val share: Double? = null, val source: String = ColorSource.USER)`.
- **Create:** `migrations/Migration1To2.kt` (`MIGRATION_1_2`).
- **Modify:** `ClothingDatabase.kt` (version 2, the new entity, `.addMigrations(MIGRATION_1_2)`), `DatabaseSeeder.kt`, `dao/ClothingDao.kt`, `dao/LookupDao.kt`, `repository/ClothingRepository.kt` and `repository/LookupRepository.kt`.
- **Test:**
  - `core/data/src/jvmTest/.../MigrationTest.kt` (new);
  - `FreshInstallSchemaTest.kt` (v2);
  - `repository/ClothingRepositoryColorsTest.kt` (new);
  - an update to `features/recommendations/.../PairScorerTest.kt`, which uses `SEEDED_COLORS`;
  - replace Task 1's `SeedShadesFixture` with the seeder.

**Seed list.** The 18 existing rows keep name, hex and family. 4 new rows are added. Shades are listed per name, and the base hex is **not** automatically a shade.

| Name | `hex` (legacy, unchanged) | `color_family` | `basic_term` | `wears_as_neutral` | Reference shades (`color_shades.hex`) |
|---|---|---|---|---|---|
| Black | `#000000` | Neutral | black | 1 | `#000000` `#1A1A1A` `#2B2B2B` |
| White | `#FFFFFF` | Neutral | white | 1 | `#FFFFFF` `#F4F4F2` `#EDEDE8` |
| Grey | `#808080` | Neutral | gray | 1 | `#808080` `#A0A0A0` `#6E6E6E` `#BEBEBE` |
| Beige | `#F5F5DC` | Neutral | brown | 1 | `#F5F5DC` `#E8DCC4` `#D9C8A9` |
| Navy | `#000080` | Neutral | blue | 0 | `#000080` `#1F2A44` `#202A5A` `#2C3E63` |
| Red | `#FF0000` | Warm | red | 0 | `#FF0000` `#B3202A` `#D62B2B` `#C41E3A` |
| Royal Blue | `#4169E1` | Cool | blue | 0 | `#4169E1` `#2E50A8` `#3A5FCD` |
| Sky Blue | `#87CEEB` | Cool | blue | 0 | `#87CEEB` `#8DB8DE` `#A7C7E7` |
| Forest Green | `#228B22` | Cool | green | 0 | `#228B22` `#2E5E3A` `#355E3B` `#4F7942` |
| Olive | `#808000` | Earth | green | 0 | `#808000` `#6B6B3A` `#708238` |
| Mint | `#98FF98` | Cool | green | 0 | `#98FF98` `#A8DCC0` `#B2E0C8` |
| Burgundy | `#800020` | Warm | red | 0 | `#800020` `#6D1A2B` `#7B2D3A` |
| Pink | `#FFC0CB` | Warm | pink | 0 | `#FFC0CB` `#F2A7B8` `#E75480` `#F4C2C2` |
| Orange | `#FFA500` | Warm | orange | 0 | `#FFA500` `#E8742A` `#F28C28` `#FF7F50` |
| Yellow | `#FFFF00` | Warm | yellow | 0 | `#FFFF00` `#F2CF3A` `#FFFACD` `#FFF59D` `#E1AD01` `#FFD300` `#F6E7A5` |
| Purple | `#800080` | Cool | purple | 0 | `#800080` `#6A3D8F` `#9B59B6` `#B57EDC` `#CDB4E0` `#E6E6FA` |
| Brown | `#A52A2A` | Earth | brown | 1 | `#6B4423` `#5C4033` `#7B5B3A` `#4E342E` |
| Tan | `#D2B48C` | Earth | brown | 1 | `#D2B48C` `#C8A47E` `#C3B091` `#C19A6B` |
| **Charcoal** (new) | `#36454F` | Neutral | gray | 1 | `#36454F` `#3C3C3C` `#4A4A4A` |
| **Cream** (new) | `#F3EBD3` | Neutral | white | 1 | `#FFFDD0` `#F3EBD3` `#FAF3E0` `#FFF5DC` |
| **Denim** (new) | `#4F6D8F` | Neutral | blue | 1 | `#3B5A80` `#4F6D8F` `#5B7BA6` `#2F4A6B` |
| **Teal** (new) | `#1F7A7A` | Cool | blue | 0 | `#008080` `#1F7A7A` `#2A9D8F` |

**Rulings behind the table** (for the owner to check):
- Brown's legacy brick-red hex is excluded from its shades.
- Tan is `wears_as_neutral` because it lies between the two sourced neutrals, beige and brown; khaki and camel are its shades. This departs from the spec's point 12, "camel not neutral".
- Denim's family is Neutral, so the engine treats it as a neutral, matching the sources.
- Teal's basic term is blue.

**Interfaces (produced):**
- `DatabaseSeeder.SEEDED_COLORS: List<SeedColor>`, with `data class SeedColor(val name: String, val hex: String, val family: String, val basicTerm: String, val wearsAsNeutral: Boolean, val shades: List<String>)`.
- `LookupDao.getAllColorShades(): List<ColorShadeEntity>` (suspend).
- `LookupRepository.getReferenceShades(): DataResult<List<ReferenceShade>>`.
- `ClothingDao`:
  - `updateItemColors(itemId: Long, colors: List<TaggedColor>)`, a `@Transaction` delete-then-insert. It replaces the `colorIds` version, and every caller is updated.
  - `suspend fun getItemColorRows(itemId: Long): List<ClothingItemColorEntity>`.
- `ClothingRepository`:
  - `insertItemWithAttributes` and `updateItemWithAttributes` take `colors: List<TaggedColor>`, replacing `List<ColorEntity>`. `insertItem`, `updateItem` and `updateItemColors` take `List<TaggedColor>` too.
  - `getItemColorRows(itemId): DataResult<List<ClothingItemColorEntity>>`.
  - `replaceAutoColors(itemId: Long, extracted: List<ExtractedColor>): DataResult<Boolean>`. If any existing row has `source = user`, it writes nothing and returns `false`. Otherwise it replaces all rows with `source = auto` rows and returns `true`.

**`MIGRATION_1_2`, in order:**
1. `DROP INDEX IF EXISTS one_ootd_per_day`.
2. `ALTER TABLE clothing_item_colors ADD COLUMN hex TEXT`, then `ADD COLUMN share REAL`, then `ADD COLUMN source TEXT NOT NULL DEFAULT 'user'`.
3. `ALTER TABLE colors ADD COLUMN basic_term TEXT NOT NULL DEFAULT ''` and `ADD COLUMN wears_as_neutral INTEGER NOT NULL DEFAULT 0`.
4. Create `color_shades` and its index. Copy the exact `CREATE` statements Room generates in `2.json`.
5. `UPDATE colors SET basic_term = …, wears_as_neutral = … WHERE name = …` for the 18 names.
6. Insert the 4 new colours with `INSERT INTO colors (name, hex, color_family, basic_term, wears_as_neutral) SELECT … WHERE NOT EXISTS (SELECT 1 FROM colors WHERE name = …)`.
7. Insert every shade with `INSERT INTO color_shades (color_id, hex) SELECT id, '#…' FROM colors WHERE name = '…'`.

All values are literal SQL from the table above.

- [ ] **Step 1: Write the failing tests.**
  - **`MigrationTest`:**
    - `v1 to v2 keeps item colours as user rows`: `helper.createDatabase(1)`, drop the partial index, and insert an item with Navy and White colour rows plus an outfit log. Then `runMigrationsAndValidate(2, listOf(MIGRATION_1_2))`. Both rows still exist with `source = 'user'`, `hex IS NULL` and `share IS NULL`. Navy has `basic_term = 'blue'` and `wears_as_neutral = 0`. Charcoal, Cream, Denim and Teal exist. `color_shades` has 83 rows. Every colour has at least one shade.
    - `migrated and fresh databases have identical colours`: list the (name, hex, family, basic_term, wears_as_neutral) and (name, shade hex) rows ordered by name and hex, from a migrated v1→v2 database and from a fresh `getDatabaseBuilder(file)` database. The lists must be equal.
  - **`FreshInstallSchemaTest`:** reads `2.json` and expects version 2.
  - **`ClothingRepositoryColorsTest`:**
    - `tagged colours round trip`: saved TaggedColors (auto with a hex and share, user with nulls) read back from `getItemColorRows` unchanged.
    - `replaceAutoColors skips user curated items`: an item with one user row isn't changed, and the call returns false.
    - `replaceAutoColors replaces auto rows`: an item with only auto rows ends with exactly the extracted rows as `auto`, and the call returns true.
    - `getReferenceShades lists every seeded shade`: 83 shades, each with a seeded `colorId`.
- [ ] **Step 2:** Run `./gradlew :core:data:jvmTest --console=plain -q` and expect FAIL.
- [ ] **Step 3: Implement** the entities, the seeder (SEEDED_COLORS as `SeedColor`; seed the shades after the colours), the migration, the DAO and repository changes and the `ColorNamingTest` fixture swap. Update every compile-breaking caller of the old `List<ColorEntity>` and `colorIds` signatures, using `TaggedColor(colorId)` with the default `user` source. That includes `PairScorerTest`'s `SEEDED_COLORS` destructuring, `WardrobeToolsTest`, the demo-backup tool's writer and the wardrobe form/worker call sites (which get a temporary user-source mapping that Task 4 replaces).
- [ ] **Step 4:** Run `./gradlew :core:data:kspAndroidMain --console=plain -q` and commit the generated `core/data/schemas/com.closet.core.data.ClothingDatabase/2.json`. Then run, in turn:
  - `./gradlew :core:data:jvmTest --console=plain -q`
  - `./gradlew :features:recommendations:jvmTest --console=plain -q`
  - `./gradlew :features:wardrobe:jvmTest --console=plain -q`
  - `./gradlew :tools:demo-backup:test --console=plain -q`

  All should PASS. In `PairScorerTest`'s seeded same-family test, update only the expected pair count for the 4 new rows. Its "no pair below 1.0" assertion must still hold.
- [ ] **Step 5:** Commit `feat(data): schema v2 — measured colour rows, basic terms, neutral flag and reference shades; first real migration (#81)`.

---

### Task 4: Android wiring, form semantics and removing Palette

**Files:**
- **Create:** `core/data/src/androidMain/kotlin/com/closet/core/data/util/BitmapPixels.kt`: `fun Bitmap.toArgbPixels(): IntArray` (via `getPixels`).
- **Modify:**
  - `BitmapUtils.decodeSampledBitmap`: both decode passes set `inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)`.
  - `features/wardrobe/src/commonMain/.../photo/ItemPhotoProcessor.kt`: `extractColors(relativePath: String, shades: List<ReferenceShade>): List<ExtractedColor>`.
  - `features/wardrobe/src/androidMain/.../photo/AndroidItemPhotoProcessor.kt`: decode with `decodeSampledBitmap(path, maxDim = 512)`, then `ColorExtractor(shades).extract(...)`.
  - `features/wardrobe/src/androidMain/.../BatchSegmentationWorker.kt`: take pixels from the masked bitmap, where alpha is the mask, then call `clothingRepository.replaceAutoColors`. The worker loads the shades once per run with `lookupDao.getAllColorShades()`.
  - `ClothingFormViewModel.kt`.
  - `FakeItemPhotoProcessor.kt`.
  - `core/data/build.gradle.kts` and `features/wardrobe/build.gradle.kts`: remove `libs.androidx.palette.ktx`. Also remove the catalog entry and the `androidxPalette` version.
- **Delete:** `core/data/src/androidMain/.../util/PaletteUtil.kt` and `ColorMatcher.kt`. Grep afterwards to confirm no references remain.
- **Test:** `features/wardrobe/src/jvmTest/.../ClothingFormViewModelTest.kt`.

**Form semantics** (decided here; the spec's "should a kept auto colour keep its hex" is answered as yes):
- **State:** the ViewModel keeps `measured: Map<Long, TaggedColor>`. It's filled from the latest extraction (`source = auto`, with a hex and share). On edit, it's loaded from `getItemColorRows`, keeping each row's source, hex and share. It also keeps `colorsEditedByUser: Boolean`, which turns true on any `onColorToggle` and resets to false when a new extraction replaces the selection.
- **Saving:** each selected colour becomes `measured[id]` when it's present, otherwise `TaggedColor(id, source = USER)`.
  - If `colorsEditedByUser` is true, every saved row's source becomes `USER`. Its measured hex and share stay.
  - Editing an item without touching colours re-saves the existing rows exactly.
- **Loading shades:** `extractColors` gets its shades from `lookupRepository.getReferenceShades()`, loaded once and cached in the ViewModel. On `DataResult.Error`, extraction is skipped. The current "leave selection if none match" behaviour stays.

- [ ] **Step 1: Write the failing tests** in `ClothingFormViewModelTest`, using the fake processor returning `ExtractedColor` lists:
  - `save after extraction stores measured auto rows`.
  - `toggling after extraction marks rows user but keeps measurements`: the added colour has a null hex, and the kept extracted colour keeps its hex and share with `source = user`.
  - `editing without touching colours preserves rows`: auto rows from the database stay auto with the same hex and share.
  - `new extraction resets the user edited flag`.
- [ ] **Step 2:** Run `./gradlew :features:wardrobe:jvmTest --console=plain -q` and expect FAIL.
- [ ] **Step 3:** Implement the changes listed above.
- [ ] **Step 4: Verify.** Run each of these, one at a time:
  - `./gradlew :features:wardrobe:jvmTest :features:wardrobe:compileCommonMainKotlinMetadata --console=plain -q`
  - `./gradlew :core:data:jvmTest --console=plain -q`
  - `./gradlew assembleFullDebug --console=plain -q`
  - `./gradlew assembleFossDebug --console=plain -q`
  - `./gradlew :features:wardrobe:lint :core:data:lint --console=plain -q`

  All should PASS. `grep -rn "Palette\|ColorMatcher" --include=*.kt core features` should return nothing outside history comments.
- [ ] **Step 5:** Commit `feat(wardrobe): extraction via shared ColorExtractor; measured rows on save; user edits protected; drop Palette (#81)`.

---

### Task 5: Accuracy check, calibration, docs and the PR checkpoint

**Files:**
- **Create:** `tools/demo-backup/src/main/kotlin/com/closet/tools/demobackup/ColorCheck.kt`. It wires a `color-check <datasetDir> [--limit N] [--seed S]` command into `DemoBackup.kt`'s argument handling.
- **Create:** `tools/demo-backup/src/main/kotlin/com/closet/tools/demobackup/LegacyHslMatcher.kt`. This is a baseline-only copy of the deleted HSL formula `sqrt(2ΔH² + ΔS² + ΔL²)` against the legacy base hexes, labelled as such in its KDoc.
- **Modify:** `Mapping.kt`'s colour table, with these new targets:
  - Charcoal → Charcoal;
  - Cream → Cream;
  - Off White → Cream;
  - Teal and Turquoise Blue → Teal;
  - Khaki stays Tan.
- **Test:** `tools/demo-backup/src/test/.../ColorCheckTest.kt`, plus `MappingTest` updates.
- **Docs:** the spec's Delivery 1 gets "done in #…"; a `CHANGELOG.md` `### Changed` entry with exactly this copy: "**Better automatic colour tags**: photos are analysed by how colours really look, with the background ignored, and each tag remembers its real shade and how much of the item it covers. Colours you set yourself are never overwritten."; the `CLAUDE.md` Image handling / `core/data` notes mention `com.closet.core.data.color` and that Palette is gone.

**`color-check` behaviour:**
- Sample `--limit` items (default 300, using `Random(seed)`, default seed 42) whose `Mapping.color(baseColour)` isn't null.
- Decode `images/<id>.jpg` with `ImageIO.read` and run `ColorExtractor` (no mask; the photos have white backgrounds).
- Print:
  - **exact agreement:** the top colour name equals the mapped name;
  - **basic-term agreement:** the same `basic_term`;
  - **empty rate;**
  - **the same two agreement metrics for the baseline,** which names each extracted cluster's hex with `LegacyHslMatcher` instead of `ColorNamer`. The baseline measures the matcher only, since Palette's swatch selection can't run on the JVM.
  - a confusion list of the top 10 (mapped → got) pairs.

- [ ] **Step 1:** Write `ColorCheckTest`. On a temporary dataset of 3 generated JPEGs (red, navy and pale yellow squares on white, with matching `styles.csv` rows), the report shows 3 checked, 3 exact matches and a 0 empty rate.
- [ ] **Step 2:** Run `./gradlew :tools:demo-backup:test --console=plain -q` and expect FAIL. Then implement, and expect PASS.
- [ ] **Step 3: Calibration (controller with the owner).**
  - Run `./gradlew :tools:demo-backup:run --args="color-check <owner's dataset dir> --limit 300"`, since the owner supplies the path. Record the new metrics and the baseline.
  - If exact agreement improves with `DELTA_E_MERGE` ∈ {0.05, 0.07, 0.09} or `MIN_SHARE` ∈ {0.12, 0.15, 0.20}, pick the best pair, update the constants and re-run Task 2's tests.
  - Record the table in the PR.
  - **Gate:** the new exact agreement is strictly better than the baseline's.
- [ ] **Step 4:** Run the full verification in chunks, one at a time:
  - `./gradlew test --console=plain -q`
  - `./gradlew :core:data:testAndroidHostTest :core:data:compileCommonMainKotlinMetadata :core:ui:compileCommonMainKotlinMetadata --console=plain -q`
  - `./gradlew lint --console=plain -q`
  - `./gradlew assembleFullDebug assembleFossDebug --console=plain -q`
  - `./gradlew iosCompileCheck -Pkotlin.native.enableKlibsCrossCompilation=true --console=plain -q`

  The CI `clock-reads` check must stay clean.
- [ ] **Step 5:** Commit `feat(tools): color-check accuracy report; calibrated extraction constants; docs (#81)`.
- [ ] **Step 6: PR checkpoint (controller).**
  - Push the branch and open the PR ("part of #81"; it doesn't close it). Include the calibration table.
  - Device smoke on the test device (full flavor):
    - The fresh install path: install over the existing build, so the migration runs on its real database. Existing items keep their colours.
    - Add an item from a photo and check its tags are sensible.
    - Background removal re-tags an untouched item, but not one whose colours were edited.
    - A Display-P3 camera photo tags sensibly.
    - No crash.
    - Nothing extra saved beyond the agreed test item.
  - Then wait for the owner's merge.
