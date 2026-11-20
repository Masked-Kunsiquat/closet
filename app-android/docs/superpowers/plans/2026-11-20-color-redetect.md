# Re-detect colors + colours-curated marker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users can re-detect an item's colours, one at a time with a preview or in bulk. A new `colors_curated` flag means automatic re-tagging never overrides a hand-made colour choice, including a cleared one.

**Architecture:**
- **Schema v3** adds `clothing_items.colors_curated`, backfilled from `user` rows.
- **Repository:** colour writes keep the flag correct.
- **`ColorRedetector`** (features/wardrobe commonMain) holds the per-item logic. The detail screen's preview sheet and a new WorkManager job, `ColorRedetectWorker`, both use it.
- **Settings** gains a bulk action that mirrors batch background removal.
- **An on-device test** proves Display P3 → sRGB decoding.

**Tech Stack:** Room KMP with `MigrationTestHelper`, Koin, WorkManager, Compose Multiplatform, Compose resources.

**Spec:** `app-android/docs/superpowers/specs/2026-11-20-color-redetect-design.md`

## Global Constraints
- **Schema rules** (`migrations/AGENTS.md`):
  - a manual `MIGRATION_2_3` with literal SQL;
  - `DROP INDEX IF EXISTS one_ootd_per_day` first;
  - bump to version 3 and register it in `configureClosetDatabase()`;
  - run `./gradlew :core:data:kspAndroidMain` and commit `3.json`;
  - extend `MigrationTest`, and update `FreshInstallSchemaTest` and `ExistingInstallUpgradeTest` to v3.
- **Never auto-overwrite user-set colours.** `replaceAutoColors` writes nothing when `colors_curated = 1`, when any row is `user`, or when the extraction is empty.
- **Destructive actions need confirmation:** the bulk "all items" path needs an `AlertDialog` that states the count (CLAUDE.md). The per-item preview sheet counts as confirmation.
- **Code rules:**
  - commonMain stays pure;
  - repositories use `DataResult` and rethrow `CancellationException`;
  - user-facing strings are Compose resources in the owning feature (`features/wardrobe`, `features/settings`) with positional placeholders.
- **Flavors:** bulk re-detect works on **both** flavors, because extraction needs no GMS. JVM and iOS bind no-op schedulers.
- **Memory is tight.** Run Gradle module-scoped, one invocation at a time, in the foreground. Never use `--rerun-tasks`, and never kill daemons.

## Review Focus
1. **Flag drift through the form's entity rewrite.** `updateItemWithAttributes` writes the whole entity, so a form save that forgets `colorsCurated` silently resets it. Every save path must set it per the spec table. (Task 2 tests every row.)
2. **A bulk run on curated items.** "Only items you haven't edited" must skip every curated item. On the migrated demo wardrobe, that means it changes nothing. (Task 4: eligible-count and worker-logic tests.)
3. **Re-detect on an item with no image, or an unreadable one.** It must say "Couldn't detect colours" and change nothing, with no crash and no empty write. (Task 3.)
4. **Re-detect while the item is open in the edit form.** Apply writes through the repository. A later form save of untouched colours must not resurrect the old rows. The form reloads on open, so the realistic window is a stale form already open. Note it in the PR if it isn't fully closed; don't add locking. (Task 3: document only.)
5. **Display P3 photos:** they decode to sRGB values. (Task 5, device test.)

---

### Task 1: Schema v3 — `colors_curated` column, migration and backfill
**Files:**
- **Modify:** `core/data/.../model/ClothingItemEntity.kt`. Add `@ColumnInfo(name = "colors_curated", defaultValue = "0") val colorsCurated: Boolean = false`.
- **Create:** `migrations/Migration2To3.kt` (`MIGRATION_2_3`).
- **Modify:** `ClothingDatabase.kt` (set version 3, register the migration) and `tools/demo-backup` (`DemoBackup.kt` creates items with `colorsCurated = true`, because it writes `user` rows).
- **Tests:** `MigrationTest`, `FreshInstallSchemaTest` and `ExistingInstallUpgradeTest` (device test, version expectation only).

