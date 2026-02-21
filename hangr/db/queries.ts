import { SQLiteDatabase } from 'expo-sqlite';

import {
  BreakdownRow,
  CalendarDay,
  Category,
  ClothingItem,
  ClothingItemWithMeta,
  Color,
  ColorBreakdownRow,
  Material,
  Occasion,
  Outfit,
  OutfitLog,
  OutfitLogWithMeta,
  OutfitWithItems,
  OutfitWithMeta,
  Pattern,
  Season,
  SizeSystem,
  SizeValue,
  StatItem,
  StatsOverview,
  Subcategory,
} from './types';

// ---------------------------------------------------------------------------
// Clothing items
/**
 * Fetches all clothing items including category/subcategory names and a computed wear count, ordered by newest first.
 *
 * Each returned item includes the original clothing item fields plus:
 * - `category_name`: the category's name or null
 * - `subcategory_name`: the subcategory's name or null
 * - `wear_count`: the number of distinct outfit logs that reference the item
 *
 * @returns An array of ClothingItemWithMeta objects with the additional meta fields described above
 */

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

/**
 * Fetches a clothing item by its id, enriched with category/subcategory names and a wear count.
 *
 * @returns The clothing item including `category_name`, `subcategory_name`, and `wear_count` (the number of distinct outfit logs that reference the item), or `null` if no item with the given id exists.
 */
export async function getClothingItemById(
  db: SQLiteDatabase,
  id: number
): Promise<ClothingItemWithMeta | null> {
  return db.getFirstAsync<ClothingItemWithMeta>(`
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
    WHERE ci.id = ?
  `, [id]);
}


export type NewClothingItem = Omit<ClothingItem, 'id' | 'created_at' | 'updated_at'>;

/**
 * Inserts a new clothing item into the database.
 *
 * @param item - Fields for the clothing item to create
 * @returns The new clothing item's row id
 */
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

/**
 * Update an existing clothing item’s fields by id.
 *
 * The provided `item` fields overwrite stored values; `updated_at` is set to the current time.
 * For the following fields, passing `null` preserves the existing value instead of replacing it:
 * `name`, `image_path`, `status`, `wash_status`, and `is_favorite`.
 *
 * @param db - The SQLite database instance
 * @param id - The id of the clothing item to update
 * @param item - Partial fields to update on the clothing item
 */
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
       image_path        = ?,
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

/**
 * Delete a clothing item by its id.
 *
 * Removes the row from `clothing_items`; related junction rows are removed by the schema's ON DELETE CASCADE.
 *
 * @param id - The id of the clothing item to delete
 */
export async function deleteClothingItem(
  db: SQLiteDatabase,
  id: number
): Promise<void> {
  // Junction rows cascade via ON DELETE CASCADE in the schema.
  await db.runAsync(`DELETE FROM clothing_items WHERE id = ?`, [id]);
}

// ---------------------------------------------------------------------------
// Junction table helpers — replace all, not append
/**
 * Replace the set of colors associated with a clothing item.
 *
 * Performs the replacement atomically: existing color links for the item are removed and then the provided
 * color IDs are associated with the item inside a single transaction.
 *
 * @param itemId - The clothing item ID to update
 * @param colorIds - Array of color IDs to associate with the clothing item
 */

export async function setClothingItemColors(
  db: SQLiteDatabase,
  itemId: number,
  colorIds: number[]
): Promise<void> {
  await db.withTransactionAsync(async () => {
    await db.runAsync(`DELETE FROM clothing_item_colors WHERE clothing_item_id = ?`, [itemId]);
    for (const colorId of colorIds) {
      await db.runAsync(
        `INSERT OR IGNORE INTO clothing_item_colors (clothing_item_id, color_id) VALUES (?, ?)`,
        [itemId, colorId]
      );
    }
  });
}

/**
 * Replace the materials linked to a clothing item with the provided set.
 *
 * Performs the replacement inside a transaction: clears existing material links for the item
 * and inserts a link for each id in `materialIds`. Duplicate links are ignored.
 *
 * @param itemId - The clothing item id whose material associations will be replaced
 * @param materialIds - Array of material ids to associate with the clothing item
 */
