# Migration conventions

Rules for writing Room database migrations in this project.
Reference: https://developer.android.com/training/data-storage/room/migrating-db-versions

---

## The rules

**Never edit an applied migration.**
Once a migration file is committed and shipped, it is permanent.
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
annotations. If they live in `onCreate` they appear in the actual DB but not in Room's
expected schema, causing a crash on the first migration validation.

Rule: **all partial indexes belong in `ClothingDatabase.onOpen()`** using `CREATE ... IF NOT EXISTS`.

**Every migration must `DROP INDEX IF EXISTS <name>` for any partial index at the top of
`migrate()` — even if the migration doesn't touch that table.**

Why "even if": `MigrationTestHelper` calls `onOpen()` on the pre-migration DB during
`createDatabase()`, which creates the partial index. If the migration doesn't drop it,
Room's post-migration validator sees an unexpected index and fails.
`onOpen()` recreates it immediately after migration completes, so runtime behaviour is unchanged.

Current partial index managed this way: `one_ootd_per_day` on `outfit_logs`.

**In migration tests**, also drop the partial index on the pre-migration DB before closing it:
```kotlin
val dbN = helper.createDatabase(TEST_DB, N)
dbN.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")
// seed data...
dbN.close()
```
This prevents the index (written by `onOpen()` during `createDatabase`) from being present
when `runMigrationsAndValidate` opens the same file and runs validation.

---

## Writing a new migration — checklist

1. Create `MigrationNToN+1.kt` in this package.
2. Write `val MIGRATION_N_N1 = object : Migration(N, N+1) { ... }`.
3. At the top of `migrate()`, add `DROP INDEX IF EXISTS one_ootd_per_day` (always, unconditionally).
4. Use literal SQL strings — no Kotlin string interpolation of variable values.
5. Add `MIGRATION_N_N1` to `ClothingDatabase.addMigrations(...)`.
6. Bump `@Database(version = N+1)`.
7. Run `./gradlew kspDebugKotlin` — commit the generated schema JSON.
8. Add a test case in `MigrationTest.kt`:
   - `val dbN = helper.createDatabase(TEST_DB, N)`
   - `dbN.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")` ← always
   - Seed any realistic data needed, then `dbN.close()`
   - `helper.runMigrationsAndValidate(TEST_DB, N+1, true, MIGRATION_N_N1)`
   - Assert the schema change and any data transformations
   - Update the full-path test to include the new migration

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

AutoMigration is preferred for simple additive changes — add
`autoMigrations = [AutoMigration(from = N, to = N+1)]` to the `@Database` annotation
and skip writing a migration file. Still run KSP and commit the schema JSON.

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

---

## Resetting the migration chain (solo dev only)

During rapid early iteration it can make sense to consolidate the migration history rather
than carry a long chain of incrementally-authored migrations. This is only safe when:

- You are the sole user (no shipped installs to protect)
- You can uninstall the app on every device before reinstalling

**How to reset:**
1. Delete all `MigrationNToN+1.kt` files in this package.
2. Delete all schema JSONs in `core/data/schemas/…/`.
3. Set `@Database(version = 1)` and remove `.addMigrations(...)` from `ClothingDatabase`.
4. Run `./gradlew kspDebugKotlin` to regenerate `1.json` from the current entity definitions.
5. Replace the migration test suite with a single `freshInstallCreatesCorrectSchema` test.
6. **Uninstall the app from every device** before reinstalling — Room cannot downgrade.

Once real users exist, this option is gone. From that point on the chain is permanent.
