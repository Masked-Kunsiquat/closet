package com.closet.core.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

// ---------------------------------------------------------------------------
// Result data classes
// ---------------------------------------------------------------------------

/**
 * A single clothing item candidate returned by the hard-filter query.
 * Carries all fields the engine needs for scoring, completeness checks, and
 * color/pattern decisions — no extra round-trips required.
 *
 * @property id Clothing item primary key.
 * @property name Display name.
 * @property imagePath Relative image path (reconstruct URI in the UI layer).
 * @property categoryId FK to categories; null if uncategorised.
 * @property subcategoryId FK to subcategories; null if unspecified.
 * @property outfitRole Value of categories.outfit_role (Top/Bottom/OnePiece/…/Other).
 *   Null when the item has no category assigned.
 * @property warmthLayer Value of categories.warmth_layer (None/Base/Mid/Outer).
 *   Null when the item has no category assigned.
 */
data class CandidateItem(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "image_path") val imagePath: String?,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "subcategory_id") val subcategoryId: Long?,
    @ColumnInfo(name = "outfit_role") val outfitRole: String?,
    @ColumnInfo(name = "warmth_layer") val warmthLayer: String?
)

/**
 * A single temperature observation for one item across its log history.
 *
 * The repository collects all rows for a given item, sorts them, and computes
 * the 10th/90th-percentile positions in Kotlin — SQLite's correlated-subquery
 * OFFSET pattern is not supported by Room's compile-time query validator.
 *
 * @property clothingItemId FK to clothing_items.
 * @property temperatureLow  The temperature_low value recorded on this log.
 * @property temperatureHigh The temperature_high value recorded on this log.
 */
data class ItemTempLog(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "temperature_low") val temperatureLow: Double,
    @ColumnInfo(name = "temperature_high") val temperatureHigh: Double
)

/**
 * Percentile bounds derived from an item's historical log temperatures.
 * Computed in the repository from [ItemTempLog] rows — not returned directly by SQL.
 *
 * @property clothingItemId FK to clothing_items.
 * @property p10TempLow  10th-percentile of temperature_low values.
 * @property p90TempHigh 90th-percentile of temperature_high values.
 * @property logCount    Number of logs with non-null temperature data (for sparse-data guard).
 */
data class ItemTempPercentiles(
    val clothingItemId: Long,
    val p10TempLow: Double,
    val p90TempHigh: Double,
    val logCount: Int
)

/**
 * Rain suitability signal for one item.
 *
 * [rainLogCount] enables the engine's sparse-data guard (< 5 logs → skip signal).
 *
 * @property clothingItemId FK to clothing_items.
 * @property rainPct Fraction of logs (0.0–1.0) where precipitation_mm > 1.0.
 * @property rainLogCount Total number of logs for this item (used for sparse-data guard).
 */
data class ItemRainSuitability(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "rain_pct") val rainPct: Double,
    @ColumnInfo(name = "rain_log_count") val rainLogCount: Int
)

/**
 * Wind suitability signal for one item.
 *
 * [windLogCount] enables the engine's sparse-data guard (< 5 logs → skip signal).
 *
 * @property clothingItemId FK to clothing_items.
 * @property windPct Fraction of logs (0.0–1.0) where wind_speed_kmh > 30.
 * @property windLogCount Total number of logs for this item (used for sparse-data guard).
 */
data class ItemWindSuitability(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "wind_pct") val windPct: Double,
    @ColumnInfo(name = "wind_log_count") val windLogCount: Int
)

/**
 * Last-worn date for a single item.
 * Used only for tie-breaking (prefer items worn least recently), not for scoring.
 *
 * @property clothingItemId FK to clothing_items.
 * @property lastWornDate Most recent YYYY-MM-DD date this item appeared in an outfit log,
 *   or null if the item has never been logged.
 */
data class ItemLastWorn(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "last_worn_date") val lastWornDate: String?
)

/**
 * A single (clothing_item_id, color_family) pair returned by the color-family bulk query.
 *
 * One row is returned per distinct color_family tagged on the item. The ViewModel groups
 * rows by [clothingItemId] and collects the families into a [Set].
 *
 * @property clothingItemId FK to clothing_items.
 * @property colorFamily Value of colors.color_family (Neutral/Earth/Cool/Warm/Bright).
 */
data class ItemColorFamily(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "color_family") val colorFamily: String
)

