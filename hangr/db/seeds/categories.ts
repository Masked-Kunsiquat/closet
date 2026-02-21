import { SQLiteDatabase } from 'expo-sqlite';

const CATEGORIES = [
  {
    name: 'Tops',
    icon: 't-shirt',
    sort_order: 1,
    subcategories: [
      'T-Shirt',
      'Tank Top',
      'Blouse',
      'Shirt',
      'Polo',
      'Sweater',
      'Hoodie',
      'Sweatshirt',
      'Cardigan',
      'Bodysuit',
    ],
  },
  {
    name: 'Bottoms',
    icon: 'pants',
    sort_order: 2,
    subcategories: [
      'Jeans',
      'Trousers/Slacks',
      'Chinos',
      'Shorts',
      'Skirt',
      'Leggings',
      'Joggers/Sweatpants',
    ],
  },
  {
    name: 'Outerwear',
    icon: 'hoodie',
    sort_order: 3,
    subcategories: ['Jacket', 'Coat', 'Blazer', 'Vest', 'Raincoat'],
  },
  {
    name: 'Dresses & Jumpsuits',
    icon: 'dress',
    sort_order: 4,
    subcategories: ['Dress', 'Romper', 'Jumpsuit'],
  },
  {
    name: 'Footwear',
    icon: 'sneaker',
    sort_order: 5,
    subcategories: ['Sneakers', 'Boots', 'Sandals', 'Dress Shoes', 'Slippers'],
  },
  {
    name: 'Accessories',
    icon: 'watch',
    sort_order: 6,
    subcategories: [
      'Belt',
      'Hat/Cap',
      'Scarf',
      'Sunglasses',
      'Watch',
      'Jewelry',
      'Tie',
      'Cufflinks',
    ],
  },
  {
    name: 'Bags',
    icon: 'handbag',
    sort_order: 7,
    subcategories: ['Backpack', 'Tote', 'Crossbody', 'Duffel'],
  },
  {
    name: 'Activewear',
    icon: 'person-simple-run',
    sort_order: 8,
    subcategories: ['Sports Bra', 'Athletic Shorts', 'Track Jacket'],
  },
  {
    name: 'Underwear & Intimates',
    icon: 'sock',
    sort_order: 9,
    subcategories: ['Underwear', 'Bra/Bralette', 'Socks', 'Tights'],
  },
  {
    name: 'Swimwear',
    icon: 'goggles',
    sort_order: 10,
    subcategories: ['One-Piece', 'Bikini/Trunks', 'Rash Guard'],
  },
] as const;

/**
 * Seed the database with the predefined categories and their subcategories from CATEGORIES.
 *
 * Runs all operations inside a transaction and ensures category and subcategory records exist;
 * subcategories are inserted with a 1-based sort order corresponding to their position in each category's list.
 */
export async function seedCategories(db: SQLiteDatabase): Promise<void> {
  await db.withTransactionAsync(async () => {
    for (const cat of CATEGORIES) {
      await db.runAsync(
        `INSERT OR IGNORE INTO categories (name, icon, sort_order) VALUES (?, ?, ?)`,
        [cat.name, cat.icon, cat.sort_order]
      );

      // Fetch the id (works whether just inserted or already existed).
      const row = await db.getFirstAsync<{ id: number }>(
        `SELECT id FROM categories WHERE name = ?`,
        [cat.name]
      );
      if (!row) continue;

      for (const [j, sub] of cat.subcategories.entries()) {
        await db.runAsync(
          `INSERT OR IGNORE INTO subcategories (category_id, name, sort_order) VALUES (?, ?, ?)`,
          [row.id, sub, j + 1]
        );
      }
    }
  });
}