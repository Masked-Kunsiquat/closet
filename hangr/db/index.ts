import { openDatabaseSync } from 'expo-sqlite';

import { runMigrations } from './migrations';

const DB_NAME = 'hangr.db';

// ---------------------------------------------------------------------------
// Open + initialize
//
// Called once at app startup (inside the root layout).
// Returns the db instance for use anywhere via the singleton below.
// ---------------------------------------------------------------------------

let _db: ReturnType<typeof openDatabaseSync> | null = null;

export function getDatabase() {
  if (_db) return _db;

  _db = openDatabaseSync(DB_NAME);

  // Enforce referential integrity on every connection.
  _db.execSync('PRAGMA foreign_keys = ON');

  // Apply any pending schema migrations.
  runMigrations(_db);

  return _db;
}