/**
 * A single (clothing_item_id, pattern_name) pair returned by the pattern bulk query.
 *
 * One row is returned per pattern tagged on the item. The ViewModel groups rows by
 * [clothingItemId] and checks whether every pattern name equals "Solid" (or the list
 * is empty) to derive [com.closet.features.recommendations.engine.EngineItem.isPatternSolid].
 *
 * @property clothingItemId FK to clothing_items.
 * @property patternName Value of patterns.name (e.g. "Solid", "Striped", "Floral").
 */
data class ItemPatternName(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "pattern_name") val patternName: String
)

/**
 * Subcategory name hint for AI prompt enrichment.
 *
 * Used to provide human-readable subcategory labels (e.g. "Button-down", "Chinos")
 * to the AI stylist prompt builder. One row per item — items with no subcategory
 * assigned are not returned.
 *
 * @property clothingItemId FK to clothing_items.
 * @property subcategoryName Value of subcategories.name.
 */
data class ItemSubcategoryHint(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "subcategory_name") val subcategoryName: String,
)

/**
 * Primary material hint for AI prompt enrichment.
 *
 * Used to provide the first material name (e.g. "Cotton", "Wool") for each item
 * to the AI stylist prompt builder. One row per (item, material) pair — items with
 * no materials tagged are not returned. The repository takes [firstOrNull] per item.
 *
 * @property clothingItemId FK to clothing_items.
 * @property materialName Value of materials.name.
 */
data class ItemMaterialHint(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "material_name") val materialName: String,
)

// ---------------------------------------------------------------------------
// DAO
// ---------------------------------------------------------------------------


/** An unordered item pair ([aId] < [bId]) with the number of distinct logs or saved outfits containing both. */
data class ItemPair(
    @ColumnInfo(name = "a_id") val aId: Long,
    @ColumnInfo(name = "b_id") val bId: Long,
    @ColumnInfo(name = "pair_count") val count: Int
)

/** Number of distinct logs or saved outfits an item appears in. */
data class ItemCount(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "item_count") val count: Int
)

/** One occasion name tagged on an item. */
data class ItemOccasionName(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "occasion_name") val occasionName: String
)

/** One non-null color hex tagged on an item, with that color's `color_family`. */
data class ItemColorHex(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "hex") val hex: String,
    @ColumnInfo(name = "color_family") val colorFamily: String,
)

/** Pre-computed item embedding blob from `item_embeddings`. */
data class ItemEmbeddingBlob(
    @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "embedding_blob") val embeddingBlob: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ItemEmbeddingBlob
        if (itemId != other.itemId) return false
        if (!embeddingBlob.contentEquals(other.embeddingBlob)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = itemId.hashCode()
        result = 31 * result + embeddingBlob.contentHashCode()
        return result
    }
}

/**
 * One clothing item row from `clothing_items` joined with `categories` for historical
 * choice-stratum reconstruction (learned taste weights, Issue #114).
 *
 * Includes all items regardless of current `wash_status` so historical wear logs can be
 * reconstructed even if a worn item is currently in the laundry.
 */
data class TrainingItemRow(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "image_path") val imagePath: String?,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "subcategory_id") val subcategoryId: Long?,
    @ColumnInfo(name = "outfit_role") val outfitRole: String?,
    @ColumnInfo(name = "warmth_layer") val warmthLayer: String?,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long?,
)

/**
 * One historical wear log header row from `outfit_logs` ordered chronologically
 * for taste-weight training (Issue #114).
 */
data class TrainingWearLogRow(
    val id: Long,
    val date: String,
    @ColumnInfo(name = "temperature_low") val temperatureLow: Double?,
    @ColumnInfo(name = "temperature_high") val temperatureHigh: Double?,
    @ColumnInfo(name = "precipitation_mm") val precipitationMm: Double?,
    @ColumnInfo(name = "wind_speed_kmh") val windSpeedKmh: Double?,
)

/**
 * One snapshot item entry from `outfit_log_items` for taste-weight training (Issue #114).
 */
data class TrainingWearLogItemRow(
    @ColumnInfo(name = "outfit_log_id") val outfitLogId: Long,
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
)


