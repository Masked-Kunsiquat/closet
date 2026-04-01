# Local RAG Wardrobe Assistant — Actionable Roadmap

A fully local, privacy-first AI Wardrobe Assistant powered by Retrieval-Augmented
Generation. Vector embeddings and LLM inference run entirely on-device. No cloud.
No API keys. No telemetry.

**Strategy: Shadow Launching.** Build the data pipeline and background workers first.
Let semantic data mature silently over several updates before the chat UI ships.
This eliminates the Cold Start Problem (embedding 300 items on day one) and means
the Assistant is warm and useful the moment the user opens it for the first time.

---

## Phase 1 — Semantic Shadow & Visual Enrichment (Data Layer)

Populate `clothing_items` with two new text columns: a structured prose description
assembled from junction tables (`ItemVectorizer`) and an AI-generated photo caption
(`ImageCaptionRepository`). Together these form the input to the embedding pipeline
in Phase 3.

**The foreground-only constraint.** The ML Kit GenAI Image Description API requires
a live UI context — it cannot run in a `WorkManager` or background service. All
caption calls must happen either (a) at the moment the user captures a photo
("At-Capture"), or (b) in a foreground Settings screen with `FLAG_KEEP_SCREEN_ON`
("Batch Enrichment").

---

### §1 — Migration 3→4 ✓ DONE

Add two nullable `TEXT` columns to `clothing_items`. Null = not yet enriched;
the workers below populate them incrementally.

**New file:** `core/data/src/main/kotlin/com/closet/core/data/migrations/Migration_3_4.kt`

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Drop partial index before altering the table (Room migration requirement).
        db.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")
        db.execSQL("ALTER TABLE clothing_items ADD COLUMN semantic_description TEXT")
        db.execSQL("ALTER TABLE clothing_items ADD COLUMN image_caption TEXT")
    }
}
```

**Modify:** `ClothingDatabase.kt`
- Bump `version` from `3` to `4`
- Register `MIGRATION_3_4` in the `Room.databaseBuilder(…).addMigrations(…)` call
- Add both columns to `ClothingItemEntity` (below)

**Modify:** `ClothingItemEntity.kt` — add two fields with `defaultValue = ""`
(Room requires `defaultValue` to match the SQL default for schema hash validation):

```kotlin
@ColumnInfo(name = "semantic_description", defaultValue = "")
val semanticDescription: String? = null,

