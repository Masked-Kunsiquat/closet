# Subject Segmentation — Actionable Roadmap

Background removal for clothing item photos, triggered on-demand by the user.
Powered by ML Kit Subject Segmentation (traditional model — no AICore required,
works on all devices).

---

## Phase 1 — Dependency & Core Segmentation Utility

### Dependency

- [x] Add `mlkitSubjectSegmentation = "16.0.0-beta1"` version to `gradle/libs.versions.toml`
- [x] Add library alias to `[libraries]` block:
  `mlkit-subject-segmentation = { group = "com.google.android.gms", name = "play-services-mlkit-subject-segmentation", version.ref = "mlkitSubjectSegmentation" }`
  (note: `play-services-mlkit-*`, not `com.google.mlkit:*`)
- [x] Add `"fullImplementation"` dep to `features/wardrobe/build.gradle.kts` + flavor dims (full/foss)
- [x] No model download flow needed — the ~200 KB model is delivered automatically
  via Google Play Services on first use

### SegmentationRepository

- [x] Create `features/wardrobe/src/full/kotlin/…/repository/SegmentationRepository.kt` (real impl)
      + `features/wardrobe/src/foss/kotlin/…/repository/SegmentationRepository.kt` (stub — throws UnsupportedOperationException)
  - `@Singleton`, `@Inject constructor(@ApplicationContext context: Context)`
  - Single public method: `suspend fun removeBackground(bitmap: Bitmap): Bitmap`
    - Creates `SubjectSegmenterOptions` with **`enableForegroundBitmap()`**
    (the API returns a ready-made masked Bitmap — no manual pixel iteration needed)
  - Gets client via `SubjectSegmentation.getClient(options)`
  - Converts input to `InputImage.fromBitmap(bitmap, 0)`
  - Wraps `client.process(inputImage)` Task via `.await()` (already available via
    `kotlinxCoroutinesPlayServices` in the version catalog)
  - Returns `result.foregroundBitmap` — a `Bitmap` with transparent background,
    `ARGB_8888` config; throws `IllegalStateException` if null (caller catches)
  - Calls `client.close()` in a `finally` block
  - Downsample input to max 1024px on longest side before processing (minimum
    512×512 required for accuracy per docs); the stored image will be the
    downsampled+masked PNG, original dimensions are not restored

- [x] `SegmentationRepository` uses `@Singleton @Inject constructor` — Hilt resolves from whichever source set is active (full/foss), no `@Provides` in `DataModule` needed

### StorageRepository — PNG save support

- [x] Add `suspend fun saveBitmap(bitmap: Bitmap, filename: String): String` to
  `core/data/src/main/kotlin/com/closet/core/data/repository/StorageRepository.kt`
  - Saves to the same app-owned images directory used by `saveImage()`
  - Compresses as `Bitmap.CompressFormat.PNG` (quality param ignored by PNG encoder)
  - Returns a relative path (same convention as `saveImage()` — just the filename,
    no leading slash) so the rest of the pipeline is unchanged

---

## Phase 2 — ViewModel Integration

### ClothingFormUiState additions (`ClothingFormViewModel.kt`)

- [x] Add `val isSegmenting: Boolean = false` to `ClothingFormUiState`
- [x] Add `val hasSegmentedImage: Boolean = false` — true after a successful
  removal; drives "Revert" button visibility
- [x] Add `val originalImageFile: File? = null` — holds the pre-segmentation
  file so revert can restore it without re-picking
  (tracked as `originalSegmentationImagePath: String?` in `FormState`, derived to `File?` in `uiState` combine)

### ClothingFormViewModel functions

- [x] Inject `SegmentationRepository` into `ClothingFormViewModel`

- [x] `fun removeBackground()`:
  - Guard: return early if `uiState.imageFile == null` or `uiState.isSegmenting`
  - Set `isSegmenting = true`
  - In `viewModelScope.launch(Dispatchers.IO)`:
    - Store current `imageFile` as `originalImageFile` in state (for revert)
    - Decode `imageFile` to `Bitmap` via `BitmapFactory.decodeFile()`
    - Call `segmentationRepository.removeBackground(bitmap)`
    - On success: save result via `storageRepository.saveBitmap(...)`, update
      `imagePath` and `imageFile` in state, set `hasSegmentedImage = true`
    - On any exception (catch, rethrow `CancellationException`): emit error
      snackbar via existing error channel; restore original image in state
    - Always: set `isSegmenting = false`

- [x] `fun revertSegmentation()`:
  - Restores `imagePath`/`imageFile` from `originalImageFile`
  - Clears `hasSegmentedImage` and `originalImageFile`
  - Deletes the segmented PNG file from storage (best-effort, log on failure)
  - `cancel()` and `save()` also updated to clean up `originalSegmentationImagePath` on exit

---

## Phase 3 — UI Integration

### Photo section in `ClothingFormScreen.kt` (or `FormComponents.kt`)

Locate the existing photo `Box`/`AsyncImage` block. The changes all live inside
or directly below that block.