/**
 * DAO for the outfit recommendation pipeline.
 *
 * All queries are one-shot suspending functions (not Flow) because the engine
 * runs on demand rather than reacting to DB changes.
 *
 * Season hard-filter logic
 * -------------------------
 * An item passes the season filter if ANY of the following is true:
 *   1. Calendar mode: the item has a season whose name matches [calendarSeason]
 *      (passed in as "Spring", "Summer", "Fall", or "Winter", the seeded `seasons.name` values).
 *   2. Temperature-band mode: the item has a season whose temp_low_c / temp_high_c
 *      range overlaps the forecast range ([tempLow], [tempHigh]).
 *   3. The item has a season whose name = 'All Season'.
 *
 * When [tempLow]/[tempHigh] are both null (weather sheet skipped), condition 2
 * never matches and only calendar mode + All Season applies.
 *
 * Occasion filter
 * ---------------
 * When [occasionId] is null the occasion EXISTS sub-select is skipped entirely.
 * When non-null, only items that have that occasion in clothing_item_occasions pass.
 */
@Dao
interface RecommendationDao {

    /**
     * Returns all Active + Clean clothing items that pass the season and (optionally)
     * occasion hard filters. These form the initial candidate pool for the engine.
     *
     * The season sub-select uses an OR of three conditions. Items with no seasons
     * tagged do NOT pass (the EXISTS sub-select returns false).
     *
     * @param calendarSeason Current calendar season name (e.g. "Winter").
     * @param tempLow        Today's forecast low °C, or null if weather was skipped.
     * @param tempHigh       Today's forecast high °C, or null if weather was skipped.
     * @param occasionId     Required occasion ID, or null to skip the occasion filter.
     */
    @Query("""
        SELECT
            ci.id,
            ci.name,
            ci.image_path,
            ci.category_id,
            ci.subcategory_id,
            cat.outfit_role,
            cat.warmth_layer
        FROM clothing_items ci
        LEFT JOIN categories cat ON ci.category_id = cat.id
        WHERE ci.status     = 'Active'
          AND ci.wash_status = 'Clean'
          AND EXISTS (
              SELECT 1
              FROM clothing_item_seasons cis
              JOIN seasons s ON cis.season_id = s.id
              WHERE cis.clothing_item_id = ci.id
                AND (
                    s.name = 'All Season'
                    OR s.name = :calendarSeason
                    OR (
                        :tempLow  IS NOT NULL
                        AND :tempHigh IS NOT NULL
                        AND s.temp_low_c  IS NOT NULL
                        AND s.temp_high_c IS NOT NULL
                        AND :tempHigh >= s.temp_low_c
                        AND :tempLow  <= s.temp_high_c
                    )
                )
          )
          AND (
              :occasionId IS NULL
              OR EXISTS (
                  SELECT 1
                  FROM clothing_item_occasions cio
                  WHERE cio.clothing_item_id = ci.id
                    AND cio.occasion_id = :occasionId
              )
          )
    """)
    suspend fun getCandidates(
        calendarSeason: String,
        tempLow: Double?,
        tempHigh: Double?,
        occasionId: Long?
    ): List<CandidateItem>

    /**
     * Returns all temperature log rows for each item in [itemIds] where both
     * temperature_low and temperature_high are recorded.
     *
     * Rows are ordered by temperature_low ASC so the repository can compute
     * 10th/90th-percentile positions directly without an additional sort.
     *
     * SQLite's correlated-subquery OFFSET pattern (ORDER BY … LIMIT 1 OFFSET N)
     * is not supported by Room's compile-time SQL validator when N itself is a
     * subquery. To keep the SQL simple and correct, this query returns all raw rows
     * and the repository derives the percentile bounds in Kotlin.
     *
     * The engine enforces the sparse-data guard (< 5 logs → skip signal) using
     * the count of rows returned for each item.
     */
    @Query("""
        SELECT
            oli.clothing_item_id,
            ol.temperature_low,
            ol.temperature_high
        FROM outfit_log_items oli
        JOIN outfit_logs ol ON oli.outfit_log_id = ol.id
        WHERE oli.clothing_item_id IN (:itemIds)
          AND ol.temperature_low  IS NOT NULL
          AND ol.temperature_high IS NOT NULL
        ORDER BY oli.clothing_item_id ASC, ol.temperature_low ASC
    """)
    suspend fun getTempLogs(itemIds: List<Long>): List<ItemTempLog>

    /**
     * Returns rain suitability for each item in [itemIds].
     *
     * rain_pct = (logs where precipitation_mm > 1.0) / (total logs for item).
     * Items with no logs at all are not returned — the engine treats missing rows
     * as "no data" and skips the rain signal for those items.
     */
    @Query("""
        SELECT
            oli.clothing_item_id,
            CAST(SUM(CASE WHEN ol.precipitation_mm > 1.0 THEN 1 ELSE 0 END) AS REAL)
                / COUNT(*) AS rain_pct,
            COUNT(*) AS rain_log_count
        FROM outfit_log_items oli
        JOIN outfit_logs ol ON oli.outfit_log_id = ol.id
        WHERE oli.clothing_item_id IN (:itemIds)
        GROUP BY oli.clothing_item_id
    """)
    suspend fun getRainSuitability(itemIds: List<Long>): List<ItemRainSuitability>