@ColumnInfo(name = "image_caption", defaultValue = "")
val imageCaption: String? = null,
```

> Follow the drop-index pattern: `AGENTS.md` requires every manual `migrate()` to
> `DROP INDEX IF EXISTS one_ootd_per_day` unconditionally at the top.

---

### §2 — `ItemVectorizer` utility ✓ DONE

Reads a fully-loaded `ClothingItemDetail` (all relations already resolved) and
serialises it into a dense English prose paragraph. This is the "structured" half
of the semantic description — no AI required.

**New file:** `core/data/src/main/kotlin/com/closet/core/data/util/ItemVectorizer.kt`

Input: `ClothingItemDetail` (already defined in `ClothingItemEntity.kt`, carries
`item`, `category`, `subcategory`, `brand`, `colors`, `materials`, `seasons`,
`occasions`, `patterns`, `sizeValue`, `wearCount`).

Output: a single `String` paragraph, e.g.:

> "Nike White Air Max Sneaker. Category: Footwear > Sneakers. Colors: White
> (Neutral family), Grey (Neutral family). Materials: Mesh, Rubber. Seasons:
> Spring, Summer. Occasions: Casual, Sport. Pattern: Solid. Size: 10 (Shoes US
> Men's). Worn 12 times. Notes: Great for gym days."

Template rules (encode everything that matters for retrieval):
- Lead with `brand.name item.name` if brand present; else just `item.name`
- `"Category: {category.name} > {subcategory.name}"` if both present
- Each junction group only included if non-empty:
  - `"Colors: {name} ({colorFamily} family), …"`
  - `"Materials: …"`, `"Seasons: …"`, `"Occasions: …"`, `"Pattern: …"`
- Size: `"{sizeValue.value} ({sizeSystem.name})"` if present; waist/inseam appended
  for bottoms
- `"Worn {wearCount} times."` always
- `"Notes: {item.notes}"` only if notes non-null and non-blank

Keep it deterministic: same inputs → same output. No randomness, no AI calls.

**Where to call it:** `ClothingRepository` — call `ItemVectorizer.describe(detail)`
after every successful `insertItemWithColors` / `updateItemWithColors` and write
the result back with `ClothingDao.updateSemanticDescription(id, text)`.

---

### §3 — `ImageCaptionRepository` (full flavor / FOSS stub) ✓ DONE

Wraps the ML Kit GenAI Image Description API to produce a one-sentence photo
caption ("A white mesh sneaker on a neutral background.").

**Dependency to add in `gradle/libs.versions.toml` + `features/wardrobe/build.gradle.kts`:**

```toml
# libs.versions.toml [libraries]
mlkit-genai-image-description = { group = "com.google.mlkit", name = "genai-image-description", version.ref = "mlkitGenai" }
```

Scope it as `"fullImplementation"(libs.mlkit.genai.image.description)` — the same
pattern used for `mlkit-genai-prompt` in `features/recommendations`.

**New file (full):**
`features/wardrobe/src/full/kotlin/…/repository/ImageCaptionRepository.kt`

```kotlin
@Singleton class ImageCaptionRepository @Inject constructor(
    @ApplicationContext val context: Context
) {
    val isSupported: Boolean = true

    /** Must be called from the main thread / a foreground Composable scope. */
    suspend fun describe(bitmap: Bitmap): String
    suspend fun isModelDownloaded(): Boolean
    suspend fun ensureModelDownloaded()
}
```

- **Actual entry point:** `ImageDescription.getClient(ImageDescriberOptions)` — the factory is on
  `ImageDescription`, not `ImageDescriber`. Returns `ListenableFuture<T>` (not a GMS `Task`);
  wrap via `suspendCancellableCoroutine` + `future.addListener({ ... }, { it.run() })`.
- Return `result.description` trimmed; throw `IllegalStateException` if null
- **`isSupported`**: same pattern as `SegmentationRepository` — exposes the flag so
  the ViewModel can gate the caption path on FOSS builds

**New file (foss stub):**
`features/wardrobe/src/foss/kotlin/…/repository/ImageCaptionRepository.kt`

```kotlin
val isSupported: Boolean = false
suspend fun describe(bitmap: Bitmap): String = throw UnsupportedOperationException(…)
suspend fun isModelDownloaded(): Boolean = false
suspend fun ensureModelDownloaded() { }
```

---

### §4 — At-Capture flow (`ClothingFormViewModel`) ✓ DONE

Wire `ImageCaptionRepository` into the existing image-selection pipeline.

**Modify:** `ClothingFormViewModel.kt`

- Inject `ImageCaptionRepository` (alongside existing `SegmentationRepository`)
- Add `val isCaptioning: Boolean = false` to `FormState` and `ClothingFormUiState`
  (same pattern as `isSegmenting`)
- After `storageRepository.saveImage(uri)` resolves in `onImageSelected()`, if
  `imageCaptionRepository.isSupported`, launch a sub-coroutine on `Dispatchers.Main`
  (Image Description API requirement) to call `describe(bitmap)` and write the
  result into a new `imageCaption: String?` field in `FormState`
- The caption is transient in the form — it only persists to the DB when `save()` is
  called. Write it to `ClothingItemEntity.imageCaption` in the `save()` payload.
- On caption failure: log + ignore (caption is best-effort; never block save)

**`ClothingFormUiState` additions:**
```kotlin
val isCaptioning: Boolean = false,     // spinner hint for future UI polish
val imageCaption: String? = null,      // surfaced for debug/review if needed
```

> `canSave` should NOT be gated on `isCaptioning` — captioning is best-effort and
> must never prevent the user from saving.

---

### §5 — Batch Enrichment (Settings UI — foreground only) ✓ DONE

For the user's existing wardrobe items, provide a one-tap "Enrich descriptions"
button in the Settings Wardrobe section. Because the Image Description API is
foreground-only, this runs as a coroutine in the ViewModel while the screen is
visible — no WorkManager.

**Modify:** `SettingsViewModel.kt`

- Inject `ImageCaptionRepository`, `ClothingDao`
- New `StateFlow`: `captionEligibleCount` — items with `image_path IS NOT NULL AND
  image_caption IS NULL` (add DAO query to `ClothingDao`)
- New `StateFlow`: `batchCaptionProgress: BatchCaptionProgress?` (null = idle)

```kotlin
data class BatchCaptionProgress(val done: Int, val total: Int, val failed: Int)
```

- `fun startBatchEnrichment()`:
  - Sets `_batchCaptionProgress` to `BatchCaptionProgress(0, total, 0)`
  - Iterates items in `viewModelScope.launch(Dispatchers.Main)` — Image Description
    requires main thread for context; process sequentially (one at a time) to avoid
    overloading the on-device model
  - After each item: decode bitmap → `describe()` → `ClothingDao.updateImageCaption(id, text)`
  - Updates progress after each item
  - On completion: clears progress, emits snackbar

**Modify:** `SettingsScreen.kt`

- Add `FLAG_KEEP_SCREEN_ON` while `batchCaptionProgress != null`:
  ```kotlin
  val view = LocalView.current
  LaunchedEffect(batchCaptionProgress) {
      if (batchCaptionProgress != null) {
          view.keepScreenOn = true
      } else {
          view.keepScreenOn = false
      }
  }
  ```
- Show progress row identical in structure to the existing batch-segmentation row:
  `LinearProgressIndicator(progress = done.toFloat() / total)` + `"$done / $total items enriched"`
- When `captionEligibleCount == 0` and no batch running: show `"All descriptions up to date"`
- The ItemVectorizer pass (pure Kotlin, no AI) CAN be done in a WorkManager job; only
  the caption pass is constrained to foreground. Consider splitting: schedule
  vectorizer eagerly after every save, batch-caption only in Settings.

---

### §6 — DAO additions for Phase 1

**Modify:** `ClothingDao.kt`

```kotlin
// Items that have an image but no caption yet (batch enrichment input)
@Query("SELECT * FROM clothing_items WHERE image_path IS NOT NULL AND image_caption IS NULL")
suspend fun getItemsNeedingCaption(): List<ClothingItemEntity>

