# Changelog

All notable changes to hangr (Android) are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions correspond to `versionName` in `app/build.gradle.kts`.

---

## [Unreleased]

---

## [0.5.0] — 2026-04-04

Phase 1 of the chat enhancement roadmap: multi-turn conversation history,
a "New chat" action, and a hardened JSON parsing pipeline for AI responses.

### Added
- **Multi-turn conversation history** — `ChatViewModel` maintains a rolling
  6-turn (3-exchange) history; each successful response appends user + assistant
  turns, failed attempts never pollute context. History is never persisted — it
  lives only for the duration of the session.
- **History threading through providers** — `ChatAiProvider.chat()` now accepts
  `history: List<ConversationTurn>`; `AnthropicChatProvider`, `OpenAiChatProvider`,
  and `GeminiChatProvider` map it to their respective message-array formats.
  `NanoChatProvider` flattens the last 1 exchange (2 turns) as a text prefix inside
  the single-string prompt to stay within Nano's character budget.
- **Context block sent once** — `ChatRepository` injects the wardrobe context block
  only in the system preamble; subsequent history turns never re-include it, keeping
  token cost flat across multi-turn sessions.
- **"New chat" action** — top bar edit icon clears conversation history and message
  list; guarded by an `AlertDialog` confirmation so accidental taps don't lose context.
- **`ConversationTurn`** value class in `core/data/ai/` with a provider-agnostic
  `Role` enum (`User` / `Assistant`).

### Fixed
- **Gemini prose responses** — added `"generationConfig":{"responseMimeType":"application/json"}`
  to every Gemini request so the API enforces JSON output at the model level, preventing
  the provider from returning plain text that crashes the parser.
- **`ChatResponseParser` fallback** — parser now strips markdown code fences and
  extracts the outermost `{…}` block from surrounding prose before attempting to
  parse, so a stray non-JSON response degrades gracefully rather than throwing
  `JsonDecodingException`.

---

## [0.4.0] — 2026-04-04

Phase 4 of the image pipeline: automatic compression of incoming photos, segmented
images, and a background worker that retroactively shrinks oversized items already
in storage. All image paths in the database stay unchanged — no migrations needed.

### Added
- **Phase 4.1 — compress on save** — `StorageRepository.saveImage()` now resizes
  any incoming photo so its longest edge is ≤ 1 600 px and re-encodes it as JPEG
  at 85 % quality before writing to `closet_images/`.
- **Phase 4.2 — WebP for segmented images** — `StorageRepository.saveSegmented()`
  saves background-removed PNGs as WebP-Lossy on API 30+ and falls back to PNG on
  older API levels; the `.webp` extension is stored in the database path.
- **Phase 4.3 — `ImageCompressionWorker`** — background `CoroutineWorker` that
  iterates every stored image path, skips files that already meet both the 1 600 px
  and 1.5 MB thresholds, re-encodes candidates to a unique temp file, and replaces
  the original only when the re-encoded file is smaller (atomic rename). Scheduled
  idle + battery-not-low on every app start (KEEP policy) and triggerable
  immediately from Settings.
- **Phase 4.4 — Storage display in Settings** — storage card shows total image
  count, size on disk, and a "Compress now" button that enqueues an immediate
  compression run; a live progress indicator reports `done / total / skipped /
  failed` while the worker runs.
- **Gemini model discovery** — Settings screen now fetches available Gemini
  models from the API rather than using a hardcoded list.

### Fixed
- **`StorageRepository` gallery photos** — switched to a single-pass URI read
  (open once, copy to temp, close) to fix intermittent failures when reading
  content-scheme URIs from the gallery.
- **Segmented image sentinel** — the "already segmented" path check now covers
  `.webp` files in addition to the original PNG sentinel so re-segmentation is
  correctly skipped for WebP-saved items.
- **`ImageCompressionWorker` — concurrent temp collision** — temp files are now
  created with `File.createTempFile()` (unique per invocation) so concurrent
  schedule + runNow worker instances never clobber each other's temp paths.
- **`ImageCompressionWorker` — corrupt image handling** — `needsCompression()`
  throws `IOException` when `BitmapFactory` bounds decode returns
  `outWidth/outHeight ≤ 0`; corrupt images now increment `failed` instead of
  being silently treated as already-compressed.
