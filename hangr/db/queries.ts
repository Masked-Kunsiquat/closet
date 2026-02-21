import { SQLiteDatabase } from 'expo-sqlite';

import {
  Category,
  ClothingItem,
  ClothingItemWithMeta,
  Color,
  Material,
  Occasion,
  Pattern,
  Season,
  SizeSystem,
  SizeValue,
  Subcategory,
} from './types';

// ---------------------------------------------------------------------------
// Clothing items
// ---------------------------------------------------------------------------

export async function getAllClothingItems(
  db: SQLiteDatabase
): Promise<ClothingItemWithMeta[]> {
  return db.getAllAsync<ClothingItemWithMeta>(`
    SELECT
      ci.*,
      c.name  AS category_name,
      sc.name AS subcategory_name,
      (
        SELECT COUNT(DISTINCT ol.id)
        FROM outfit_logs ol
        JOIN outfit_items oi ON ol.outfit_id = oi.outfit_id
        WHERE oi.clothing_item_id = ci.id
      ) AS wear_count
    FROM clothing_items ci
    LEFT JOIN categories    c  ON ci.category_id    = c.id
    LEFT JOIN subcategories sc ON ci.subcategory_id = sc.id
    ORDER BY ci.created_at DESC
  `);
}

export async function getClothingItemById(
  db: SQLiteDatabase,
  id: number
): Promise<ClothingItem | null> {
  return db.getFirstAsync<ClothingItem>(
    `SELECT * FROM clothing_items WHERE id = ?`,
    [id]
  );
}

/** Wear count = number of outfit_logs that include this item via outfit_items. */
export async function getWearCount(
  db: SQLiteDatabase,
  clothingItemId: number
): Promise<number> {
  const row = await db.getFirstAsync<{ count: number }>(
    `SELECT COUNT(DISTINCT ol.id) AS count
     FROM outfit_logs ol
     JOIN outfit_items oi ON ol.outfit_id = oi.outfit_id
     WHERE oi.clothing_item_id = ?`,
    [clothingItemId]
  );
  return row?.count ?? 0;
}

export type NewClothingItem = Omit<ClothingItem, 'id' | 'created_at' | 'updated_at'>;