- [x] Overlay a `CircularProgressIndicator` centered over the photo `Box` when
  `uiState.isSegmenting == true`; dim the image with `Modifier.alpha(0.4f)` while
  segmenting so the spinner is readable

- [x] Show a **"Remove background"** `OutlinedButton` below the photo when:
  - `uiState.imageFile != null`
  - `uiState.isSegmenting == false`
  - `uiState.hasSegmentedImage == false`
  - `onClick = { viewModel.removeBackground() }`

- [x] Show a **"Revert"** `TextButton` (or secondary label) below the photo when
  `uiState.hasSegmentedImage == true && !uiState.isSegmenting`
  - `onClick = { viewModel.revertSegmentation() }`
  - Label: "Undo background removal"

- [x] Disable the photo-picker launcher button (`Add Photo` / `Change Photo`)
  while `uiState.isSegmenting == true`

### String resources (`features/wardrobe/src/main/res/values/strings.xml`)

- [x] `wardrobe_remove_background` — "Remove background"
- [x] `wardrobe_revert_segmentation` — "Undo background removal"
- [x] `wardrobe_segmentation_error` — "Couldn't remove background. Try a photo with a clearer subject." (added in Phase 2)

---

## Phase 4 — Display correctness

### Transparent PNG in closet grid & detail view

ML Kit segmentation output is a PNG with a transparent background. The existing
`AsyncImage` + `ContentScale.Fit` setup already handles this correctly — Coil
renders transparent pixels against whatever background the parent `Box` provides
(which is `MaterialTheme.colorScheme.surfaceVariant` in both the grid card and
the detail screen hero). No changes required to display code.

- [ ] Smoke-test on a physical device: add a segmented item, verify grid card and
  detail view render without a white halo or black fill where the background was

### Edit form — segmented image round-trip

When the user opens an existing item with a segmented PNG in edit mode, the
"Remove background" button must not reappear (the image is already clean).

- [x] In `loadItemForEditing()`, if the stored `imagePath` ends with `.png`,
  set `hasSegmentedImage = true` in the initial state so the button stays hidden
  and "Undo" is not shown (the original is gone — no revert target)

---

## Phase 5 — Edge quality (confidence mask)

Replace the hard-cut `enableForegroundBitmap()` with `enableForegroundConfidenceMask()`
so edge pixels (collar, cuffs, loose fabric) get partial alpha instead of a binary cut.
No new dependency needed — same `play-services-mlkit-subject-segmentation` library.

### SegmentationRepository (full flavor)

- [x] Change `SubjectSegmenterOptions` to use `.enableForegroundConfidenceMask()` instead
  of `.enableForegroundBitmap()`
- [x] After `.await()`, read `result.foregroundConfidenceMask!!` (a `FloatBuffer`, row-major)
- [x] Build the output bitmap manually:
  - Copy input to an `ARGB_8888` mutable bitmap
  - `getPixels()` into an `IntArray`
  - For each pixel `i`: `alpha = (mask.get() * 255).toInt().coerceIn(0, 255)`
  - Write alpha: `pixels[i] = (pixels[i] and 0x00FFFFFF) or (alpha shl 24)`
  - `setPixels()` back; `mask.rewind()`
- [x] Remove the `foregroundBitmap` null-check (no longer used)

---

## Phase 6 — Batch segmentation

Process all existing wardrobe items in the background. Triggered from Settings;
survives the app being killed via WorkManager. Progress is surfaced as a
**Live Update** notification (Android 16 / API 36+) with a standard progress
notification fallback for older devices.

No flavor split needed — WorkManager and system notifications are AOSP; no GMS.

### Dependencies (`gradle/libs.versions.toml` + `features/wardrobe/build.gradle.kts`)

- [x] `androidx.work:work-runtime-ktx` + `androidx-hilt-work` added to version catalog
  and `features/wardrobe/build.gradle.kts`; `ksp(androidx-hilt-compiler)` added to both
  `features/wardrobe` and `app`
- [x] No new MLKit dependency — reuses `play-services-mlkit-subject-segmentation`
  already on `features/wardrobe`

### `BatchSegmentationWorker` (`features/wardrobe/src/main/kotlin/…`)

