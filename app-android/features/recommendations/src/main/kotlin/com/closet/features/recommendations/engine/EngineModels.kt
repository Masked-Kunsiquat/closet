package com.closet.features.recommendations.engine

import com.closet.core.data.dao.ItemLastWorn
import com.closet.core.data.dao.ItemRainSuitability
import com.closet.core.data.dao.ItemTempPercentiles
import com.closet.core.data.dao.ItemWindSuitability

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
    val isPatternSolid: Boolean
)

// ---------------------------------------------------------------------------
// Engine input — all pre-fetched signals
// ---------------------------------------------------------------------------

/**
 * Complete input package passed to [OutfitRecommendationEngine.recommend].
 *
 * All DB queries have already been executed by the time this object is built.
 * The engine is pure: it performs no I/O and has no Android/Hilt dependencies.
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
 */
data class EngineInput(
    val candidates: List<EngineItem>,
    val tempPercentiles: Map<Long, ItemTempPercentiles>,
    val rainSuitability: Map<Long, ItemRainSuitability>,
    val windSuitability: Map<Long, ItemWindSuitability>,
    val lastWornDates: Map<Long, ItemLastWorn>,
    val weather: EngineWeather?
)

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
 * A single recommended outfit combination produced by [OutfitRecommendationEngine].
 *
 * @property items  The items that form this outfit. Guaranteed to satisfy category
 *                  completeness: (Top + Bottom) OR (OnePiece), with optional
 *                  Outerwear / Footwear / Accessory.
 * @property score  Final outfit score: mean of item suitability scores × outfit-level
 *                  multipliers. Higher is better. Always > 0.
 */
data class OutfitCombo(
    val items: List<EngineItem>,
    val score: Double
)