@Query("SELECT COUNT(*) FROM clothing_items WHERE image_path IS NOT NULL AND image_caption IS NULL")
fun getCaptionEligibleCount(): Flow<Int>

// Write-back after vectorizer / caption runs
@Query("UPDATE clothing_items SET semantic_description = :text, updated_at = :now WHERE id = :id")
suspend fun updateSemanticDescription(id: Long, text: String, now: Instant = Instant.now())

@Query("UPDATE clothing_items SET image_caption = :caption, updated_at = :now WHERE id = :id")
suspend fun updateImageCaption(id: Long, caption: String, now: Instant = Instant.now())
```

---

## Phase 2 — Vector Storage

Store embedding vectors alongside the Room database without disrupting the existing
schema. Target: float32 arrays of dimension 384 (matching the `all-MiniLM-L6-v2`
model proposed for Phase 3).

### Storage strategy: Room BLOB column (Phase 2A) → sqlite-vss (Phase 2B)

**Phase 2A (pragmatic, ships first):** Store vectors as `BLOB` in a new Room entity.
For a wardrobe of 300 items × 384 dimensions × 4 bytes = ~460 KB loaded into memory.
Cosine similarity over 300 vectors in Kotlin takes < 1 ms on a modern ARM CPU.
This is sufficient until the wardrobe exceeds ~2,000 items.

**Migration 4→5:** Add a new entity `ItemEmbeddingEntity`:

**New file:** `core/data/src/main/kotlin/com/closet/core/data/model/ItemEmbeddingEntity.kt`

```kotlin
@Entity(
    tableName = "item_embeddings",
    foreignKeys = [ForeignKey(
        entity = ClothingItemEntity::class,
        parentColumns = ["id"], childColumns = ["item_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("item_id", unique = true)]
)
data class ItemEmbeddingEntity(
    @PrimaryKey val itemId: Long,
    @ColumnInfo(name = "embedding_blob") val embeddingBlob: ByteArray, // float32 LE, 384 dims
    @ColumnInfo(name = "model_version") val modelVersion: String,      // e.g. "minilm-l6-v2-q8"
    @ColumnInfo(name = "embedded_at") val embeddedAt: Instant
)
```

Store float arrays as `ByteBuffer.allocate(dims * 4).apply { asFloatBuffer().put(floats) }.array()`.

**New DAO:** `EmbeddingDao.kt`

```kotlin
@Dao interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: ItemEmbeddingEntity)

    @Query("SELECT * FROM item_embeddings")
    suspend fun getAll(): List<ItemEmbeddingEntity>

    // Items that have a semantic_description but no embedding (or stale model version)
    @Query("""
        SELECT ci.id FROM clothing_items ci
        LEFT JOIN item_embeddings ie ON ci.id = ie.item_id
        WHERE ci.semantic_description IS NOT NULL
          AND (ie.item_id IS NULL OR ie.model_version != :modelVersion)
    """)
    suspend fun getItemIdsNeedingEmbedding(modelVersion: String): List<Long>
}
```

**Phase 2B (future, when warranted):** Migrate to `sqlite-vss` for native ANN
(approximate nearest-neighbour) search. Requires bundling the `sqlite-vss` native
`.so` for `arm64-v8a` (primary) and `armeabi-v7a` (fallback). Use a custom
`SupportSQLiteOpenHelper.Factory` that loads the extension before Room opens the DB.
This is a standalone migration step; the `ItemEmbeddingEntity` BLOB column can be
dropped and replaced with the VSS virtual table. Defer until Phase 2A proves
insufficient at scale.

---

## Phase 3 — Background Embedding (ONNX Runtime)

Silently embed `semantic_description` strings into vectors during charging + idle.
Never touch the main thread; never compete with foreground work.

### Dependencies (`gradle/libs.versions.toml` + `core/data/build.gradle.kts`)

```toml
onnxRuntime = "1.21.0"
onnx-runtime-android = { group = "com.microsoft.onnxruntime", name = "onnxruntime-android", version.ref = "onnxRuntime" }
```

No flavor split needed — ONNX Runtime is Apache-2 with no GMS dependency.

### Model: `snowflake-arctic-embed-xs` (quantized INT8)

- Produces 384-dimensional sentence embeddings; Apache 2.0 license, no GMS dependency
- Stronger retrieval quality than `all-MiniLM-L6-v2` at the same dimension / size point —
  meaningfully better Phase 4+ chat relevance with zero schema or pipeline changes
- INT8 quantized `.onnx` file: ~23 MB (ships in `core/data/src/main/assets/models/arctic-embed-xs-q8.onnx`)
- Tokenizer vocab file (≈ 250 KB) at `core/data/src/main/assets/models/vocab.txt`
- BERT WordPiece tokenizer (same as MiniLM) — `input_ids`, `attention_mask`, `token_type_ids`
- Mean-pooling of token embeddings → L2-normalise → 384-float vector

### `EmbeddingWorker`

**New file:** `core/data/src/main/kotlin/com/closet/core/data/worker/EmbeddingWorker.kt`

```kotlin
@HiltWorker
class EmbeddingWorker @AssistedInject constructor(…) : CoroutineWorker(…) {
    override suspend fun doWork(): Result
}
```

WorkManager constraints: `requiresCharging(true)`, `requiresDeviceIdle(true)`,
`setRequiredNetworkType(NetworkType.NOT_REQUIRED)`. Enqueue as unique periodic work
(`PeriodicWorkRequest`, 1-hour interval) so it re-runs after new items are added.

Per-item loop:
1. Tokenise `semanticDescription + " " + (imageCaption ?: "")` using the bundled vocab
2. Run `OrtSession.run(inputTensors)` — last_hidden_state output
3. Mean-pool across sequence length; L2-normalise
4. `EmbeddingDao.upsert(ItemEmbeddingEntity(itemId, floatsToBlob(vector), MODEL_VERSION, Instant.now()))`
5. `setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))`

Constants (same `BatchSegmentationWork` pattern):
**New file:** `core/data/src/main/kotlin/com/closet/core/data/worker/EmbeddingWork.kt`

```kotlin
object EmbeddingWork {
    const val NAME = "embedding_worker"
    const val MODEL_VERSION = "arctic-embed-xs-q8-v1"
    const val KEY_DONE = "done"
    const val KEY_TOTAL = "total"
    const val KEY_FAILED = "failed"
}
```

### Scheduler

Follow the existing `BatchSegmentationScheduler` interface pattern:
- `EmbeddingScheduler` interface in `core/data`
- `EmbeddingSchedulerImpl` (backed by WorkManager) in `core/data` — no feature module
  split needed (no GMS)
- Provided from `DataModule` alongside the existing WorkManager singleton

---

## Phase 4 — Retrieval & Chat UI

### §4.1 — Extract `EmbeddingEncoder` ✓ DONE

The ONNX tokenize → embed → L2-normalise pipeline currently lives as private methods
inside `EmbeddingWorker`. Before Phase 4 can do query-time embedding, extract that
logic into a shared singleton:

**New file:** `core/data/src/main/kotlin/com/closet/core/data/util/EmbeddingEncoder.kt`

```kotlin
@Singleton
class EmbeddingEncoder @Inject constructor(@ApplicationContext val context: Context) {
    /** `true` if both the ONNX model and vocabulary loaded successfully. */
    val isAvailable: Boolean

