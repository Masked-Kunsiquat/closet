# Migration conventions

Rules for writing Room database migrations in this project.
Reference: https://developer.android.com/training/data-storage/room/migrating-db-versions

---

## The rules

**Never edit an applied migration.**
Once a migration file is committed and could be on a real device, it is permanent.
Add a new migration instead.

**Always bump `@Database(version = N)` in `ClothingDatabase.kt`.**
Room will crash on open if the entity schema version doesn't match the registered migration chain.

**Always register new migrations in `ClothingDatabase.addMigrations(...)`.**
Forgetting this causes an `IllegalStateException` on open.

**Write self-contained SQL — no references to Kotlin constants or helper classes.**
If a constant is later renamed or a seeder method changes, old migration SQL silently breaks.
Exception: the `columnExists()` helper in `MigrationHelpers.kt` is safe to use as a guard,
but only in migrations where the column state is genuinely uncertain (avoid in new work —
if the schema version is correct, columns either exist or they don't).

**Run the KSP task after every version bump to regenerate the schema JSON.**
```bash
./gradlew kspDebugKotlin
```
Commit the generated `core/data/schemas/com.closet.core.data.ClothingDatabase/<version>.json`
alongside the migration file. Room uses these files to validate migrations in tests.

**Do not call `DatabaseSeeder` methods from inside migrations.**
Seeder logic can change over time. Inline the INSERT statements you need directly in the
migration SQL so the migration behaviour is frozen at the point it was written.

**Do not use `fallbackToDestructiveMigration()` in production builds.**
This silently wipes all user data when a migration path is missing. It is never acceptable.

---

## Partial indexes and `onOpen`

Room cannot represent SQLite partial indexes (e.g. `WHERE is_ootd = 1`) in `@Entity`
annotations. If a partial index is placed in `onCreate` it will appear in the actual DB
but not in Room's expected schema, causing a crash on the first migration validation after.

Rule: **all partial indexes belong in `ClothingDatabase.onOpen()`** using `CREATE ... IF NOT EXISTS`.
Any migration that touches a table with a partial index must `DROP` the index first
so Room's post-migration schema check passes clean. `onOpen` recreates it immediately after.

Current partial index managed this way: `one_ootd_per_day` on `outfit_logs`.

---

## Writing a new migration — checklist

1. Create `MigrationNToN+1.kt` in this package.
2. Write an `val MIGRATION_N_N1 = object : Migration(N, N+1) { ... }`.
3. If any table has a partial index, `DROP INDEX IF EXISTS <name>` at the top of `migrate()`.
4. Use literal SQL strings. No Kotlin string interpolation of variable values.
5. Add `MIGRATION_N_N1` to `ClothingDatabase.addMigrations(...)`.
6. Bump `@Database(version = N+1)`.
7. Run `./gradlew kspDebugKotlin` — commit the generated schema JSON.
8. Add a test case in `MigrationTest.kt`:
   - `helper.createDatabase(TEST_DB, N)` — optionally seed realistic data
   - `helper.runMigrationsAndValidate(TEST_DB, N+1, true, MIGRATION_N_N1)`
   - Assert the schema change and any data transformations
   - Update `migrateFullPath1To6` to include the new migration

---

## When to use automated vs manual migrations

| Change | Use |
|--------|-----|
| Add a new table | `@AutoMigration(from, to)` |
| Add a nullable column | `@AutoMigration(from, to)` |
| Rename a table or column | `@AutoMigration` + `@RenameTable` / `@RenameColumn` spec |
| Delete a table or column | `@AutoMigration` + `@DeleteTable` / `@DeleteColumn` spec |
| Backfill data, create indexes, split tables | Manual `Migration` class |
| Anything involving a partial index | Manual `Migration` class (see above) |

This project has used manual migrations exclusively so far. AutoMigration is fine for future
additive changes — just add `autoMigrations = [AutoMigration(from = N, to = N+1)]` to the
`@Database` annotation and skip writing a migration file.

---

## Running the migration tests

Migration tests are instrumented (they run on a device or emulator). They do **not** run
on Gradle sync or during a normal build.

```bash
# Run migration tests only
./gradlew :core:data:connectedAndroidTest

# Run all instrumented tests across the project
./gradlew connectedAndroidTest
```

Tests live in `core/data/src/androidTest/kotlin/com/closet/core/data/MigrationTest.kt`.
Run them before opening any PR that touches the database schema.
