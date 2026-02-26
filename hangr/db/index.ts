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
/**
 * Gets the singleton database instance, ensuring foreign keys are enabled and migrations and seeds have run.
 *
 * On first call this opens and prepares the database; subsequent calls return the same in-progress or initialized instance. If initialization fails, internal state is reset so callers can retry.
 *
 * @returns The initialized database instance.
 */

export function getDatabase(): Promise<Db> {
  if (_ready) return _ready;

  _ready = (async () => {
    const t0 = Date.now();
    try {
      _db = openDatabaseSync(DB_NAME);

      // Referential integrity must be on before anything else.
      await _db.execAsync('PRAGMA foreign_keys = ON');

      await runMigrations(_db);
      await runSeeds(_db);

      if (__DEV__) console.log(`[db] initialized in ${Date.now() - t0}ms`);
      return _db;
    } catch (e) {
      if (__DEV__) console.error('[db] init failed', e);
      // Reset so the next call can retry initialization from scratch.
      _db?.closeSync?.();
      _db = null;
      _ready = null;
      throw e;
    }
  })();

  return _ready;
}