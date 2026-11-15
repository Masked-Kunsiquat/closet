# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**hangr (Android)** — a native Android port of the hangr personal digital wardrobe app. Kotlin, Jetpack Compose, Room/SQLite on-device. Local-first, no accounts, no cloud.

Run all Gradle commands from the `app-android/` directory.

**Git note:** Claude Code's working directory is `app-android/` and git commands run from here too. Use paths **relative to `app-android/`** — e.g. `git add CLAUDE.md`, not `git add app-android/CLAUDE.md` (that doubles the prefix and fails).

## Commands

```bash
./gradlew assembleDebug          # Build debug APK (all flavors)
./gradlew assembleFullDebug      # Build full-flavor debug APK (GMS features)
./gradlew assembleFossDebug      # Build FOSS-flavor debug APK (no GMS)
./gradlew installFullDebug       # Install full flavor on connected device/emulator
./gradlew installFossDebug       # Install FOSS flavor on connected device/emulator
./gradlew clean build            # Full clean rebuild
./gradlew lint                   # Lint all modules
./gradlew :app:lint              # Lint specific module
./gradlew :features:wardrobe:lint
./gradlew kspDebugKotlin         # Run KSP (generates Room schema in core/data/schemas/)
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests
```

Unit and instrumented tests exist for the stats feature. Migration tests live in `core/data/src/androidTest/.../MigrationTest.kt`. Run migration tests before any PR that touches the schema.

## Architecture

### Module structure

```
app/                   — Application entry point, NavGraph, MainActivity
core/data/             — Database, DAOs, Repositories, entities, Koin data module
core/ui/               — Material 3 theme, shared Composable components
features/wardrobe/     — Closet screen, item detail, add/edit form, bulk wash, brand management
features/outfits/      — Outfits screen, outfit builder, OOTD journal, wardrobe picker, day detail sheet
features/stats/        — Stats screen, StatsViewModel, breakdown sections
features/recommendations/ — Outfit recommendations, AI providers (NanoProvider, AnthropicProvider, OpenAiProvider)
features/settings/     — Settings screen, AI settings sub-screen, model picker, key management
features/chat/         — RAG wardrobe chat: ChatScreen, ChatViewModel, ChatRepository, provider impls
```

Module dependencies: `app` → `features/*` → `core/ui` → `core/data`.

### MVVM + Clean Architecture

- **ViewModels** are registered in their module's Koin module with `viewModelOf(::X)` and obtained with `koinViewModel()`. They expose `StateFlow<UiState>` built from `combine()` on repository `Flow`s. Use `SharingStarted.WhileSubscribed(5_000)`.
- **Repositories** wrap all operations in `DataResult<T>` and re-throw `CancellationException`. Never let raw exceptions escape a repository.
- **Screens** (Composables ending in `Screen`) collect ViewModel state and pass only lambdas down to child composables.

### Product flavors

Two flavors on the `distribution` dimension. Only `app/`, `features/recommendations/`, `features/wardrobe/`, and `features/chat/` declare flavor dimensions; all other modules are single-variant.

- **`full`** — includes GMS-backed features (MLKit GenAI Prompt API / Gemini Nano). Use for local dev, GitHub releases, sideload APKs. Android Studio defaults to `fullDebug`.
- **`foss`** — no Google Play Services dependencies. GMS features are stubbed to no-ops. Target: F-Droid distribution.

GMS-only code lives in `src/full/kotlin/`; stubs live in `src/foss/kotlin/`. Each flavor source set defines its own `*FlavorModule` Koin module, so the active source set decides the bindings at compile time — no runtime conditionals needed. `"fullImplementation"(...)` in `build.gradle.kts` scopes GMS deps to the full flavor only.

### Dependency injection

Koin throughout (`koin-bom` in `gradle/libs.versions.toml`). No annotations, no code generation.