    /** Encodes [text] using the same ONNX model as EmbeddingWorker. < 50 ms on flagship. */
    suspend fun encode(text: String): Result<FloatArray>
}
```

Both `EmbeddingWorker` and the new `ChatRepository` delegate to this class.

---

### §4.2 — `EmbeddingIndex` singleton ✓ DONE

Load all stored embeddings into memory once at app start so query-time search is
instantaneous. At 300 items × 384 dims × 4 bytes ≈ 460 KB this is negligible.

**New file:** `core/data/src/main/kotlin/com/closet/core/data/util/EmbeddingIndex.kt`

```kotlin
@Singleton
class EmbeddingIndex @Inject constructor(private val embeddingDao: EmbeddingDao) {
    private var index: List<Pair<Long, FloatArray>> = emptyList()

    suspend fun load() {
        index = embeddingDao.getAll().map { entity ->
            entity.itemId to entity.toFloatArray()
        }
    }

    /** Returns item IDs ranked by cosine similarity to [queryVec], highest first. */
    fun search(queryVec: FloatArray, topK: Int = 5): List<Long> =
        index
            .map { (id, vec) -> id to dot(queryVec, vec) }  // L2-normalised → dot = cosine
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }
}
```

Call `EmbeddingIndex.load()` in `ClosetApp.onCreate()` (or lazily on first chat open).
Invalidate + reload after `EmbeddingWorker` completes a run.

---

### §4.3 — Multi-provider `ChatAiProvider` ✓ DONE

The chat feature is **multi-modal from day one**: Gemini Nano (on-device), Claude,
OpenAI-compatible, and Google Gemini (cloud). This mirrors the existing
`OutfitAiProvider` pattern in `features/recommendations`.

#### Add `Gemini` to the `AiProvider` enum

**Modify:** `core/data/src/main/kotlin/com/closet/core/data/model/Enums.kt`

```kotlin
enum class AiProvider(val label: String) {
    Nano("On-device (Nano)"),
    OpenAi("OpenAI-compatible"),
    Anthropic("Anthropic (Claude)"),
    Gemini("Google Gemini");          // ← new
    …
}
```

Also add `Gemini` API key storage to `AiPreferencesRepository` (alongside the
existing `getAnthropicApiKey` / `getOpenAiApiKey` pair).

#### `ChatAiProvider` interface

**New file:** `core/data/src/main/kotlin/com/closet/core/data/ai/ChatAiProvider.kt`

```kotlin
interface ChatAiProvider {
    /**
     * Sends [userMessage] to the model with [context] prepended as a system prompt.
     * Returns a [ChatResponse] describing what the UI should render, or
     * [Result.failure] on any error. Never throws.
     */
    suspend fun chat(userMessage: String, context: String): Result<ChatResponse>
}

