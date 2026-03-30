# Changelog

All notable changes to hangr (Android) are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions correspond to `versionName` in `app/build.gradle.kts`.

---

## [Unreleased]

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

[Unreleased]: https://github.com/Masked-Kunsiquat/closet/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/Masked-Kunsiquat/closet/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/Masked-Kunsiquat/closet/releases/tag/v0.1.0
