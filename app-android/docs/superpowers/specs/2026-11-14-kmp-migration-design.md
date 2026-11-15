# hangr → Kotlin Multiplatform + Compose Multiplatform — Design

**Date:** 2026-11-14
**Status:** Draft, awaiting approval
**Starting point:** `main` @ `866f5be` (v0.7.1)

## Intent

Restructure hangr from an Android-only Jetpack Compose app into Kotlin Multiplatform (KMP) with
Compose Multiplatform (CMP), so that **iOS** and **desktop (JVM)** can be added later without
rebuilding the app from the ground up. The owner works in KMP elsewhere (MediaTracker), and this
brings hangr in line with that.

**Android first.** Android is the only platform being shipped. iOS and desktop apps aren't built
until the owner is satisfied with Android. This migration makes the code *ready* for them: shared
code that compiles for iOS, and platform seams defined as interfaces. Shipping on those platforms
is a later project (see "Deferred: other platforms").

The app stays local-first. On iOS and desktop the on-device Google features (Gemini Nano, ML Kit)
are unavailable. Those platforms get AI through the existing cloud providers, and through the
OpenAI-compatible provider pointed at a self-hosted server (Ollama, LM Studio, etc.) for users
who have the hardware. Chat retrieval stays on-device on every platform, using the bundled ONNX
embedding model.

## Goals

1. All business logic, data access, ViewModels and Compose UI live in `commonMain` wherever the
   underlying APIs allow it.
2. Android keeps full feature parity throughout, in both the `full` and `foss` flavors.
3. Every KMP module declares `android`, `jvm` and `ios` targets. `commonMain` compiles for all
   of them, and CI enforces this, so adding an iOS or desktop app later is shell + platform work,
   not a rewrite.
4. Every platform-specific capability sits behind a common interface. Android gets the real
   implementation. iOS and JVM get compile-only stubs (`TODO()` or no-op) until those platforms
   are picked up.
5. Hilt is replaced by Koin across the whole codebase.

## Non-goals

- Shipping, running or testing iOS or desktop apps. No `iosApp` or `desktopApp` modules, no real
  iOS/JVM platform implementations, and no ONNX Runtime bundling on those platforms. iOS and JVM
  are compiled, not run.
- A2UI. Revisit once A2UI is past alpha and this migration is stable.
- On-device generative AI on iOS or desktop (Apple Foundation Models, MLX, local captioning or
  segmentation). This can be added later behind the same provider interfaces.
- Changing the embedding model. See "Follow-ups".
- Enabling chat routing (DB-answered questions) on foss, iOS or desktop. See "Follow-ups".
- Restoring backups across platforms. `.hangr` backup/restore stays Android-only in this
  migration. The owner has no existing library to carry over.
- Moving data between devices or syncing. Cloud backup remains deferred per CLAUDE.md.
- Publishing to the App Store or packaging installers. This design covers making the apps buildable
  and runnable, not store submission.
- Any change to the schema's *shape*: tables, columns and indexes stay exactly as in v6. The one
  exception is squashing the migration history (see "Migration consolidation"), which changes the
  version number and nothing else.
- New features or new screens. Features with no equivalent on a platform are hidden, not
  reimplemented (see "Platform gaps").

## Anti-goals (failure even if it "works")

- Android regressions in either flavor, including a foss build that pulls in a GMS dependency.
- The schema's shape changing during the move to Room KMP. The fresh-install schema test that
  replaces `MigrationTest` (see "Migration consolidation") must stay green. There is no user
  library to preserve.
- A long-lived half-migrated `main` that doesn't build. Every phase lands green.
- Shared code full of `expect/actual` for things that should be plain interfaces bound by DI.
- JVM-only APIs (`java.*`, Android types) leaking into `commonMain`. The iOS CI job exists to catch
  this, because the Android and JVM targets alone won't.

## Current state (as of v0.7.1)

| Module | Kotlin lines | Android/Hilt-coupled files | Tests |
|---|---|---|---|
| `app` | 1.3k | 10 | 0 |
| `core/data` | 9.5k | 31 | MigrationTest (instrumented), StatsDaoTest, ChatResponseParserTest |
| `core/ui` | 3.3k | 2 | 0 |
| `features/wardrobe` | 7.0k | 20 (flavored) | ClothingFormViewModelTest |
| `features/recommendations` | 4.8k | 11 (flavored) | OutfitRecommendationEngineTest |
| `features/chat` | 3.1k | 13 (flavored) | ChatViewModel, RegexDateParser, ChatRouterFoss, ChatRouterPattern tests |
| `features/outfits` | 3.3k | 9 | 0 |
| `features/settings` | 2.4k | 4 | 0 |
| `features/stats` | 2.2k | 3 | SegmentedBar, StatPeriod, StatsViewModel tests |