/**
 * Structured response returned by every [ChatAiProvider] implementation.
 * The model is instructed to emit this schema so the UI can choose the
 * correct message type without post-hoc classification.
 */
sealed interface ChatResponse {
    /** Plain conversational answer — no items to surface. */
    data class Text(val text: String) : ChatResponse

    /**
     * Answer references specific wardrobe items (e.g. "haven't worn lately").
     * [itemIds] are the resolved DB IDs from the retrieval step — always non-empty.
     */
    data class WithItems(val text: String, val itemIds: List<Long>) : ChatResponse

    /**
     * Answer is an outfit suggestion.
     * [itemIds] are the 2-4 items that form the outfit — always non-empty.
     * [reason] is the AI's one-sentence rationale.
     */
    data class WithOutfit(val text: String, val itemIds: List<Long>, val reason: String) : ChatResponse
}
```

#### Provider implementations

| Class | File | Notes |
|-------|------|-------|
| `NanoChatProvider` | `features/chat/src/full/…` | Wraps Gemini Nano Prompt API; full flavor only; FOSS stub returns `UnsupportedOperationException` |
| `AnthropicChatProvider` | `features/chat/src/main/…` | Ktor POST to `api.anthropic.com/v1/messages`; reuses `AiHttpClient` + `AiPreferencesRepository`; same auth pattern as `AnthropicProvider` |
| `OpenAiChatProvider` | `features/chat/src/main/…` | Ktor POST to configurable base URL (compatible with OpenAI, Ollama, LM Studio, etc.) |
| `GeminiChatProvider` | `features/chat/src/main/…` | Ktor POST to `generativelanguage.googleapis.com`; needs Gemini API key in `AiPreferencesRepository` |

A `ChatAiProviderSelector` (injected into `ChatRepository`) reads
`AiPreferencesRepository.getSelectedProvider()` at call time and delegates to the
appropriate implementation — same pattern as `OutfitCoherenceScorer`.

#### Structured response schema

All providers are given the same system prompt instructing them to reply **only**
with a JSON object:

```json
{
  "type": "text" | "items" | "outfit",
  "text": "…",
  "item_ids": [3, 7, 12],
  "reason": "…"
}
```

- `item_ids` is always a list of DB item IDs from the context block (so the IDs are
  already constrained to real wardrobe items — the model cannot hallucinate unknown IDs).
- `reason` is only present when `type = "outfit"`.
- For providers that support native tool/function calling (Anthropic, OpenAI), prefer
  that over schema-in-prompt for reliability.

---

### §4.4 — `ChatRepository` (~1-2 days)

**New file:** `core/data/src/main/kotlin/com/closet/core/data/repository/ChatRepository.kt`

```kotlin
@Singleton
class ChatRepository @Inject constructor(
    private val encoder: EmbeddingEncoder,
    private val index: EmbeddingIndex,
    private val clothingDao: ClothingDao,
    private val providerSelector: ChatAiProviderSelector,
) {
    suspend fun query(userMessage: String): Result<ChatResponse>
}
```

Pipeline inside `query()`:

```text
1. encoder.encode(userMessage)                    → Result<FloatArray> (queryVec)
2. index.search(queryVec, topK = 5)               → List<Long> (item IDs)
3. clothingDao.getItemDetails(ids)                → List<ClothingItemDetail>
4. buildContextBlock(details)                     → String (formatted prose)
5. providerSelector.current().chat(userMessage, contextBlock)  → Result<ChatResponse>
```

`buildContextBlock` formats each item as a numbered entry (name, category, wear count,
colors, occasions, image caption if present) — identical to the prompt structure in the
original roadmap sketch above.

---

### §4.5 — `features/chat` module ✓ DONE

New Gradle module. Depends on `core/data` and `core/ui`. No flavor split needed at the
module level — `NanoChatProvider` handles its own `full`/`foss` source set split
(same pattern as `NanoProvider` in `features/recommendations`).

```text
features/chat/
  src/
    main/kotlin/com/closet/features/chat/
      ChatNavigation.kt          — @Serializable ChatDestination, navigateTo*, chatScreen ext
      ChatScreen.kt              — root Composable; collects ChatViewModel state
      ChatViewModel.kt           — StateFlow<ChatUiState>, sendMessage()
      ChatUiState.kt             — message list, loading, input
      model/ChatMessage.kt       — domain message model (User / Assistant subtypes)
    full/kotlin/…/chat/ai/NanoChatProvider.kt
    foss/kotlin/…/chat/ai/NanoChatProvider.kt   — stub
