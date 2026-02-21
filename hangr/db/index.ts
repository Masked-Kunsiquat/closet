import { openDatabaseSync } from 'expo-sqlite';

import { runMigrations } from './migrations';
import { runSeeds } from './seeds';

const DB_NAME = 'hangr.db';

type Db = ReturnType<typeof openDatabaseSync>;

let _db: Db | null = null;
let _ready: Promise<Db> | null = null;

// ---------------------------------------------------------------------------
// getDatabase()
//
// Call this anywhere you need the DB. The first call opens the connection,
// runs migrations, and seeds. Subsequent calls return the same instance.
// Always await — the DB is not usable until migrations complete.
// ---------------------------------------------------------------------------

export function getDatabase(): Promise<Db> {
  if (_ready) return _ready;

  _ready = (async () => {
    _db = openDatabaseSync(DB_NAME);

    // Referential integrity must be on before anything else.
    await _db.execAsync('PRAGMA foreign_keys = ON');

    await runMigrations(_db);
    await runSeeds(_db);

    return _db;
  })();

  return _ready;
}