export async function setClothingItemMaterials(
  db: SQLiteDatabase,
  itemId: number,
  materialIds: number[]
): Promise<void> {
  await db.withTransactionAsync(async () => {
    await db.runAsync(`DELETE FROM clothing_item_materials WHERE clothing_item_id = ?`, [itemId]);
    for (const id of materialIds) {
      await db.runAsync(
        `INSERT OR IGNORE INTO clothing_item_materials (clothing_item_id, material_id) VALUES (?, ?)`,
        [itemId, id]
      );
    }
  });
}

/**
 * Replace the seasons associated with a clothing item in a single transaction.
 *
 * @param itemId - The clothing item id whose season associations will be replaced
 * @param seasonIds - Array of season ids to associate with the clothing item; existing associations will be removed
 */
export async function setClothingItemSeasons(
  db: SQLiteDatabase,
  itemId: number,
  seasonIds: number[]
): Promise<void> {
  await db.withTransactionAsync(async () => {
    await db.runAsync(`DELETE FROM clothing_item_seasons WHERE clothing_item_id = ?`, [itemId]);
    for (const id of seasonIds) {
      await db.runAsync(
        `INSERT OR IGNORE INTO clothing_item_seasons (clothing_item_id, season_id) VALUES (?, ?)`,
        [itemId, id]
      );
    }
  });
}

/**
 * Replace the occasions linked to a clothing item with the provided set of occasion IDs.
 *
 * Performs the replacement in a single transaction: existing occasion links for the item are removed and then the supplied `occasionIds` are inserted.
 *
 * @param itemId - The ID of the clothing item to update
 * @param occasionIds - Array of occasion IDs that should be associated with the clothing item
 */
export async function setClothingItemOccasions(
  db: SQLiteDatabase,
  itemId: number,
  occasionIds: number[]
): Promise<void> {
  await db.withTransactionAsync(async () => {
    await db.runAsync(`DELETE FROM clothing_item_occasions WHERE clothing_item_id = ?`, [itemId]);
    for (const id of occasionIds) {
      await db.runAsync(
        `INSERT OR IGNORE INTO clothing_item_occasions (clothing_item_id, occasion_id) VALUES (?, ?)`,
        [itemId, id]
      );
    }
  });
}

/**
 * Replace a clothing item's associated patterns with the provided list.
 *
 * This clears any existing pattern links for the given clothing item and then inserts links for each `patternId`. If `patternIds` is empty, all pattern associations for the item are removed.
 *
 * @param itemId - The id of the clothing item to update
 * @param patternIds - Array of pattern ids to associate with the clothing item
 */
export async function setClothingItemPatterns(
  db: SQLiteDatabase,
  itemId: number,
  patternIds: number[]
): Promise<void> {
  await db.withTransactionAsync(async () => {
    await db.runAsync(`DELETE FROM clothing_item_patterns WHERE clothing_item_id = ?`, [itemId]);
    for (const id of patternIds) {
      await db.runAsync(
        `INSERT OR IGNORE INTO clothing_item_patterns (clothing_item_id, pattern_id) VALUES (?, ?)`,
        [itemId, id]
      );
    }
  });
}

/**
 * Fetches the IDs of colors associated with a clothing item.
 *
 * @param itemId - The clothing item ID to look up
 * @returns An array of `color_id` values linked to the specified clothing item (empty if none)
 */
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

/**
 * Fetches material IDs associated with a clothing item.
 *
 * @param itemId - The clothing item ID to retrieve material IDs for
 * @returns An array of material IDs associated with the clothing item (empty if none)
 */
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

/**
 * Fetches season IDs associated with a clothing item.
 *
 * @returns An array of season IDs linked to the specified clothing item.
 */
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

/**
 * Retrieves all occasion IDs associated with a clothing item.
 *
 * @param itemId - The clothing item's id
 * @returns An array of occasion ids linked to the specified clothing item
 */
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

