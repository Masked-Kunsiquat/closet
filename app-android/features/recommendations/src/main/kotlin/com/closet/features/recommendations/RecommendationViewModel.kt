package com.closet.features.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.dao.CandidateItem
import com.closet.core.data.dao.ItemColorFamily
import com.closet.core.data.dao.ItemLastWorn
import com.closet.core.data.dao.ItemPatternName
import com.closet.core.data.dao.ItemRainSuitability
import com.closet.core.data.dao.ItemTempPercentiles
import com.closet.core.data.dao.ItemWindSuitability
import com.closet.core.data.model.AiProvider
import com.closet.core.data.model.OccasionEntity
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.repository.OutfitRepository
import com.closet.core.data.repository.RecommendationRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.repository.WeatherRepository
import com.closet.core.data.util.DataResult
import com.closet.features.recommendations.engine.EngineInput
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.EngineWeather
import com.closet.features.recommendations.engine.OutfitCombo
import com.closet.features.recommendations.ai.OutfitCoherenceScorer
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.model.WeatherConditions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.closet.core.data.model.StyleVibe
import timber.log.Timber
import java.time.LocalDate
import java.time.Month
import javax.inject.Inject

/**
 * ViewModel for the outfit recommendations flow.
 *
 * ### State machine
 * [RecommendationUiState.Idle] → [RecommendationUiState.OccasionSheet]
 *   → [RecommendationUiState.WeatherSheet] → [RecommendationUiState.Loading]
 *   → [RecommendationUiState.Results] or [RecommendationUiState.Error]
 *
 * The [Results] state can loop back to [Loading] on Regenerate.
 * Any state can return to [Idle] via [onDismiss].
 *
 * ### Engine invocation
 * All four score queries run in parallel via [async]/[coroutineScope]. The results
 * are mapped to [EngineInput] and handed off to [OutfitRecommendationEngine] — a pure
 * class that performs no I/O.
 *
 * ### One-shot events
 * - [logItEvent]: emits the item IDs from the selected combo for the UI to navigate to
 *   outfit logging. Navigation is never performed inside the ViewModel.
 * - [saveResult]: emits a [SaveResult] when "Save for later" completes or fails.
 */
