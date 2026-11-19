package com.closet.features.recommendations.engine

import com.closet.core.data.dao.ItemLastWorn
import com.closet.core.data.dao.ItemRainSuitability
import com.closet.core.data.dao.ItemTempPercentiles
import com.closet.core.data.dao.ItemWindSuitability
import kotlinx.datetime.LocalDate

// ---------------------------------------------------------------------------
// Item-level input — one row per candidate item
// ---------------------------------------------------------------------------

/**
 * All per-item data the engine needs for scoring, completeness, and color/pattern
 * decisions. Assembled by the ViewModel (or caller) from the repository before
 * handing off to [OutfitRecommendationEngine].
 *
 * @property id             Clothing item primary key.
 * @property name           Display name.
 * @property imagePath      Relative image path (for UI only, not used by engine logic).
 * @property categoryId     FK to categories.
 * @property subcategoryId  FK to subcategories.
 * @property outfitRole     Value of categories.outfit_role as a raw string
 *                          (Top / Bottom / OnePiece / Outerwear / Footwear / Accessory / Other).
 *                          Null when the item has no category.
 * @property warmthLayer    Value of categories.warmth_layer
 *                          (None / Base / Mid / Outer). Null when uncategorised.
 * @property colorFamilies  Set of color_family strings for the item's tagged colors.
 *                          Empty when no colors are tagged; engine treats as Neutral.
 * @property isPatternSolid True when the item's pattern junction is empty OR every
 *                          tagged pattern name is "Solid". False = patterned.
 * @property colorHexes     The item's tagged colors that have a hex, each with its color family,
 *                          in color-id order.
 * @property formalityLevel Median formality (1 to 5) of the item's occasions, or null if unknown.
 * @property subcategoryName Human-readable subcategory name (e.g. "Sweater", "Shorts"), or null if none.
 * @property materials      Set of tagged material names (e.g. "Wool", "Linen"). Empty when none tagged.
 */
data class EngineItem(
    val id: Long,
    val name: String,
    val imagePath: String?,
    val categoryId: Long?,
    val subcategoryId: Long?,
    val outfitRole: String?,
    val warmthLayer: String?,
    val colorFamilies: Set<String>,
    val isPatternSolid: Boolean,
    val colorHexes: List<ColorHex> = emptyList(),
    val formalityLevel: Double? = null,
    val subcategoryName: String? = null,
    val materials: Set<String> = emptySet(),
)

/** One tagged color's hex code and its `color_family` (Neutral / Earth / Cool / Warm / Bright). */
data class ColorHex(val hex: String, val family: String)

// ---------------------------------------------------------------------------
// Engine input — all pre-fetched signals
// ---------------------------------------------------------------------------

/**
 * Complete input package passed to [OutfitRecommendationEngine.recommend].
 *
 * All DB queries have already been executed by the time this object is built.
 * The engine is pure: it performs no I/O and has no Android/DI dependencies.
 *
 * @property candidates       Items that passed the DAO hard filters (Active + Clean
 *                            + season/occasion). The engine re-applies an Active+Clean
 *                            guard as a safety check.
 * @property tempPercentiles  Map from item ID to pre-computed 10th/90th temp percentiles.
 *                            Items absent from the map have insufficient log data.
 * @property rainSuitability  Map from item ID to rain suitability row.
 *                            Items absent from the map have no log history.
 * @property windSuitability  Map from item ID to wind suitability row.
 *                            Items absent from the map have no log history.
 * @property lastWornDates    Map from item ID to last-worn date row.
 *                            Items absent are treated as "never worn" (highest tie-break priority).
 * @property weather          Today's weather conditions, or null if the weather sheet
 *                            was skipped. When null, all weather-based suitability signals
 *                            and layering validation are skipped.
 * @property today            Today's date, for the wear-recency multiplier. Null (the default)
 *                            disables recency scoring. Passed in so the engine never reads a clock.
 * @property coWear           Co-wear and saved-outfit statistics: the habit term of the outfit score.
 * @property itemNeighbors    Top-k cosine neighbors per item for Level 1 history generalization.
 * @property subcategoryCoWear Aggregate subcategory-pair co-wear stats for Level 2 fallback.
 * @property tasteWeights     Personalized taste weights learned from wear history, or
 *                            [com.closet.features.recommendations.engine.learning.TasteWeights.DEFAULT]
 *                            at cold start.
 */
