import { SQLiteDatabase } from 'expo-sqlite';

// ---------------------------------------------------------------------------
// Migration registry
//
// Add new migrations here, in order. Never remove or reorder entries.
// Each migration runs exactly once; the version is tracked in `user_version`.
// ---------------------------------------------------------------------------

type Migration = {
  version: number;
  up: (db: SQLiteDatabase) => void;
};

const migrations: Migration[] = [
  // 001 will be added when the schema is ready (Phase 1)
  // { version: 1, up: (db) => { db.execSync(`...`); } },
];

// ---------------------------------------------------------------------------
// Runner
// ---------------------------------------------------------------------------

export function runMigrations(db: SQLiteDatabase): void {
  const currentVersion: number = (
    db.getFirstSync<{ user_version: number }>('PRAGMA user_version') ?? {
      user_version: 0,
    }
  ).user_version;

  const pending = migrations.filter((m) => m.version > currentVersion);

  if (pending.length === 0) return;

  for (const migration of pending) {
    db.withTransactionSync(() => {
      migration.up(db);
      db.execSync(`PRAGMA user_version = ${migration.version}`);
    });
  }
}
