package com.closet.features.recommendations

import com.closet.features.recommendations.engine.OutfitCombo
import com.closet.features.recommendations.model.WeatherConditions

/**
 * State machine for the outfit recommendations flow.
 *
 * Transitions:
 *   Idle → OccasionSheet  (user taps "Get suggestions")
 *   OccasionSheet → WeatherSheet  (occasion selected or skipped)
 *   WeatherSheet → Loading  (weather confirmed or skipped)
 *   Loading → Results    (engine returns ≥1 combo)
 *   Loading → NoResults  (engine returns 0 combos — valid, not an error)
 *   Loading → Error      (repo or engine failure)
 *   Results → Loading    (user taps Regenerate)
 *   NoResults → Loading  (user taps Regenerate)
 *   any → Idle  (user dismisses)
 */
sealed interface RecommendationUiState {

    /** Nothing in progress — entry point state. */
    data object Idle : RecommendationUiState

    /** Occasion picker bottom sheet is open. */
    data object OccasionSheet : RecommendationUiState

    /**
     * Weather input bottom sheet is open.
     *
     * @property occasionId The occasion the user selected, or null if skipped.
     * @property prefill Weather pre-populated from the WeatherRepository cache,
     *   or null if no cached forecast is available. The UI shows a "Pulled from
     *   location data" chip when this is non-null.
     */
    data class WeatherSheet(
        val occasionId: Long?,
        val prefill: WeatherConditions?
    ) : RecommendationUiState

    /** Engine pipeline is running. No user interaction expected in this state. */
    data object Loading : RecommendationUiState

    /**
     * Engine has returned results. Displayed as a horizontal carousel of 3 cards.
     *
     * @property combos Up to 3 ranked outfit combinations.
     * @property occasionId The occasion used for this run, or null if skipped.
     * @property weather The weather conditions used for this run, or null if skipped.
     */
    data class Results(
        val combos: List<OutfitCombo>,
        val occasionId: Long?,
        val weather: WeatherConditions?
    ) : RecommendationUiState

    /**
     * Engine ran successfully but no valid combos could be formed (e.g. wardrobe
     * is too small, or all items are dirty/inactive for the given filters).
     * This is a valid outcome — not an error.
     *
     * @property occasionId The occasion used for this run, or null if skipped.
     * @property weather The weather conditions used for this run, or null if skipped.
     */
    data class NoResults(
        val occasionId: Long?,
        val weather: WeatherConditions?,
    ) : RecommendationUiState

    /**
     * Something went wrong during the engine pipeline.
     *
     * @property message Human-readable description of the failure for display.
     */
    data class Error(val message: String) : RecommendationUiState
}