- **One module per Gradle module.** Each module declares a top-level `val <name>Module` in its `di/*KoinModule.kt` (e.g. `dataModule` in `core/data/di/DataKoinModule.kt`, `wardrobeModule` in `features/wardrobe/di/WardrobeKoinModule.kt`). `appModule` and `allModules` (every module, in dependency order) live in `app/di/AppKoinModule.kt`.
- **Flavor modules.** Flavor-specific bindings (GMS impls vs. FOSS stubs) go in a `*FlavorModule.kt` defined in **both** `src/full/kotlin/.../di/` and `src/foss/kotlin/.../di/` with the same `val` name (`recommendationsFlavorModule`, `chatFlavorModule`, `wardrobeFlavorModule`). Never bind a flavor class from the `main` module.
- **Startup.** `ClosetApp.onCreate()` calls `startKoin { androidLogger(...); androidContext(this@ClosetApp); workManagerFactory(); modules(allModules) }` before anything else. Framework classes (`ClosetApp`, `MainActivity`, `BackupForegroundService`) use `by inject()`.
- **ViewModels** are registered with `viewModelOf(::X)` and obtained in Composables with `koinViewModel()` (`org.koin.compose.viewmodel.koinViewModel`). To share a ViewModel with a previous back-stack entry, pass `viewModelStoreOwner = entry`.
- **Workers** are registered with `workerOf(::X)`; their constructors are plain `(context: Context, params: WorkerParameters, …)`. `workManagerFactory()` supplies the factory, so the manifest removes `androidx.work.WorkManagerInitializer` — keep that removal.
- **Scope.** DAOs, repositories and providers are `single`. The AI inference `HttpClient` is qualified: resolve it with `get(AiHttpClientQualifier)` (`core/data/di/AiHttpClient.kt`). The five AI providers (`AnthropicProvider`, `OpenAiProvider`, `AnthropicChatProvider`, `GeminiChatProvider`, `OpenAiChatProvider`) must be registered with an explicit `single { X(get(AiHttpClientQualifier), get(), get()) }`, never `singleOf` (pinned by tests in the chat and recommendations Koin tests).
- **Verification.** Every module has a `verify()` unit test, and `app/src/test/.../di/AppKoinGraphTest.kt` verifies the whole graph. Add new definitions' dependencies to the module, not to `extraTypes`.

`AiPreferencesRepository` is constructed **only** in `dataModule`'s explicit `single { AiPreferencesRepository(androidContext(), get<EncryptedKeyStore>()) }`. Don't switch it to `singleOf(::AiPreferencesRepository)` or construct it anywhere else.

### Database (Room)

