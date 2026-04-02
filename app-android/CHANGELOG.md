# Changelog

All notable changes to hangr (Android) are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions correspond to `versionName` in `app/build.gradle.kts`.

---

## [Unreleased]

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

[Unreleased]: https://github.com/Masked-Kunsiquat/closet/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/Masked-Kunsiquat/closet/compare/v0.1.2...v0.2.0
[0.1.2]: https://github.com/Masked-Kunsiquat/closet/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/Masked-Kunsiquat/closet/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/Masked-Kunsiquat/closet/releases/tag/v0.1.0
