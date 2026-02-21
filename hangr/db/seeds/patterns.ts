import { SQLiteDatabase } from 'expo-sqlite';

const PATTERNS = [
  'Solid',
  'Striped',
  'Plaid/Tartan',
  'Checkered',
  'Floral',
  'Geometric',
  'Animal Print',
  'Abstract',
  'Tie-Dye',
  'Camouflage',
  'Paisley',
  'Polka Dot',
  'Houndstooth',
  'Graphic',
  'Color Block',
  'Ombre',
  'Other',
] as const;

/**
 * Populate the patterns table with a predefined set of pattern names.
 *
 * Inserts each name from `PATTERNS` into the `patterns` table using `INSERT OR IGNORE`
 * and runs all inserts inside a single transaction so the operation is atomic.
 */
export async function seedPatterns(db: SQLiteDatabase): Promise<void> {
  await db.withTransactionAsync(async () => {
    for (const name of PATTERNS) {
      await db.runAsync(
        `INSERT OR IGNORE INTO patterns (name) VALUES (?)`,
        [name]
      );
    }
  });
}