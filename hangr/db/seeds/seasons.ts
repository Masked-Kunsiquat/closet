import { SQLiteDatabase } from 'expo-sqlite';

const SEASONS = [
  { name: 'Spring',     icon: 'flower'      },
  { name: 'Summer',     icon: 'sun'         },
  { name: 'Fall',       icon: 'leaf'        },
  { name: 'Winter',     icon: 'snowflake'   },
  { name: 'All Season', icon: 'thermometer' },
] as const;

/**
 * Populate the `seasons` table with predefined season entries.
 *
 * Runs all inserts inside a single transaction and uses `INSERT OR IGNORE` so calling it multiple times does not create duplicates.
 */
export async function seedSeasons(db: SQLiteDatabase): Promise<void> {
  await db.withTransactionAsync(async () => {
    for (const row of SEASONS) {
      await db.runAsync(
        `INSERT OR IGNORE INTO seasons (name, icon) VALUES (?, ?)`,
        [row.name, row.icon]
      );
    }
  });
}