- **`ImageCompressionWorker` — WebP/API mismatch** — `formatFor()` returns
  `null` for `.webp` on API < 30 and unknown extensions; the call site throws
  `IOException` rather than writing PNG bytes into a mismatched container.
- **`ImageCompressionWorker` — `done` counter** — `done` is incremented only
  when the atomic rename succeeds (temp was smaller); files where the re-encoded
  output was larger than the original now increment `skipped` instead.
- **`ImageCompressionWorker` — extreme aspect ratios** — scaled width/height are
  clamped to `coerceAtLeast(1)` before `Bitmap.createScaledBitmap` to prevent
  `IllegalArgumentException` on degenerate images.
- **`ImageCompressionWorker` — short filename prefix** — temp file prefix is
  padded to ≥ 3 chars to satisfy `File.createTempFile`'s minimum prefix length.
- **`ImageCompressionWorker` — `inSampleSize` over-downscale** — the loop now
  uses `while (longest / (sampleSize * 2) >= MAX_DIMENSION)` so the decoded
  image stays at or above `MAX_DIMENSION`; the subsequent `createScaledBitmap`
  step handles the precise trim rather than the power-of-two decode stepping
  past the threshold and discarding extra detail.
- **`ImageCompressionWorker` — batch abort on path error** — `canonicalPath`
  resolution and `file.exists()` check moved inside the per-image `try` block
  so an `IOException` or `SecurityException` during path resolution increments
  `failed` and continues to the next image rather than aborting the whole batch.
- **`DatePicker` epoch conversion** — date values are now converted using UTC to
  avoid off-by-one-day errors in negative-offset timezones.
- **Settings — backup WAL checkpoint** — WAL is flushed before the backup
  export to ensure the copied database file is consistent.
- **Settings — Nano download dialog guard** — the Nano model download dialog
  is no longer shown when a download is already in progress.

---

## [0.3.0] — 2026-04-03

Local backup and restore for the full wardrobe. Export your entire closet
to a portable `.hangr` file and restore it on any device, with Android
Auto Backup wired up as a best-effort supplement.

### Added
- **Backup & Restore screen** — reachable from Settings; two rows: "Export
  backup" and "Restore from backup", with a determinate/indeterminate
  progress bar and step label while the operation runs.
- **`BackupRepository.export()`** — WAL checkpoint → copy `closet.db`,
  `closet_images/`, and the three DataStore `.preferences_pb` files →
  write `manifest.json` → ZIP to a temp file → hand off to SAF
  `ACTION_CREATE_DOCUMENT`. Temp files always cleaned up in `finally`.
- **`RestoreRepository.restore()`** — open `.hangr` via SAF, unzip to
  temp with canonical-path traversal guard, validate manifest (rejects
  backups from a newer schema version), close + overwrite the Room DB,
  reopen to run any pending migrations, replace all images, overwrite
  DataStore prefs. API keys are excluded from backups; a non-dismissable
  restart dialog prompts the user to re-enter them after restore.
- **`BackupForegroundService`** — drives export and restore on a
  background coroutine; runs in the foreground so the OS cannot kill it
  mid-operation on large wardrobes. Exposes `progress: StateFlow` observed
  by `BackupViewModel`. Try-catch on both coroutine bodies so unexpected
  exceptions always surface as `BackupProgress.Error` rather than leaving
  the service stuck.
- **`BackupManifest`** — serialised as `manifest.json` inside the ZIP;
  carries `schemaVersion`, `appVersion`, `createdAt`, `imageCount`, and
  `apiKeysExcluded: true`.
- **`BackupProgress`** sealed interface — `Idle | Running(step, done,
  total) | Success(outputUri?) | Error(message)`; distinguishes export
  success (`outputUri != null`) from restore success (`null`) so the UI
  shows the right post-completion action.
- **`BackupViewModel`** — Hilt ViewModel that mirrors the service
  `StateFlow` and starts/cancels the service via explicit intents.
