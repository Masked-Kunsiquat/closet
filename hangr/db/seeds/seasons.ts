import { SQLiteDatabase } from 'expo-sqlite';

const SEASONS = [
  { name: 'Spring',     icon: 'flower'      },
  { name: 'Summer',     icon: 'sun'         },
  { name: 'Fall',       icon: 'leaf'        },
  { name: 'Winter',     icon: 'snowflake'   },
  { name: 'All Season', icon: 'thermometer' },
] as const;

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
