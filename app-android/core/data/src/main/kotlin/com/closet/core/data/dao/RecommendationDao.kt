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

// ---------------------------------------------------------------------------
// DAO
// ---------------------------------------------------------------------------

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
 *      (passed in as "Spring", "Summer", "Autumn", or "Winter").
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
}
