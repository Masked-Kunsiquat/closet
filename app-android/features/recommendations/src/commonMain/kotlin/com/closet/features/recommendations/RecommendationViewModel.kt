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
import com.closet.core.data.time.AppClock
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.repository.OutfitRepository
import com.closet.core.data.repository.RecommendationRepository
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.repository.WeatherRepository
import com.closet.core.data.util.DataResult
import com.closet.features.recommendations.engine.CoWearStats
import com.closet.features.recommendations.engine.EngineInput
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.EngineWeather
import com.closet.features.recommendations.engine.OutfitCombo
import com.closet.features.recommendations.engine.formalityLevel
import com.closet.features.recommendations.engine.key
import com.closet.features.recommendations.ai.OutfitCoherenceScorer
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.model.WeatherConditions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
import kotlin.random.Random
import co.touchlab.kermit.Logger

private val logger = Logger.withTag("RecommendationViewModel")

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
class RecommendationViewModel(
    private val recommendationRepository: RecommendationRepository,
    private val engine: OutfitRecommendationEngine,
    private val scorer: OutfitCoherenceScorer,
    private val weatherRepository: WeatherRepository,
    private val outfitRepository: OutfitRepository,
    private val lookupRepository: LookupRepository,
    private val imagePathResolver: ImagePathResolver,
    private val aiPrefsRepo: AiPreferencesRepository,
    private val clock: AppClock,
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

    /**
     * Keys of the outfits shown in the current suggestion session. Regenerate excludes them
     * so it shows unseen outfits; cleared when a new session starts (weather confirmed or
     * skipped) and reset when every outfit has been shown (the cycle restarts).
     */
    private val shownComboKeys = mutableSetOf<Set<Long>>()

    /** Keys of the page shown last; the cycle restart excludes them so it doesn't repeat that page. */
    private var lastPageKeys: Set<Set<Long>> = emptySet()

    private var runJob: Job? = null

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
        runEngine(occasionId = occasionId, weather = weather, firstOfSession = true)
    }

    /** User skips the weather sheet — run the engine with no weather input. */
    fun onWeatherSkipped() {
        val occasionId = (uiState.value as? RecommendationUiState.WeatherSheet)?.occasionId
        runEngine(occasionId = occasionId, weather = null, firstOfSession = true)
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
        runEngine(occasionId = occasionId, weather = weather, firstOfSession = false)
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
                    logger.e("RecommendationViewModel: failed to save outfit", result.throwable)
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
     * 5. Calls [OutfitRecommendationEngine.recommend], excluding every combo shown so far this
     *    session ([shownComboKeys]; cleared when [firstOfSession]).
     * 6. Wrap-around: if exclusion leaves nothing, retries excluding only the last page
     *    ([lastPageKeys]), then with no exclusion, and resets the session set — the cycle
     *    restarts instead of showing "no results".
     * 7. Optional AI curation of the pool; the shown page's keys are recorded in [shownComboKeys].
     * 8. Transitions to [Results], [NoResults] or [Error].
     */
    private fun runEngine(occasionId: Long?, weather: WeatherConditions?, firstOfSession: Boolean) {
        runJob?.cancel()
        if (firstOfSession) { // new suggestion session
            shownComboKeys.clear()
            lastPageKeys = emptySet()
        }
        _uiState.value = RecommendationUiState.Loading
        runJob = viewModelScope.launch {
            try {
                val combos = fetchAndRun(occasionId, weather, firstOfSession)
                lastPageKeys = combos.map { it.key() }.toSet()
                shownComboKeys.addAll(lastPageKeys)
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
                logger.e("RecommendationViewModel: engine pipeline failed", e)
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
        weather: WeatherConditions?,
        firstOfSession: Boolean
    ): List<OutfitCombo> = coroutineScope {
        val today = clock.today()
        val calendarSeason = seasonFor(today.month)

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
        val logPairsDeferred = async {
            recommendationRepository.getCoWearPairs(itemIds).unwrapOrThrow("getCoWearPairs")
        }
        val logItemCountsDeferred = async {
            recommendationRepository.getItemLogCounts(itemIds).unwrapOrThrow("getItemLogCounts")
        }
        val logTotalDeferred = async {
            recommendationRepository.getTotalLogCount().unwrapOrThrow("getTotalLogCount")
        }
        val savedPairsDeferred = async {
            recommendationRepository.getSavedOutfitPairs(itemIds).unwrapOrThrow("getSavedOutfitPairs")
        }
        val savedItemCountsDeferred = async {
            recommendationRepository.getSavedItemCounts(itemIds).unwrapOrThrow("getSavedItemCounts")
        }
        val savedTotalDeferred = async {
            recommendationRepository.getSavedOutfitCount().unwrapOrThrow("getSavedOutfitCount")
        }
        val occasionNamesDeferred = async {
            recommendationRepository.getItemOccasionNames(itemIds).unwrapOrThrow("getItemOccasionNames")
        }
        val colorHexesDeferred = async {
            recommendationRepository.getItemColorHexes(itemIds).unwrapOrThrow("getItemColorHexes")
        }

        val tempPercentiles: List<ItemTempPercentiles> = tempPercentilesDeferred.await()
        val rain: List<ItemRainSuitability> = rainDeferred.await()
        val wind: List<ItemWindSuitability> = windDeferred.await()
        val lastWorn: List<ItemLastWorn> = lastWornDeferred.await()
        val colorFamilyRows: List<ItemColorFamily> = colorFamiliesDeferred.await()
        val patternNameRows: List<ItemPatternName> = patternNamesDeferred.await()
        val occasionNamesMap: Map<Long, List<String>> = occasionNamesDeferred.await()
            .groupBy({ it.clothingItemId }, { it.occasionName })
        val colorHexesMap: Map<Long, List<String>> = colorHexesDeferred.await()
            .groupBy({ it.clothingItemId }, { it.hex })

        val saved = CoWearStats.SAVED_OUTFIT_WEIGHT
        val pairCounts = HashMap<Pair<Long, Long>, Double>()
        logPairsDeferred.await().forEach { pairCounts.addTo(it.aId to it.bId, it.count.toDouble()) }
        savedPairsDeferred.await().forEach { pairCounts.addTo(it.aId to it.bId, it.count * saved) }
        val itemCounts = HashMap<Long, Double>()
        logItemCountsDeferred.await().forEach { itemCounts.addTo(it.clothingItemId, it.count.toDouble()) }
        savedItemCountsDeferred.await().forEach { itemCounts.addTo(it.clothingItemId, it.count * saved) }
        val coWear = CoWearStats(
            pairCounts = pairCounts,
            itemCounts = itemCounts,
            total = logTotalDeferred.await() + saved * savedTotalDeferred.await(),
        )

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
                isPatternSolid = patternNames.isEmpty() || patternNames.all { it == "Solid" },
                colorHexes = colorHexesMap[item.id] ?: emptyList(),
                formalityLevel = formalityLevel(occasionNamesMap[item.id] ?: emptyList()),
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
            weather = engineWeather,
            today = today,
            coWear = coWear
        )

        // 6. Run the pure engine — expand the pool when AI is ready so the scorer
        //    has more combos to curate from. Regenerate excludes combos already shown
        //    this session. Only the session's first run seeds the jitter with the date (the
        //    day's first pick is stable within a day but varies between days); Regenerate
        //    uses an unseeded Random.
        val aiReady = isAiReady()
        val runEngineOnce = { exclude: Set<Set<Long>> ->
            // First pick of a session is seeded by the date; Regenerate is unseeded.
            val jitter = if (firstOfSession) Random(today.toEpochDays()) else Random(Random.nextLong())
            if (aiReady) {
                engine.recommend(
                    input, topN = 25, candidatesPerSlot = 3, exclude = exclude, jitter = jitter
                )
            } else {
                engine.recommend(input, exclude = exclude, jitter = jitter)
            }
        }
        val shownSoFar = shownComboKeys.toSet()
        var programmaticCombos = runEngineOnce(shownSoFar)
        if (programmaticCombos.isEmpty() && shownSoFar.isNotEmpty()) {
            // Everything has been shown: restart the cycle, avoiding the page just seen if
            // possible. runEngine then records only the new page's keys.
            shownComboKeys.clear()
            if (lastPageKeys.isNotEmpty() && lastPageKeys != shownSoFar) {
                programmaticCombos = runEngineOnce(lastPageKeys)
            }
            if (programmaticCombos.isEmpty()) programmaticCombos = runEngineOnce(emptySet())
        }
        if (programmaticCombos.isEmpty()) return@coroutineScope emptyList()

        // 7. Per-item scores used by the engine — needed for ClothingItemDto suitability hints.
        //    Uses the engine's own per-item scoring (weather signals + wear recency).
        val itemScoresForScorer: Map<Long, Double> = engineItems.associate { item ->
            item.id to engine.itemScore(item, input)
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
            logger.w("RecommendationViewModel: scorer threw unexpectedly — ignoring", e)
            null
        }

        // AI fallback shows only the default page, so only what is shown gets recorded.
        return@coroutineScope aiCombos ?: programmaticCombos.take(OutfitRecommendationEngine.TOP_N)
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

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

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
     * Resolves a relative image path to an absolute path string for Coil.
     *
     * Returns null when [path] is null so the UI can show a placeholder.
     */
    fun resolveImage(path: String?): String? = path?.let(imagePathResolver::absolutePath)

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

private fun <K> MutableMap<K, Double>.addTo(key: K, amount: Double) {
    this[key] = (this[key] ?: 0.0) + amount
}
