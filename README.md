# hangr

A native Android personal digital wardrobe app. Built with Kotlin and Jetpack Compose, emphasizing a local-first, privacy-focused experience.

## Features

- **Digital Wardrobe**: Manage your clothing items with detailed metadata (colors, materials, seasons, etc.).
- **Outfit Builder**: Create and save outfits from your wardrobe.
- **OOTD Journal**: Log your daily outfits and track wear history.
- **Statistics**: Insightful breakdowns of your wardrobe usage, costs, and habits.
- **AI Recommendations**: Get outfit suggestions powered by local (Gemini Nano) or cloud-based AI providers.
- **Privacy First**: No accounts, no cloud sync. All data stays on your device in a Room/SQLite database.

## Architecture & Tech Stack

The project follows **MVVM + Clean Architecture** principles and is modularized by feature.

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3
- **Dependency Injection**: [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- **Database**: [Room](https://developer.android.com/training/data-storage/room)
- **Navigation**: [Type-safe Compose Navigation](https://developer.android.com/guide/navigation/design/type-safety)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
- **Charts**: [Vico](https://patrykandpatrick.com/vico/)
- **Logging**: [Timber](https://github.com/JakeWharton/timber)

### Module Structure

All app code lives in `app-android/`.

- `:app`: Application entry point and navigation graph.
- `:core:data`: Database, repositories, entities, and data models.
- `:core:ui`: Shared design system, theme, and reusable UI components.
- `:features:wardrobe`: Wardrobe management and item details.
- `:features:outfits`: Outfit creation and daily logging.
- `:features:stats`: Wardrobe analytics and statistics.
- `:features:recommendations`: AI-powered outfit suggestions.
- `:features:settings`: App configuration and AI provider management.
- `:features:chat`: RAG-based wardrobe chat with intent routing.

## Product Flavors

The app supports two distribution flavors:
- **`full`**: Includes Google Mobile Services (GMS) features like Gemini Nano (via AICore).
- **`foss`**: Completely free of GMS dependencies, suitable for F-Droid distribution. AI features are stubbed or restricted to non-GMS providers.

## Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 35
- JDK 17

### Building

Run all Gradle commands from the `app-android/` directory.

```bash
./gradlew assembleFullDebug   # full-flavor debug APK
./gradlew assembleFossDebug   # FOSS-flavor debug APK
./gradlew test                # unit tests
```

### Database Migrations

Database migrations are strictly tracked. If you modify the schema, follow the checklist in `app-android/core/data/src/main/kotlin/com/closet/core/data/migrations/AGENTS.md` and run migration tests:

```bash
./gradlew connectedAndroidTest
```

## Contributing

Refer to `app-android/CLAUDE.md` for development guidelines, coding standards, and architectural patterns.