Stack: AGP 9.1.0, Kotlin 2.3.20, KSP, Hilt 2.59.2, Compose BOM 2026.03.00, Room 2.8.4, Navigation
Compose 2.9.7 (type-safe), Ktor 3.1.3, DataStore 1.1.4, Coil 2.7.0, Vico 3.0.3, Timber, WorkManager,
ONNX Runtime 1.21 (Android), security-crypto, androidx.palette, ML Kit (GenAI Prompt, Image
Description, Subject Segmentation, Entity Extraction, Language ID).

### Android-only dependencies and their fate

| Dependency / API | Used by | Plan |
|---|---|---|
| Hilt | everything | → Koin (Phase 1) |
| Product flavors in library modules | chat, recommendations, wardrobe | → separate Android modules (Phase 2) |
| Timber | ~40 files | → Kermit |
| Coil 2 | ui, features | → Coil 3 (KMP) |
| Room 2.8 (Android) | core/data | → Room KMP + bundled SQLite driver |
| DataStore Preferences | core/data, core/ui | → DataStore KMP (`okio` path per platform) |
| Ktor (OkHttp engine) | AI + weather clients | common; engine per platform (OkHttp on Android/JVM, Darwin on iOS) |
| Navigation Compose 2.9 | app, features | → JetBrains `navigation-compose` multiplatform, same `@Serializable` routes |
| androidx ViewModel + `hiltViewModel()` | features | → KMP `androidx.lifecycle` ViewModel + `koinViewModel()` |
| Vico 3 | stats | → Vico's multiplatform artifact; builder verifies it covers the column charts used |
| security-crypto (`EncryptedKeyStore`) | AI keys | interface `SecretStore`; Android = existing impl, iOS = Keychain, JVM = OS keyring or encrypted file (builder's call) |
| WorkManager (embedding, compression, batch segmentation) | core/data, settings, wardrobe | stays Android; `EmbeddingScheduler`-style interfaces in common; iOS/JVM run work in-process with coroutines |
| ONNX Runtime (`EmbeddingEncoder`, `arctic-embed-xs-q8`, `vocab.txt`) | chat RAG | interface in common. Android = existing; JVM = `onnxruntime` Java artifact (native libs for Windows first, macOS/Linux best-effort); iOS = ONNX Runtime iOS package, **bundled**. The 23 MB model + vocab move from Android `assets/` to a location all targets can read (Compose resources or per-platform bundling, builder's call). `WordPieceTokenizer` is pure Kotlin and moves to common. |
| `android.graphics`, palette (`BitmapUtils`, `PaletteUtil`, `ColorMatcher`) | core/data, wardrobe | interface `ImageProcessor` in common; Android = existing; iOS/JVM = Skia via Compose `ImageBitmap` where possible, otherwise hidden (see "Platform gaps") |
| `LocationProvider` | weather | interface in common; Android = existing; iOS = CoreLocation; JVM = none |
| Image pickers / camera / `ActivityResult` | wardrobe, settings, app | per-platform launchers behind a small common interface |
| ML Kit / Gemini Nano | chat, recs, wardrobe (`full` only) | stays Android `full`; common code sees capability flags |
| Backup/restore (`BackupForegroundService`, raw DB copy) | app | stays Android-only (non-goal) |

## Approaches considered

1. **Convert in place, module by module (chosen).** Add KMP targets to the existing Gradle modules,
   move code into `commonMain` incrementally, and keep Android building at every step. This reuses
   the existing module boundaries (`core:data` → `core:ui` → `features/*` → `app`) and test suites.
   The trade-off is a mixed state during the migration.
2. **Fresh KMP project, port feature by feature.** This starts from a clean template. It was
   rejected because it means two apps in parallel, the tests would have to be carried over by hand,
   and the `full`/`foss` setup would have to be rebuilt from scratch.
3. **Share only logic and data, with native UI per platform.** This is roughly what MediaTracker
   does: a KMP `shared` module with Android-only Compose UI. It was rejected for hangr because iOS
   is a target, and native per-platform UI would mean writing every screen again in SwiftUI.

Decisions made with the owner: approach 1, Koin for dependency injection, iOS plus desktop as the
new targets, and bundling ONNX Runtime so chat works on iOS.

## Conventions borrowed from MediaTracker

MediaTracker (`C:\Users\blain\AndroidStudioProjects\MediaTracker`) is the owner's in-progress KMP
project. It targets Android + JVM, has no iOS target yet, and uses manual DI (`AppContainer`) with
Android-only Compose UI. hangr copies its data-layer and testing conventions, but not its UI or DI
setup:

- **Room tests run on the JVM.** Room-touching tests (DAO tests, `MigrationTest`) live in
  `jvmTest` against the bundled SQLite driver, using Room's `MigrationTestHelper` from
  `androidx.room:room-testing`. `MigrationTest` and `StatsDaoTest` move there from `androidTest`,
  so migration checks no longer need a device. Pure-Kotlin tests go in `commonTest`.
- **The verification command names `:jvmTest` explicitly.** `./gradlew test` doesn't run the
  `jvmTest` tasks. Each phase's verification command lists them, and CLAUDE.md documents this.
- **A cancellation-safe catch helper.** Use one shared helper (MediaTracker's
  `catchingNonCancellation` pattern) instead of hand-written `CancellationException` rethrows.
  This fits hangr's existing "always rethrow `CancellationException`" rule. Adopting it across the
  codebase is the builder's call; it is required in new or moved code.
- **Gradle plugin choice differs.** MediaTracker keeps `com.android.library` + KMP via the
  `android.builtInKotlin=false` / `android.newDsl=false` opt-outs. hangr uses the new
  `com.android.kotlin.multiplatform.library` plugin instead, because those opt-outs are deprecated
  under AGP 9. That choice is what forces the flavor extraction in Phase 2.

## Design

### Target module layout

```
app-android/                          (repo dir name kept; rename is optional, later)
├── app/                    Android application. Keeps full/foss flavors. Koin start-up,
│                           MainActivity, Android-only launchers, backup/restore. Flavor source
│                           sets supply the Koin module that binds GMS or stub implementations.
├── iosApp/                 Xcode project; hosts the shared ComposeUIViewController.
├── desktopApp/             JVM application (Compose Desktop `main()`).
├── shared/                 KMP umbrella module (named after MediaTracker's): exports the iOS
│                           framework and the root App() composable + NavHost. Absorbs the
│                           platform-neutral parts of today's `app` module.
├── core/data/              KMP: commonMain + androidMain + iosMain + jvmMain
├── core/ui/                KMP + CMP: theme, shared components, Compose resources
├── features/*/             KMP + CMP, **no flavors**
└── platform/
    ├── gms/                Android library (com.android.library), GMS-only implementations:
    │                       Nano chat/recs providers, ML Kit ChatRouter + language ID + date
    │                       parser, image captioning, subject segmentation. Depended on via
    │                       `fullImplementation` from `app` only.
    └── (foss stubs)        Live in each feature's `src/main` (later `commonMain`) with a
                            `<feature>NoOpModule`; `app/src/foss` includes them.
```

Whether `platform/gms` is one module or one per feature is the builder's call.

### How flavors survive

Today Hilt picks bindings from `src/full` or `src/foss` at compile time. After the migration:

- Each flavored capability is expressed as an interface in the owning feature's `commonMain`
  (e.g. `ChatRouter`, `ChatDateParser`, `OnDeviceChatProvider`, `ImageCaptioner`,
  `SubjectSegmenter`, `OnDeviceRecommendationProvider`), plus a capability flag the UI can read
  to hide unavailable features.
- `platform/gms` implements them for Android `full`.
- `app/src/foss/` and `app/src/full/` each define a Koin module, `flavorModule`, which `app` adds
  at `startKoin`. No runtime conditionals.
- iOS and desktop bind the same no-op stubs that foss uses. A shared `noOpCapabilitiesModule` in
  `commonMain` is fine, and foss can reuse it.

**Chat routing: Phase 2 is a pure refactor.** How routing works today:

- `full`'s `ChatRouter` runs regex patterns (`ChatRouterPatterns`). It only routes messages that
  ML Kit Language ID classifies as English with confidence ≥ 0.7, and it uses `ChatDateParser`,
  which is ML Kit entity extraction.
- `foss`'s `ChatRouter` is a no-op that always returns `Unrouted`. `ChatRouterFossTest` asserts
  this. `foss`'s `ChatDateParser` uses `regexParseDate`.

After Phase 2:

- **Shared interfaces.** `ChatRouter`, `ChatDateParser` and a new `LanguageDetector` are interfaces
  in `features/chat` `commonMain`.
- **Pure-Kotlin code moves to `commonMain`.** This covers `ChatRouterPatterns`, `RegexDateParser`,
  and the regex routing logic, which is extracted from `full`'s `ChatRouter` so it no longer
  depends on ML Kit types. `ChatRouterPatternTest` moves to `commonTest`.
- **`full` keeps its current behavior.** `platform/gms` supplies the ML Kit `LanguageDetector`, the
  ML Kit `ChatDateParser`, and wires them into the shared regex router.
- **foss, iOS and desktop stay unrouted.** They bind the no-op router, so behavior is unchanged
  for foss, and `ChatRouterFossTest` moves to `commonTest` unchanged.
- **Enabling routing on those platforms is a follow-up**, not part of this migration.

### Dependency injection (Koin)

- Plain Koin DSL modules, not koin-annotations.
- One Koin module per Gradle module (`dataModule`, `uiModule`, `chatModule`, …) plus platform
  modules (`androidPlatformModule`, `iosPlatformModule`, `jvmPlatformModule`) for Context-bound and
  OS-bound implementations.
- Singletons stay singletons (`single {}`). ViewModels use `viewModel {}` / `koinViewModel()`.
- WorkManager workers: Koin's `workmanager` integration (`KoinWorkerFactory`) replaces `HiltWorker`.
- **Keep the existing constraint:** `AiPreferencesRepository` is constructed only in the data
  module's provider (now a Koin `single`), never by constructor injection.

### Database

- Room KMP with the bundled SQLite driver on all platforms.
- All existing conventions carry over: `PRAGMA foreign_keys = ON`, `DatabaseSeeder` on create,
  the `one_ootd_per_day` partial index created in `onOpen` and dropped at the top of every
  `migrate()`, and schema JSON exported to `core/data/schemas/`.
- `MigrationTest` and `StatsDaoTest` move to `core/data` `jvmTest` (see "Conventions borrowed from
  MediaTracker") and must pass at the end of every phase from Phase 3 on. Before Phase 3 they stay
  instrumented tests.
- DAO functions are already all `suspend` or `Flow`, which Room KMP requires. No change needed.
- Database file location: `Context.getDatabasePath` on Android, the app's Documents/Application
  Support directory on iOS, and the user's app-data directory on desktop.

### Migration consolidation

hangr has no users, so the migration history is squashed rather than ported. This was decided with
the owner on 2026-11-14.

- It happens as the **first step of Phase 3**, before Room moves to KMP, so the five migrations are
  never ported or re-tested under the bundled driver.
- `@Database(version = 1)`, with the current v6 shape as the baseline schema.
- Delete `Migration1To2`…`Migration5To6`, `MigrationHelpers.kt` (if nothing else uses it) and the
  `addMigrations(...)` call. Delete `core/data/schemas/` 1–6 and regenerate a single `1.json`.
- `MigrationTest` is replaced by a fresh-install schema test (in `jvmTest`). It checks:
  - every table, column and index matches the regenerated `1.json`;
  - `PRAGMA foreign_keys` is on;
  - the seeded lookup rows are present;
  - the `one_ootd_per_day` partial index exists after `onOpen`.
- The `onOpen` partial-index rule stays. The "drop `one_ootd_per_day` at the top of every
  `migrate()`" rule goes away until a real migration exists again. Update
  `core/data/.../migrations/AGENTS.md` and CLAUDE.md to match.
- **Version 1 is the first released schema.** Every schema change after this point gets a normal
  migration again.
- **Side effects, all accepted because there are no users:**
  - A device with a v6 dev install must uninstall first, because Room refuses a downgrade from 6 to 1.
  - `.hangr` backups made before the reset are rejected by `RestoreRepository`'s newer-schema check.
  - The six instrumented-test failures on `main`, in `MigrationTest` and `StatsDaoTest`, are partly
    resolved by this: the two `MigrationTest` ones disappear with the migrations. The four
    `StatsDaoTest` failures are unrelated and still need their own fix.

### Images and files

- `StorageRepository` keeps the relative-path rule. The common interface exposes "root directory"
  and "copy into storage". Each platform supplies its root (Android `filesDir`, iOS Documents,
  desktop app-data dir). Path reconstruction stays in the repository/ViewModel layer.
- Coil 3 loads from the reconstructed absolute path on all platforms.

### Embeddings

- `EmbeddingEncoder` becomes a common interface with one implementation per platform, all running
  `arctic-embed-xs-q8` through ONNX Runtime. `WordPieceTokenizer`, `EmbeddingIndex` and cosine
  search are pure Kotlin in `commonMain`.
- The encoder exposes a `modelVersion` that is written to the existing `model_version` column, so a
  later model swap only needs to re-embed items whose stored version doesn't match. No schema
  change is needed for that.

### UI

- Compose Multiplatform version chosen to match the Compose BOM 2026.03.00 feature level or newer.
- Material 3 theme, accent palettes and dark-first palette move to `core/ui` `commonMain`. Dynamic
  color (Material You) stays an Android-only option. Other platforms use the selected accent.
- Android `R.drawable` / `R.string` references move to Compose Multiplatform resources
  (`Res.drawable.*`, `Res.string.*`).
- The destructive-action `AlertDialog` pattern, `DataResult`, `AppError`, and
  `CancellationException` rethrow rules are unchanged.

### Platform gaps

A feature with no implementation on a platform is hidden behind a capability flag. It never gets
a new screen or a new input as part of this migration.

- Desktop has no location, so weather is hidden and recommendations run without weather input.
- Desktop and iOS have no Gemini Nano, ML Kit captioning or subject segmentation, so those are
  hidden, the same way foss handles them today.
- Camera capture: available where the platform has a camera API that's straightforward to wire;
  otherwise only "pick from files" is shown.
- Desktop targets Windows first; macOS and Linux are best-effort.

### Logging

Timber → Kermit, with debug-only logging (`BuildConfig.DEBUG` on Android; an equivalent
build-time flag on iOS and desktop).

## Build and CI

- **The owner has no Mac.** Local development and verification cover Android and desktop (JVM)
  only.
- **iOS is compiled in CI.** A GitHub Actions workflow runs on a `macos` runner, which is free for
  this public repo. It is added in Phase 0 and compiles the iOS targets of every KMP module on
  every PR from Phase 3 on. It catches non-common APIs leaking into `commonMain`. A failed iOS
  compile blocks the merge.
- **The iOS job is compile-only.** Nothing iOS is run or tested in this migration.
- **Existing CI is unchanged.** The current `release.yml` (Ubuntu) keeps building the Android
  release.

## Phases

Each phase is one or more PRs. **Exit criteria for every phase:** `assembleFullDebug` and
`assembleFossDebug` succeed, unit tests pass (including every module's `jvmTest` once it exists),
`MigrationTest` passes, the foss APK has no GMS classes, and a manual smoke test of the app on an
Android device or emulator passes. From Phase 3 on, the macOS CI job also has to compile iOS.

0. **Spike (throwaway) + CI.** On a branch, prove the toolchain: AGP 9.1 + KMP Android library
   plugin + Room KMP + KSP + Koin + CMP in `core/data`, with Android, JVM and iOS targets compiling.
   iOS is compiled through the macOS CI job. Record the version set that works. The CI workflow is
   kept and merged; the spike code isn't merged as-is.
1. **Hilt → Koin, Android only.** No KMP yet. The behavior is identical. This isolates the riskiest
   mechanical change so failures aren't tangled with KMP problems.
2. **Extract flavors.** Introduce capability interfaces. Move `src/full` code to `platform/gms`, and
   `src/foss` stubs to each feature's `src/main` (bound by `app/src/foss`'s `flavorModule`). Feature modules become single-variant. This is a pure
   refactor with no behavior change on either flavor (see "Chat routing").
3. **`core/data` → KMP.** First, the migration consolidation (its own PR). Then Room KMP, DataStore KMP, Ktor engines, Kermit, and platform interfaces
   (`SecretStore`, `ImageProcessor`, `LocationProvider`, `EmbeddingEncoder`, work schedulers).
   Room tests move to `jvmTest`. iOS and JVM implementations can be stubs at this stage, but must
   compile. *As built:* `StorageRepository` stays in `androidMain` and the `ImageProcessor`
   interface is deferred: both move in Phase 5 with their first common consumer (`BitmapUtils`,
   `PaletteUtil`, `ColorMatcher`, `CaptionEnrichmentProvider` and the workers stay Android-only
   until then). The JVM ONNX encoder
   (and the iOS one) is a follow-up; JVM and iOS bind `UnavailableEmbeddingEncoder` until then.
   `WordPieceTokenizer` (uses `java.text.Normalizer`) and `BatchCaptionResult` (uses
   `java.util.UUID`) stay in `androidMain` for now.
4. **`core/ui` → CMP.** Theme, components, resources, Coil 3.
5. **Features → KMP/CMP, one per PR,** in this order: stats → settings → outfits → recommendations
   → chat → wardrobe. Stats goes first because it's the most-tested and least platform-bound.
   Wardrobe goes last because it has the most platform code (camera, picker, segmentation).
6. **Shared root.** Move `NavHost` and the root `App()` composable into `shared`, so `app` is
   reduced to Android start-up (Koin, `MainActivity`, flavors, Android launchers, backup/restore).
   `shared` declares the iOS framework export so the future `iosApp` has something to link, but
   no app shell is created.

## Deferred: other platforms

Picked up only when the owner is satisfied with Android. Each is its own project with its own
design pass:

- **Desktop:** a `desktopApp` module and real JVM implementations (`SecretStore`, file roots,
  ONNX Runtime Java with native libraries for Windows first). No location, so weather is hidden.
- **iOS:** an `iosApp` Xcode project and real iOS implementations (Keychain, CoreLocation, ONNX
  Runtime iOS bundled so chat works, the Darwin Ktor engine), plus a CI job that launches the app
  on a simulator. Manual testing needs Mac access.

The "Platform gaps" rules and the decision to bundle ONNX Runtime on iOS (made with the owner)
still apply when that work starts.

## Follow-ups (after the migration, not part of it)

1. **Optional EmbeddingGemma 2 download (text + image).** The ONNX export
   (`onnx-community/embeddinggemma-2-ONNX`) at q4 is about 283 MB: the text model is 174 MB and the
   vision encoder is 109 MB. Embedding item photos directly would make chat retrieval match
   visual attributes without needing Gemini Nano captions. This would benefit foss, iOS and
   desktop most. The experiment should:
   - measure RAM and latency on a mid-range Android phone;
   - check the float16 warning on q4f16;
   - replace `WordPieceTokenizer` with Gemma's SentencePiece tokenizer;
   - compare retrieval against `arctic-embed-xs` on a 30–50 item test wardrobe.

   If it wins, it becomes an optional download in settings, with `arctic-embed-xs` staying the
   bundled default.
2. **Chat routing off GMS.** Turn on the shared regex router for foss, iOS and desktop, with a
   replacement for ML Kit's English check.
3. **Cross-platform backup/restore.** Restore a `.hangr` backup on iOS or desktop.
4. **Optional local chat model (Gemma 4 via LiteRT-LM).** For users who don't want a cloud
   provider and whose device has no Gemini Nano. Run Gemma 4 E2B with Google's LiteRT-LM
   library (`com.google.ai.edge.litertlm:litertlm-android`; check the current version on Maven).
   - **Model:** `litert-community/gemma-4-E2B-it-litert-lm`, one `.litertlm` file of about
     2.6 GB, Apache 2.0, not gated. Offer E4B as the larger option for phones with 12 GB of RAM
     or more.
   - **Works in foss.** LiteRT-LM has no Play Services or AICore dependency, so this is the
     local option for both flavors.
   - **Shape:** a new `ChatAiProvider` (e.g. `LocalLlmProvider`), plus a download flow shared
     with the EmbeddingGemma 2 download (follow-up 1) on one model-download screen in settings.
     The routing order is Nano, then the local model, then the cloud provider.
   - **Stream the reply.** Expect a few tokens per second on older phones.
   - **To check:** answer quality on the RAG context block against Nano and a cloud model; RAM
     and latency on a mid-range phone; whether its image input could stand in for Nano image
     captions on devices without Nano.
   - **Alternative:** llama.cpp with small Qwen3.5 GGUF models, if one runtime for Android, iOS
     and desktop matters more than Google's supported Android route.

## Left to the builder

- Exact library versions (Koin, CMP, Kermit, Vico multiplatform, Coil 3, Room KMP, ONNX Runtime per
  platform), pinned in `libs.versions.toml`.
- Whether `platform/gms` is split per feature.
- Desktop `SecretStore` implementation.
- Where the ONNX model and vocab live so every target can load them.
- iOS project tooling (plain Xcode project vs. KMP template layout).
- How far to adopt the cancellation helper in code that isn't otherwise being touched.
- Updating `CLAUDE.md` and `app-android/CLAUDE.md` as each phase lands (Hilt → Koin, flavor
  layout, source-set layout, new verification command).
