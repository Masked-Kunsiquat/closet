package com.closet.features.recommendations.model

/**
 * Input parameters for a single outfit recommendation run.
 *
 * Both fields are optional — the engine degrades gracefully when they are absent:
 * - [occasionId] null → no occasion filter applied to the candidate query.
 * - [weather] null → season filter runs in calendar-only mode; all weather-based
 *   suitability signals and layering validation are skipped.
 *
 * @property occasionId ID of the [com.closet.core.data.model.OccasionEntity] the user
 *   selected on the occasion sheet, or null if the sheet was skipped.
 * @property weather Today's weather conditions as supplied by the UI layer (either
 *   auto-filled from the WeatherRepository cache or entered manually), or null if the
 *   weather sheet was skipped entirely.
 */
data class RecommendationRequest(
    val occasionId: Long?,
    val weather: WeatherConditions?
)