- **Android Auto Backup rules** — `backup_rules.xml`
  (`<full-backup-content>`, API 30 and below) and
  `data_extraction_rules.xml` (`<data-extraction-rules>`, API 31+);
  both include `closet.db`, `closet_images/`, and the three preference
  stores; both exclude `ai_keys_encrypted.xml` (device-locked Keystore,
  non-portable). Cloud backup has `disableIfNoEncryptionCapabilities="true"`.
- **`ClothingDatabase.checkpointWal()` / `.databaseVersion`** — thin
  wrappers over `openHelper` so the `app` module can trigger a WAL flush
  and read the schema version without a direct Room compile dependency.
- **Image compression roadmap** (Phase 4 in
  `docs/roadmaps/backup-restore-roadmap.md`) — actionable plan to cap
  incoming photos at 1600 px / JPEG 85 %, use WebP-lossy for segmented
  images on API 30+, and migrate existing libraries via a background
  `ImageCompressionWorker`.

### Fixed
- **`AiSettingsScreen` compilation** — corrected imports for `AiProvider`,
  `StyleVibe`, and `BatchSegmentationWork`; replaced non-existent
  `AiProvider.GeminiNano` with `AiProvider.Nano`; made `NanoStatus`
  `when` exhaustive with correct `Checking`, `Downloading`, and `Failed`
  branches; added missing string resources.
- **`SettingsUiState` enum defaults** — `ClosetAccent.Blue` →
  `ClosetAccent.Amber`; `WeatherService.OpenWeatherMap` →
  `WeatherService.OpenMeteo`.
- **`SettingsViewModel` repository API alignment** — all
  `aiPreferencesRepository.property` accesses replaced with their correct
  `getMethod()` call-style; `setOpenAiKey` / `setAnthropicKey` /
  `setGeminiKey` renamed to match actual method names; fixed
  `fetchOpenAiModels` nullable-URL argument.
- **`PreferencesRepository.setLastHandledBatchId`** — now returns
  `Result<Unit>` and wraps the DataStore edit in try/catch; call site
  in `SettingsViewModel` logs failures via Timber instead of silently
  dropping them.
- **`BackupForegroundService.resetProgress()`** — now no-ops when state
  is `Running` to prevent clobbering an in-flight operation.
- **Foreground service type compliance (API 34+)** — both `startForeground`
  calls now use the three-argument overload with
  `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` on API 29+, matching
  the `android:foregroundServiceType="dataSync"` manifest declaration.
- **`ServiceInfo` wrong package** — was `android.app.ServiceInfo`
  (no `FOREGROUND_SERVICE_TYPE_*` constants there); corrected to
  `android.content.pm.ServiceInfo`.
- **`CancellationException` import** — a `replace_all` operation had
  stripped the `kotlinx.coroutines.` package prefix from the import in
  `BackupForegroundService`; restored.
- **`room-runtime` missing from `app` module** — Kotlin compiler could not
  resolve `RoomDatabase` as a supertype of `ClothingDatabase` in the `app`
  module; added explicit `implementation(libs.room.runtime)` dependency.

---

## [0.2.0] — 2026-04-02

Phase 4 of the RAG pipeline: wardrobe chat. Ask natural-language questions
about your closet and get answers grounded in your actual items via
cosine-similarity retrieval + your choice of AI provider.

### Added
- **`EmbeddingEncoder`** — extracted from `EmbeddingWorker`; encodes a query
  string into a 384-float vector using the same ONNX model as the background
  indexer, enabling query-time embedding without re-running the worker.
- **`EmbeddingIndex`** — in-memory FAISS-style flat index loaded at app start;
  `search(vec, topK)` returns item IDs ranked by cosine similarity.
- **`ChatAiProvider` infrastructure** — pluggable provider abstraction with
  three implementations: `OpenAiChatProvider` (OpenAI-compatible REST, supports
  custom base URL for self-hosted models), `AnthropicChatProvider`, and
  `GeminiChatProvider`; `ChatAiProviderSelector` resolves the active provider
  from `AiPreferencesRepository` at call time.
- **`ChatRepository`** — full RAG query pipeline: encode query → top-5 cosine
  search → fetch `ClothingItemDetail` for matched IDs → build context block
  (name, category, colours, materials, sanitised description) → call active
  provider; `CancellationException` propagates correctly through all `Result`
  unwrap points.