export async function insertClothingItem(
  db: SQLiteDatabase,
  item: NewClothingItem
): Promise<number> {
  const result = await db.runAsync(
    `INSERT INTO clothing_items
       (name, brand, category_id, subcategory_id, size_value_id,
        waist, inseam, purchase_price, purchase_date, purchase_location,
        image_path, notes, status, wash_status, is_favorite)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      item.name,
      item.brand,
      item.category_id,
      item.subcategory_id,
      item.size_value_id,
      item.waist,
      item.inseam,
      item.purchase_price,
      item.purchase_date,
      item.purchase_location,
      item.image_path,
      item.notes,
      item.status,
      item.wash_status,
      item.is_favorite,
    ]
  );
  return result.lastInsertRowId;
}

export async function updateClothingItem(
  db: SQLiteDatabase,
  id: number,
  item: Partial<NewClothingItem>
): Promise<void> {
  await db.runAsync(
    `UPDATE clothing_items SET
       name              = COALESCE(?, name),
       brand             = ?,
       category_id       = ?,
       subcategory_id    = ?,
       size_value_id     = ?,
       waist             = ?,
       inseam            = ?,
       purchase_price    = ?,
       purchase_date     = ?,
       purchase_location = ?,
       image_path        = COALESCE(?, image_path),
       notes             = ?,
       status            = COALESCE(?, status),
       wash_status       = COALESCE(?, wash_status),
       is_favorite       = COALESCE(?, is_favorite),
       updated_at        = datetime('now')
     WHERE id = ?`,
    [
      item.name ?? null,
      item.brand ?? null,
      item.category_id ?? null,
      item.subcategory_id ?? null,
      item.size_value_id ?? null,
      item.waist ?? null,
      item.inseam ?? null,
      item.purchase_price ?? null,
      item.purchase_date ?? null,
      item.purchase_location ?? null,
      item.image_path ?? null,
      item.notes ?? null,
      item.status ?? null,
      item.wash_status ?? null,
      item.is_favorite ?? null,
      id,
    ]
  );
}

export async function deleteClothingItem(
  db: SQLiteDatabase,
  id: number
): Promise<void> {
  // Junction rows cascade via ON DELETE CASCADE in the schema.
  await db.runAsync(`DELETE FROM clothing_items WHERE id = ?`, [id]);
}

// ---------------------------------------------------------------------------
// Junction table helpers — replace all, not append
// ---------------------------------------------------------------------------

export async function setClothingItemColors(
  db: SQLiteDatabase,
  itemId: number,
  colorIds: number[]
): Promise<void> {
  await db.runAsync(`DELETE FROM clothing_item_colors WHERE clothing_item_id = ?`, [itemId]);
  for (const colorId of colorIds) {
    await db.runAsync(
      `INSERT OR IGNORE INTO clothing_item_colors (clothing_item_id, color_id) VALUES (?, ?)`,
      [itemId, colorId]
    );
  }
}

export async function setClothingItemMaterials(
  db: SQLiteDatabase,
  itemId: number,
  materialIds: number[]
): Promise<void> {
  await db.runAsync(`DELETE FROM clothing_item_materials WHERE clothing_item_id = ?`, [itemId]);
  for (const id of materialIds) {
    await db.runAsync(
      `INSERT OR IGNORE INTO clothing_item_materials (clothing_item_id, material_id) VALUES (?, ?)`,
      [itemId, id]
    );
  }
}

export async function setClothingItemSeasons(
  db: SQLiteDatabase,
  itemId: number,
  seasonIds: number[]
): Promise<void> {
  await db.runAsync(`DELETE FROM clothing_item_seasons WHERE clothing_item_id = ?`, [itemId]);
  for (const id of seasonIds) {
    await db.runAsync(
      `INSERT OR IGNORE INTO clothing_item_seasons (clothing_item_id, season_id) VALUES (?, ?)`,
      [itemId, id]
    );
  }
}

export async function setClothingItemOccasions(
  db: SQLiteDatabase,
  itemId: number,
  occasionIds: number[]
): Promise<void> {
  await db.runAsync(`DELETE FROM clothing_item_occasions WHERE clothing_item_id = ?`, [itemId]);
  for (const id of occasionIds) {
    await db.runAsync(
      `INSERT OR IGNORE INTO clothing_item_occasions (clothing_item_id, occasion_id) VALUES (?, ?)`,
      [itemId, id]
    );
  }
}

export async function setClothingItemPatterns(
  db: SQLiteDatabase,
  itemId: number,
  patternIds: number[]
): Promise<void> {
  await db.runAsync(`DELETE FROM clothing_item_patterns WHERE clothing_item_id = ?`, [itemId]);
  for (const id of patternIds) {
    await db.runAsync(
      `INSERT OR IGNORE INTO clothing_item_patterns (clothing_item_id, pattern_id) VALUES (?, ?)`,
      [itemId, id]
    );
  }
}

export async function getClothingItemColorIds(
  db: SQLiteDatabase,
  itemId: number
): Promise<number[]> {
  const rows = await db.getAllAsync<{ color_id: number }>(
    `SELECT color_id FROM clothing_item_colors WHERE clothing_item_id = ?`,
    [itemId]
  );
  return rows.map((r) => r.color_id);
}

export async function getClothingItemMaterialIds(
  db: SQLiteDatabase,
  itemId: number
): Promise<number[]> {
  const rows = await db.getAllAsync<{ material_id: number }>(
    `SELECT material_id FROM clothing_item_materials WHERE clothing_item_id = ?`,
    [itemId]
  );
  return rows.map((r) => r.material_id);
}

export async function getClothingItemSeasonIds(
  db: SQLiteDatabase,
  itemId: number
): Promise<number[]> {
  const rows = await db.getAllAsync<{ season_id: number }>(
    `SELECT season_id FROM clothing_item_seasons WHERE clothing_item_id = ?`,
    [itemId]
  );
  return rows.map((r) => r.season_id);
}

export async function getClothingItemOccasionIds(
  db: SQLiteDatabase,
  itemId: number
): Promise<number[]> {
  const rows = await db.getAllAsync<{ occasion_id: number }>(
    `SELECT occasion_id FROM clothing_item_occasions WHERE clothing_item_id = ?`,
    [itemId]
  );
  return rows.map((r) => r.occasion_id);
}

export async function getClothingItemPatternIds(
  db: SQLiteDatabase,
  itemId: number
): Promise<number[]> {
  const rows = await db.getAllAsync<{ pattern_id: number }>(
    `SELECT pattern_id FROM clothing_item_patterns WHERE clothing_item_id = ?`,
    [itemId]
  );
  return rows.map((r) => r.pattern_id);
}

// ---------------------------------------------------------------------------
// Lookup tables
// ---------------------------------------------------------------------------

export async function getCategories(db: SQLiteDatabase): Promise<Category[]> {
  return db.getAllAsync<Category>(`SELECT * FROM categories ORDER BY sort_order`);
}

export async function getSubcategories(
  db: SQLiteDatabase,
  categoryId: number
): Promise<Subcategory[]> {
  return db.getAllAsync<Subcategory>(
    `SELECT * FROM subcategories WHERE category_id = ? ORDER BY sort_order`,
    [categoryId]
  );
}

export async function getSeasons(db: SQLiteDatabase): Promise<Season[]> {
  return db.getAllAsync<Season>(`SELECT * FROM seasons ORDER BY id`);
}

export async function getOccasions(db: SQLiteDatabase): Promise<Occasion[]> {
  return db.getAllAsync<Occasion>(`SELECT * FROM occasions ORDER BY id`);
}

export async function getColors(db: SQLiteDatabase): Promise<Color[]> {
  return db.getAllAsync<Color>(`SELECT * FROM colors ORDER BY name`);
}

export async function getMaterials(db: SQLiteDatabase): Promise<Material[]> {
  return db.getAllAsync<Material>(`SELECT * FROM materials ORDER BY name`);
}

export async function getPatterns(db: SQLiteDatabase): Promise<Pattern[]> {
  return db.getAllAsync<Pattern>(`SELECT * FROM patterns ORDER BY name`);
}

export async function getSizeSystems(db: SQLiteDatabase): Promise<SizeSystem[]> {
  return db.getAllAsync<SizeSystem>(`SELECT * FROM size_systems ORDER BY id`);
}

export async function getSizeValues(
  db: SQLiteDatabase,
  systemId: number
): Promise<SizeValue[]> {
  return db.getAllAsync<SizeValue>(
    `SELECT * FROM size_values WHERE size_system_id = ? ORDER BY sort_order`,
    [systemId]
  );
}

// ---------------------------------------------------------------------------
// Junction filter helpers — return item IDs matching a given lookup value
// Used by FilterPanel to resolve color/season/occasion filters in JS
// ---------------------------------------------------------------------------

export async function getItemIdsByColor(
  db: SQLiteDatabase,
  colorId: number
): Promise<number[]> {
  const rows = await db.getAllAsync<{ clothing_item_id: number }>(
    `SELECT clothing_item_id FROM clothing_item_colors WHERE color_id = ?`,
    [colorId]
  );
  return rows.map((r) => r.clothing_item_id);
}

export async function getItemIdsBySeason(
  db: SQLiteDatabase,
  seasonId: number
): Promise<number[]> {
  const rows = await db.getAllAsync<{ clothing_item_id: number }>(
    `SELECT clothing_item_id FROM clothing_item_seasons WHERE season_id = ?`,
    [seasonId]
  );
  return rows.map((r) => r.clothing_item_id);
}

export async function getItemIdsByOccasion(
  db: SQLiteDatabase,
  occasionId: number
): Promise<number[]> {
  const rows = await db.getAllAsync<{ clothing_item_id: number }>(
    `SELECT clothing_item_id FROM clothing_item_occasions WHERE occasion_id = ?`,
    [occasionId]
  );
  return rows.map((r) => r.clothing_item_id);
}

export async function getDistinctBrands(db: SQLiteDatabase): Promise<string[]> {
  const rows = await db.getAllAsync<{ brand: string }>(
    `SELECT DISTINCT brand FROM clothing_items WHERE brand IS NOT NULL AND brand != '' ORDER BY brand`
  );
  return rows.map((r) => r.brand);
}
