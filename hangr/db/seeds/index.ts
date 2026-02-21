import { SQLiteDatabase } from 'expo-sqlite';

import { seedCategories } from './categories';
import { seedColors } from './colors';
import { seedMaterials } from './materials';
import { seedOccasions } from './occasions';
import { seedPatterns } from './patterns';
import { seedSeasons } from './seasons';
import { seedSizes } from './sizes';

// Seeds run after all migrations. Order matters where there are foreign keys.
// Categories must exist before subcategories (handled inside seedCategories).
/**
 * Executes all database seeders in the required order to populate reference data.
 *
 * Runs category seeds first because of foreign-key dependencies, then seasons, occasions, materials, patterns, sizes, and colors. Intended to be called after migrations.
 */

export async function runSeeds(db: SQLiteDatabase): Promise<void> {
  await seedCategories(db);
  await seedSeasons(db);
  await seedOccasions(db);
  await seedMaterials(db);
  await seedPatterns(db);
  await seedSizes(db);
  await seedColors(db);
}