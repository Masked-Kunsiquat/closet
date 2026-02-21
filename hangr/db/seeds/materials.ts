import { SQLiteDatabase } from 'expo-sqlite';

const MATERIALS = [
  'Cotton',
  'Polyester',
  'Wool',
  'Linen',
  'Silk',
  'Denim',
  'Leather',
  'Faux Leather',
  'Suede',
  'Velvet',
  'Cashmere',
  'Nylon',
  'Spandex/Elastane',
  'Rayon/Viscose',
  'Fleece',
  'Chiffon',
  'Satin',
  'Corduroy',
  'Jersey',
  'Mesh',
  'Modal',
  'Bamboo',
  'Other',
] as const;

/**
 * Seed the materials table with predefined fabric names.
 *
 * Inserts each name from MATERIALS into the `materials` table; duplicate names are ignored so existing rows are not modified.
 */
export async function seedMaterials(db: SQLiteDatabase): Promise<void> {
  await db.withTransactionAsync(async () => {
    for (const name of MATERIALS) {
      await db.runAsync(
        `INSERT OR IGNORE INTO materials (name) VALUES (?)`,
        [name]
      );
    }
  });
}