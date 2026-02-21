import { SQLiteDatabase } from 'expo-sqlite';

const OCCASIONS = [
  { name: 'Casual',            icon: 'coffee'        },
  { name: 'Work/Business',     icon: 'briefcase'     },
  { name: 'Formal',            icon: 'crown-simple'  },
  { name: 'Athletic',          icon: 'barbell'       },
  { name: 'Loungewear',        icon: 'couch'         },
  { name: 'Date Night',        icon: 'heart'         },
  { name: 'Vacation',          icon: 'island'        },
  { name: 'Outdoor/Hiking',    icon: 'mountains'     },
  { name: 'Special Occasion',  icon: 'cheers'        },
] as const;

/**
 * Seeds the `occasions` table with a predefined set of occasion records.
 *
 * Inserts each occasion (name and icon) unless a duplicate exists; all inserts are performed inside a single transaction to ensure atomicity.
 */
export async function seedOccasions(db: SQLiteDatabase): Promise<void> {
  await db.withTransactionAsync(async () => {
    for (const row of OCCASIONS) {
      await db.runAsync(
        `INSERT OR IGNORE INTO occasions (name, icon) VALUES (?, ?)`,
        [row.name, row.icon]
      );
    }
  });
}