/**
 * Fetches pattern IDs associated with a clothing item.
 *
 * @param itemId - The clothing item ID to retrieve pattern IDs for
 * @returns An array of pattern IDs linked to the clothing item (empty if none)
 */
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
/**
 * Fetches all category records ordered by their `sort_order`.
 *
 * @returns An array of Category objects ordered by `sort_order`
 */

export async function getCategories(db: SQLiteDatabase): Promise<Category[]> {
  return db.getAllAsync<Category>(`SELECT * FROM categories ORDER BY sort_order`);
}

/**
 * Fetches all subcategories for the specified category ordered by `sort_order`.
 *
 * @param categoryId - The parent category's identifier
 * @returns An array of `Subcategory` rows for the given category, ordered by `sort_order`
 */
export async function getSubcategories(
  db: SQLiteDatabase,
  categoryId: number
): Promise<Subcategory[]> {
  return db.getAllAsync<Subcategory>(
    `SELECT * FROM subcategories WHERE category_id = ? ORDER BY sort_order`,
    [categoryId]
  );
}

/**
 * Fetches all season records from the database.
 *
 * @returns All seasons ordered by `id`.
 */
export async function getSeasons(db: SQLiteDatabase): Promise<Season[]> {
  return db.getAllAsync<Season>(`SELECT * FROM seasons ORDER BY id`);
}

/**
 * Fetches all occasion lookup records ordered by `id`.
 *
 * @returns An array of `Occasion` records sorted by `id`.
 */
export async function getOccasions(db: SQLiteDatabase): Promise<Occasion[]> {
  return db.getAllAsync<Occasion>(`SELECT * FROM occasions ORDER BY id`);
}

/**
 * Retrieves all color records ordered by name.
 *
 * @returns An array of Color records ordered by name
 */
export async function getColors(db: SQLiteDatabase): Promise<Color[]> {
  return db.getAllAsync<Color>(`SELECT * FROM colors ORDER BY name`);
}

/**
 * Retrieves all material lookup records ordered by name.
 *
 * @returns An array of Material records ordered by `name`.
 */
export async function getMaterials(db: SQLiteDatabase): Promise<Material[]> {
  return db.getAllAsync<Material>(`SELECT * FROM materials ORDER BY name`);
}

/**
 * Fetches all pattern lookup records ordered by name.
 *
 * @returns An array of Pattern records ordered by `name`
 */
export async function getPatterns(db: SQLiteDatabase): Promise<Pattern[]> {
  return db.getAllAsync<Pattern>(`SELECT * FROM patterns ORDER BY name`);
}

/**
 * Retrieve all size systems ordered by `id`.
 *
 * @returns An array of size system records ordered by `id`
 */
export async function getSizeSystems(db: SQLiteDatabase): Promise<SizeSystem[]> {
  return db.getAllAsync<SizeSystem>(`SELECT * FROM size_systems ORDER BY id`);
}

/**
 * Fetches all size values belonging to a specific size system.
 *
 * @param systemId - The id of the size system whose values should be returned
 * @returns The list of size values for the specified size system, ordered by `sort_order`
 */
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
/**
 * Fetches the IDs of clothing items associated with the specified color.
 *
 * @param colorId - The ID of the color to filter by
 * @returns An array of clothing item IDs linked to the given color
 */

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

/**
 * Get IDs of clothing items linked to the specified season.
 *
 * @param seasonId - The season's id to filter clothing items by
 * @returns An array of clothing item IDs associated with the given season
 */
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

/**
 * Fetches IDs of clothing items associated with the specified occasion.
 *
 * @param occasionId - The occasion's id to filter clothing items by
 * @returns An array of clothing item IDs linked to the given occasion
 */
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

/**
 * Retrieve distinct non-empty brand names from clothing items.
 *
 * @returns An array of distinct, non-null, non-empty brand strings from the clothing_items table, ordered by brand.
 */
