---
name: closet-migrations
description: Write database migrations and seed data for the Closet app. Use when creating or modifying database schema, writing seed data, adding columns, or setting up initial data for any table.
---

# Closet — Migrations & Seed Data

## Migration Conventions

Full conventions and checklist: `app-android/core/data/src/main/kotlin/com/closet/core/data/migrations/AGENTS.md`

### Key Rules
- Migrations live in `ClothingDatabase.kt` as `Migration(from, to)` objects passed to `addMigrations()`
- Never edit an applied migration — add a new one
- Current DB version: **6**
- Room schema JSON exported to `core/data/schemas/` — commit it alongside every migration
- Run migration tests before any PR that touches the schema: `./gradlew connectedAndroidTest`

### Every migration must start with:
```kotlin
override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")
    // ... rest of migration
}
```

### Migration Template
```kotlin
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")
        db.execSQL("ALTER TABLE clothing_items ADD COLUMN new_column TEXT")
    }
}
```

### OOTD Partial Index
Lives in `ClothingDatabase.onOpen()`, not `onCreate()` or any migration:
```kotlin
override fun onOpen(db: SupportSQLiteDatabase) {
    db.execSQL("PRAGMA foreign_keys = ON")
    db.execSQL("""
        CREATE UNIQUE INDEX IF NOT EXISTS one_ootd_per_day
        ON outfit_logs(date) WHERE is_ootd = 1
    """)
}
```

---

## Seed Data Conventions
- All inserts use `INSERT OR IGNORE` — seeds must be idempotent
- Never use `INSERT OR REPLACE` (breaks foreign key references via row ID change)
- Seeds run in `DatabaseSeeder` on `onCreate` only
- Values must match the canonical reference below exactly

### Seed Template (Kotlin)
```kotlin
private fun seedSeasons(db: SupportSQLiteDatabase) {
    val seasons = listOf(
        "Spring" to "flower",
        "Summer" to "sun",
        "Fall" to "leaf",
        "Winter" to "snowflake",
        "All Season" to "thermometer",
    )
    seasons.forEach { (name, icon) ->
        db.execSQL(
            "INSERT OR IGNORE INTO seasons (name, icon) VALUES (?, ?)",
            arrayOf(name, icon)
        )
    }
}
```

---

## Canonical Seed Values

See [reference.md](reference.md) for full materials, patterns, subcategories, and size systems.

### Categories
| Name | sort_order |
|------|------------|
| Tops | 1 |
| Bottoms | 2 |
| Outerwear | 3 |
| Dresses & Jumpsuits | 4 |
| Footwear | 5 |
| Accessories | 6 |
| Bags | 7 |
| Activewear | 8 |
| Underwear & Intimates | 9 |
| Swimwear | 10 |

### Seasons
Spring, Summer, Fall, Winter, All Season

### Occasions
Casual, Work/Business, Formal, Athletic, Loungewear, Date Night, Vacation, Outdoor/Hiking, Special Occasion

---

## Common Mistakes
- Do not store derived fields (wear count, cost per wear) in any table
- Do not use absolute image paths anywhere, even as examples
- Do not skip `INSERT OR IGNORE`
- Do not add columns without a migration
- Do not forget to `DROP INDEX IF EXISTS one_ootd_per_day` at the top of every migration
- Do not put the OOTD index in a migration or `onCreate` — it belongs in `onOpen` only