    /**
     * Returns wind suitability for each item in [itemIds].
     *
     * wind_pct = (logs where wind_speed_kmh > 30) / (total logs for item).
     * Items with no logs at all are not returned — the engine treats missing rows
     * as "no data" and skips the wind signal for those items.
     */
    @Query("""
        SELECT
            oli.clothing_item_id,
            CAST(SUM(CASE WHEN ol.wind_speed_kmh > 30 THEN 1 ELSE 0 END) AS REAL)
                / COUNT(*) AS wind_pct,
            COUNT(*) AS wind_log_count
        FROM outfit_log_items oli
        JOIN outfit_logs ol ON oli.outfit_log_id = ol.id
        WHERE oli.clothing_item_id IN (:itemIds)
        GROUP BY oli.clothing_item_id
    """)
    suspend fun getWindSuitability(itemIds: List<Long>): List<ItemWindSuitability>

    /**
     * Returns one row per (clothing_item_id, color_family) pair for items in [itemIds].
     *
     * An item appears once per distinct color_family across all its tagged colors.
     * Items with no colors are not returned; the engine treats a missing item as Neutral.
     *
     * Used by the ViewModel to build the [com.closet.features.recommendations.engine.EngineItem]
     * colorFamilies set before invoking the engine.
     */
    @Query("""
        SELECT DISTINCT
            cic.clothing_item_id,
            co.color_family
        FROM clothing_item_colors cic
        JOIN colors co ON cic.color_id = co.id
        WHERE cic.clothing_item_id IN (:itemIds)
        ORDER BY cic.clothing_item_id ASC
    """)
    suspend fun getItemColorFamilies(itemIds: List<Long>): List<ItemColorFamily>

    /**
     * Returns one row per (clothing_item_id, pattern_name) pair for items in [itemIds].
     *
     * An item appears once per distinct pattern it has tagged. Items with no patterns
     * are not returned; the ViewModel treats a missing item as solid.
     *
     * Used to derive [com.closet.features.recommendations.engine.EngineItem.isPatternSolid]:
     * an item is solid when it has no patterns, or every pattern name equals "Solid".
     */
    @Query("""
        SELECT
            cip.clothing_item_id,
            p.name AS pattern_name
        FROM clothing_item_patterns cip
        JOIN patterns p ON cip.pattern_id = p.id
        WHERE cip.clothing_item_id IN (:itemIds)
        ORDER BY cip.clothing_item_id ASC
    """)
    suspend fun getItemPatternNames(itemIds: List<Long>): List<ItemPatternName>

    /**
     * Returns the most recent wear date for each item in [itemIds].
     *
     * Used exclusively for tie-breaking (prefer the item worn least recently).
     * Items that have never been logged are not returned; the engine treats a
     * missing row as "never worn" and ranks it highest in ties.
     *
     * outfit_log_items links item → outfit_log; date lives on outfit_logs.
     */
    @Query("""
        SELECT
            oli.clothing_item_id,
            MAX(ol.date) AS last_worn_date
        FROM outfit_log_items oli
        JOIN outfit_logs ol ON oli.outfit_log_id = ol.id
        WHERE oli.clothing_item_id IN (:itemIds)
        GROUP BY oli.clothing_item_id
    """)
    suspend fun getLastWornDates(itemIds: List<Long>): List<ItemLastWorn>

    /**
     * Returns the subcategory name for each item in [itemIds] that has a subcategory assigned.
     *
     * One row per item. Items with no subcategory (subcategory_id IS NULL) are not returned;
     * the repository represents them as null in [ItemAiContextHint.subcategoryName].
     *
     * Used exclusively for AI prompt enrichment — not consumed by the scoring engine.
     */
    @Query("""
        SELECT ci.id AS clothing_item_id, sc.name AS subcategory_name
        FROM clothing_items ci
        JOIN subcategories sc ON ci.subcategory_id = sc.id
        WHERE ci.id IN (:itemIds)
        ORDER BY ci.id ASC
    """)
    suspend fun getItemSubcategoryHints(itemIds: List<Long>): List<ItemSubcategoryHint>

