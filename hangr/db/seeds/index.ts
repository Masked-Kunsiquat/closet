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
// All others are independent.

export async function runSeeds(db: SQLiteDatabase): Promise<void> {
  await seedCategories(db);
  await seedSeasons(db);
  await seedOccasions(db);
  await seedMaterials(db);
  await seedPatterns(db);
  await seedSizes(db);
  await seedColors(db);
}