export async function getDistinctBrands(db: SQLiteDatabase): Promise<string[]> {
  const rows = await db.getAllAsync<{ brand: string }>(
    `SELECT DISTINCT brand FROM clothing_items WHERE brand IS NOT NULL AND brand != '' ORDER BY brand`
  );
  return rows.map((r) => r.brand);
}

// ---------------------------------------------------------------------------
// Outfits
/**
 * Fetches all outfits with aggregated metadata.
 *
 * Returns each outfit augmented with `item_count` (number of linked clothing items) and `cover_image` (an image_path from any linked item, if present). Results are ordered by `created_at` descending.
 *
 * @returns An array of `OutfitWithMeta` objects containing outfit fields plus `item_count` and optional `cover_image`.
 */

export async function getAllOutfits(db: SQLiteDatabase): Promise<OutfitWithMeta[]> {
  return db.getAllAsync<OutfitWithMeta>(`
    SELECT
      o.*,
      COUNT(oi.clothing_item_id)                              AS item_count,
      (SELECT ci.image_path
       FROM outfit_items oi2
       JOIN clothing_items ci ON ci.id = oi2.clothing_item_id
       WHERE oi2.outfit_id = o.id AND ci.image_path IS NOT NULL
       LIMIT 1)                                               AS cover_image
    FROM outfits o
    LEFT JOIN outfit_items oi ON oi.outfit_id = o.id
    GROUP BY o.id
    ORDER BY o.created_at DESC
  `);
}

/**
 * Retrieves a single outfit and its associated clothing items with metadata.
 *
 * The returned outfit includes an `items` array where each item contains category and subcategory names and a `wear_count`.
 *
 * @param outfitId - ID of the outfit to fetch
 * @returns The outfit augmented with its `items` metadata, or `null` if no outfit with the given ID exists
 */
export async function getOutfitWithItems(
  db: SQLiteDatabase,
  outfitId: number
): Promise<OutfitWithItems | null> {
  const outfit = await db.getFirstAsync<Outfit>(
    `SELECT * FROM outfits WHERE id = ?`,
    [outfitId]
  );
  if (!outfit) return null;

  const items = await db.getAllAsync<ClothingItemWithMeta>(`
    SELECT
      ci.*,
      c.name  AS category_name,
      sc.name AS subcategory_name,
      (
        SELECT COUNT(DISTINCT ol.id)
        FROM outfit_logs ol
        JOIN outfit_items oi2 ON ol.outfit_id = oi2.outfit_id
        WHERE oi2.clothing_item_id = ci.id
      ) AS wear_count
    FROM outfit_items oi
    JOIN clothing_items ci ON ci.id = oi.clothing_item_id
    LEFT JOIN categories    c  ON ci.category_id    = c.id
    LEFT JOIN subcategories sc ON ci.subcategory_id = sc.id
    WHERE oi.outfit_id = ?
    ORDER BY c.sort_order, ci.name
  `, [outfitId]);

  return { ...outfit, items };
}

export type NewOutfit = { name: string | null; notes: string | null };

/**
 * Create a new outfit and associate it with the provided clothing items.
 *
 * @param outfit - Object with `name` and `notes` fields to store on the new outfit
 * @param itemIds - Array of clothing item IDs to link to the outfit; duplicate links are ignored
 * @returns The ID of the newly created outfit
 */
export async function insertOutfit(
  db: SQLiteDatabase,
  outfit: NewOutfit,
  itemIds: number[]
): Promise<number> {
  let outfitId = 0;
  await db.withTransactionAsync(async () => {
    const result = await db.runAsync(
      `INSERT INTO outfits (name, notes) VALUES (?, ?)`,
      [outfit.name, outfit.notes]
    );
    outfitId = result.lastInsertRowId;
    for (const itemId of itemIds) {
      await db.runAsync(
        `INSERT OR IGNORE INTO outfit_items (outfit_id, clothing_item_id) VALUES (?, ?)`,
        [outfitId, itemId]
      );
    }
  });
  return outfitId;
}