**`MIGRATION_2_3`:**
1. `DROP INDEX IF EXISTS one_ootd_per_day`
2. `ALTER TABLE clothing_items ADD COLUMN colors_curated INTEGER NOT NULL DEFAULT 0`
3. `UPDATE clothing_items SET colors_curated = 1 WHERE id IN (SELECT DISTINCT clothing_item_id FROM clothing_item_colors WHERE source = 'user')`

- [ ] **Step 1: Tests.**
  - `v2 to v3 backfills colors_curated from user rows`: on the v2 schema, create 3 items:
    - (a) one `user` row → expect `1`;
    - (b) only `auto` rows → expect `0`;
    - (c) no rows → expect `0`.
  - Also `runMigrationsAndValidate(3, listOf(MIGRATION_1_2, MIGRATION_2_3))` from v1 must pass. The v1 items become `user`, then curated (`1`).
  - `FreshInstallSchemaTest` reads `3.json` and expects version 3.
- [ ] **Step 2:** RED: `./gradlew :core:data:jvmTest --console=plain -q`.
- [ ] **Step 3:** Implement. Then run `./gradlew :core:data:kspAndroidMain --console=plain -q` and commit `core/data/schemas/com.closet.core.data.ClothingDatabase/3.json`.
- [ ] **Step 4:** GREEN: `:core:data:jvmTest`, then `:tools:demo-backup:test`, then `assembleFullDebug`.
- [ ] **Step 5:** Commit `feat(data): schema v3 — clothing_items.colors_curated, backfilled from user colour rows (#128)`.

### Task 2: The flag in every colour write
**Files:**
- `core/data/.../repository/ClothingRepository.kt`, `dao/ClothingDao.kt`
- `features/wardrobe/.../ClothingFormViewModel.kt`
- tests: `ClothingRepositoryColorsTest`, `ClothingFormViewModelTest`

**Interfaces (produced):**
- `ClothingDao.setColorsCurated(itemId: Long, curated: Boolean)`.
- `ClothingRepository.applyRedetectedColors(itemId: Long, extracted: List<ExtractedColor>): DataResult<Boolean>`.
  - **Empty extraction:** returns `false` and writes nothing.
  - **Otherwise,** in one transaction: replace every row with `auto` rows (hex and share), set `colors_curated = 0`, and return `true`.
  - It ignores the curated flag and `user` rows on purpose, because the user asked for it.

**Changes:**
- **`updateColorsAsUser`:** in the changed case, it also sets `colors_curated = 1` in the same transaction.
- **`replaceAutoColors`:** it also returns `false` without writing when `colors_curated = 1`. This adds to the existing user-row and empty checks.
- **The form** sets `ClothingItemEntity.colorsCurated` explicitly on save:
  - `true` if `colorsEditedByUser`;
  - otherwise `false` if a new photo's extraction set the selection this session (track `colorsFromExtraction`: true when `extractColors` applies, false on any toggle);
  - otherwise the loaded entity's value (`originalEntity?.colorsCurated ?: false`).
- **Inserts** use the same rule, with `false` as the base.

- [ ] **Step 1: Tests**, one per row of the spec table:
  - **Form, edited:** a toggle, then save gives `colorsCurated = true`. A clear-all, then save gives `true` with zero rows.
  - **Form, new photo:** on a curated item, pick a new photo with a successful extraction, don't touch it, save → `false`.
  - **Form, untouched:** editing a curated item without touching colours, save → `true` (unchanged). The same for an uncurated item → `false`.
  - **Repository:**
    - `updateColorsAsUser` (changed) sets the flag. Unchanged writes nothing.
    - `replaceAutoColors` on a flagged item with zero rows returns `false` and still has zero rows. This is the clear-all case from #127's review.
    - `applyRedetectedColors` replaces `user` rows with `auto` rows and clears the flag. With an empty extraction it returns `false` and writes nothing.
- [ ] **Step 2:** RED: `:core:data:jvmTest`, then `:features:wardrobe:jvmTest`.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** GREEN for the same tasks, then `:features:wardrobe:compileCommonMainKotlinMetadata`.
- [ ] **Step 5:** Commit `feat(wardrobe): colors_curated set by every colour write; replaceAutoColors respects it (#128)`.

