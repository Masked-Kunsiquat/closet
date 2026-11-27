# Changelog

All notable changes to hangr (Android) are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions correspond to `versionName` in `app/build.gradle.kts`.

---

## [Unreleased]

### Added
- **Re-detect colors**: re-measure an item's colours from its photo with a preview, or re-detect your whole wardrobe from Settings. Colours you set by hand, including clearing them, are never changed automatically.
- **Two new learned taste signals** (intensity match, and light/dark contrast within a hue). They have no effect until they are learned, and learned taste is currently paused (see Fixed).
- **Chat colour search understands basic colour words**: "blue" covers navy, royal, sky, denim and teal, and "grey" covers charcoal. Item results now include each colour's share of the item.

### Changed
- **Better automatic colour tags**: photos are analysed by how colours really look, with the background ignored, and each tag remembers its real shade and how much of the item it covers. Colours you set yourself are never overwritten.
- **Clothing-realistic base colours**: ten base colour swatches were re-picked so they look like clothes (Brown is brown, Navy is navy), and Navy is no longer treated as a neutral.
- **Outfit colour harmony uses each item's measured shade**: suggestions use the painter's colour wheel (complementary and analogous pairs get a small boost), neutrals sit out, and accent colours are weighted by their share of the item. Harmony is now a bonus only; there are no colour-clash penalties.

### Fixed
- Learned taste no longer inverts the rules (suggestions without shoes); weights retrain once.
- **Learned taste is paused**: suggestions use the built-in rules, which still favour outfits you wear together. On real wear histories, learned weights could make suggestions worse (for example, dropping shoes). The learner's checks were hardened (it is tested on outfits it hasn't seen, repeats aren't counted as extra evidence, and it needs 20 different outfits), but it stays off until a redesign proves itself on realistic histories.

---

## [0.9.0] — 2026-11-18

This release brings the complete **Recommendation Engine v2**: piece-by-piece assembly (stochastic beam search), learned pairwise compatibility from your actual wear history, formality and hue-based color harmony, and embedding backoff for new or rarely worn clothes. Under the hood, the **Kotlin Multiplatform migration is now complete across all feature modules and root navigation (Phases 4–6)**, time access is unified through an injectable `AppClock`, and the app targets Android 16 (API 36) with predictive back gestures enabled.