```

UI preview skeleton already lives at:
`features/recommendations/src/main/kotlin/…/recommendations/chat/ChatPreview.kt`
— move into `features/chat/` when the module is created.

---

### §4.6 — Chat UI design ✓ DONE

Three message content types, surfaced as inline attachments rather than navigation:

| Type | Trigger | Renders |
|------|---------|---------|
| `Text` | Factual Q&A ("how many times have I worn X?") | Plain assistant bubble |
| `WithItems` | Item-set queries ("what haven't I worn?") | Bubble + horizontal `LazyRow` of tappable item chips → item detail |
| `WithOutfit` | Outfit queries ("what should I wear tonight?") | Bubble + compact `OutfitMiniCard` |

**`OutfitMiniCard`** (inline in the chat thread, not a navigation target):
- 2×2 image grid — same visual language as `OutfitComboCard` in `features/recommendations`
- AI reason line with `AutoAwesome` icon prefix
- Two inline actions: **"Log it"** (outlined button) + **"Alternatives →"** (text button
  → deeplinks to `RecommendationDestination` with occasion/context pre-seeded)

**Welcome state:** centered wardrobe icon + 4 `AssistChip` suggestion prompts
("What should I wear tonight?", "What haven't I worn lately?", etc.) — replaced by
the message list on first send.

**Entry point:** 5th bottom-nav tab (alongside Closet, Outfits, Journal, Stats).
Chat is a distinct interaction paradigm, not a detail screen — a tab is appropriate.

**Top bar subtitle ("Powered by AI"):** Displays the active provider's `label` from
`AiProvider.label` (e.g. "Powered by Claude", "Powered by On-device (Nano)"). Sourced
from `AiPreferencesRepository.getSelectedProvider()` as a `StateFlow` in the ViewModel.
Simple to wire — one extra `collectAsStateWithLifecycle()` in the screen.

**Chat history:** In-memory for the session only (no DB table in v1). Clear on screen exit.

---

### §4.7 — Navigation wiring (~half day)

**Modify:** `app/src/main/kotlin/com/closet/navigation/ClosetNavGraph.kt`

1. Add `ChatDestination` to `topLevelRoutes` list (5th tab, chat-bubble icon from `core/ui`)
2. Call `chatScreen(navController)` inside the `NavHost` block
3. Pass `onNavigateToItem = { id → navController.navigateToDetail(id) }` so item chips
   in chat messages navigate to item detail
4. Pass `onNavigateToRecommendations = { navController.navigateToRecommendations() }` for
   the "Alternatives →" CTA on outfit cards

---

## Immediate Action Plan — Starting Phase 1 Today

### Files to modify

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Add `mlkitGenaiImageDescription` version + library alias |
| `features/wardrobe/build.gradle.kts` | Add `"fullImplementation"(libs.mlkit.genai.image.description)` |
| `core/data/src/…/ClothingDatabase.kt` | Bump version 3→4, register `MIGRATION_3_4` |
| `core/data/src/…/model/ClothingItemEntity.kt` | Add `semanticDescription` + `imageCaption` columns |
| `core/data/src/…/dao/ClothingDao.kt` | Add 4 queries from §6 above |

### Files to create

| File | Purpose |
|------|---------|
| `core/data/src/…/migrations/Migration_3_4.kt` | ALTER TABLE adds both columns; drops/recreates OOTD partial index |
| `core/data/src/…/util/ItemVectorizer.kt` | Pure-Kotlin paragraph serialiser for `ClothingItemDetail` |
| `features/wardrobe/src/full/…/repository/ImageCaptionRepository.kt` | ML Kit Image Description wrapper |
| `features/wardrobe/src/foss/…/repository/ImageCaptionRepository.kt` | FOSS stub |

### Wire-up order

1. Write `Migration_3_4.kt` first — run `./gradlew kspDebugKotlin` to regenerate
   the Room schema JSON and confirm the migration compiles clean.
2. Add `ItemVectorizer` and call it from `ClothingRepository.insertItemWithColors` /
   `updateItemWithColors` — this starts populating `semantic_description` for every
   new save immediately.
3. Add `ImageCaptionRepository` + wire into `ClothingFormViewModel.onImageSelected()`
   for At-Capture captioning.
4. Add the Batch Enrichment button to `SettingsScreen` last, once the per-item
   caption path is proven working end-to-end.

> Run migration tests (`./gradlew connectedAndroidTest`) before any PR that touches
> the schema. The existing `MigrationTest.kt` pattern in `core/data/src/androidTest`
> must be extended to cover Migration 3→4.

---

## Debug — Semantic Description Inspector (dev builds only)

A lightweight diagnostic card on the Item Detail screen that surfaces
`semantic_description` and `image_caption` directly, so you can inspect data
quality as Phase 1 iterates without shipping any user-facing RAG UI.

**Scope:** `BuildConfig.DEBUG` only. No Phase 2–4 dependencies. No new DB queries —
reads from the `ClothingItemDetail` already loaded by `ItemDetailViewModel`.

---

### Behaviour

- A subtly styled "Dev" card appears at the bottom of `ItemDetailScreen`, below all
  production content. It is invisible in release builds (guarded by
  `if (BuildConfig.DEBUG)`).
- Tapping the card opens a `ModalBottomSheet` displaying:
  - **Semantic description** — the structured prose from `ItemVectorizer`
    (`ClothingItemDetail.semanticDescription`), or a muted placeholder
    `"Not yet generated"` when null.
  - **Image caption** — the AI photo caption (`ClothingItemDetail.imageCaption`),
    or `"Not yet generated"` when null.
- A "Copy" icon button next to each field copies the raw text to the clipboard
  (`ClipboardManager`) so you can paste it into notes or compare revisions.
- The sheet has no edit controls — it is read-only.

---

### Implementation sketch

**`ItemDetailScreen.kt`**

```kotlin
// At the bottom of the screen content, inside a Column:
if (BuildConfig.DEBUG) {
    SemanticDebugCard(
        semanticDescription = uiState.item?.semanticDescription,
        imageCaption        = uiState.item?.imageCaption,
    )
}
```

**`SemanticDebugCard` composable** (can live in
`features/wardrobe/src/main/kotlin/.../ui/component/SemanticDebugCard.kt` or
inline in the screen file if you prefer to keep it local):

```kotlin
@Composable
fun SemanticDebugCard(
    semanticDescription: String?,
    imageCaption: String?,
) {
    var showSheet by remember { mutableStateOf(false) }

    // Trigger card — tappable row with a debug badge
    OutlinedCard(
        onClick = { showSheet = true },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Badge { Text("DEV") }
            Spacer(Modifier.width(8.dp))
            Text(
                "Semantic data",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Bottom sheet
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            SemanticDebugSheetContent(
                semanticDescription = semanticDescription,
                imageCaption        = imageCaption,
                onDismiss           = { showSheet = false },
            )
        }
    }
}