/**
 * Update an outfit's name and notes and replace its set of items atomically.
 *
 * @param db - The database instance
 * @param outfitId - The ID of the outfit to update
 * @param outfit - Object containing the new `name` and `notes` for the outfit
 * @param itemIds - Array of clothing item IDs to associate with the outfit; existing associations are replaced
 */
export async function updateOutfit(
  db: SQLiteDatabase,
  outfitId: number,
  outfit: NewOutfit,
  itemIds: number[]
): Promise<void> {
  await db.withTransactionAsync(async () => {
    await db.runAsync(
      `UPDATE outfits SET name = ?, notes = ?, updated_at = datetime('now') WHERE id = ?`,
      [outfit.name, outfit.notes, outfitId]
    );
    await db.runAsync(`DELETE FROM outfit_items WHERE outfit_id = ?`, [outfitId]);
    for (const itemId of itemIds) {
      await db.runAsync(
        `INSERT OR IGNORE INTO outfit_items (outfit_id, clothing_item_id) VALUES (?, ?)`,
        [outfitId, itemId]
      );
    }
  });
}

/**
 * Deletes an outfit by id.
 *
 * Also removes any outfit_items linked to the outfit.
 *
 * @param outfitId - The id of the outfit to delete
 */
export async function deleteOutfit(db: SQLiteDatabase, outfitId: number): Promise<void> {
  // outfit_items cascade via ON DELETE CASCADE
  await db.runAsync(`DELETE FROM outfits WHERE id = ?`, [outfitId]);
}

// ---------------------------------------------------------------------------
// Outfit logs
/**
 * Fetches all outfit logs for a specific date, including related metadata.
 *
 * @param date - Date string in `YYYY-MM-DD` format to filter logs.
 * @returns An array of outfit logs augmented with `outfit_name`, `item_count`, and `cover_image`, ordered by `is_ootd` (descending) then `created_at` (ascending).
 */

export async function getLogsByDate(
  db: SQLiteDatabase,
  date: string
): Promise<OutfitLogWithMeta[]> {
  return db.getAllAsync<OutfitLogWithMeta>(`
    SELECT
      ol.*,
      o.name AS outfit_name,
      COUNT(oi.clothing_item_id) AS item_count,
      (SELECT ci.image_path
       FROM outfit_items oi2
       JOIN clothing_items ci ON ci.id = oi2.clothing_item_id
       WHERE oi2.outfit_id = ol.outfit_id AND ci.image_path IS NOT NULL
       LIMIT 1) AS cover_image
    FROM outfit_logs ol
    LEFT JOIN outfits o     ON o.id  = ol.outfit_id
    LEFT JOIN outfit_items oi ON oi.outfit_id = ol.outfit_id
    WHERE ol.date = ?
    GROUP BY ol.id
    ORDER BY ol.is_ootd DESC, ol.created_at ASC
  `, [date]);
}

/**
 * Fetches an outfit log by its id.
 *
 * @param logId - The id of the outfit log to retrieve
 * @returns The outfit log if found, `null` otherwise
 */
export async function getLogById(
  db: SQLiteDatabase,
  logId: number
): Promise<OutfitLog | null> {
  return db.getFirstAsync<OutfitLog>(
    `SELECT * FROM outfit_logs WHERE id = ?`,
    [logId]
  );
}

/**
 * Insert a new outfit log row.
 *
 * @param log - Object describing the log: `outfit_id` (nullable outfit id), `date` (YYYY-MM-DD string), `is_ootd` (0 or 1), and `notes` (nullable)
 * @returns The inserted outfit_log row id
 */
export async function insertOutfitLog(
  db: SQLiteDatabase,
  log: { outfit_id: number | null; date: string; is_ootd: 0 | 1; notes: string | null }
): Promise<number> {
  const result = await db.runAsync(
    `INSERT INTO outfit_logs (outfit_id, date, is_ootd, notes) VALUES (?, ?, ?, ?)`,
    [log.outfit_id, log.date, log.is_ootd, log.notes]
  );
  return result.lastInsertRowId;
}

