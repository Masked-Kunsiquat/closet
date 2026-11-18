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
./gradlew :core:ui:jvmTest                 # core/ui JVM tests (preferences, Koin module; commonTest runs here too)
./gradlew :core:ui:compileCommonMainKotlinMetadata  # local stand-in for the iOS compile of core/ui (CI only)
```

`core/data` is a Kotlin Multiplatform module (targets `android`, `jvm`, `iosArm64`, `iosSimulatorArm64`). Its source sets are `commonMain`, `androidMain`, `jvmMain`, `iosMain`, `commonTest`, `jvmTest`, `androidHostTest` and `androidDeviceTest`; it has no `BuildConfig` (the debug flag is `BuildFlags`, bound in `appModule`).

- **`commonMain`:** Room (database, entities, DAOs), the repositories, `ai/*`, the Ktor clients and weather, preferences, `EmbeddingIndex`, the work constants, and the platform interfaces. Nothing platform-specific: no `android.*`, `java.*` or WorkManager imports (`compileCommonMainKotlinMetadata` catches leaks).
- **`androidMain`:** the platform implementations (`EncryptedKeyStore`, `AndroidLocationProvider`, `OnnxEmbeddingEncoder`, the WorkManager schedulers in `androidDataModule`, `WorkInfo.toWorkStatus()`), the workers, the Android database/DataStore builders, and code still built on `Uri`/`Bitmap`/`Context` (`StorageRepository`, `CaptionEnrichmentProvider`, `BitmapUtils`, `PaletteUtil`, `ColorMatcher`, `WordPieceTokenizer`).
- **Platform interfaces** (common; Android binds the real one, `jvmDataModule`/`iosDataModule` bind honest stubs): `SecretStore` (`InMemorySecretStore`), `LocationProvider` (`NoLocationProvider`), `EmbeddingEncoder` (`UnavailableEmbeddingEncoder`; `modelVersion` is the stored `model_version`), and `EmbeddingScheduler` / `ImageCompressionScheduler` / `BatchSegmentationScheduler` (`NoOp*Scheduler`), and `ShortcutUsageReporter` in `com.closet.core.data.shortcuts` (`NoOpShortcutUsageReporter`; Android binds `AndroidShortcutUsageReporter` in `androidDataModule`). `AppClock` (`com.closet.core.data.time`; `SystemAppClock` in production, `FixedAppClock` in tests) — read now/today through it, never `Clock.System`, except entity timestamp defaults and worker timestamps. Schedulers expose `workStatus: Flow<WorkStatus?>` — never `WorkInfo` — so feature code reads `WorkStatus`/`WorkState`. Android's `BatchSegmentationScheduler` is bound by `wardrobeModule`.
- **Logging:** `core/data` logs with Kermit (`Logger.withTag(...)`), not Timber.

`core/ui` is also a Kotlin/Compose Multiplatform module (targets `android`, `jvm`, `iosArm64`, `iosSimulatorArm64`). Its source sets are `commonMain`, `androidMain`, `jvmMain`, `iosMain`, `commonTest`, `jvmTest` and `androidHostTest`.

- **`commonMain`:** theme, shared components (including `ResErrorSnackbarEffect`), mappers (`asUserMessage`, icon mapping), `PreferencesRepository` and `composeResources`. **`androidMain`:** the platform theme actuals (`PlatformTheme.android.kt`, dynamic color) and `androidUiModule`. `jvmMain`/`iosMain` hold the platform theme actuals and their Koin modules.
- **Koin:** `uiModule = commonUiModule + androidUiModule` (androidMain); `jvmUiModule(dataDirectory: File)` and `iosUiModule` build the DataStore themselves. `PreferencesRepository` takes the `named("closet_prefs")` `DataStore<Preferences>`.
- **Platform-neutral helpers:** `com.closet.core.ui.format` holds localized date labels (`formatDayMonthLong`, `formatDateLong`, `formatMonthDay`, `formatMonthYear`, `formatWeekdayDay`, `weekdayNarrow`), `formatCurrency` (the device locale's currency format) and `parseHexColor` (`#RRGGBB`/`#AARRGGBB` to `Color?`) — never `java.time` formatters in common code.
- **Previews:** KMP feature modules (e.g. `features/stats`) have no `debugImplementation`; add `androidx.compose.ui:ui-tooling` through the runtime-only `"androidRuntimeClasspath"(libs.androidx.ui.tooling)` configuration (with the Compose BOM) so `@Preview`s render.

Full verification before a PR:

```bash
./gradlew test :core:data:jvmTest :core:data:testAndroidHostTest :core:data:compileCommonMainKotlinMetadata :core:ui:jvmTest :core:ui:compileCommonMainKotlinMetadata :features:stats:jvmTest :features:stats:compileCommonMainKotlinMetadata :features:settings:jvmTest :features:settings:compileCommonMainKotlinMetadata :features:outfits:jvmTest :features:outfits:compileCommonMainKotlinMetadata :features:recommendations:jvmTest :features:recommendations:compileCommonMainKotlinMetadata :features:chat:jvmTest :features:chat:compileCommonMainKotlinMetadata :features:wardrobe:jvmTest :features:wardrobe:compileCommonMainKotlinMetadata :shared:jvmTest :shared:compileCommonMainKotlinMetadata lint assembleFullDebug assembleFossDebug
```

CI runs a subset of that on every PR and push to `main`: `.github/workflows/ci.yml` runs `test :core:data:jvmTest` (the root `test` includes every KMP module's `jvmTest`), `lint`, and `assembleFullDebug assembleFossDebug` on Ubuntu, and `ios-compile.yml` runs `iosCompileCheck` (iOS main and test sources, klib cross-compilation, no linking) on Ubuntu. `ios-link.yml` is a manual-only macOS job that links the `:shared` framework (`:shared:linkDebugFrameworkIosSimulatorArm64`); trigger it from the Actions tab when the iOS export changes. The `compileCommonMainKotlinMetadata` tasks run only locally (the iOS compile covers the same sources in CI), so run the full command above before a PR. Instrumented tests don't run in CI; run them on a device.

Unit and instrumented tests exist for the stats feature. Room tests (`FreshInstallSchemaTest`, `StatsDaoTest`, the repository tests) live in `core/data/src/jvmTest/` and run on the JVM against the bundled SQLite driver with `./gradlew :core:data:jvmTest` (no device needed); run them before any PR that touches the schema. `core/data/src/androidDeviceTest/` keeps only the Android-only checks (`DatabasePathTest`, `ExistingInstallUpgradeTest`); run `./gradlew :core:data:connectedAndroidDeviceTest` when changing the database builder or driver.

## Architecture

### Module structure

```
app/                   — Android start-up (Application, MainActivity), flavors, launcher shortcuts, backup/restore
shared/                — KMP/CMP root: `App()` (theme, bottom bar, `NavHost`), `AppShortcut`; iOS framework export
core/data/             — Database, DAOs, Repositories, entities, Koin data module
core/ui/               — KMP/CMP module: Material 3 theme, shared Composable components, Compose resources, preferences
features/wardrobe/     — KMP module: Closet screen, item detail, add/edit form, bulk wash, brand management (commonMain UI, ViewModels, routes, navigation and Koin; androidMain previews, the photo processor and shortcut pinner, `BatchSegmentationWorker`, the `expect` UI hooks' actuals and the `Bitmap`-typed flavor stubs)
features/outfits/      — KMP module: Outfits screen, outfit builder, OOTD journal, wardrobe picker, day detail sheet (commonMain UI, ViewModels, routes, navigation and Koin)
features/stats/        — KMP module: Stats screen, StatsViewModel, breakdown sections (commonMain UI, ViewModel and navigation; androidMain previews, Koin module)
features/recommendations/ — KMP module: outfit recommendations (commonMain engine, AI providers (AnthropicProvider, OpenAiProvider; NanoProvider is in platform/gms), UI, ViewModel, navigation and Koin; androidMain previews)
features/settings/     — KMP module: Settings screen, AI settings sub-screen, SettingsViewModel (commonMain UI, ViewModel, navigation and Koin; androidMain previews and the permission / keep-screen-on actuals)
features/chat/         — KMP module: wardrobe chat (commonMain repository, providers, routers, UI, ViewModel, navigation and Koin; androidMain previews)
platform/gms/          — GMS/ML Kit implementations of feature capability interfaces (full flavor only)
```

Module dependencies: `app` → `shared` → `features/*` → `core/ui` → `core/data`; `app (full)` → `platform/gms` → `features/*`.

### MVVM + Clean Architecture

- **ViewModels** are registered in their module's Koin module with `viewModelOf(::X)` and obtained with `koinViewModel()`. They expose `StateFlow<UiState>` built from `combine()` on repository `Flow`s. Use `SharingStarted.WhileSubscribed(5_000)`.
- **Repositories** wrap all operations in `DataResult<T>` and re-throw `CancellationException`. Never let raw exceptions escape a repository.
- **Screens** (Composables ending in `Screen`) collect ViewModel state and pass only lambdas down to child composables.

### Product flavors

Two flavors on the `distribution` dimension. Only `app/` declares flavors; every other module is single-variant.

- **`full`** — includes GMS-backed features (MLKit GenAI Prompt API / Gemini Nano). Use for local dev, GitHub releases, sideload APKs. Android Studio defaults to `fullDebug`.
- **`foss`** — no Google Play Services dependencies. GMS features are stubbed to no-ops. Target: F-Droid distribution.

GMS code lives in `platform/gms` and is bound by its `gmsModule`. Pure-Kotlin no-op stubs live in each feature's main code (`commonMain` for the KMP features; `features/wardrobe`'s stubs stay in `androidMain` because they are `Bitmap`-typed), bound by a `<feature>NoOpModule` (`chatNoOpModule`, `recommendationsNoOpModule`, `wardrobeNoOpModule`). `app/src/full` and `app/src/foss` each define `flavorModule` (full includes `gmsModule`; foss includes the three `*NoOpModule`s), so the active source set decides the bindings at compile time — no runtime conditionals needed. `"fullImplementation"(project(":platform:gms"))` in `app/build.gradle.kts` is the only GMS scoping; ML Kit / Play Services deps are declared only in `platform/gms/build.gradle.kts`.

### Dependency injection

Koin throughout (`koin-bom` in `gradle/libs.versions.toml`). No annotations, no code generation.

- **One module per Gradle module.** Each module declares a top-level `val <name>Module` in its `di/*KoinModule.kt` (e.g. `dataModule` in `core/data/di/DataKoinModule.kt`, which includes `commonDataModule` (commonMain) and `androidDataModule` (androidMain); `wardrobeModule` in `features/wardrobe/di/WardrobeKoinModule.kt`). `appModule` and `allModules` (every module, in dependency order) live in `app/di/AppKoinModule.kt`.
- **Flavor modules.** A feature exposes each GMS-backed capability as an interface with a no-op stub in its main code (`commonMain`, or `src/main` in a non-KMP module), bound in a `<feature>NoOpModule`. The GMS implementations are bound in `gmsModule` (`platform/gms/di/GmsKoinModule.kt`). `app` picks one set via `flavorModule` (`app/src/full` → `includes(gmsModule)`, `app/src/foss` → `includes(<feature>NoOpModule…)`), listed in `allModules`. Never bind a GMS class outside `platform/gms`, and never include a `*NoOpModule` or `gmsModule` from a feature's main module.
- **Startup.** `ClosetApp.onCreate()` calls `startKoin { androidLogger(...); androidContext(this@ClosetApp); workManagerFactory(); modules(allModules) }` before anything else. Framework classes (`ClosetApp`, `MainActivity`, `BackupForegroundService`) use `by inject()`.
- **ViewModels** are registered with `viewModelOf(::X)` and obtained in Composables with `koinViewModel()` (`org.koin.compose.viewmodel.koinViewModel`). To share a ViewModel with a previous back-stack entry, pass `viewModelStoreOwner = entry`.
- **Workers** are registered with `workerOf(::X)`; their constructors are plain `(context: Context, params: WorkerParameters, …)`. `workManagerFactory()` supplies the factory, so the manifest removes `androidx.work.WorkManagerInitializer` — keep that removal.
- **Scope.** DAOs, repositories and providers are `single`.
- **AI HTTP client.** AI inference calls need 120 s timeouts; everything else uses the bare `HttpClient` (30 s). A class that makes AI calls takes an `AiHttpClient` constructor parameter (`core/data/di/AiHttpClient.kt`), never a bare `HttpClient`, so plain `singleOf` resolution gives it the right client. Both clients are built by `buildHttpClient(engine, ...)` in `commonMain/.../di/HttpClients.kt` (the engine is bound per platform: OkHttp on Android and JVM, Darwin on iOS); add any new secret-bearing header to its `SENSITIVE_HEADERS`.
- **Feature modules in KMP.** A KMP feature module declares its Koin module in commonMain (`features/settings` `settingsModule`) and verifies it with a jvmTest graph `verify()`. `BatchCaptioner` is the common batch half of `CaptionEnrichmentProvider`; JVM and iOS bind `UnavailableNanoInitializer` and `NoOpBatchCaptioner`.
- **Verification.** Every module that declares a Koin module has a `verify()` unit test (`platform/gms` has `GmsKoinTest`), and `app/src/test/.../di/AppKoinGraphTest.kt` verifies the whole graph in both flavors. Add new definitions' dependencies to the module, not to `extraTypes`. The one exception: a module test may whitelist a type bound by a *sibling* module it can't depend on (e.g. `SettingsKoinModuleTest`); `AppKoinGraphTest` has no such whitelist and covers it.

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

Type-safe navigation on JetBrains `navigation-compose` (`libs.jetbrains.navigation.compose`, which resolves to androidx 2.9.7 on Android). Destinations are `@Serializable` data classes/objects in each feature's commonMain (backup's are in `app`). Most are registered via extension functions in the feature's `*Navigation.kt`; the wardrobe item screens (`ClosetDestination`, `ClothingDetailDestination`, `AddClothingDestination`, `EditClothingDestination`, `BrandManagementDestination`) are registered directly with `composable<…>` in `shared`'s `App.kt`, so a change to their screen wiring goes there, while their route classes stay in `features/wardrobe`'s `WardrobeNavigation.kt`. The root `NavHost`, bottom bar and theme live in `shared` (`App()`); `app` supplies Android-only destinations (backup) and launcher-shortcut intents through `App`'s parameters.

### Error handling

`DataResult<T>` sealed interface (`Loading | Success | Error`) for all repository returns. `AppError` sealed class hierarchy (`DatabaseError`, `ValidationError`, `Unexpected`) for domain errors. ViewModels map errors to UI state; never surface raw exceptions in UI.

### Design system

Material 3 with a dark-first palette. Accent palettes: Amber (default), Coral, Sage, Sky, Lavender, Rose. Dynamic color (Material You) enabled on Android 12+. All color/theme tokens live in `core/ui/src/commonMain/.../theme/`; dynamic color is Android-only, in `PlatformTheme.android.kt`. Never hardcode colors; use the theme.

**Resources.** Shared drawables and strings live in `core/ui/src/commonMain/composeResources/` and are referenced through the generated `com.closet.core.ui.resources.Res` (e.g. `Res.drawable.ic_icon_sun`, `Res.string.error_unexpected`; import each accessor from `com.closet.core.ui.resources`). Use `org.jetbrains.compose.resources.painterResource`/`stringResource`/`getString` for them; string placeholders are positional (`%1$s`). `core/ui` exposes `components-resources` as `api`. A feature's own strings live in its `composeResources` with an internal generated `Res` (`com.closet.features.<name>.resources`; `features/stats`, `features/settings`, `features/outfits`, `features/recommendations`, `features/chat` and `features/wardrobe`). Shared icons are Material Symbols named `ic_symbol_*` in `core/ui` (e.g. `Res.drawable.ic_symbol_checkroom`); every feature module uses Compose resources (no feature keeps its own Android `R` strings). JVM tests that call Compose resources (`getString`) need a Skiko native runtime: apply `gradle/skiko-jvm-test-runtime.gradle.kts` after the `kotlin { }` block (it adds the host's catalog `skiko-awt-runtime-*` to `jvmTestImplementation`), never the `compose.*` accessors. Launcher shortcut icons are copies in `app/` and `features/wardrobe` `res/drawable/` (the shortcut XMLs reference `@drawable/...`).

### Image handling

Store only **relative paths** in the DB. Copy picked images into app-owned storage via `StorageRepository`, then store the relative path. Reconstruct full URI at display time in the repository/ViewModel layer before passing to Coil 3 (`coil3.compose.AsyncImage`). Common ViewModels resolve paths through `ImagePathResolver` rather than Android `Context`.

### Charts

Vico (`vico-compose-m3 3.0.3`) for column charts in `features/stats/` (`commonMain`). Import from `com.patrykandpatrick.vico.compose`.

### Logging

Timber only in `app` and `platform/gms`. Debug tree planted in `ClosetApp.onCreate()` under `BuildConfig.DEBUG` only. `core/data` and the KMP features (`features/stats`, `features/settings`, `features/outfits`, `features/recommendations`, `features/chat`, `features/wardrobe`) use Kermit (multiplatform).

### Destructive actions

Every destructive user action (delete item, delete outfit, delete log entry) must be guarded by an `AlertDialog` confirmation. Pattern: local `var showDeleteDialog by remember { mutableStateOf(false) }` → button sets it true → dialog calls the ViewModel. Never call a delete ViewModel function directly from a button click.

### Chat pipeline

`ChatRepository` selects the provider via `ChatAiProviderSelector.current()` on every query, then picks a path:

- **Tool path.** If the provider implements `ToolCallingChatProvider` and `supportsTools()` is true (checked per query after selection; a throwing `supportsTools()` counts as false), `ToolChatRunner.run(...)` drives the model: at most 4 tool rounds, then a final answer with tools disabled. Neither the router nor RAG runs. The final reply uses the usual JSON contract (`ChatResponseParser`), and the runner drops any `item_id` that did not appear in a tool result during that turn ("item_ids only from tool results"); an `items`/`outfit` reply left with no ids becomes `Text`.
- **Router + RAG path** (providers without tool support, and the fallback). `ChatRouter` is checked first; unrouted queries encode via `EmbeddingEncoder` (`OnnxEmbeddingEncoder` on Android, same model as `EmbeddingWorker`) -> cosine search via `EmbeddingIndex` (top-5) -> fetch `ClothingItemDetail` -> build context block (name, category, colours, materials, `semanticDescription`, `imageCaption`) -> `provider.chat(...)`. A provider-selection failure only surfaces here, so routed stat queries still answer offline.

The tool layer lives in `core/data/.../ai/tools/` (`ToolRegistry`, the read-only wardrobe tools, `ToolChatRunner`). Tools are read-only, names are `snake_case`, every tool description must be at most 180 characters, results are compact JSON with item lists capped at 20, and an unknown filter value returns an error with `valid_values`.

Provider details:

- **Nano** offers tools only when `FUNCTION_CALLING` is available. Availability is persisted as `NanoAvailability` (`READY` / `DOWNLOADING` / `NOT_SUPPORTED` / `FAILED` / `UNKNOWN`) and written by Settings, so opening Settings -> AI refreshes it.
- **Forced tools-off final step:** Nano and Gemini flatten tool history to text; Anthropic keeps `tools` with `tool_choice: none`; OpenAI omits tools.
- **OpenAI-compatible rejection** of tools is remembered per base URL + model, and that message is retried on router + RAG.
- **Malformed arguments** (unparseable, or not a JSON object) carry `ARGS_PARSE_ERROR_KEY`, so the runner returns an error without running the tool. Absent or null arguments mean `{}`.
- **Color matching** in `search_items` uses tagged colors or whole-word mentions in `semanticDescription` / `imageCaption`; each item carries `match: "tagged" | "description"` and tagged matches come first.

`CancellationException` must be rethrown at every `getOrElse` / `Result` unwrap point - never swallow it.

### MLKit namespaces

Two distinct MLKit namespaces — do not confuse them. Both are used only from `platform/gms` (full flavor); no feature module may import either:
- `com.google.mlkit:genai-*` — AICore-backed GenAI APIs (Gemini Nano Prompt API, Image Description, etc.). Not available on all devices. Full flavor only.
- `com.google.android.gms:play-services-mlkit-*` — traditional Vision ML APIs (Subject Segmentation, Barcode Scanning, etc.). Work on all devices via Play Services. No AICore required.

## Key files

| File | Purpose |
|------|---------|
| `shared/src/commonMain/.../App.kt` | Root `App()` — theme, bottom bar and `NavHost`; all routes registered here |
| `app/src/.../shortcuts/ShortcutActions.kt` | App shortcut constants (IDs, actions, extras) — shared hub for `shortcuts.xml`, `MainActivity` and `ClosetViewModel`; `ShortcutIntents.kt` maps an `Intent` to `AppShortcut` |
| `core/data/src/commonMain/.../ClothingDatabase.kt` | Room DB, `configureClosetDatabase()` (driver, callbacks, migrations), `DatabaseSeeder` invocation |
| `core/data/src/.../dao/ClothingDao.kt` | Clothing queries with `wear_count` join |
| `core/data/src/.../repository/ClothingRepository.kt` | CRUD, junction helpers, `DataResult` wrapping |
| `core/data/src/.../util/DataResult.kt` | `Loading / Success / Error` sealed interface |
| `core/data/src/.../util/AppError.kt` | Domain error hierarchy |
| `core/data/src/.../di/DataKoinModule.kt` | `dataModule` = `commonDataModule` (DAOs, repositories, HTTP clients, weather) + `androidDataModule` (DB, WorkManager, schedulers, workers, OkHttp engine, `OnnxEmbeddingEncoder`, `AndroidLocationProvider`, DataStores, `EncryptedKeyStore`) |
| `app/src/.../di/AppKoinModule.kt` | `appModule` and `allModules` (the list passed to `startKoin`) |
| `core/ui/src/.../theme/Color.kt` | Palette constants and accent enums |
| `gradle/libs.versions.toml` | Centralized version catalog (all deps here) |
| `core/data/src/.../migrations/AGENTS.md` | Full migration conventions and checklist |
| `core/data/src/.../dao/StatsDao.kt` | Stats queries (wear counts, breakdowns by category/color/occasion) |
| `features/stats/src/.../StatsViewModel.kt` | Stats state with `StatPeriod` filter |
| `features/chat/src/.../ChatRepository.kt` | Chat entry point (tool path, or router → encode → search → context → provider) |
| `core/data/src/commonMain/.../util/EmbeddingIndex.kt` | In-memory FAISS-style flat index; `search(vec, topK)` returns item IDs by cosine similarity |
| `core/data/src/commonMain/.../util/EmbeddingEncoder.kt` | Encoder interface (`HIDDEN_SIZE`, `modelVersion`); Android impl is `androidMain/.../OnnxEmbeddingEncoder.kt` |
| `core/data/src/commonMain/.../worker/EmbeddingWork.kt` | `EmbeddingScheduler` interface + work name constants (`WorkStatus.kt` beside it) |
| `core/data/src/.../repository/AiPreferencesRepository.kt` | Encrypted storage for AI provider keys and model selections |

## What is deferred — do not build

Collage builder, outfit planning for future dates, packing lists, goals/missions, cloud backup.