@Composable
private fun SemanticDebugSheetContent(
    semanticDescription: String?,
    imageCaption: String?,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Semantic data inspector",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        DebugTextField(
            label = "Semantic description",
            value = semanticDescription,
            onCopy = { clipboardManager.setText(AnnotatedString(semanticDescription ?: "")) },
        )
        Spacer(Modifier.height(12.dp))
        DebugTextField(
            label = "Image caption",
            value = imageCaption,
            onCopy = { clipboardManager.setText(AnnotatedString(imageCaption ?: "")) },
        )
    }
}

@Composable
private fun DebugTextField(label: String, value: String?, onCopy: () -> Unit) {
    val display = value ?: "Not yet generated"
    val muted   = value == null
    OutlinedTextField(
        value = display,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        textStyle = LocalTextStyle.current.copy(
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
        ),
        trailingIcon = {
            if (value != null) {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )
}
```

---

### What to expose

| Field | Source | Notes |
|-------|--------|-------|
| `semanticDescription` | `ClothingItemDetail.semanticDescription` | Populated by `ItemVectorizer` on every save |
| `imageCaption` | `ClothingItemDetail.imageCaption` | Populated at-capture or via Batch Enrichment |

Both fields are already loaded by `ItemDetailViewModel` as part of the normal
`ClothingItemDetail` fetch — no extra DAO query needed.

---

### Files to touch

| File | Change |
|------|--------|
| `features/wardrobe/src/main/kotlin/.../ui/ItemDetailScreen.kt` | Add `if (BuildConfig.DEBUG)` guard + `SemanticDebugCard` call |
| `features/wardrobe/src/main/kotlin/.../ui/component/SemanticDebugCard.kt` | New file — composable + sheet content (or inline in screen) |

> This section can be implemented at any point during Phase 1 iteration.
> Delete (or keep behind the debug flag permanently) once Phase 4 ships a real
> data-quality workflow.
