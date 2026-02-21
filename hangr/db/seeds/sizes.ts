import { SQLiteDatabase } from 'expo-sqlite';

const SIZE_SYSTEMS: { name: string; values: string[] }[] = [
  {
    name: 'Letter',
    values: ['XS', 'S', 'M', 'L', 'XL', 'XXL', 'XXXL'],
  },
  {
    name: "Women's Numeric",
    values: ['00', '0', '2', '4', '6', '8', '10', '12', '14', '16'],
  },
  {
    name: 'Shoes (US Men\'s)',
    values: [
      '6', '6.5', '7', '7.5', '8', '8.5', '9', '9.5',
      '10', '10.5', '11', '11.5', '12', '13', '14', '15',
    ],
  },
  {
    name: "Shoes (US Women's)",
    values: [
      '5', '5.5', '6', '6.5', '7', '7.5',
      '8', '8.5', '9', '9.5', '10', '11',
    ],
  },
  {
    name: 'Shoes (EU)',
    values: [
      '35', '36', '37', '38', '39', '40',
      '41', '42', '43', '44', '45', '46', '47', '48',
    ],
  },
  {
    name: 'Shoes (UK)',
    values: [
      '3', '3.5', '4', '4.5', '5', '5.5', '6', '6.5', '7', '7.5',
      '8', '8.5', '9', '9.5', '10', '10.5', '11', '11.5', '12', '13', '14',
    ],
  },
  {
    name: 'Bra',
    values: [
      '30A', '30B', '30C', '30D',
      '32A', '32B', '32C', '32D',
      '34A', '34B', '34C', '34D', '34DD',
      '36A', '36B', '36C', '36D', '36DD',
      '38B', '38C', '38D', '38DD',
    ],
  },
  {
    name: 'One Size',
    values: ['One Size'],
  },
];

/**
 * Seeds the database with predefined size systems and their ordered values.
 *
 * Runs all insertions inside a single transaction. For each system, it ensures a size_system row exists (duplicates are ignored) and inserts each associated value with a 1-based sort order; duplicate values are ignored. If a system's row cannot be located after insertion, its values are skipped.
 */
export async function seedSizes(db: SQLiteDatabase): Promise<void> {
  await db.withTransactionAsync(async () => {
    for (const system of SIZE_SYSTEMS) {
      await db.runAsync(
        `INSERT OR IGNORE INTO size_systems (name) VALUES (?)`,
        [system.name]
      );

      const row = await db.getFirstAsync<{ id: number }>(
        `SELECT id FROM size_systems WHERE name = ?`,
        [system.name]
      );
      if (!row) continue;

      for (const [i, value] of system.values.entries()) {
        await db.runAsync(
          `INSERT OR IGNORE INTO size_values (size_system_id, value, sort_order) VALUES (?, ?, ?)`,
          [row.id, value, i + 1]
        );
      }
    }
  });
}