/**
 * Delete the outfit log with the given id from the database.
 *
 * @param logId - The id of the outfit log to delete
 */
export async function deleteOutfitLog(db: SQLiteDatabase, logId: number): Promise<void> {
  await db.runAsync(`DELETE FROM outfit_logs WHERE id = ?`, [logId]);
}

/**
 * Mark a specific outfit_log as the outfit of the day for its date, clearing any other OOTD for that date.
 *
 * Executes atomically in a transaction to ensure the partial-unique constraint is not violated.
 *
 * @param logId - The id of the outfit_log to mark as OOTD
 * @param date - The date (YYYY-MM-DD) for which to clear previous OOTD and set the new one
 */
export async function setOotd(
  db: SQLiteDatabase,
  logId: number,
  date: string
): Promise<void> {
  await db.withTransactionAsync(async () => {
    await db.runAsync(
      `UPDATE outfit_logs SET is_ootd = 0 WHERE date = ? AND is_ootd = 1`,
      [date]
    );
    await db.runAsync(
      `UPDATE outfit_logs SET is_ootd = 1 WHERE id = ?`,
      [logId]
    );
  });
}

/**
 * Clears OOTD status for a given log (sets is_ootd = 0).
 */
export async function clearOotd(db: SQLiteDatabase, logId: number): Promise<void> {
  await db.runAsync(`UPDATE outfit_logs SET is_ootd = 0 WHERE id = ?`, [logId]);
}

/**
 * Summarizes days in a month that have at least one outfit log.
 *
 * @param yearMonth - Month prefix in `YYYY-MM` format (e.g. "2025-03")
 * @returns An array of CalendarDay rows with `date`, `log_count`, and `has_ootd` for each date that contains one or more logs, ordered by date.
 */
export async function getCalendarDaysForMonth(
  db: SQLiteDatabase,
  yearMonth: string   // e.g. '2025-03'
): Promise<CalendarDay[]> {
  return db.getAllAsync<CalendarDay>(`
    SELECT
      date,
      COUNT(*)                  AS log_count,
      MAX(is_ootd)              AS has_ootd
    FROM outfit_logs
    WHERE date LIKE ?
    GROUP BY date
    ORDER BY date
  `, [`${yearMonth}-%`]);
}

// ---------------------------------------------------------------------------
// Stats
// ---------------------------------------------------------------------------

/**
 * Returns headline statistics for the active closet.
 *
 * Counts total active items, how many have been worn at least once within the
 * optional date range, how many have never been worn in that range, and the
 * sum of purchase prices. Pass `fromDate = null` for All Time.
 *
 * @param fromDate - Earliest `YYYY-MM-DD` date to include in wear counts, or `null` for all time
 */
export async function getStatsOverview(
  db: SQLiteDatabase,
  fromDate: string | null
): Promise<StatsOverview> {
  const row = await db.getFirstAsync<StatsOverview>(`
    SELECT
      COUNT(*) AS total_items,
      COUNT(CASE WHEN EXISTS (
        SELECT 1 FROM outfit_logs ol
        JOIN outfit_items oi ON oi.outfit_id = ol.outfit_id
        WHERE oi.clothing_item_id = ci.id
          AND (? IS NULL OR ol.date >= ?)
      ) THEN 1 END) AS worn_items,
      COUNT(CASE WHEN NOT EXISTS (
        SELECT 1 FROM outfit_logs ol
        JOIN outfit_items oi ON oi.outfit_id = ol.outfit_id
        WHERE oi.clothing_item_id = ci.id
          AND (? IS NULL OR ol.date >= ?)
      ) THEN 1 END) AS never_worn_items,
      SUM(purchase_price) AS total_value
    FROM clothing_items ci
    WHERE ci.status = 'Active'
  `, [fromDate, fromDate, fromDate, fromDate]);

  return row ?? { total_items: 0, worn_items: 0, never_worn_items: 0, total_value: null };
}

