# Migration conventions

Rules for Room schema changes in this project.
Reference: https://developer.android.com/training/data-storage/room/migrating-db-versions

`core/data` is Kotlin Multiplatform with Room KMP: `ClothingDatabase`, its entities, DAOs and
`configureClosetDatabase()` (driver, callbacks) live in `commonMain`, and each platform has its own
builder — `ClothingDatabase.getDatabase(context)` in `androidMain`
(`DatabaseBuilder.android.kt`), `getDatabaseBuilder(file)` in `jvmMain`, `getDatabaseBuilder()` in
`iosMain`. Every platform uses the bundled SQLite driver, so migrations and callbacks work on an
`androidx.sqlite.SQLiteConnection`, not a `SupportSQLiteDatabase`. Migration files go in this
package (`commonMain`).

---

## Where things stand

**The database is at version 3.** Version 1 is a squashed baseline: the old 1→6 migration chain
was reset on 2026-11-15 (KMP Phase 3), when hangr had no users, and version 1 is the v6 shape
exactly. The schema exports are `core/data/schemas/com.closet.core.data.ClothingDatabase/<N>.json`.

Migrations in this package:
- `MIGRATION_1_2` (`Migration1To2.kt`, colour v2, #81): measured colour columns on
  `clothing_item_colors` (`hex`, `share`, `source`), `basic_term` and `wears_as_neutral` on
  `colors`, the 4 new colours and the seeded `color_shades` table. Tested by `jvmTest`'s
  `MigrationTest`.
- `MIGRATION_2_3` (`Migration2To3.kt`, re-detect colours, #128): `clothing_items.colors_curated`,
  backfilled to 1 for items with a `user` colour row. Tested by `MigrationTest`.

**Every schema change needs a real migration** — the reset was a one-off.

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
`configureClosetDatabase()` in `ClothingDatabase.kt` (`.addMigrations(...)`), so every platform's
builder gets it.** A missing migration throws `IllegalStateException` on open.

**Write self-contained SQL — no references to Kotlin constants or helper classes.**
If a constant is later renamed or a seeder method changes, old migration SQL silently breaks.

**Run the KSP task after every version bump to regenerate the schema JSON.**
```bash
./gradlew :core:data:kspAndroidMain
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

Rule: **all partial indexes belong in the `onOpen` callback in `configureClosetDatabase()`** using
`CREATE ... IF NOT EXISTS`.

Current partial index managed this way: `one_ootd_per_day` on `outfit_logs`.

**When you write the first migration after the baseline:** `MigrationTestHelper` can leave the
partial index in the pre-migration database, and Room's post-migration validator then fails on an
unexpected index. Handle it the way the old chain did:
- at the top of every `migrate(connection)`, unconditionally `DROP INDEX IF EXISTS one_ootd_per_day`
  (`onOpen` recreates it right after the migration, so runtime behaviour is unchanged);
- in the migration test, drop it on the pre-migration DB before closing it:
  ```kotlin
  val connectionN = helper.createDatabase(N)          // an androidx.sqlite.SQLiteConnection
  connectionN.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")
  // seed data...
  connectionN.close()
  ```

---

## Writing a new migration — checklist

1. Create `MigrationNToN+1.kt` in this package (`commonMain`) with
   `val MIGRATION_N_N1 = object : Migration(N, N+1) { override fun migrate(connection: SQLiteConnection) { ... } }`,
   running SQL with `connection.execSQL(...)` (`androidx.sqlite.execSQL`).
2. At the top of `migrate(connection)`, add `DROP INDEX IF EXISTS one_ootd_per_day` (see above).
3. Use literal SQL strings — no Kotlin string interpolation of variable values.
4. Register it with `.addMigrations(MIGRATION_N_N1)` in `configureClosetDatabase()` (`ClothingDatabase.kt`).
5. Bump `@Database(version = N+1)`.
6. Run `./gradlew :core:data:kspAndroidMain` and commit the generated schema JSON.
7. Add a `MigrationTest` in `core/data/src/jvmTest` using Room KMP's `MigrationTestHelper`
   (`androidx.room:room-testing`; add it to `jvmTest`'s dependencies) with the committed schema
   directory, a temp database file, `BundledSQLiteDriver()` and `ClothingDatabase::class`:
   - `val connectionN = helper.createDatabase(N)` and drop the partial index (see above)
   - seed realistic data, then `connectionN.close()`
   - `helper.runMigrationsAndValidate(N+1, listOf(MIGRATION_N_N1))`
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

Schema, DAO and repository tests run on the JVM (no device needed) and are part of `./gradlew test`:

```bash
./gradlew :core:data:jvmTest
```

`core/data/src/jvmTest/kotlin/com/closet/core/data/FreshInstallSchemaTest.kt` opens a fresh
database file through `getDatabaseBuilder(file)` (the bundled driver and the real
`configureClosetDatabase()` callbacks) and checks it against the newest schema JSON (identity
hash, every table/column/index), plus `PRAGMA foreign_keys`, the seeded lookup rows, the
`one_ootd_per_day` partial index and the category triggers. The `jvmTest` task passes the schema
directory as the `closet.schemaDir` system property. Run it before any PR that touches the schema.

Two Android-only checks stay on the device (`./gradlew :core:data:connectedAndroidDeviceTest`):
`DatabasePathTest` (the file is `closet.db` under `Context.getDatabasePath`) and
`ExistingInstallUpgradeTest` (a database written by the old framework-SQLite build opens in place
with the bundled driver, keeping its rows and seeds).