- [x] `@HiltWorker class BatchSegmentationWorker @AssistedInject constructor` — `CoroutineWorker`
- [x] `setForeground(createForegroundInfo(done, total))` foreground service wiring;
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` perms in manifest;
  default WorkManager initializer disabled; `ClosetApp` implements `Configuration.Provider`
- [x] Queries `ClothingDao.getItemsNeedingSegmentation()` (non-PNG items with an image)
- [x] Per-item loop: `decodeSampledBitmap` → `removeBackground()` → `saveBitmap()` →
  `updateItemImagePath()`; per-item exception isolation (`catch → Timber.e → failed++`);
  `CancellationException` re-thrown
- [x] `setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))` after each item
- [x] Returns `Result.success(workDataOf(KEY_DONE to done, KEY_FAILED to failed))`
- [x] Constants (`NAME`, `KEY_DONE`, `KEY_TOTAL`, `KEY_FAILED`) extracted to
  `core/data/.../worker/BatchSegmentationWork.kt` so settings module can reference them
  without depending on `features/wardrobe`

### Live Update notification (`createForegroundInfo`)

- [x] **API 36+ (Android 16)**: `Notification.Builder` + `Notification.ProgressStyle`
  with `setProgress(done * 10_000 / total)` — standardised Live Update appearance
  (ref: https://developer.android.com/develop/ui/views/notifications/live-update)
- [x] **API < 36 fallback**: `NotificationCompat.Builder` with
  `setProgress(total, done, false)` — standard determinate progress bar
- [x] Branch at runtime via `Build.VERSION_CODES.BAKLAVA` (= 36)
- [x] Notification channel: `"segmentation_batch"` — importance LOW (silent, no sound)

### Settings integration (`features/settings`)

- [x] Add a new "Wardrobe" section to `SettingsScreen`
- [x] Row: **"Remove backgrounds"** — subtitle shows item count eligible;
  tapping calls `viewModel.startBatchSegmentation()` (idempotent via `KEEP` policy)
- [x] While the worker is RUNNING or ENQUEUED: show progress row with
  `LinearProgressIndicator` driven by `WorkInfo.progress` (done/total)
- [x] When eligible count is 0: show "All backgrounds already removed" label
- [x] On completion (`WorkInfo.State.SUCCEEDED`): snackbar via `LaunchedEffect`
  keyed on `workInfo.id + state` — "X items updated" or "X items updated, Y skipped"
- [x] Architecture: `BatchSegmentationScheduler` interface in `core/data`;
  impl + `WorkManager` singleton provided from new `WardrobeModule` in `features/wardrobe`
  so `features/settings` has no dependency on `features/wardrobe`

### `SettingsViewModel` additions

- [x] Inject `WorkManager`, `ClothingDao`, `BatchSegmentationScheduler`
- [x] `segmentationEligibleCount: StateFlow<Int>` from `ClothingDao.getSegmentationEligibleCount()`
- [x] `batchSegWorkInfo: StateFlow<WorkInfo?>` from
  `workManager.getWorkInfosForUniqueWorkFlow(NAME).map { it.firstOrNull() }`
- [x] `fun startBatchSegmentation()` — delegates to `BatchSegmentationScheduler.schedule()`

---

## Phase 7 — Model readiness (first-run download gate)

The Play Services ML Kit model (`mobile_bg_removal_v8.f16.tflite`) is **not bundled in
the APK** — it is downloaded in the background by Google Play Services on first use.
If the user taps "Remove background" before the download completes, the Task fails
silently. This phase adds an explicit download gate so the UI reflects true readiness.

### `RemoteModelManager` helper (`SegmentationRepository`, full flavor only)

- [ ] Add `suspend fun isModelDownloaded(): Boolean` — calls
  `RemoteModelManager.getInstance().isModelDownloaded(CustomRemoteModel(…)).await()`
  using the same `CustomModelDownloadConditions` / model name the SDK resolves at runtime
  (name: `"subject_segmentation"` — match the module resolved by `DynamiteModule`)
- [ ] Add `suspend fun ensureModelDownloaded()` — calls
  `RemoteModelManager.getInstance().download(model, conditions).await()`; no-op if
  already downloaded; throws on failure (caller catches)
- [ ] FOSS stub: `isModelDownloaded()` returns `false`; `ensureModelDownloaded()` no-ops

### `ClothingFormViewModel` changes

- [ ] On `removeBackground()` entry: call `isModelDownloaded()` before setting
  `isSegmenting = true`
  - If not downloaded: set a new `isDownloadingModel: Boolean = true` state flag,
    call `ensureModelDownloaded()`, then proceed
  - On download failure: emit error snackbar ("Couldn't download segmentation model");
    clear `isDownloadingModel`; return early without segmenting
- [ ] Add `val isDownloadingModel: Boolean = false` to `ClothingFormUiState`

### UI changes (`ClothingFormScreen.kt`)

- [ ] While `isDownloadingModel == true`: show an indeterminate `CircularProgressIndicator`
  over the photo with label "Downloading model…" (reuse existing segmenting overlay,
  swap the label)
- [ ] Disable "Remove background" button while `isDownloadingModel == true`

### `BatchSegmentationWorker` changes

- [ ] At the start of `doWork()`, before the item loop: call `ensureModelDownloaded()`
  - On failure: return `Result.failure(workDataOf("error" to "model_download_failed"))`
  - This prevents starting the foreground service + notification for a run that will
    immediately fail on every item

### String resources

- [ ] `wardrobe_downloading_model` — "Downloading segmentation model…"
- [ ] `wardrobe_model_download_error` — "Couldn't download segmentation model. Check your connection and try again."

---

## Deferred / out of scope

- **Manual touch-up** (paint-to-include / paint-to-exclude brush strokes) — custom
  canvas compositing; significant UI work with no MLKit support
- **AICore** — Subject Segmentation is a traditional ML Kit Vision API, not an
  AICore feature. There is no AICore variant of it. The `play-services-mlkit`
  implementation is the only backend and works on all devices (API 24+)