@HiltViewModel
class RecommendationViewModel @Inject constructor(
    private val recommendationRepository: RecommendationRepository,
    private val engine: OutfitRecommendationEngine,
    private val scorer: OutfitCoherenceScorer,
    private val weatherRepository: WeatherRepository,
    private val outfitRepository: OutfitRepository,
    private val lookupRepository: LookupRepository,
    private val storageRepository: StorageRepository,
    private val aiPrefsRepo: AiPreferencesRepository,
) : ViewModel() {

    // -------------------------------------------------------------------------
    // Occasions list — loaded once, used by OccasionSheet
    // -------------------------------------------------------------------------

    /**
     * Whether AI coherence scoring is currently enabled.
     * Exposed so the UI can conditionally show the style vibe shortcut row.
     */
    val aiEnabled: StateFlow<Boolean> = aiPrefsRepo.getAiEnabled()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    /**
     * The current style vibe label string ("Smart Casual", "Minimalist", etc.) for display.
     * Used by the shortcut row in [RecommendationScreen] to show the active vibe.
     */
    val styleVibeLabel: StateFlow<String> = aiPrefsRepo.getStyleVibe()
        .map { it.label }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StyleVibe.SmartCasual.label
        )

    /**
     * Full list of occasions from the lookup table.
     * Collected in [RecommendationScreen] and passed to [OccasionSheet].
     */
    val occasions: StateFlow<List<OccasionEntity>> = lookupRepository.getOccasions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // -------------------------------------------------------------------------
    // UI state
    // -------------------------------------------------------------------------

    private val _uiState = MutableStateFlow<RecommendationUiState>(RecommendationUiState.Idle)

    val uiState: StateFlow<RecommendationUiState> = _uiState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecommendationUiState.Idle
        )

    // -------------------------------------------------------------------------
    // One-shot events
    // -------------------------------------------------------------------------

    /**
     * Emits the item IDs from a combo when the user taps "Log it".
     * The UI collects this and navigates to the outfit logging flow with the IDs pre-loaded.
     */
    private val _logItEvent = MutableSharedFlow<List<Long>>()
    val logItEvent: SharedFlow<List<Long>> = _logItEvent.asSharedFlow()

    /**
     * Emits a [SaveResult] when "Save for later" completes or fails.
     * The UI collects this to show a confirmation snackbar or an error message.
     */
    private val _saveResult = MutableSharedFlow<SaveResult>()
    val saveResult: SharedFlow<SaveResult> = _saveResult.asSharedFlow()

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    /** User taps "Get suggestions" — open the occasion picker sheet. */
    fun onGetSuggestionsClicked() {
        _uiState.value = RecommendationUiState.OccasionSheet
    }

    /** User selects an occasion — move to weather sheet, carrying the occasion ID. */
    fun onOccasionSelected(occasionId: Long) {
        openWeatherSheet(occasionId)
    }

    /** User skips the occasion sheet — move to weather sheet with no occasion filter. */
    fun onOccasionSkipped() {
        openWeatherSheet(occasionId = null)
    }

    /**
     * User confirms the weather sheet — extract the occasion from the current state,
     * then run the engine with the supplied weather.
     */
    fun onWeatherConfirmed(weather: WeatherConditions) {
        val occasionId = (uiState.value as? RecommendationUiState.WeatherSheet)?.occasionId
        runEngine(occasionId = occasionId, weather = weather)
    }

    /** User skips the weather sheet — run the engine with no weather input. */
    fun onWeatherSkipped() {
        val occasionId = (uiState.value as? RecommendationUiState.WeatherSheet)?.occasionId
        runEngine(occasionId = occasionId, weather = null)
    }

    /**
     * User taps "Regenerate" — re-run the engine with the same occasion and weather
     * that produced the current [RecommendationUiState.Results] or [RecommendationUiState.NoResults].
     */
    fun onRegenerate() {
        val (occasionId, weather) = when (val s = uiState.value) {
            is RecommendationUiState.Results -> s.occasionId to s.weather
            is RecommendationUiState.NoResults -> s.occasionId to s.weather
            else -> return
        }
        runEngine(occasionId = occasionId, weather = weather)
    }

    /**
     * User taps "Log it" on a combo — emits the combo's item IDs via [logItEvent]
     * for the UI to navigate to outfit logging.
     */
    fun onLogIt(combo: OutfitCombo) {
        viewModelScope.launch {
            _logItEvent.emit(combo.items.map { it.id })
        }
    }

    /**
     * User taps "Save for later" — creates a named outfit from the combo.
     *
     * Uses [OutfitRepository.createOutfit] (existing outfit-saving infrastructure).
     * Emits a [SaveResult] via [saveResult] when complete.
     */
    fun onSaveForLater(combo: OutfitCombo) {
        viewModelScope.launch {
            val itemIds = combo.items.map { it.id }
            val result = outfitRepository.createOutfit(
                name = null,   // unnamed — user can rename in the outfit gallery
                notes = null,
                itemIds = itemIds
            )
            when (result) {
                is DataResult.Success -> _saveResult.emit(SaveResult.Saved(result.data))
                is DataResult.Error -> {
                    Timber.e(result.throwable, "RecommendationViewModel: failed to save outfit")
                    _saveResult.emit(SaveResult.Failed(result.throwable.message ?: "Failed to save outfit"))
                }
                DataResult.Loading -> Unit // createOutfit is suspend — Loading is unreachable here
            }
        }
    }

    /** User dismisses any sheet or the suggestions screen — return to Idle. */
    fun onDismiss() {
        _uiState.value = RecommendationUiState.Idle
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Opens the weather sheet, then attempts to pre-populate it from the WeatherRepository
     * cache. If a cached forecast is available, [RecommendationUiState.WeatherSheet.prefill]
     * is set so the UI can show the "Pulled from location data" chip.
     */
    private fun openWeatherSheet(occasionId: Long?) {
        // Open immediately with no prefill — the cache check happens asynchronously below.
        _uiState.value = RecommendationUiState.WeatherSheet(
            occasionId = occasionId,
            prefill = null
        )
        viewModelScope.launch {
            val result = weatherRepository.getForecast()
            if (result is DataResult.Success) {
                val today = result.data.firstOrNull()
                if (today != null) {
                    val prefill = WeatherConditions(
                        tempLowC = today.tempLow,
                        tempHighC = today.tempHigh,
                        isRaining = today.precipitationMm?.let { it > 1.0 } ?: false,
                        isWindy = today.windSpeedKmh > 30.0
                    )
                    // Only update if the user hasn't already moved on from WeatherSheet
                    val current = _uiState.value
                    if (current is RecommendationUiState.WeatherSheet &&
                        current.occasionId == occasionId
                    ) {
                        _uiState.value = current.copy(prefill = prefill)
                    }
                }
            }
            // If the forecast fetch fails, leave prefill = null — the user fills in manually.
        }
    }

    /**
     * Runs the full engine pipeline:
     * 1. Transitions to [Loading].
     * 2. Calls [RecommendationRepository.getCandidates] for the hard-filtered pool.
     * 3. Executes the four score queries in parallel.
     * 4. Maps results to [EngineInput] (including [CandidateItem] → [EngineItem]).
     * 5. Calls [OutfitRecommendationEngine.recommend].
     * 6. Transitions to [Results] or [Error].
     */
    private fun runEngine(occasionId: Long?, weather: WeatherConditions?) {
        _uiState.value = RecommendationUiState.Loading
        viewModelScope.launch {
            try {
                val combos = fetchAndRun(occasionId = occasionId, weather = weather)
                _uiState.value = if (combos.isEmpty()) {
                    RecommendationUiState.NoResults(occasionId = occasionId, weather = weather)
                } else {
                    RecommendationUiState.Results(
                        combos = combos,
                        occasionId = occasionId,
                        weather = weather
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "RecommendationViewModel: engine pipeline failed")
                _uiState.value = RecommendationUiState.Error(
                    message = e.message ?: "Something went wrong. Please try again."
                )
            }
        }
    }

    /**
     * Executes the full data-fetch + engine pipeline, returning the top-N [OutfitCombo]s.
     *
     * All four score queries run concurrently via [async]/[coroutineScope].
     * [DataResult.Error] from any query is converted to an exception so the caller
     * can handle it uniformly.
     *
     * @throws IllegalStateException if any repository call returns [DataResult.Error].
     */
    private suspend fun fetchAndRun(
        occasionId: Long?,
        weather: WeatherConditions?
    ): List<OutfitCombo> = coroutineScope {
        val calendarSeason = currentCalendarSeason()

        // 1. Fetch candidates (sequential — item IDs are needed for subsequent queries)
        val candidatesResult = recommendationRepository.getCandidates(
            calendarSeason = calendarSeason,
            tempLow = weather?.tempLowC,
            tempHigh = weather?.tempHighC,
            occasionId = occasionId
        )
        val candidates = candidatesResult.unwrapOrThrow("getCandidates")
        if (candidates.isEmpty()) return@coroutineScope emptyList()

        val itemIds = candidates.map { it.id }

        // 2. Run all score queries in parallel
        val tempPercentilesDeferred = async {
            recommendationRepository.getTempPercentiles(itemIds).unwrapOrThrow("getTempPercentiles")
        }
        val rainDeferred = async {
            recommendationRepository.getRainSuitability(itemIds).unwrapOrThrow("getRainSuitability")
        }
        val windDeferred = async {
            recommendationRepository.getWindSuitability(itemIds).unwrapOrThrow("getWindSuitability")
        }
        val lastWornDeferred = async {
            recommendationRepository.getLastWornDates(itemIds).unwrapOrThrow("getLastWornDates")
        }
        val colorFamiliesDeferred = async {
            recommendationRepository.getItemColorFamilies(itemIds).unwrapOrThrow("getItemColorFamilies")
        }
        val patternNamesDeferred = async {
            recommendationRepository.getItemPatternNames(itemIds).unwrapOrThrow("getItemPatternNames")
        }

        val tempPercentiles: List<ItemTempPercentiles> = tempPercentilesDeferred.await()
        val rain: List<ItemRainSuitability> = rainDeferred.await()
        val wind: List<ItemWindSuitability> = windDeferred.await()
        val lastWorn: List<ItemLastWorn> = lastWornDeferred.await()
        val colorFamilyRows: List<ItemColorFamily> = colorFamiliesDeferred.await()
        val patternNameRows: List<ItemPatternName> = patternNamesDeferred.await()

        // 3. Index score results by item ID for O(1) lookup
        val tempMap: Map<Long, ItemTempPercentiles> =
            tempPercentiles.associateBy { it.clothingItemId }
        val rainMap: Map<Long, ItemRainSuitability> =
            rain.associateBy { it.clothingItemId }
        val windMap: Map<Long, ItemWindSuitability> =
            wind.associateBy { it.clothingItemId }
        val lastWornMap: Map<Long, ItemLastWorn> =
            lastWorn.associateBy { it.clothingItemId }

        // Group color families into per-item sets
        val colorFamilyMap: Map<Long, Set<String>> =
            colorFamilyRows
                .groupBy { it.clothingItemId }
                .mapValues { (_, rows) -> rows.map { it.colorFamily }.toSet() }

        // Group pattern names into per-item lists; derive solid flag
        val patternNamesMap: Map<Long, List<String>> =
            patternNameRows
                .groupBy { it.clothingItemId }
                .mapValues { (_, rows) -> rows.map { it.patternName } }

        // 4. Map CandidateItem → EngineItem
        val engineItems: List<EngineItem> = candidates.map { item ->
            val patternNames = patternNamesMap[item.id] ?: emptyList()
            EngineItem(
                id = item.id,
                name = item.name,
                imagePath = item.imagePath,
                categoryId = item.categoryId,
                subcategoryId = item.subcategoryId,
                outfitRole = item.outfitRole,
                warmthLayer = item.warmthLayer,
                colorFamilies = colorFamilyMap[item.id] ?: emptySet(),
                isPatternSolid = patternNames.isEmpty() || patternNames.all { it == "Solid" }
            )
        }

        // 5. Build EngineInput
        val engineWeather = weather?.toEngineWeather()
        val input = EngineInput(
            candidates = engineItems,
            tempPercentiles = tempMap,
            rainSuitability = rainMap,
            windSuitability = windMap,
            lastWornDates = lastWornMap,
            weather = engineWeather
        )

        // 6. Run the pure engine — expand the pool when AI is ready so the scorer
        //    has more combos to curate from.
        val aiReady = isAiReady()
        val programmaticCombos = if (aiReady) {
            engine.recommend(input, topN = 25, candidatesPerSlot = 3)
        } else {
            engine.recommend(input)
        }
        if (programmaticCombos.isEmpty()) return@coroutineScope emptyList()

        // 7. Per-item scores used by the engine — needed for ClothingItemDto suitability hints.
        //    Re-derive using the same logic the engine used (safeItems are all in engineItems
        //    since the hard filter is a no-op; engine exposes no public score map, so we
        //    derive here to avoid coupling to engine internals).
        val itemScoresForScorer: Map<Long, Double> = engineItems.associate { item ->
            val baseScore = deriveItemScore(item, input)
            item.id to baseScore
        }

        // 8. Optional AI coherence scoring — replaces top-3 with AI-curated combos if successful
        val styleVibeLabel = aiPrefsRepo.getStyleVibe().first().label
        val aiCombos = try {
            scorer.score(
                combos = programmaticCombos,
                engineInput = input,
                itemScores = itemScoresForScorer,
                styleVibe = styleVibeLabel,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "RecommendationViewModel: scorer threw unexpectedly — ignoring")
            null
        }

        return@coroutineScope aiCombos ?: programmaticCombos
    }

    /**
     * Returns true when AI scoring is both enabled by the user and actually ready to run.
     *
     * Readiness rules per provider:
     * - [AiProvider.Nano]      — [AiPreferencesRepository.getAiReady] must be true (model downloaded).
     * - [AiProvider.OpenAi]   — an API key must have been stored.
     * - [AiProvider.Anthropic] — an API key must have been stored.
     * - [AiProvider.Gemini]   — an API key must have been stored.
     *
     * The master [AiPreferencesRepository.getAiEnabled] toggle is checked first; if the
     * user has AI off, this returns false without reading provider-specific state.
     *
     * Kept as a clean helper so the coherence scorer gating uses the same logic.
     */
    private suspend fun isAiReady(): Boolean {
        if (!aiPrefsRepo.getAiEnabled().first()) return false
        return when (aiPrefsRepo.getSelectedProvider().first()) {
            AiProvider.Nano -> aiPrefsRepo.getAiReady().first()
            AiProvider.OpenAi -> aiPrefsRepo.getOpenAiApiKey().first().isNotBlank()
            AiProvider.Anthropic -> aiPrefsRepo.getAnthropicApiKey().first().isNotBlank()
            AiProvider.Gemini -> aiPrefsRepo.getGeminiApiKey().first().isNotBlank()
        }
    }

    /**
     * Derives a per-item suitability score for use as a context hint in [OutfitCoherenceScorer].
     *
     * Mirrors the scoring logic in [OutfitRecommendationEngine.scoreItem] but is intentionally
     * kept as a private helper here so the engine remains a pure class with no exposed score map.
     * If the engine's scoring logic changes, this helper must be kept in sync.
     */
    private fun deriveItemScore(item: EngineItem, input: EngineInput): Double {
        var score = 1.0
        val weather = input.weather ?: return score

        // Temperature signal (skip if < 5 logs)
        val tempPercentile = input.tempPercentiles[item.id]
        if (tempPercentile != null && tempPercentile.logCount >= 5) {
            val forecastLow = weather.tempLowC
            val forecastHigh = weather.tempHighC
            if (forecastLow != null && forecastHigh != null) {
                val outsideRange =
                    forecastHigh < tempPercentile.p10TempLow ||
                    forecastLow > tempPercentile.p90TempHigh
                if (outsideRange) score *= 0.55
            }
        }

        // Rain signal (skip if < 5 logs)
        if (weather.isRaining) {
            val rain = input.rainSuitability[item.id]
            if (rain != null && rain.rainLogCount >= 5) {
                if (rain.rainPct < 0.20) score *= 0.60
            }
        }

        // Wind signal (skip if < 5 logs)
        if (weather.isWindy) {
            val wind = input.windSuitability[item.id]
            if (wind != null && wind.windLogCount >= 5) {
                if (wind.windPct < 0.20) score *= 0.70
            }
        }

        return score
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /**
     * Returns the current northern-hemisphere calendar season as a season name string
     * matching the seeded values in the `seasons` table
     * (Spring / Summer / Autumn / Winter).
     *
     * Month assignments:
     * - Spring: March, April, May
     * - Summer: June, July, August
     * - Autumn: September, October, November
     * - Winter: December, January, February
     */
    private fun currentCalendarSeason(): String {
        return when (LocalDate.now().month) {
            Month.MARCH, Month.APRIL, Month.MAY -> "Spring"
            Month.JUNE, Month.JULY, Month.AUGUST -> "Summer"
            Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER -> "Autumn"
            else -> "Winter"
        }
    }

    /**
     * Maps a feature-layer [WeatherConditions] to the engine's [EngineWeather].
     *
     * Both types carry identical fields — the mapping exists to keep the engine
     * package free of any dependency on the feature model layer.
     */
    private fun WeatherConditions.toEngineWeather(): EngineWeather = EngineWeather(
        tempLowC = tempLowC,
        tempHighC = tempHighC,
        isRaining = isRaining,
        isWindy = isWindy
    )

    /**
     * Resolves a relative image path to an absolute [java.io.File] for Coil.
     *
     * Mirrors the pattern used in [ClosetViewModel] and [ClothingDetailViewModel].
     * Returns null when [path] is null so the UI can show a placeholder.
     */
    fun resolveImage(path: String): java.io.File = storageRepository.getFile(path)

    /**
     * Unwraps a [DataResult.Success] payload or throws an [IllegalStateException] with
     * a message that includes [operationName]. [DataResult.Loading] is treated as an
     * impossible state for suspend functions and also throws.
     */
    private fun <T> DataResult<T>.unwrapOrThrow(operationName: String): T = when (this) {
        is DataResult.Success -> data
        is DataResult.Error -> throw IllegalStateException(
            "RecommendationViewModel: $operationName failed", throwable
        )
        DataResult.Loading -> throw IllegalStateException(
            "RecommendationViewModel: $operationName returned Loading — this should never happen for suspend calls"
        )
    }
}

// -------------------------------------------------------------------------
// Save result
// -------------------------------------------------------------------------

/**
 * One-shot result for "Save for later" — emitted via [RecommendationViewModel.saveResult].
 */
sealed interface SaveResult {
    /** The outfit was saved successfully. [outfitId] is the new outfit's primary key. */
    data class Saved(val outfitId: Long) : SaveResult

    /** The save failed. [message] is a human-readable description. */
    data class Failed(val message: String) : SaveResult
}