    /**
     * Returns all material names for each item in [itemIds] that has at least one material tagged.
     *
     * One row per (clothing_item_id, material) pair. Items with no materials are not returned.
     * The repository takes [firstOrNull] per item to derive the primary material for AI prompts.
     *
     * Used exclusively for AI prompt enrichment — not consumed by the scoring engine.
     */
    @Query("""
        SELECT cim.clothing_item_id, m.name AS material_name
        FROM clothing_item_materials cim
        JOIN materials m ON cim.material_id = m.id
        WHERE cim.clothing_item_id IN (:itemIds)
        ORDER BY cim.clothing_item_id ASC
    """)
    suspend fun getItemMaterialHints(itemIds: List<Long>): List<ItemMaterialHint>

    /** Pairs of [itemIds] worn together; each log counts once per pair; a < b. */
    @Query("""
        SELECT a.clothing_item_id AS a_id, b.clothing_item_id AS b_id,
               COUNT(DISTINCT a.outfit_log_id) AS pair_count
        FROM outfit_log_items a
        JOIN outfit_log_items b
          ON a.outfit_log_id = b.outfit_log_id AND a.clothing_item_id < b.clothing_item_id
        WHERE a.clothing_item_id IN (:itemIds) AND b.clothing_item_id IN (:itemIds)
        GROUP BY a.clothing_item_id, b.clothing_item_id
    """)
    suspend fun getCoWearPairs(itemIds: List<Long>): List<ItemPair>

    /** Distinct logs each of [itemIds] appears in. */
    @Query("""
        SELECT clothing_item_id, COUNT(DISTINCT outfit_log_id) AS item_count
        FROM outfit_log_items
        WHERE clothing_item_id IN (:itemIds)
        GROUP BY clothing_item_id
    """)
    suspend fun getItemLogCounts(itemIds: List<Long>): List<ItemCount>

    @Query("SELECT COUNT(*) FROM outfit_logs")
    suspend fun getTotalLogCount(): Int

    /** Pairs of [itemIds] saved together in an outfit; each outfit counts once per pair; a < b. */
    @Query("""
        SELECT a.clothing_item_id AS a_id, b.clothing_item_id AS b_id,
               COUNT(DISTINCT a.outfit_id) AS pair_count
        FROM outfit_items a
        JOIN outfit_items b
          ON a.outfit_id = b.outfit_id AND a.clothing_item_id < b.clothing_item_id
        WHERE a.clothing_item_id IN (:itemIds) AND b.clothing_item_id IN (:itemIds)
        GROUP BY a.clothing_item_id, b.clothing_item_id
    """)
    suspend fun getSavedOutfitPairs(itemIds: List<Long>): List<ItemPair>

    /** Saved outfits each of [itemIds] belongs to. */
    @Query("""
        SELECT clothing_item_id, COUNT(DISTINCT outfit_id) AS item_count
        FROM outfit_items
        WHERE clothing_item_id IN (:itemIds)
        GROUP BY clothing_item_id
    """)
    suspend fun getSavedItemCounts(itemIds: List<Long>): List<ItemCount>

    @Query("SELECT COUNT(*) FROM outfits")
    suspend fun getSavedOutfitCount(): Int

    /** Occasion names tagged on [itemIds] (used to derive formality). */
    @Query("""
        SELECT cio.clothing_item_id, o.name AS occasion_name
        FROM clothing_item_occasions cio
        JOIN occasions o ON cio.occasion_id = o.id
        WHERE cio.clothing_item_id IN (:itemIds)
        ORDER BY cio.clothing_item_id ASC
    """)
    suspend fun getItemOccasionNames(itemIds: List<Long>): List<ItemOccasionName>

    /** Non-null color hexes tagged on [itemIds] with their families, in a stable order. */
    @Query("""
        SELECT cic.clothing_item_id, co.hex AS hex, co.color_family AS color_family
        FROM clothing_item_colors cic
        JOIN colors co ON cic.color_id = co.id
        WHERE cic.clothing_item_id IN (:itemIds) AND co.hex IS NOT NULL
        ORDER BY cic.clothing_item_id ASC, cic.color_id ASC
    """)
    suspend fun getItemColorHexes(itemIds: List<Long>): List<ItemColorHex>

    /** Embeddings for [itemIds] produced by [modelVersion]. */
    @Query("""
        SELECT item_id, embedding_blob
        FROM item_embeddings
        WHERE item_id IN (:itemIds) AND model_version = :modelVersion
    """)
    suspend fun getItemEmbeddings(itemIds: List<Long>, modelVersion: String): List<ItemEmbeddingBlob>

