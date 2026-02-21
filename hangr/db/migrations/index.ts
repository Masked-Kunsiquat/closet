import { SQLiteDatabase } from 'expo-sqlite';

import migration001 from './001_initial_schema';

// ---------------------------------------------------------------------------
// Migration registry
//
// Add new migrations here in order. Never remove or reorder entries.
// Each migration runs exactly once, tracked in the schema_migrations table.
// ---------------------------------------------------------------------------

type Migration = {
  version: number;
  up: (db: SQLiteDatabase) => Promise<void>;
};

const migrations: Migration[] = [migration001];

// ---------------------------------------------------------------------------
// Runner
/**
 * Applies any pending schema migrations to the provided SQLite database and records each applied version.
 *
 * Each migration in the registered list is applied at most once; applied migrations are recorded in
 * the `schema_migrations` table. Each migration is executed inside a transaction so application and
 * recording occur atomically.
 */

export async function runMigrations(db: SQLiteDatabase): Promise<void> {
  // Ensure the tracking table exists before anything else.
  await db.execAsync(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      version    INTEGER PRIMARY KEY,
      applied_at TEXT    NOT NULL DEFAULT (datetime('now'))
    )
  `);

  for (const migration of migrations) {
    const already = await db.getFirstAsync<{ version: number }>(
      'SELECT version FROM schema_migrations WHERE version = ?',
      [migration.version]
    );

    if (already) continue;

    await db.withTransactionAsync(async () => {
      await migration.up(db);
      await db.runAsync(
        'INSERT INTO schema_migrations (version) VALUES (?)',
        [migration.version]
      );
    });
  }
}