/**
 * Returns the top worn active clothing items sorted by wear count descending.
 *
 * Only items with at least one wear in the given date range are returned.
 * Pass `fromDate = null` for All Time.
 *
 * @param fromDate - Earliest `YYYY-MM-DD` date to include, or `null` for all time
 * @param limit - Maximum number of rows to return (default 15)
 */
export async function getMostWornItems(
  db: SQLiteDatabase,
  fromDate: string | null,
  limit = 15
): Promise<StatItem[]> {
  return db.getAllAsync<StatItem>(`
    SELECT
      ci.id,
      ci.name,
      ci.image_path,
      COUNT(DISTINCT ol.id) AS wear_count
    FROM clothing_items ci
    JOIN outfit_items oi ON oi.clothing_item_id = ci.id
    JOIN outfit_logs ol  ON ol.outfit_id = oi.outfit_id
    WHERE ci.status = 'Active'
      AND (? IS NULL OR ol.date >= ?)
    GROUP BY ci.id
    ORDER BY wear_count DESC
    LIMIT ?
  `, [fromDate, fromDate, limit]);
}

/**
 * Returns the least worn active clothing items (worn at least once) sorted by wear count ascending.
 *
 * Only items with at least one wear in the given date range are returned.
 * Pass `fromDate = null` for All Time.
 *
 * @param fromDate - Earliest `YYYY-MM-DD` date to include, or `null` for all time
 * @param limit - Maximum number of rows to return (default 15)
 */
export async function getLeastWornItems(
  db: SQLiteDatabase,
  fromDate: string | null,
  limit = 15
): Promise<StatItem[]> {
  return db.getAllAsync<StatItem>(`
    SELECT
      ci.id,
      ci.name,
      ci.image_path,
      COUNT(DISTINCT ol.id) AS wear_count
    FROM clothing_items ci
    JOIN outfit_items oi ON oi.clothing_item_id = ci.id
    JOIN outfit_logs ol  ON ol.outfit_id = oi.outfit_id
    WHERE ci.status = 'Active'
      AND (? IS NULL OR ol.date >= ?)
    GROUP BY ci.id
    HAVING wear_count > 0
    ORDER BY wear_count ASC
    LIMIT ?
  `, [fromDate, fromDate, limit]);
}

/**
 * Returns active clothing items that have never been worn in the given date range.
 *
 * Pass `fromDate = null` for All Time.
 *
 * @param fromDate - Earliest `YYYY-MM-DD` date to include, or `null` for all time
 * @param limit - Maximum number of rows to return (default 15)
 */
export async function getNeverWornItems(
  db: SQLiteDatabase,
  fromDate: string | null,
  limit = 15
): Promise<StatItem[]> {
  return db.getAllAsync<StatItem>(`
    SELECT ci.id, ci.name, ci.image_path, 0 AS wear_count
    FROM clothing_items ci
    WHERE ci.status = 'Active'
      AND NOT EXISTS (
        SELECT 1 FROM outfit_items oi
        JOIN outfit_logs ol ON ol.outfit_id = oi.outfit_id
        WHERE oi.clothing_item_id = ci.id
          AND (? IS NULL OR ol.date >= ?)
      )
    ORDER BY ci.name ASC
    LIMIT ?
  `, [fromDate, fromDate, limit]);
}

/**
 * Returns the count of active items per category, ordered by count descending.
 *
 * This reflects current inventory — it is not filtered by date range.
 */
export async function getBreakdownByCategory(db: SQLiteDatabase): Promise<BreakdownRow[]> {
  return db.getAllAsync<BreakdownRow>(`
    SELECT c.name AS label, COUNT(DISTINCT ci.id) AS count
    FROM clothing_items ci
    JOIN categories c ON c.id = ci.category_id
    WHERE ci.status = 'Active'
    GROUP BY c.id
    ORDER BY count DESC
  `);
}

/**
 * Returns the count of active items per color, ordered by count descending.
 *
 * This reflects current inventory — it is not filtered by date range.
 */
