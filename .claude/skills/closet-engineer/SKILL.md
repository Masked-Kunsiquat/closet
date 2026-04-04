---
name: closet-engineer
description: Core engineering conventions for the Closet app. Use when writing any code, creating files, designing architecture, adding features, or making any technical decisions for the Closet project.
---

# Closet — Engineering Conventions

## What This Project Is
**hangr** — a native Android wardrobe and outfit tracking app. Kotlin, Jetpack Compose, Room/SQLite on-device. Local-first, no accounts, no cloud.

**Mantra: built well over built fast.**

## Tech Stack
- Kotlin + Jetpack Compose (Material 3)
- Room/SQLite for all data (no network DB)
- Hilt for dependency injection
- WorkManager for background tasks
- DataStore for preferences
- Coil for image loading
- Vico for charts
- Local filesystem for images (relative paths ONLY — never absolute)
- Two product flavors: `full` (GMS features) and `foss` (no GMS, F-Droid target)

## Module Structure
```
app/                   — Application entry point, NavGraph, MainActivity
core/data/             — Database, DAOs, Repositories, entities, DI module
core/ui/               — Material 3 theme, shared Composable components
features/wardrobe/     — Closet screen, item detail, add/edit form, bulk wash, brands
features/outfits/      — Outfits screen, outfit builder, OOTD journal, day detail
features/stats/        — Stats screen, StatsViewModel, breakdown sections
features/recommendations/ — Outfit recommendations engine
features/settings/     — Settings screen, AI settings, model picker, key management
features/chat/         — RAG wardrobe chat: ChatScreen, ChatViewModel, providers
```
Module dependencies: `app` → `features/*` → `core/ui` → `core/data`.

## Architecture — MVVM + Clean
- **ViewModels** are `@HiltViewModel`. Expose `StateFlow<UiState>` via `combine()` on repository `Flow`s. Use `SharingStarted.WhileSubscribed(5_000)`.
- **Repositories** wrap all operations in `DataResult<T>` and re-throw `CancellationException`. Never let raw exceptions escape.
- **Screens** (Composables ending in `Screen`) collect ViewModel state and pass only lambdas down to child composables.

## Database Rules
- `PRAGMA foreign_keys = ON` at DB open time, always
- Never store derived fields — Wear Count and Cost Per Wear are always computed at query time via SQL joins
- Image paths are always relative — never absolute, never hardcoded
- Junction tables (`clothing_item_colors`, `_materials`, `_seasons`, `_occasions`, `_patterns`) use delete-then-insert helpers. Never append.
- OOTD partial index lives in `onOpen()`, not `onCreate()` or migrations:
  ```sql
  CREATE UNIQUE INDEX IF NOT EXISTS one_ootd_per_day ON outfit_logs(date) WHERE is_ootd = 1
  ```
- Every manual `migrate()` must `DROP INDEX IF EXISTS one_ootd_per_day` unconditionally at the top
- Current DB version: **6**

## Migration Rules
- Migrations live in `ClothingDatabase.kt`
- Never edit an applied migration — add a new one
- Room schema JSON exported to `core/data/schemas/`
- Run migration tests before any PR that touches the schema: `./gradlew connectedAndroidTest`
- Full migration conventions: `core/data/src/main/kotlin/com/closet/core/data/migrations/AGENTS.md`

## Error Handling
- `DataResult<T>` sealed interface (`Loading | Success | Error`) for all repository returns
- `AppError` sealed class hierarchy (`DatabaseError`, `ValidationError`, `Unexpected`)
- ViewModels map errors to UI state — never surface raw exceptions in UI
- Always re-throw `CancellationException` — never swallow it

## Image Handling
- Copy picked images into app-owned storage via `StorageRepository`
- Store only the relative path in the DB
- Reconstruct full URI at display time in repository/ViewModel layer before passing to Coil
- Incoming photos compressed to max 1600 px / JPEG 85 % on save
- Segmented images saved as WebP-Lossy on API 30+

## Destructive Actions
Every destructive user action (delete item, delete outfit, delete log) must be guarded by an `AlertDialog` confirmation. Pattern: `var showDeleteDialog by remember { mutableStateOf(false) }` → button sets true → dialog calls ViewModel. Never call delete directly from a button click.

## Product Flavors
- `full` — GMS features (MLKit GenAI, Gemini Nano). Use for local dev and GitHub releases.
- `foss` — No GMS. All GMS features stubbed to no-ops. Target: F-Droid.
- GMS-only code in `src/full/kotlin/`; stubs in `src/foss/kotlin/`
- Use `"fullImplementation"(...)` to scope GMS deps to the full flavor only

## Dependency Injection
- Hilt throughout. `@HiltAndroidApp` on `ClosetApp`, `@AndroidEntryPoint` on `MainActivity`
- All DAOs and Repositories are `@Singleton` from `DataModule` in `core/data/di/`
- `AiPreferencesRepository` is bound only via `DataModule.provideAiPreferencesRepository()` — no `@Inject` constructor

## Logging
Timber. Debug tree planted in `ClosetApp.onCreate()` under `BuildConfig.DEBUG` only.

## What Is Deferred — Do Not Build
- Collage builder
- Outfit planning (future dates)
- Packing lists / trip planning
- Goals/missions
- Cloud sync (manual backup/restore is shipped; cloud sync is not)
- Color dropper from image

## General Rules
- Ask before adding a new dependency
- Prefer explicit naming over abbreviations
- Never invent fields not in the project brief
- Never store derived data in the database
- Read `closet-migrations` reference files before generating schema, seeds, or migrations