- **Chat screen** (`features/chat`) — `ChatViewModel`, `ChatScreen`, and
  `ChatUiState`; renders a scrolling message list with `OutfitMiniCard` for
  outfit references, auto-scrolls to latest message, and handles cold-start
  with an empty-state prompt.
- **Chat as 5th bottom-nav tab** — navigation wiring in `ClosetNavGraph`; tab
  hidden when AI is disabled in settings.
- **AI Settings sub-screen** (`AiSettingsScreen`) — extracted from the main
  Settings screen; shows provider picker, per-provider key/model fields with
  live model-list fetch (OpenAI + Anthropic), embedding index size + rebuild
  trigger, and batch caption/segmentation controls.
- **`BatchCaptionProgress` / `BatchCaptionResult`** — data classes for
  in-flight progress and terminal result of a batch caption enrichment run.
- **Batch caption enrichment** — `CaptionEnrichmentProvider` now exposes
  `progress`, `result`, and `startBatchEnrichment()`; the full-flavor
  `ImageCaptionRepository` implements sequential on-device captioning with
  IO-dispatched bitmap decode, per-item failure counting, and a guaranteed
  `_progress = null` in a `finally` block.
- **Caption result dedupe token** — `BatchCaptionResult` carries a `resultId`
  UUID; `SettingsUiState` tracks `lastHandledCaptionId` so the result snackbar
  is not re-shown on screen re-subscription.
- **`SettingsUiState`** — single `StateFlow<SettingsUiState>` replaces the
  previous ~20 individual public `StateFlow`s on `SettingsViewModel`; built
  from nested `combine()` calls over private intermediate flows.

### Fixed
- **`CancellationException` propagation** — `ChatRepository` `getOrElse` lambdas
  and `provider.chat()` result now rethrow `CancellationException` instead of
  wrapping it in `Result.failure`, ensuring coroutine cancellation is never
  swallowed.
- **Stale embedding index** — `SettingsViewModel` reloads the in-memory index
  after a user-triggered rebuild completes (WorkInfo `SUCCEEDED` / `FAILED`).
- **`scheduleForceRebuild()` → `runNow()`** — corrected the call site to the
  method that actually exists on `EmbeddingScheduler`.
- **`UUID.fromString` crash** — `lastHandledBatchId` map now uses `runCatching`
  so a malformed stored value emits `null` instead of crashing the flow.
- **Bitmap decode on Main thread** — `BitmapUtils.decodeSampledBitmap` in
  `ImageCaptionRepository.startBatchEnrichment` moved to `withContext(Dispatchers.IO)`.
- **Null bitmap silently skipped** — an unreadable image now increments the
  `failed` counter and logs a warning instead of silently `continue`-ing.
- **`semanticDescription` leaking notes** — `ChatRepository` strips everything
  from `"Notes:"` onward and collapses whitespace before appending to the RAG
  context block.
- **`LazyColumn` key collision** in `ChatScreen` — messages now keyed by stable ID.
- **Cold-start UX** — chat screen shows an empty-state prompt rather than a
  blank list on first launch.
- Various `NanoStatus` type reference and `nanoInitializer.status` alignment
  fixes in `SettingsViewModel`.

---

## [0.1.2] — 2026-03-30

Android App Shortcuts — Phase 1 intent-routing infrastructure (in progress).

### Added
- **`ShortcutActions`** — action/extra constants for all shortcut intents
  (`ACTION_QUICK_ADD`, `ACTION_LOG_FIT`, `ACTION_LAUNDRY_DAY`, `ACTION_CATEGORY`).
- **Shortcut intent routing** — `MainActivity` forwards shortcut intents via a
  `MutableStateFlow`; `ClosetNavGraph` consumes them in a `LaunchedEffect` and
  drives the `NavController` to the correct destination on cold-start and re-launch.

---

## [0.1.1] — 2026-03-30

Phase 2A + Phase 3 of the RAG pipeline: on-device embedding storage and the
background embedding worker. Items now get a 384-float semantic vector written
to `item_embeddings` whenever their `semantic_description` changes, enabling
future cosine-similarity search without a network call.

