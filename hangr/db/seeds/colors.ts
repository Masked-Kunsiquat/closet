import { SQLiteDatabase } from 'expo-sqlite';

// hex values are display hints only — not enforced, nullable in schema.
const COLORS: { name: string; hex: string | null }[] = [
  { name: 'Black',       hex: '#0A0A0A' },
  { name: 'White',       hex: '#F5F5F5' },
  { name: 'Grey',        hex: '#808080' },
  { name: 'Beige',       hex: '#C8A97A' },
  { name: 'Brown',       hex: '#7B4F2E' },
  { name: 'Tan',         hex: '#C4974F' },
  { name: 'Navy',        hex: '#1B2A4A' },
  { name: 'Blue',        hex: '#2563EB' },
  { name: 'Light Blue',  hex: '#93C5FD' },
  { name: 'Teal',        hex: '#0D9488' },
  { name: 'Green',       hex: '#16A34A' },
  { name: 'Olive',       hex: '#6B7C3A' },
  { name: 'Yellow',      hex: '#EAB308' },
  { name: 'Orange',      hex: '#EA580C' },
  { name: 'Red',         hex: '#DC2626' },
  { name: 'Burgundy',    hex: '#7F1D1D' },
  { name: 'Pink',        hex: '#EC4899' },
  { name: 'Blush',       hex: '#F4A8B8' },
  { name: 'Purple',      hex: '#7C3AED' },
  { name: 'Lavender',    hex: '#C4B5FD' },
  { name: 'Gold',        hex: '#CA8A04' },
  { name: 'Silver',      hex: '#9CA3AF' },
  { name: 'Cream',       hex: '#FEF9EF' },
  { name: 'Charcoal',    hex: '#374151' },
  { name: 'Camel',       hex: '#C19A6B' },
  { name: 'Multicolor',  hex: null      },
];

export async function seedColors(db: SQLiteDatabase): Promise<void> {
  await db.withTransactionAsync(async () => {
    for (const color of COLORS) {
      await db.runAsync(
        `INSERT OR IGNORE INTO colors (name, hex) VALUES (?, ?)`,
        [color.name, color.hex]
      );
    }
  });
}
