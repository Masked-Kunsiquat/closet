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
./gradlew :core:data:kspAndroidMain        # Run Room KSP (generates the schema in core/data/schemas/)
./gradlew test                             # Unit tests (all modules; core/data's `test` runs testAndroidHostTest + jvmTest)
./gradlew connectedAndroidTest             # Instrumented tests
./gradlew :core:data:testAndroidHostTest   # core/data unit tests (Android host)
./gradlew :core:data:jvmTest               # core/data JVM tests: schema, DAO, repository (no device needed)
./gradlew :core:data:connectedAndroidDeviceTest   # core/data instrumented tests (database path, existing-install upgrade)
./gradlew :core:data:compileCommonMainKotlinMetadata  # local stand-in for the iOS compile (CI only)
```

`core/data` is a Kotlin Multiplatform module (targets `android`, `jvm`, `iosArm64`, `iosSimulatorArm64`). Its source sets are `commonMain`, `androidMain`, `jvmMain`, `iosMain`, `commonTest`, `jvmTest`, `androidHostTest` and `androidDeviceTest`; it has no `BuildConfig` (the debug flag is `BuildFlags`, bound in `appModule`).

Full verification before a PR:

```bash
./gradlew test :core:data:jvmTest :core:data:testAndroidHostTest :core:data:compileCommonMainKotlinMetadata lint assembleFullDebug assembleFossDebug
```

Unit and instrumented tests exist for the stats feature. Room tests (`FreshInstallSchemaTest`, `StatsDaoTest`, the repository tests) live in `core/data/src/jvmTest/` and run on the JVM against the bundled SQLite driver with `./gradlew :core:data:jvmTest` (no device needed); run them before any PR that touches the schema. `core/data/src/androidDeviceTest/` keeps only the Android-only checks (`DatabasePathTest`, `ExistingInstallUpgradeTest`); run `./gradlew :core:data:connectedAndroidDeviceTest` when changing the database builder or driver.

## Architecture

### Module structure

```
app/                   — Application entry point, NavGraph, MainActivity
core/data/             — Database, DAOs, Repositories, entities, Koin data module
core/ui/               — Material 3 theme, shared Composable components
features/wardrobe/     — Closet screen, item detail, add/edit form, bulk wash, brand management
features/outfits/      — Outfits screen, outfit builder, OOTD journal, wardrobe picker, day detail sheet
features/stats/        — Stats screen, StatsViewModel, breakdown sections
features/recommendations/ — Outfit recommendations, AI providers (AnthropicProvider, OpenAiProvider; NanoProvider is in platform/gms)
features/settings/     — Settings screen, AI settings sub-screen, model picker, key management
features/chat/         — RAG wardrobe chat: ChatScreen, ChatViewModel, ChatRepository, provider impls
platform/gms/          — GMS/ML Kit implementations of feature capability interfaces (full flavor only)
```

Module dependencies: `app` → `features/*` → `core/ui` → `core/data`; `app (full)` → `platform/gms` → `features/*`.

### MVVM + Clean Architecture

- **ViewModels** are registered in their module's Koin module with `viewModelOf(::X)` and obtained with `koinViewModel()`. They expose `StateFlow<UiState>` built from `combine()` on repository `Flow`s. Use `SharingStarted.WhileSubscribed(5_000)`.
- **Repositories** wrap all operations in `DataResult<T>` and re-throw `CancellationException`. Never let raw exceptions escape a repository.
- **Screens** (Composables ending in `Screen`) collect ViewModel state and pass only lambdas down to child composables.

### Product flavors

Two flavors on the `distribution` dimension. Only `app/` declares flavors; every other module is single-variant.

- **`full`** — includes GMS-backed features (MLKit GenAI Prompt API / Gemini Nano). Use for local dev, GitHub releases, sideload APKs. Android Studio defaults to `fullDebug`.
- **`foss`** — no Google Play Services dependencies. GMS features are stubbed to no-ops. Target: F-Droid distribution.

GMS code lives in `platform/gms` and is bound by its `gmsModule`. Pure-Kotlin no-op stubs live in each feature's `src/main`, bound by a `<feature>NoOpModule` (`chatNoOpModule`, `recommendationsNoOpModule`, `wardrobeNoOpModule`). `app/src/full` and `app/src/foss` each define `flavorModule` (full includes `gmsModule`; foss includes the three `*NoOpModule`s), so the active source set decides the bindings at compile time — no runtime conditionals needed. `"fullImplementation"(project(":platform:gms"))` in `app/build.gradle.kts` is the only GMS scoping; ML Kit / Play Services deps are declared only in `platform/gms/build.gradle.kts`.

### Dependency injection

Koin throughout (`koin-bom` in `gradle/libs.versions.toml`). No annotations, no code generation.

- **One module per Gradle module.** Each module declares a top-level `val <name>Module` in its `di/*KoinModule.kt` (e.g. `dataModule` in `core/data/di/DataKoinModule.kt`, `wardrobeModule` in `features/wardrobe/di/WardrobeKoinModule.kt`). `appModule` and `allModules` (every module, in dependency order) live in `app/di/AppKoinModule.kt`.
- **Flavor modules.** A feature exposes each GMS-backed capability as an interface with a no-op stub in its `src/main`, bound in a `<feature>NoOpModule`. The GMS implementations are bound in `gmsModule` (`platform/gms/di/GmsKoinModule.kt`). `app` picks one set via `flavorModule` (`app/src/full` → `includes(gmsModule)`, `app/src/foss` → `includes(<feature>NoOpModule…)`), listed in `allModules`. Never bind a GMS class outside `platform/gms`, and never include a `*NoOpModule` or `gmsModule` from a feature's main module.
- **Startup.** `ClosetApp.onCreate()` calls `startKoin { androidLogger(...); androidContext(this@ClosetApp); workManagerFactory(); modules(allModules) }` before anything else. Framework classes (`ClosetApp`, `MainActivity`, `BackupForegroundService`) use `by inject()`.
- **ViewModels** are registered with `viewModelOf(::X)` and obtained in Composables with `koinViewModel()` (`org.koin.compose.viewmodel.koinViewModel`). To share a ViewModel with a previous back-stack entry, pass `viewModelStoreOwner = entry`.
- **Workers** are registered with `workerOf(::X)`; their constructors are plain `(context: Context, params: WorkerParameters, …)`. `workManagerFactory()` supplies the factory, so the manifest removes `androidx.work.WorkManagerInitializer` — keep that removal.
- **Scope.** DAOs, repositories and providers are `single`.
- **AI HTTP client.** AI inference calls need 120 s timeouts; everything else uses the bare `HttpClient` (30 s). A class that makes AI calls takes an `AiHttpClient` constructor parameter (`core/data/di/AiHttpClient.kt`), never a bare `HttpClient`, so plain `singleOf` resolution gives it the right client. Both clients are built by `buildHttpClient(...)` in `DataKoinModule.kt`; add any new secret-bearing header to its `SENSITIVE_HEADERS`.
- **Verification.** Every module has a `verify()` unit test (`platform/gms` has `GmsKoinTest`), and `app/src/test/.../di/AppKoinGraphTest.kt` verifies the whole graph in both flavors. Add new definitions' dependencies to the module, not to `extraTypes`. The one exception: a module test may whitelist a type bound by a *sibling* module it can't depend on (e.g. `SettingsKoinModuleTest`); `AppKoinGraphTest` has no such whitelist and covers it.

### Database (Room)

- **Room KMP.** `ClothingDatabase`, entities, DAOs, `DatabaseSeeder` and the DAO-only repositories live in `commonMain`; `configureClosetDatabase()` (in `ClothingDatabase.kt`) applies the shared settings: the bundled SQLite driver on every platform, `Dispatchers.IO`, and the `onCreate`/`onOpen` callbacks. Per-platform builders: `ClothingDatabase.getDatabase(context)` / `closeAndReset()` in `androidMain/DatabaseBuilder.android.kt` (companion extensions — import them), `getDatabaseBuilder(file)` in `jvmMain`, `getDatabaseBuilder()` in `iosMain`. Raw SQL goes through `useWriterConnection`/`useReaderConnection` on an `androidx.sqlite.SQLiteConnection`; there is no `openHelper`.
- **File:** `closet.db`, current version **1** — a squashed baseline (the old 1→6 chain was reset on 2026-11-15). Every schema change from here on needs a real migration.
- **Initialization order:** `PRAGMA foreign_keys = ON` → create tables → `DatabaseSeeder` seeds lookup data on `onCreate`.
- **Migrations** go in `core/data/src/commonMain/.../migrations/`, take an `SQLiteConnection` (`migrate(connection)`), and are registered with `.addMigrations(...)` in `configureClosetDatabase()` (there are none yet). Never edit an applied migration — add a new one. Room schema JSON is exported to `core/data/schemas/`.
- **Junction tables** (`clothing_item_colors`, `_materials`, `_seasons`, `_occasions`, `_patterns`) use delete-then-insert helpers in `ClothingRepository`. Never append.
- **Computed fields** — `wear_count` (`COUNT(DISTINCT outfit_logs.id)` joined via the `outfit_log_items` snapshot, not the live `outfit_items`; log wears with `LogDao.insertLogAndSnapshot`, including in tests) and `cost_per_wear` (`purchase_price / wear_count`, `null` when wears = 0 or price is null) are never columns; always computed in queries.
- **OOTD partial index** — `one_ootd_per_day` lives in the `onOpen` callback in `configureClosetDatabase()` via `CREATE … IF NOT EXISTS`, **not** `onCreate` or migrations. Room can't represent partial indexes in `@Entity` and crashes if it finds an unexpected index during migration validation.
- When the first real migration is written, it and its test must drop `one_ootd_per_day` first; `AGENTS.md` has the pattern.
- Full migration conventions and checklist: `core/data/src/commonMain/kotlin/com/closet/core/data/migrations/AGENTS.md`.

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

Two distinct MLKit namespaces — do not confuse them. Both are used only from `platform/gms` (full flavor); no feature module may import either:
- `com.google.mlkit:genai-*` — AICore-backed GenAI APIs (Gemini Nano Prompt API, Image Description, etc.). Not available on all devices. Full flavor only.
- `com.google.android.gms:play-services-mlkit-*` — traditional Vision ML APIs (Subject Segmentation, Barcode Scanning, etc.). Work on all devices via Play Services. No AICore required.

## Key files

| File | Purpose |
|------|---------|
| `app/src/.../navigation/ClosetNavGraph.kt` | Root `NavHost` — all routes registered here |
| `app/src/.../shortcuts/ShortcutActions.kt` | App shortcut constants (IDs, actions, extras) — shared hub for `shortcuts.xml`, `MainActivity`, `ClosetNavGraph`, and `ClosetViewModel` |
| `core/data/src/commonMain/.../ClothingDatabase.kt` | Room DB, `configureClosetDatabase()` (driver, callbacks, migrations), `DatabaseSeeder` invocation |
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
