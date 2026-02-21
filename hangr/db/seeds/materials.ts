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

export async function seedMaterials(db: SQLiteDatabase): Promise<void> {
  for (const name of MATERIALS) {
    await db.runAsync(
      `INSERT OR IGNORE INTO materials (name) VALUES (?)`,
      [name]
    );
  }
}