### Task 3: `ColorRedetector` and per-item re-detect with preview
**Files:**
- **Create:** `features/wardrobe/src/commonMain/.../ColorRedetector.kt`.
- **Modify:** `ClothingDetailViewModel.kt`, `ClothingDetailScreen.kt` (Colors-section action and preview sheet), and the wardrobe `composeResources` strings.
- **Koin:** register `ColorRedetector` in `wardrobeModule` (`singleOf`), and update the wardrobe Koin `verify()` test if needed.
- **Tests:** `ColorRedetectorTest` (jvmTest, with `FakeItemPhotoProcessor` and a mocked repository) and `ClothingDetailViewModelTest`.

**Interfaces:**
- `class ColorRedetector(photoProcessor: ItemPhotoProcessor, lookupRepository: LookupRepository, clothingRepository: ClothingRepository)`:
  - `suspend fun propose(imagePath: String?): List<ExtractedColor>`. It loads the shades once per instance, returns `emptyList()` when there is no image or the shades fail to load, and otherwise returns `photoProcessor.extractColors(path, shades)`.
  - `suspend fun apply(itemId: Long, proposed: List<ExtractedColor>): DataResult<Boolean>`. It delegates to `applyRedetectedColors`.
- **`ClothingDetailViewModel`:**
  - `redetectState: StateFlow<RedetectState>`, where `RedetectState` is `Idle | Measuring | Preview(current: List<ColorEntity>, proposed: List<Pair<ColorEntity, Double>>) | Failed`.
  - `onRedetectColors()`, `onApplyRedetect()`, `onCancelRedetect()`.
  - `Failed` shows a snackbar ("Couldn't detect colours") and goes back to `Idle`.

**UI:**
- The Colors-section header gets a "Re-detect colors" action (a text button or icon with that label), shown when the item has an image.
- The preview is a bottom sheet with two lists, Current and Proposed. Each colour shows its chip and swatch, and proposed ones also show their share as a percentage. **Apply** and **Cancel** buttons.
- Strings: `wardrobe_redetect_colors`, `wardrobe_redetect_current`, `wardrobe_redetect_proposed`, `wardrobe_redetect_apply`, `wardrobe_redetect_none`.

- [ ] **Step 1: Tests.**
  - `ColorRedetectorTest`: no image gives empty; a shades error gives empty and doesn't call extraction; shades load once; `apply` delegates.
  - `ClothingDetailViewModelTest`:
    - re-detect → Preview with the shares;
    - Apply → `applyRedetectedColors` is called with the proposed colours;
    - Cancel → no write;
    - empty → Failed, with no write.
- [ ] **Step 2:** RED: `:features:wardrobe:jvmTest`.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** GREEN: `:features:wardrobe:jvmTest`, then `:features:wardrobe:compileCommonMainKotlinMetadata`, then `assembleFullDebug`.
- [ ] **Step 5:** Commit `feat(wardrobe): re-detect an item's colours with a preview (#128)`.

### Task 4: Bulk re-detect — scheduler, worker, Settings
**Files:**
- **Create:**
  - `core/data/.../worker/ColorRedetectWork.kt`: `object ColorRedetectWork { NAME = "color_redetect"; KEY_ONLY_UNCURATED; KEY_DONE/KEY_TOTAL/KEY_FAILED/KEY_UNCHANGED }` and `interface ColorRedetectScheduler { val isSupported: Boolean; fun schedule(onlyUncurated: Boolean); val workStatus: Flow<WorkStatus?> }`;
  - `NoOpColorRedetectScheduler` in `NoOpSchedulers.kt`;
  - `features/wardrobe/src/androidMain/.../ColorRedetectWorker.kt`.