- **File:** `closet.db`, current version **6**.
- **Initialization order:** `PRAGMA foreign_keys = ON` → create tables → `DatabaseSeeder` seeds lookup data on `onCreate`.
- **Migrations** live in `ClothingDatabase.kt`. Never edit an applied migration — add a new one. Room schema JSON is exported to `core/data/schemas/`.
- **Junction tables** (`clothing_item_colors`, `_materials`, `_seasons`, `_occasions`, `_patterns`) use delete-then-insert helpers in `ClothingRepository`. Never append.
- **Computed fields** — `wear_count` (`COUNT(DISTINCT outfit_logs.id)` joined via `outfit_items`) and `cost_per_wear` (`purchase_price / wear_count`, `null` when wears = 0 or price is null) are never columns; always computed in queries.
- **OOTD partial index** — `one_ootd_per_day` lives in `ClothingDatabase.onOpen()` via `CREATE … IF NOT EXISTS`, **not** `onCreate` or migrations. Room can't represent partial indexes in `@Entity` and crashes if it finds an unexpected index during migration validation.
- Every manual `migrate()` must `DROP INDEX IF EXISTS one_ootd_per_day` unconditionally at the top (even if the migration doesn't touch `outfit_logs`).
- Migration tests must also drop that index on the pre-migration DB before closing it (see `AGENTS.md` for the pattern).
- Full migration conventions and checklist: `core/data/src/main/kotlin/com/closet/core/data/migrations/AGENTS.md`.

### Navigation

Type-safe Compose Navigation (2.9.7+). Destinations are `@Serializable` data classes/objects in `app/navigation/`. Feature modules add routes via extension functions in their own `*Navigation.kt` files.

### Error handling

`DataResult<T>` sealed interface (`Loading | Success | Error`) for all repository returns. `AppError` sealed class hierarchy (`DatabaseError`, `ValidationError`, `Unexpected`) for domain errors. ViewModels map errors to UI state; never surface raw exceptions in UI.

### Design system

Material 3 with a dark-first palette. Accent palettes: Amber (default), Coral, Sage, Sky, Lavender, Rose. Dynamic color (Material You) enabled on Android 12+. All color/theme tokens live in `core/ui/theme/`. Never hardcode colors; use the theme.

### Image handling

Store only **relative paths** in the DB. Copy picked images into app-owned storage via `StorageRepository`, then store the relative path. Reconstruct full URI at display time in the repository/ViewModel layer before passing to Coil.

### Charts

Vico (`vico-compose-m3 3.0.3`) for column charts in `features/stats/`. Import from `com.patrykandpatrick.vico.compose`.

### Logging

Timber. Debug tree planted in `ClosetApp.onCreate()` under `BuildConfig.DEBUG` only.

### Destructive actions

Every destructive user action (delete item, delete outfit, delete log entry) must be guarded by an `AlertDialog` confirmation. Pattern: local `var showDeleteDialog by remember { mutableStateOf(false) }` → button sets it true → dialog calls the ViewModel. Never call a delete ViewModel function directly from a button click.

### RAG pipeline (chat)

`ChatRepository` runs: encode query via `EmbeddingEncoder` (ONNX, same model as `EmbeddingWorker`) → cosine search via `EmbeddingIndex` (top-5) → fetch `ClothingItemDetail` for matched IDs → build context block (name, category, colours, materials, `semanticDescription`, `imageCaption`) → call active provider via `ChatAiProviderSelector`. `CancellationException` must be rethrown at every `getOrElse` / `Result` unwrap point — never swallow it.

### MLKit namespaces

Two distinct MLKit namespaces — do not confuse them:
- `com.google.mlkit:genai-*` — AICore-backed GenAI APIs (Gemini Nano Prompt API, Image Description, etc.). Not available on all devices. Full flavor only.
- `com.google.android.gms:play-services-mlkit-*` — traditional Vision ML APIs (Subject Segmentation, Barcode Scanning, etc.). Work on all devices via Play Services. No AICore required.

## Key files

| File | Purpose |
|------|---------|
| `app/src/.../navigation/ClosetNavGraph.kt` | Root `NavHost` — all routes registered here |
| `app/src/.../shortcuts/ShortcutActions.kt` | App shortcut constants (IDs, actions, extras) — shared hub for `shortcuts.xml`, `MainActivity`, `ClosetNavGraph`, and `ClosetViewModel` |
| `core/data/src/.../ClothingDatabase.kt` | Room DB, migrations, `DatabaseSeeder` invocation |
| `core/data/src/.../dao/ClothingDao.kt` | Clothing queries with `wear_count` join |
| `core/data/src/.../repository/ClothingRepository.kt` | CRUD, junction helpers, `DataResult` wrapping |
| `core/data/src/.../util/DataResult.kt` | `Loading / Success / Error` sealed interface |
| `core/data/src/.../util/AppError.kt` | Domain error hierarchy |
| `core/data/src/.../di/DataKoinModule.kt` | `dataModule`: Koin singletons for the DB, DAOs, repositories, HTTP clients, workers |
| `app/src/.../di/AppKoinModule.kt` | `appModule` and `allModules` (the list passed to `startKoin`) |
| `core/ui/src/.../theme/Color.kt` | Palette constants and accent enums |
| `gradle/libs.versions.toml` | Centralized version catalog (all deps here) |
| `core/data/src/.../migrations/AGENTS.md` | Full migration conventions and checklist |
| `core/data/src/.../dao/StatsDao.kt` | Stats queries (wear counts, breakdowns by category/color/occasion) |
| `features/stats/src/.../StatsViewModel.kt` | Stats state with `StatPeriod` filter |
| `features/chat/src/.../ChatRepository.kt` | RAG query pipeline (encode → search → context → provider) |
| `core/data/src/.../util/EmbeddingIndex.kt` | In-memory FAISS-style flat index; `search(vec, topK)` returns item IDs by cosine similarity |
| `core/data/src/.../util/EmbeddingEncoder.kt` | Query-time ONNX encoder (reuses `EmbeddingWorker`'s model) |
| `core/data/src/.../worker/EmbeddingWork.kt` | `EmbeddingScheduler` interface + work name constants |
| `core/data/src/.../repository/AiPreferencesRepository.kt` | Encrypted storage for AI provider keys and model selections |

## What is deferred — do not build

Collage builder, outfit planning for future dates, packing lists, goals/missions, cloud backup.