### Added
- **Migration 4 → 5** — `item_embeddings` table: stores a little-endian float32
  BLOB (384 dims), `model_version`, `input_snapshot` (for staleness detection),
  and `embedded_at` timestamp.
- **`EmbeddingWorker`** — periodic background worker (charging + idle) that
  tokenises `semantic_description + image_caption` with a BERT WordPiece
  tokenizer, runs `snowflake-arctic-embed-xs` INT8 ONNX inference, mean-pools
  the `last_hidden_state`, L2-normalises to a unit vector, and upserts into
  `item_embeddings`. Reports `done / total / failed` progress via WorkManager.
- **`WordPieceTokenizer`** — pure-Kotlin BERT WordPiece tokenizer reading
  `assets/models/vocab.txt`; pads/truncates to 128 tokens.
- **`EmbeddingScheduler`** — schedules `EmbeddingWorker` as unique periodic
  work (1-hour interval) via WorkManager.
- **`SemanticDebugCard`** — debug-only UI card on the item detail screen that
  surfaces `semantic_description` and `image_caption` with copy-to-clipboard,
  shown only in `BuildConfig.DEBUG` builds.

### Fixed
- **Colour re-extraction after background removal** — `ClothingFormViewModel`
  now re-runs Android Palette on the segmented PNG after `removeBackground()`
  completes (transparent pixels are skipped so only subject colours are
  sampled). The colour junction table and `semantic_description` are updated
  to reflect the post-segmentation state.
- **`BatchSegmentationWorker` colour gap** — batch background removal now also
  re-extracts colours from the in-memory masked bitmap, updates the junction
  table, and calls `revectorizeItem` after each successful PNG save.
- **`OnnxTensor` partial-creation leak** — tensors are now created inside
  nested `use {}` blocks so a failure during the second or third
  `createTensor` call cannot leave earlier tensors unclosed.
- **`OrtSession.SessionOptions` resource leak** — `SessionOptions` is now
  wrapped in `use {}` so the native handle is always released.
- **`KEY_TOTAL` missing from `EmbeddingWorker` success results** — both
  `Result.success()` return sites now include `KEY_TOTAL` for consistent
  progress data.
- **`vectorizeItem` silent on success** — now logs item ID, description
  length, and full description text at DEBUG level under `ClothingRepository`.

---

## [0.1.0] — 2026-03-22

Initial working release. Core wardrobe management, background removal, and
Phase 1 of the RAG pipeline (semantic descriptions + image captions).

### Added
- Full wardrobe CRUD (add / edit / delete clothing items with photos).
- Category, subcategory, colour, material, season, occasion, and pattern
  tagging with junction-table storage.
- Outfit builder and OOTD logging with a partial-index uniqueness constraint.
- Stats screen: wear counts, cost-per-wear, breakdowns by category / colour /
  occasion with Vico column charts.
- **Subject segmentation** — ML Kit Subject Segmentation removes image
  backgrounds in-form and via `BatchSegmentationWorker` for existing items.
  Model readiness gate prevents silent per-item failures.
- **Migration 3 → 4** — `semantic_description` and `image_caption` shadow
  columns on `clothing_items` for RAG input.
- **`ItemVectorizer`** — deterministic prose generator that serialises a
  `ClothingItemDetail` into a natural-language description; output written to
  `semantic_description` after every insert or update.
- **`ImageCaptionRepository`** — wraps ML Kit Image Description API
  (Gemini Nano / AICore) to generate a short caption at image-capture time;
  FOSS stub provided for F-Droid builds.
- **Batch caption enrichment** — Settings screen action to backfill
  `image_caption` for existing items that pre-date at-capture captioning.
- Two product flavors: `full` (GMS / Play Services) and `foss` (no GMS,
  F-Droid target).

[Unreleased]: https://github.com/Masked-Kunsiquat/closet/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/Masked-Kunsiquat/closet/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/Masked-Kunsiquat/closet/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/Masked-Kunsiquat/closet/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/Masked-Kunsiquat/closet/compare/v0.1.2...v0.2.0
[0.1.2]: https://github.com/Masked-Kunsiquat/closet/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/Masked-Kunsiquat/closet/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/Masked-Kunsiquat/closet/releases/tag/v0.1.0