- **Modify:**
  - `ClothingDao`: `getColorRedetectEligibleCount(onlyUncurated: Boolean): Flow<Int>` and `getColorRedetectItems(onlyUncurated: Boolean): List<…>` for items with `image_path IS NOT NULL AND (:onlyUncurated = 0 OR colors_curated = 0)`.
  - Koin: the jvm/ios data modules bind the no-op scheduler. `wardrobeModule` binds the Android scheduler (WorkManager unique work, `REPLACE` policy, input data) and `workerOf(::ColorRedetectWorker)`.
  - `features/settings`: `SettingsViewModel` (eligible counts for both modes, status, result handled) and `AiSettingsScreen` (or wherever batch background removal lives): a button, a confirmation `AlertDialog` with the checkbox (default on) and the count, progress, and a result snackbar. Add settings strings.
- **Worker:** like `BatchSegmentationWorker`, it runs in the foreground with a notification and progress. Per item it calls `ColorRedetector.propose` then `apply`. An empty proposal counts as unchanged; an exception counts as failed. After a successful apply it revectorizes the item, as the batch worker does.
- **Tests:**
  - DAO counts (`:core:data:jvmTest`): curated, uncurated and no-image items, in both modes.
  - `SettingsViewModelTest`: counts per mode, `schedule(onlyUncurated)` is called with the dialog's choice, result handling.
  - Koin verify tests for core/data jvm, settings and wardrobe.
  - The worker loop itself is Android-only. Its per-item logic is covered through `ColorRedetector` (Task 3).

- [ ] **Step 1:** Write the tests above.
- [ ] **Step 2:** RED, running `:core:data:jvmTest`, then `:features:settings:jvmTest`, then `:features:wardrobe:jvmTest`.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** GREEN on the same three, then `assembleFullDebug`, then `assembleFossDebug`.
- [ ] **Step 5:** Commit `feat(settings): bulk re-detect colours (only-unedited by default), both flavors (#128)`.

### Task 5: Display P3 device test, docs and PR checkpoint
**Files:**
- **Create:** `core/data/src/androidDeviceTest/kotlin/com/closet/core/data/BitmapDecodeColorSpaceTest.kt`.
- **Docs:**
  - a `CHANGELOG.md` `### Added` entry with this exact copy: "**Re-detect colors**: re-measure an item's colours from its photo with a preview, or re-detect your whole wardrobe from Settings. Colours you set by hand, including clearing them, are never changed automatically.";
  - a note in the spec, and in the color v2 spec's head notes, that #128 is done.

**P3 test:**
- Create a `Bitmap.createBitmap(32, 32, ARGB_8888, true, ColorSpace.get(DISPLAY_P3))` filled with P3 colour (0.85, 0.30, 0.20), and write it as PNG to the cache dir.
- Decode it with `BitmapUtils.decodeSampledBitmap(path, 512)`.
- Assert `bitmap.colorSpace == ColorSpace.get(SRGB)`.
- Assert the centre pixel equals `ColorSpace.connect(DISPLAY_P3, SRGB).transform(0.85f, 0.30f, 0.20f)` × 255, ±2 per channel.

- [ ] **Step 1:** Write the test.
- [ ] **Step 2 (controller, test device only):** `ANDROID_SERIAL=<test device> ./gradlew :core:data:connectedAndroidDeviceTest --console=plain -q`. Expect PASS. On FAIL, the decode isn't converting; stop and report.
- [ ] **Step 3:** Run the full verification in chunks:
  - `test`
  - `:core:data:testAndroidHostTest` plus the metadata compiles
  - `lint`
  - `assembleFullDebug` and `assembleFossDebug`
  - `iosCompileCheck`
  - the clock-reads check
- [ ] **Step 4:** Commit `test(data): Display P3 photos decode to sRGB (device test); docs (#128)`.
- [ ] **Step 5: PR checkpoint (controller).**
  - **PR body:** "Closes #128" (it does close it), plus "Part of #81". Don't write any closing verb next to #81.
  - **Device smoke** on the test device, installed over the existing build so the v2→v3 migration runs:
    - the demo items stay intact;
    - re-detect one demo item, check the preview and Apply;
    - run bulk "only unedited", which re-tags 0 items, since the demo wardrobe is curated;
    - the P3 test passes;
    - no crash.
  - Bulk "all items" is the owner's call. Then wait for the owner to merge.