### Added
- **Recommendation Engine v2** (issues #94, #96):
  - **Learned pairwise compatibility & history backoff**: scores pairs from observed co-wear history. For new or rarely worn clothes, wear history is generalized using 384-dimensional ONNX item embeddings (nearest cosine neighbors) and wardrobe-wide subcategory co-wear patterns.
  - **Step-by-step assembly (stochastic beam search)**: builds outfits piece by piece around what goes together, replacing the old combinatorial search.
  - **Formality & hue harmony**: guards against mixing disparate formalities (e.g. sneakers with suits) and scores color harmony from measured hex values.
  - **Smarter regeneration & variety**: Regenerate explores new combinations without repeating, diversity is held inside the beam per core, and clothes you haven't worn in a while rotate in.
- **Injectable `AppClock`** (issue #112) — time access across data repositories, features, and background workers is abstracted through a common `AppClock` with CI enforcement against direct system clock reads.
- **Richer demo wardrobe** (issue #102) — seeded with realistic prices, brands, materials, patterns, favorites, and 120 wear logs over 180 days for dev and entry testing.

### Changed
- **The app's navigation and root screen are Kotlin Multiplatform** (KMP Phase 6): a shared `App()` in `:shared` with the bottom bar, navigation graph, and iOS framework export. The multiplatform migration is complete; Android behaviour is unchanged.
- **All feature modules are Kotlin Multiplatform** (KMP Phase 5): wardrobe, outfits, journal, recommendations, chat, settings, and stats screens are in `commonMain` using Compose resources and Material Symbols icons.
- **targetSdk 35 → 36** — predictive back (the back-to-home preview) is on by default on Android 14+.
- **`core/ui` is Kotlin Multiplatform / Compose Multiplatform** (KMP Phase 4): theme, shared components and preferences are in common code, and icons and strings use Compose resources. Dynamic color stays Android-only.

### Fixed
- **Fall clothes left out of suggestions in September–November**: the recommendation season filter looked for "Autumn", but the season is named "Fall".
- **Keyboard covering the screen** — the app now resizes above the keyboard
  (`adjustResize` + IME insets) instead of panning the whole window, so top
  bars stay visible and nothing slides under the status bar.
- **Error messages showing a literal `%s`** — placeholder values are filled in.
- Two deprecated Compose APIs replaced (`menuAnchor`, `LocalClipboardManager`).

---

## [0.8.0] — 2026-11-16

Chat can now look things up in your wardrobe: the model calls read-only tools
(search, wear history, laundry, …) instead of guessing from a handful of
retrieved items. Gemini Nano chat works again on supported phones. Under the
hood, this release also carries Kotlin Multiplatform migration Phases 2–3: GMS
code is extracted into its own module, and `core/data` is now multiplatform
(Android, JVM, iOS).

> ⚠️ **Uninstall any v0.7.x build before installing this one.** The database
> schema was reset to a version-1 baseline, and Room refuses to downgrade from
> version 6. `.hangr` backups made with v0.7.x or older can't be restored.

### Changed
- **Flavors extracted to `:platform:gms`** — every ML Kit / Play Services
  implementation now lives in one module behind capability interfaces
  (`ChatRouter`, `OnDeviceChatProvider`, `OnDeviceRecommendationProvider`,
  `SubjectSegmenter`, …), with no-op stubs in each feature. Only `app` declares
  flavors. The regex chat routing is shared (`PatternChatRouter`); only the
  English check and date extraction use ML Kit.
- **Room migrations squashed into a version-1 baseline** — the 1→6 chain and
  `MigrationTest` are gone. `FreshInstallSchemaTest` pins the schema, the seed
  data, the `one_ootd_per_day` index and the category triggers.
- **`core/data` is Kotlin Multiplatform**:
  - **Database:** Room KMP on the bundled SQLite driver, with
    kotlinx-datetime replacing `java.time`.
  - **Preferences:** DataStore KMP (1.3.0) for the AI and weather
    preferences, with API keys behind a `SecretStore` interface.
  - **Networking:** Ktor clients and the weather and model-discovery code are
    now in shared code.
  - **Logging:** Kermit replaces Timber in `core/data`.
  - **Platform interfaces:** `LocationProvider`, `EmbeddingEncoder`, and
    `workStatus` on the work schedulers. JVM and iOS bind honest stubs for
    now.
  - **Tests:** the Room tests run on the JVM (`:core:data:jvmTest`).
- **Coil 2.7 → 3** app-wide; compileSdk 37.
- **ML Kit GenAI Prompt API beta1 → beta5** — Nano replies can be longer than
  256 tokens, and chat replies and recommendations use structured output
  instead of parsing free text. Settings shows the on-device model's name.

### Added
- **Chat tool calling** on Gemini Nano (when the device supports function
  calling), Anthropic, Gemini and OpenAI-compatible providers. Eight read-only
  tools: `search_items`, `get_items`, `wear_history`, `items_not_worn_since`,
  `most_worn`, `outfits_worn_on`, `laundry_list`, `wardrobe_summary`. Replies
  only show items the tools actually returned. Providers without tool support
  (or an OpenAI-compatible server that rejects tools) keep the router + RAG
  path.
- **Color search reads descriptions** — `search_items` also matches a color
  named in an item's description or caption, so a mis-tagged item still turns
  up (tagged matches are listed first).
- **Multi-turn Nano chat** — earlier messages in the conversation are sent as
  context.
- **`tools/demo-backup`** — a JVM tool that builds a demo-wardrobe `.hangr`
  backup from a public product-image dataset, for testing and screenshots.
- **CI** — a `CI` workflow runs unit tests, lint and both debug builds on every
  PR. `ios-compile` also compiles the iOS test sources. Both are required to
  merge into `main`.
- **Dependabot** for GitHub Actions — one grouped PR a month.

### Fixed
- **Nano chat "Something went wrong"** — beta1 rejected the requested output
  length, and the "model ready" state set in Settings was never saved. Both
  fixed.
- **Nano availability messages** — chat now says whether the model is
  downloading, unsupported on this device, or failed, instead of a generic
  "not ready".
- **Nano confusing list positions with item IDs** in its replies.
- **API keys in debug logs** — the HTTP log redaction never matched Ktor's
  `-> Authorization:` lines and didn't cover `key=` in URLs, so keys reached
  logcat in debug builds. Google Weather errors (whose messages include the
  `?key=` URL) are now sanitized before logging.
- **Weather with location permission revoked** — falls back to the last known
  coordinates instead of failing with an unexpected error.
- **Snackbar strings after a configuration change** — read through
  `LocalResources`, so they follow the current locale and configuration.
- **Logging during Koin start** — the release build no longer has a log writer
  while Koin starts.

### Security
- GitHub Actions are pinned by commit SHA and bumped to current major versions
  (Node 24). Every workflow's token is read-only by default.

---

## [0.7.2] — 2026-11-14

Hilt replaced with Koin, Kotlin 2.4, and an iOS compile check in CI: the
groundwork for the Kotlin Multiplatform migration.

### Changed
- **Hilt → Koin** — one Koin module per Gradle module, flavor bindings through
  `flavorModule`, and a whole-graph `verify()` test (`AppKoinGraphTest`) in both
  flavors. AI calls use a dedicated `AiHttpClient` type (120 s timeouts).
- **Kotlin 2.3.20 → 2.4.21, KSP 2.3.2 → 2.3.14.**

### Added
- **`ios-compile` CI workflow** — compiles every Kotlin Multiplatform module for
  iOS on a macOS runner.

### Fixed
- **`OutfitRepository.updateOutfit`** — a thrown `AppError` now becomes
  `DataResult.Error` instead of escaping the repository.
- **App start with a broken key store** — `AiPreferencesRepository` is resolved
  eagerly, so a key store failure stops launch instead of surfacing later.
- **Stale tests** — `StatsViewModelTest`, `ClothingFormViewModelTest` and
  `StatsDaoTest` were updated to the current APIs.

---

## [0.7.1] — 2026-04-05

Test coverage for Phases 1–3 of the chat enhancement roadmap, plus hardening
of the `ChatRouter` pattern-matching logic.

### Added
- **`ChatViewModelTest`** — 14 unit tests covering message flow, rolling
  conversation history (6-turn cap, stat responses excluded), `clearChat()`,
  and `toAssistantMessage()` mapping for all response types. Uses
  `UnconfinedTestDispatcher` + a `backgroundScope` collector to keep
  `WhileSubscribed` upstream active; single-send assertions use
  `advanceUntilIdle()` + `uiState.value` to avoid Turbine races with
  intermediate `Thinking` emissions.
- **`RegexDateParserTest`** — 16 unit tests for `regexParseDate()`: ISO
  dates, all 12 full and abbreviated month names, ordinal suffixes, explicit
  year, case-insensitivity, invalid calendar days (`April 31` → `null`), and
  unhandled relative expressions.
- **`ChatRouterPatternTest`** (full flavor, `src/testFull/`) — 15 unit tests
  for `ChatRouterPatterns`: `ITEM_NAME_PATTERN` (including `wear count for`
  and present-tense `wear` variants), `DAYS_PATTERN` (including `weekdays`
  word-boundary guard), `WORE_ON_INTERROGATIVE_PATTERN`, and pattern-priority
  ordering (`matchesNeverWorn` before `matchesNotWornSince`).
- **`ChatRouterFossTest`** (FOSS flavor, `src/testFoss/`) — smoke test
  confirming the FOSS stub always returns `Unrouted`.
- **`ChatRouterPatterns`** internal object — regex constants and pure-logic
  matchers (`matchItemName`, `parseDays`, `matchesWoreOnInterrogative`,
  `matchesNeverWorn`, `matchesNotWornSince`) extracted from `ChatRouter` so
  unit tests call the exact production logic without instantiating the
  GMS-bound `ChatRouter` constructor.

### Fixed
- **`ITEM_NAME_PATTERN`** — extended to match `wear count for …` and
  present-tense `"how many times did I wear …"` queries; `matchItemName()`
  previously returned `null` for these, causing the wear-count route to fall
  through to `Unrouted`.
- **`DAYS_PATTERN`** — trailing `\b` added; `"5 weekdays"` no longer
  incorrectly triggers the not-worn-since route.
- **`matchesNeverWorn`** — broad `("never" in lower && "wear" in lower)` guard
  replaced with `NEVER_WORN_PATTERN` regex requiring a past-tense form
  (`worn`, `wore`, `tried on`); advice-style queries such as "never wear white
  after labor day" no longer route to `routeNeverWorn()`.

---

## [0.7.0] — 2026-04-04

Phase 3 of the chat enhancement roadmap: action intents. The model can now embed
a suggested next action in its response, surfaced as a chip below the message bubble.
Also includes bug fixes to the Phase 2 router and DAO query correctness.

### Added
- **`ChatAction`** sealed interface (`LogOutfit(itemIds)`, `OpenItem(itemId)`,
  `OpenRecommendations`) in `core/data/ai/`. Actions are optional — the model only
  emits one when intent is unambiguous.
- **Action field on `ChatResponse`** — `WithItems` and `WithOutfit` both gain an
  optional `action: ChatAction?` field; `WithStat` and `Text` are unaffected.
- **`ActionChipRow`** composable in `ChatScreen` — a single `AssistChip` rendered
  below the message bubble when an action is present. `LogOutfit` routes through
  the existing log confirmation flow (`onNavigateToLog`), never logging directly.
  `OpenItem` opens the item detail screen; `OpenRecommendations` opens the
  recommendations screen.
- **`ChatResponseParser` action parsing** — optional `"action"` object is parsed
  from provider JSON with silent failure: a missing, malformed, or type-mismatched
  action block returns `null` and never fails the parent response. `log_outfit` is
  only accepted on `"outfit"` responses with 2–4 item IDs.
- **System prompt updated** — `ChatPromptPrefix.SYSTEM_PROMPT` documents the
  `action` field with one example per action type and rules instructing the model
  to omit the field rather than guess.

### Fixed
- **`LogDao` historical snapshot** — `LOGS_BY_DATE_QUERY` and `getMostRecentLog`
  now join `outfit_log_items` (snapshot) instead of live `outfits`/`outfit_items`;
  outfit renames and composition changes no longer retroactively rewrite historical
  log display.
- **`ChatRouter` language-ID fail-closed** — `isEnglish()` now returns `false` on
  ML Kit failure instead of `true`; unresolvable language checks fall through to
  RAG rather than risking a misrouted stat answer on non-English input.
- **`ChatRouter` FOSS flavor split** — `ChatRouter` moved to flavor source sets;
  full flavor retains ML Kit language ID and pattern routing; FOSS stub always
  returns `Unrouted` so `kotlinx-coroutines-play-services` and `mlkit.language.id`
  (both GMS-transitive) are excluded from the FOSS build.
- **`matchesWornOn` false positives** — bare `"wore on"` substring replaced with
  `WORE_ON_INTERROGATIVE_PATTERN` (`\bwhat\b.{0,15}\bi\b\s*\bwore on\b`);
  incidental uses like "top I wore on Tuesday" no longer trigger the worn-on route.
- **`getItemsNotWornSince` boundary** — DAO predicate changed from `>=` to `>`
  so items last worn exactly N days ago correctly appear in "not worn in N days"
  results.

---

## [0.6.0] — 2026-04-04

Phase 2 of the chat enhancement roadmap: intent routing for stat queries.
Common data questions are answered directly from DAO queries without
touching the RAG pipeline — faster, zero token cost, fully offline.

### Added
- **`ChatRouter`** — pre-LLM intent router in `features/chat/`. Intercepts
  three query patterns before the encoder or AI provider is invoked:
  1. "How many times have I worn [item]?" → `ClothingDao.getWearCountByName()`
  2. "What haven't I worn in [N] days?" → `ClothingDao.getItemsNotWornSince()`
  3. "What did I wear on [date]?" → `LogDao.getLogsForDateOnce()`
  Unrecognised or ambiguous patterns return `Unrouted` and fall through to RAG.
- **ML Kit Language ID router guard** — `com.google.mlkit:language-id` (bundled,
  ~900 KB, no Play Services required) gates the router on English-only input.
  Non-English queries and low-confidence detections (< 0.7) skip pattern matching
  and fall through to RAG. Works on both `full` and `foss` flavors.
- **`ChatResponse.WithStat`** — new response subtype for routed data answers;
  carries `label` (e.g. "Wear count"), `value` (e.g. "5 times"), and optional
  `itemIds` for the item rail.
- **`StatBubble`** — new composable in `ChatScreen`; renders a compact card
  with a label/value row and an optional `ItemChip` rail for referenced items.
- **New DAO queries** — `ClothingDao.getWearCountByName()` (fuzzy `LIKE`,
  shortest-name-first), `ClothingDao.getItemsNotWornSince()` (`NOT EXISTS`
  subquery via `outfit_log_items`), `LogDao.getLogsForDateOnce()` (one-shot
  `suspend` variant of the existing `Flow`-based `getLogsByDate`).
- **Routed responses skip history** — `WithStat` answers are data facts, not
  conversational turns; they don't pollute the rolling history so follow-up
  questions continue to reach RAG naturally.
- **ML Kit Language ID router guard** — `com.google.mlkit:language-id` (bundled,
  ~900 KB, no Play Services required) gates the router on English-only input.
  Non-English queries and low-confidence detections (< 0.7) skip pattern matching
  and fall through to RAG. Works on both `full` and `foss` flavors.
- **ML Kit Entity Extraction date parser** (`full` flavor) — `ChatDateParser` uses
  the English entity extraction model (~5.6 MB, downloaded via Play Services on
  first use) to handle natural date expressions ("yesterday", "last Monday",
  "3 days ago") that the regex parser cannot handle; FOSS flavor uses regex only.
  `setReferenceTime` is passed on every call so relative expressions resolve
  correctly against the current moment.
- **Extended router — 8 patterns total** — five additional intents beyond the
  original three:
  - "What have I never worn?" → `getItemsNeverWorn()`
  - "What's in my laundry?" / "What needs washing?" → `getItemsNeedingWash()`
  - "What did I wear last?" → `getMostRecentLog()`
  - "How many items do I own?" → `getItemCount()`
  - "What's my most worn item?" → `getMostWornItem()`

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

[Unreleased]: https://github.com/Masked-Kunsiquat/closet/compare/v0.8.0...HEAD
[0.8.0]: https://github.com/Masked-Kunsiquat/closet/compare/v0.7.2...v0.8.0
[0.7.2]: https://github.com/Masked-Kunsiquat/closet/compare/v0.7.1...v0.7.2
[0.7.1]: https://github.com/Masked-Kunsiquat/closet/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/Masked-Kunsiquat/closet/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/Masked-Kunsiquat/closet/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/Masked-Kunsiquat/closet/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/Masked-Kunsiquat/closet/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/Masked-Kunsiquat/closet/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/Masked-Kunsiquat/closet/compare/v0.1.2...v0.2.0
[0.1.2]: https://github.com/Masked-Kunsiquat/closet/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/Masked-Kunsiquat/closet/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/Masked-Kunsiquat/closet/releases/tag/v0.1.0