    /** Pairs of subcategories worn together in outfit logs; each log counts once per pair; a < b. */
    @Query("""
        SELECT ca.subcategory_id AS a_id, cb.subcategory_id AS b_id,
               COUNT(DISTINCT a.outfit_log_id) AS pair_count
        FROM outfit_log_items a
        JOIN clothing_items ca ON a.clothing_item_id = ca.id
        JOIN outfit_log_items b
          ON a.outfit_log_id = b.outfit_log_id
        JOIN clothing_items cb ON b.clothing_item_id = cb.id
        WHERE ca.subcategory_id IS NOT NULL
          AND cb.subcategory_id IS NOT NULL
          AND ca.subcategory_id < cb.subcategory_id
        GROUP BY ca.subcategory_id, cb.subcategory_id
    """)
    suspend fun getSubcategoryCoWearPairs(): List<ItemPair>

    /** Pairs of subcategories saved together in outfits; each outfit counts once per pair; a < b. */
    @Query("""
        SELECT ca.subcategory_id AS a_id, cb.subcategory_id AS b_id,
               COUNT(DISTINCT a.outfit_id) AS pair_count
        FROM outfit_items a
        JOIN clothing_items ca ON a.clothing_item_id = ca.id
        JOIN outfit_items b
          ON a.outfit_id = b.outfit_id
        JOIN clothing_items cb ON b.clothing_item_id = cb.id
        WHERE ca.subcategory_id IS NOT NULL
          AND cb.subcategory_id IS NOT NULL
          AND ca.subcategory_id < cb.subcategory_id
        GROUP BY ca.subcategory_id, cb.subcategory_id
    """)
    suspend fun getSubcategorySavedOutfitPairs(): List<ItemPair>

    /** Distinct logs each subcategory appears in. */
    @Query("""
        SELECT ci.subcategory_id AS clothing_item_id, COUNT(DISTINCT oli.outfit_log_id) AS item_count
        FROM outfit_log_items oli
        JOIN clothing_items ci ON oli.clothing_item_id = ci.id
        WHERE ci.subcategory_id IS NOT NULL
        GROUP BY ci.subcategory_id
    """)
    suspend fun getSubcategoryLogCounts(): List<ItemCount>

    /** Distinct saved outfits each subcategory appears in. */
    @Query("""
        SELECT ci.subcategory_id AS clothing_item_id, COUNT(DISTINCT oi.outfit_id) AS item_count
        FROM outfit_items oi
        JOIN clothing_items ci ON oi.clothing_item_id = ci.id
        WHERE ci.subcategory_id IS NOT NULL
        GROUP BY ci.subcategory_id
    """)
    suspend fun getSubcategorySavedItemCounts(): List<ItemCount>

    // -------------------------------------------------------------------------
    // Learned taste weights training queries (engine v2, #114)
    // -------------------------------------------------------------------------

    /** Maximum primary key in `outfit_logs` (or null when empty), combined with log count for the training watermark. */
    @Query("SELECT MAX(id) FROM outfit_logs")
    suspend fun getMaxLogId(): Long?

    /** All active wardrobe items (regardless of current wash status) for historical stratum reconstruction. */
    @Query("""
        SELECT
            ci.id,
            ci.name,
            ci.image_path,
            ci.category_id,
            ci.subcategory_id,
            cat.outfit_role,
            cat.warmth_layer,
            ci.created_at
        FROM clothing_items ci
        LEFT JOIN categories cat ON ci.category_id = cat.id
        WHERE ci.status = 'Active'
        ORDER BY ci.id ASC
    """)
    suspend fun getTrainingItems(): List<TrainingItemRow>

    /** All historical wear logs in chronological order for taste-weight training. */
    @Query("""
        SELECT
            id,
            date,
            temperature_low,
            temperature_high,
            precipitation_mm,
            wind_speed_kmh
        FROM outfit_logs
        ORDER BY date ASC, id ASC
    """)
    suspend fun getTrainingWearLogs(): List<TrainingWearLogRow>

    /** All snapshot (outfit_log_id, clothing_item_id) rows from `outfit_log_items`. */
    @Query("""
        SELECT
            outfit_log_id,
            clothing_item_id
        FROM outfit_log_items
        ORDER BY outfit_log_id ASC, clothing_item_id ASC
    """)
    suspend fun getTrainingWearLogItems(): List<TrainingWearLogItemRow>
}
