package com.closet.features.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.dao.CandidateItem
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
import com.closet.features.recommendations.engine.EngineInput
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.EngineWeather
import com.closet.features.recommendations.engine.OutfitCombo
import com.closet.features.recommendations.engine.buildItemNeighbors
import com.closet.features.recommendations.engine.formalityLevel
import com.closet.features.recommendations.engine.withEffectiveWeather
import com.closet.features.recommendations.engine.key
import com.closet.features.recommendations.engine.learning.ConditionalLogitOptimizer
import com.closet.features.recommendations.engine.learning.HistoricalWearLog
import com.closet.features.recommendations.engine.learning.TasteWeightTrainer
import com.closet.features.recommendations.engine.learning.TasteWeights
import com.closet.features.recommendations.ai.OutfitCoherenceScorer
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.model.WeatherConditions
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** Candidates → score queries → EngineInput → engine; taste weights come from [resolveTasteWeights]. */
    private val pipeline = RecommendationPipeline(
        recommendationRepository = recommendationRepository,
        engine = engine,
        computationDispatcher = computationDispatcher,
        tasteWeights = { totalLogCount -> resolveTasteWeights(totalLogCount) },
    )

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
     * Data fetch + engine for one run; returns the [OutfitCombo]s to show.
     *
     * [pipeline] builds the [EngineInput] once (candidates, parallel score queries, taste
     * weights); this function owns the session parts: the exclusion wrap-around, the AI pool
     * size, the jitter choice and the AI scorer.
     *
     * @throws IllegalStateException if any repository call returns [DataResult.Error].
     */
    private suspend fun fetchAndRun(
        occasionId: Long?,
        weather: WeatherConditions?,
        firstOfSession: Boolean
    ): List<OutfitCombo> {
        val today = clock.today()

        // 1–6. Candidates → parallel queries → EngineInput (see RecommendationPipeline).
        val baseRequest = PipelineRequest(
            today = today,
            occasionId = occasionId,
            weather = weather,
            topN = OutfitRecommendationEngine.TOP_N,
            jitter = Random(today.toEpochDays()),
        )
        val input = pipeline.engineInput(baseRequest) ?: return emptyList()

        // 6. Run the pure engine — expand the pool when AI is ready so the scorer
        //    has more combos to curate from. Regenerate excludes combos already shown
        //    this session. Only the session's first run seeds the engine's selection with the
        //    date (the day's first pick is stable within a day but varies between days);
        //    Regenerate uses an unseeded Random.
        val aiReady = isAiReady()
        val topN = if (aiReady) 25 else OutfitRecommendationEngine.TOP_N
        val runEngineOnce = { exclude: Set<Set<Long>> ->
            // First pick of a session is seeded by the date; Regenerate is unseeded.
            val jitter = if (firstOfSession) Random(today.toEpochDays()) else Random(Random.nextLong())
            pipeline.recommend(input, baseRequest.copy(topN = topN, exclude = exclude, jitter = jitter))
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
        if (programmaticCombos.isEmpty()) return emptyList()

        // 7. Per-item scores used by the engine — needed for ClothingItemDto suitability hints.
        //    Uses the engine's own per-item scoring (weather signals + wear recency).
        val itemScoresForScorer: Map<Long, Double> = input.candidates.associate { item ->
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
        return aiCombos ?: programmaticCombos.take(OutfitRecommendationEngine.TOP_N)
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
     * Resolves the user's personalized [TasteWeights] from [AiPreferencesRepository], retraining
     * on [computationDispatcher] when `totalLogCount >= 20` and the database log watermark
     * (`"$TASTE_WEIGHTS_SCHEMA_VERSION:$totalLogCount:$maxLogId"`) differs from the stored watermark.
     *
     * Any failure during training logs a warning and falls back to the stored or [TasteWeights.DEFAULT]
     * weights so recommendation generation is never blocked.
     *
     * While [LEARNED_TASTE_ENABLED] is false, this always returns [TasteWeights.DEFAULT] and never
     * trains or reads stored weights.
     */
    private suspend fun resolveTasteWeights(totalLogCount: Int): TasteWeights {
        if (!LEARNED_TASTE_ENABLED || totalLogCount < ConditionalLogitOptimizer.MIN_STRATA_FOR_TRAINING) {
            return TasteWeights.DEFAULT
        }
        return try {
            val maxLogId = (recommendationRepository.getMaxLogId() as? DataResult.Success)?.data ?: 0L
            val currentWatermark = "$TASTE_WEIGHTS_SCHEMA_VERSION:$totalLogCount:$maxLogId"
            val storedWatermark = aiPrefsRepo.getTasteWeightsWatermark().first()
            val storedRaw = aiPrefsRepo.getTasteWeightsRaw().first()

            if (storedWatermark == currentWatermark && !storedRaw.isNullOrBlank()) {
                return TasteWeights.deserialize(storedRaw)
            }

            val trained = trainTasteWeightsFromRepo()
            val persistResult = aiPrefsRepo.setTasteWeights(trained.serialize(), currentWatermark)
            if (persistResult is DataResult.Error) {
                logger.w("RecommendationViewModel: failed to persist taste weights — using in-memory weights", persistResult.throwable)
            }
            trained
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w("RecommendationViewModel: taste weight resolution failed — falling back to DEFAULT", e)
            TasteWeights.DEFAULT
        }
    }

    private suspend fun trainTasteWeightsFromRepo(): TasteWeights = coroutineScope {
        val trainingItemsDeferred = async {
            recommendationRepository.getTrainingItems().unwrapOrThrow("getTrainingItems")
        }
        val wearLogsDeferred = async {
            recommendationRepository.getTrainingWearLogs().unwrapOrThrow("getTrainingWearLogs")
        }
        val wearLogItemsDeferred = async {
            recommendationRepository.getTrainingWearLogItems().unwrapOrThrow("getTrainingWearLogItems")
        }

        val trainingItems = trainingItemsDeferred.await()
        val wearLogs = wearLogsDeferred.await()
        val wearLogItems = wearLogItemsDeferred.await()
        if (trainingItems.isEmpty() || wearLogs.size < ConditionalLogitOptimizer.MIN_STRATA_FOR_TRAINING) {
            return@coroutineScope TasteWeights.DEFAULT
        }

        val allItemIds = trainingItems.map { it.id }
        val colorFamiliesDeferred = async {
            recommendationRepository.getItemColorFamilies(allItemIds).unwrapOrThrow("getItemColorFamilies")
        }
        val patternNamesDeferred = async {
            recommendationRepository.getItemPatternNames(allItemIds).unwrapOrThrow("getItemPatternNames")
        }
        val occasionNamesDeferred = async {
            recommendationRepository.getItemOccasionNames(allItemIds).unwrapOrThrow("getItemOccasionNames")
        }
        val colorHexesDeferred = async {
            recommendationRepository.getItemColorHexes(allItemIds).unwrapOrThrow("getItemColorHexes")
        }
        val embeddingsDeferred = async {
            recommendationRepository.getItemEmbeddings(allItemIds).unwrapOrThrow("getItemEmbeddings")
        }
        val subcategoryNamesDeferred = async {
            recommendationRepository.getItemSubcategoryNames(allItemIds).unwrapOrThrow("getItemSubcategoryNames")
        }
        val materialNamesDeferred = async {
            recommendationRepository.getItemMaterialNames(allItemIds).unwrapOrThrow("getItemMaterialNames")
        }
        val tempPercentilesDeferred = async {
            recommendationRepository.getTempPercentiles(allItemIds).unwrapOrThrow("getTempPercentiles")
        }
        val rainDeferred = async {
            recommendationRepository.getRainSuitability(allItemIds).unwrapOrThrow("getRainSuitability")
        }

        val colorFamilyMap = colorFamiliesDeferred.await()
            .groupBy { it.clothingItemId }
            .mapValues { (_, rows) -> rows.map { it.colorFamily }.toSet() }
        val patternNamesMap = patternNamesDeferred.await()
            .groupBy { it.clothingItemId }
            .mapValues { (_, rows) -> rows.map { it.patternName } }
        val occasionNamesMap = occasionNamesDeferred.await()
            .groupBy({ it.clothingItemId }, { it.occasionName })
        val colorHexesMap = colorHexesDeferred.await()
            .groupBy({ it.clothingItemId }, { it.toColorHex() })
        val embeddingsMap = embeddingsDeferred.await()
        val subcategoryNamesMap = subcategoryNamesDeferred.await()
        val materialNamesMap = materialNamesDeferred.await()
        val tempMap = tempPercentilesDeferred.await().associateBy { it.clothingItemId }
        val rainMap = rainDeferred.await().associateBy { it.clothingItemId }

        withContext(computationDispatcher) {
            val createdDates = HashMap<Long, LocalDate>(trainingItems.size)
            val engineItems = trainingItems.map { row ->
                val patterns = patternNamesMap[row.id] ?: emptyList()
                row.createdAtEpochMillis?.let { millis ->
                    runCatching {
                        Instant.fromEpochMilliseconds(millis)
                            .toLocalDateTime(TimeZone.UTC)
                            .date
                    }.getOrNull()?.let { date -> createdDates[row.id] = date }
                }
                EngineItem(
                    id = row.id,
                    name = row.name,
                    imagePath = row.imagePath,
                    categoryId = row.categoryId,
                    subcategoryId = row.subcategoryId,
                    outfitRole = row.outfitRole,
                    warmthLayer = row.warmthLayer,
                    colorFamilies = colorFamilyMap[row.id] ?: emptySet(),
                    isPatternSolid = patterns.isEmpty() || patterns.all { it == "Solid" },
                    colorHexes = colorHexesMap[row.id] ?: emptyList(),
                    formalityLevel = formalityLevel(occasionNamesMap[row.id] ?: emptyList()),
                    subcategoryName = subcategoryNamesMap[row.id],
                    materials = materialNamesMap[row.id] ?: emptySet(),
                ).withEffectiveWeather(
                    warmthOverride = row.warmthOverride,
                    rainReadyOverride = row.rainReadyOverride,
                    temps = tempMap[row.id],
                    rain = rainMap[row.id],
                )
            }

            val itemsByLogId = wearLogItems.groupBy({ it.outfitLogId }, { it.clothingItemId })
            val historicalLogs = wearLogs.mapNotNull { row ->
                val date = runCatching { LocalDate.parse(row.date.take(10)) }.getOrNull() ?: return@mapNotNull null
                val ids = itemsByLogId[row.id].orEmpty()
                if (ids.isEmpty()) return@mapNotNull null
                val hasWeather = row.temperatureLow != null ||
                    row.temperatureHigh != null ||
                    row.precipitationMm != null ||
                    row.windSpeedKmh != null
                val logWeather = if (hasWeather) {
                    EngineWeather(
                        tempLowC = row.temperatureLow,
                        tempHighC = row.temperatureHigh,
                        isRaining = (row.precipitationMm ?: 0.0) > 1.0,
                        isWindy = (row.windSpeedKmh ?: 0.0) > 30.0,
                    )
                } else {
                    null
                }
                HistoricalWearLog(
                    logId = row.id,
                    date = date,
                    itemIds = ids,
                    weather = logWeather,
                )
            }

            val neighbors = buildItemNeighbors(embeddingsMap)
            TasteWeightTrainer.trainFromHistory(
                items = engineItems,
                logs = historicalLogs,
                createdDates = createdDates,
                itemNeighbors = neighbors,
                engine = engine,
            ).weights
        }
    }

    companion object {
        internal const val TASTE_WEIGHTS_SCHEMA_VERSION = "v5"

        /**
         * Learned taste is paused (#142): suggestions use [TasteWeights.DEFAULT], which already
         * includes the co-wear habit at default strength. On real histories, learned weights made
         * suggestions worse (dropped shoes) because training compares cores with fixed extras
         * while serving also uses the weights to choose extras. The trainer stays built and
         * tested; turn this back on only after a redesign passes the realistic-history
         * scoreboard.
         */
        internal const val LEARNED_TASTE_ENABLED = false
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /**
     * Resolves a relative image path to an absolute path string for Coil.
     *
     * Returns null when [path] is null so the UI can show a placeholder.
     */
    fun resolveImage(path: String?): String? = path?.let(imagePathResolver::absolutePath)
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