export async function getBreakdownByColor(db: SQLiteDatabase): Promise<ColorBreakdownRow[]> {
  return db.getAllAsync<ColorBreakdownRow>(`
    SELECT col.name AS label, col.hex, COUNT(DISTINCT ci.id) AS count
    FROM clothing_items ci
    JOIN clothing_item_colors cic ON cic.clothing_item_id = ci.id
    JOIN colors col ON col.id = cic.color_id
    WHERE ci.status = 'Active'
    GROUP BY col.id
    ORDER BY count DESC
  `);
}

/**
 * Returns the count of active items per brand, ordered by count descending.
 *
 * Items with no brand are grouped under "No Brand".
 * This reflects current inventory — it is not filtered by date range.
 */
export async function getBreakdownByBrand(db: SQLiteDatabase): Promise<BreakdownRow[]> {
  return db.getAllAsync<BreakdownRow>(`
    SELECT COALESCE(NULLIF(ci.brand, ''), 'No Brand') AS label, COUNT(DISTINCT ci.id) AS count
    FROM clothing_items ci
    WHERE ci.status = 'Active'
    GROUP BY COALESCE(NULLIF(ci.brand, ''), 'No Brand')
    ORDER BY count DESC
  `);
}

/**
 * Returns the count of active items per material, ordered by count descending.
 *
 * This reflects current inventory — it is not filtered by date range.
 */
export async function getBreakdownByMaterial(db: SQLiteDatabase): Promise<BreakdownRow[]> {
  return db.getAllAsync<BreakdownRow>(`
    SELECT m.name AS label, COUNT(DISTINCT ci.id) AS count
    FROM clothing_items ci
    JOIN clothing_item_materials cim ON cim.clothing_item_id = ci.id
    JOIN materials m ON m.id = cim.material_id
    WHERE ci.status = 'Active'
    GROUP BY m.id
    ORDER BY count DESC
  `);
}

/**
 * Returns the count of active items per occasion, ordered by count descending.
 *
 * This reflects current inventory — it is not filtered by date range.
 */
export async function getBreakdownByOccasion(db: SQLiteDatabase): Promise<BreakdownRow[]> {
  return db.getAllAsync<BreakdownRow>(`
    SELECT o.name AS label, COUNT(DISTINCT ci.id) AS count
    FROM clothing_items ci
    JOIN clothing_item_occasions cio ON cio.clothing_item_id = ci.id
    JOIN occasions o ON o.id = cio.occasion_id
    WHERE ci.status = 'Active'
    GROUP BY o.id
    ORDER BY count DESC
  `);
}

/**
 * Returns the count of active items per season, ordered by count descending.
 *
 * This reflects current inventory — it is not filtered by date range.
 */
export async function getBreakdownBySeason(db: SQLiteDatabase): Promise<BreakdownRow[]> {
  return db.getAllAsync<BreakdownRow>(`
    SELECT s.name AS label, COUNT(DISTINCT ci.id) AS count
    FROM clothing_items ci
    JOIN clothing_item_seasons cis ON cis.clothing_item_id = ci.id
    JOIN seasons s ON s.id = cis.season_id
    WHERE ci.status = 'Active'
    GROUP BY s.id
    ORDER BY count DESC
  `);
}

// ---------------------------------------------------------------------------
// App settings
// ---------------------------------------------------------------------------

/**
 * Returns all app settings as a key/value record.
 */
export async function getAllSettings(db: SQLiteDatabase): Promise<Record<string, string>> {
  const rows = await db.getAllAsync<{ key: string; value: string }>(
    'SELECT key, value FROM app_settings'
  );
  const result: Record<string, string> = {};
  for (const row of rows) result[row.key] = row.value;
  return result;
}

/**
 * Upserts a single app setting.
 */
export async function setSetting(
  db: SQLiteDatabase,
  key: string,
  value: string
): Promise<void> {
  await db.runAsync(
    'INSERT INTO app_settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value',
    [key, value]
  );
}