# Migration conventions

Rules for Room schema changes in this project.
Reference: https://developer.android.com/training/data-storage/room/migrating-db-versions

---

## Where things stand

**The database is at version 1, a squashed baseline.** The old 1→6 migration chain was reset
on 2026-11-15 (KMP Phase 3), when hangr had no users. Version 1 is the v6 shape exactly; its
schema export is `core/data/schemas/com.closet.core.data.ClothingDatabase/1.json`.

There are no migration files in this package yet. **Every schema change from here on needs a
real migration** — the reset was a one-off.

Side effects of the reset: a device with an older dev install (version 6) must uninstall before
installing a version-1 build (Room refuses to downgrade), and `.hangr` backups made before the
reset are rejected by `RestoreRepository`'s newer-schema check.

---

## The rules

**Never change an entity without a migration.**
Version 1 is the first released schema. Changing an `@Entity` without bumping the version makes
Room fail on open with an identity-hash mismatch.

**Never edit an applied migration.**
Once a migration file is committed and could be on a real device, it is permanent. Add a new one.

**Always bump `@Database(version = N)` in `ClothingDatabase.kt`, and register the migration in
`ClothingDatabase`'s builder (`.addMigrations(...)`).** A missing migration throws
`IllegalStateException` on open.

**Write self-contained SQL — no references to Kotlin constants or helper classes.**
If a constant is later renamed or a seeder method changes, old migration SQL silently breaks.

**Run the KSP task after every version bump to regenerate the schema JSON.**
```bash
./gradlew kspDebugKotlin
```
Commit the generated `core/data/schemas/com.closet.core.data.ClothingDatabase/<version>.json`
alongside the migration file. Room uses these files to validate migrations in tests, and
`FreshInstallSchemaTest` checks the newest one against a fresh install.

**Do not call `DatabaseSeeder` methods from inside migrations.**
Seeder logic can change over time. Inline the INSERT statements you need directly in the
migration SQL so the migration behaviour is frozen at the point it was written.

**Do not use `fallbackToDestructiveMigration()` in production builds.**
This silently wipes all user data when a migration path is missing. It is never acceptable.

---

## Partial indexes and `onOpen`

Room cannot represent SQLite partial indexes (e.g. `WHERE is_ootd = 1`) in `@Entity`
annotations. If they live in `onCreate` they appear in the actual DB but not in Room's
expected schema, which breaks migration validation.

Rule: **all partial indexes belong in `ClothingDatabase.onOpen()`** using `CREATE ... IF NOT EXISTS`.

Current partial index managed this way: `one_ootd_per_day` on `outfit_logs`.

**When you write the first migration after the baseline:** `MigrationTestHelper` can leave the
partial index in the pre-migration database, and Room's post-migration validator then fails on an
unexpected index. Handle it the way the old chain did:
- at the top of every `migrate()`, unconditionally `DROP INDEX IF EXISTS one_ootd_per_day`
  (`onOpen()` recreates it right after the migration, so runtime behaviour is unchanged);
- in the migration test, drop it on the pre-migration DB before closing it:
  ```kotlin
  val dbN = helper.createDatabase(TEST_DB, N)
  dbN.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")
  // seed data...
  dbN.close()
  ```

---

## Writing a new migration — checklist

1. Create `MigrationNToN+1.kt` in this package with `val MIGRATION_N_N1 = object : Migration(N, N+1) { ... }`.
2. At the top of `migrate()`, add `DROP INDEX IF EXISTS one_ootd_per_day` (see above).
3. Use literal SQL strings — no Kotlin string interpolation of variable values.
4. Register it with `.addMigrations(MIGRATION_N_N1)` in `ClothingDatabase.getDatabase()`.
5. Bump `@Database(version = N+1)`.
6. Run `./gradlew kspDebugKotlin` and commit the generated schema JSON.
7. Add a `MigrationTest` (instrumented, `core/data/src/androidTest`) using `MigrationTestHelper`:
   - `val dbN = helper.createDatabase(TEST_DB, N)` and drop the partial index (see above)
   - seed realistic data, then `dbN.close()`
   - `helper.runMigrationsAndValidate(TEST_DB, N+1, true, MIGRATION_N_N1)`
   - assert the schema change and any data transformations
8. Update `FreshInstallSchemaTest` to read the new `<N+1>.json` and expect version `N+1`.

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

## Running the schema tests

Schema tests are instrumented (they run on a device or emulator), not part of `./gradlew test`.

```bash
./gradlew :core:data:connectedDebugAndroidTest
```

`core/data/src/androidTest/kotlin/com/closet/core/data/FreshInstallSchemaTest.kt` opens a fresh
database through `ClothingDatabase.getDatabase()` and checks it against the newest schema JSON
(identity hash, every table/column/index), plus `PRAGMA foreign_keys`, the seeded lookup rows,
the `one_ootd_per_day` partial index and the category triggers. Run it before any PR that touches
the schema. When `core/data` moves to KMP it moves to `jvmTest`.
