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