data class EngineInput(
    val candidates: List<EngineItem>,
    val tempPercentiles: Map<Long, ItemTempPercentiles>,
    val rainSuitability: Map<Long, ItemRainSuitability>,
    val windSuitability: Map<Long, ItemWindSuitability>,
    val lastWornDates: Map<Long, ItemLastWorn>,
    val weather: EngineWeather?,
    val today: LocalDate? = null,
    val coWear: CoWearStats = CoWearStats.EMPTY,
    val itemNeighbors: Map<Long, List<ItemNeighbor>> = emptyMap(),
    val subcategoryCoWear: CoWearStats = CoWearStats.EMPTY,
    val tasteWeights: com.closet.features.recommendations.engine.learning.TasteWeights =
        com.closet.features.recommendations.engine.learning.TasteWeights.DEFAULT,
)

/**
 * A cosine neighbor of an item with its cosine similarity in [-1.0, 1.0].
 *
 * @property itemId     Candidate clothing item primary key.
 * @property similarity Cosine similarity to the target item (typically >= 0.75).
 */
data class ItemNeighbor(val itemId: Long, val similarity: Double)

/**
 * Weather conditions passed into the engine.
 *
 * Mirrors [com.closet.features.recommendations.model.WeatherConditions] but lives
 * in the engine package so the engine has no dependency on the feature model layer.
 * The caller maps one to the other before invoking the engine.
 *
 * @property tempLowC  Today's forecast low temperature (°C). Null = unknown.
 * @property tempHighC Today's forecast high temperature (°C). Null = unknown.
 * @property isRaining True if precipitation is expected (precipitation_mm > 1.0 threshold).
 * @property isWindy   True if wind is expected (wind_speed_kmh > 30 threshold).
 */
data class EngineWeather(
    val tempLowC: Double?,
    val tempHighC: Double?,
    val isRaining: Boolean,
    val isWindy: Boolean
)

// ---------------------------------------------------------------------------
// Engine output
// ---------------------------------------------------------------------------

/**
 * A single recommended outfit combination produced by [OutfitRecommendationEngine]
 * or the AI coherence scorer.
 *
 * @property items        The items that form this outfit. Guaranteed to satisfy category
 *                        completeness: (Top + Bottom) OR (OnePiece), with optional
 *                        Outerwear / Footwear / Accessory.
 * @property score        Final outfit score: mean of item suitability scores × outfit-level
 *                        multipliers. Higher is better. Always > 0.
 * @property isAiSelected True when this combo was selected by the AI coherence scorer
 *                        (surfaced as an "AI pick" badge in the UI). False for programmatic
 *                        results.
 * @property reason       Optional explanation from the AI model for why this combination
 *                        was chosen. Non-null only when [isAiSelected] is true and the
 *                        model produced a reason. Surfaced behind a "why?" tap in the UI.
 */
data class OutfitCombo(
    val items: List<EngineItem>,
    val score: Double,
    val isAiSelected: Boolean = false,
    val reason: String? = null,
)

/**
 * Identity of a combo: the set of its item IDs. Used for exclusion of already-shown combos
 * ([OutfitRecommendationEngine.recommend]'s `exclude`) and by the ViewModel's session tracking.
 */
fun OutfitCombo.key(): Set<Long> = items.map { it.id }.toSet()

// ---------------------------------------------------------------------------
// Co-wear statistics
// ---------------------------------------------------------------------------

/**
 * Weighted co-occurrence counts over candidate items: wear logs count 1 each, saved outfits
 * [SAVED_OUTFIT_WEIGHT] each.
 *
 * @property pairCounts Weighted count of logs/outfits containing both items; keys are
 *                      `(smallerId, largerId)`.
 * @property itemCounts Weighted count of logs/outfits containing the item.
 * @property total      Number of logs + [SAVED_OUTFIT_WEIGHT] x number of saved outfits.
 */
data class CoWearStats(
    val pairCounts: Map<Pair<Long, Long>, Double>,
    val itemCounts: Map<Long, Double>,
    val total: Double,
) {
    companion object {
        /** Weight of one saved outfit relative to one wear log. */
        const val SAVED_OUTFIT_WEIGHT = 0.5

        val EMPTY = CoWearStats(emptyMap(), emptyMap(), 0.0)
    }
}
