package com.closet.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.ai.NanoInitResult
import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.model.AiProvider
import com.closet.core.data.repository.BatchCaptioner
import com.closet.core.data.repository.ImageStorageStats
import com.closet.core.data.util.EmbeddingIndex
import com.closet.core.data.worker.EmbeddingScheduler
import com.closet.core.data.worker.ImageCompressionScheduler
import com.closet.core.data.model.StyleVibe
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherService
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.repository.ModelDiscoveryRepository
import com.closet.core.data.repository.WeatherPreferencesRepository
import com.closet.core.data.worker.BatchSegmentationScheduler
import com.closet.core.data.worker.ColorRedetectScheduler
import com.closet.core.data.worker.WorkState
import com.closet.core.data.worker.WorkStatus
import com.closet.core.ui.preferences.PreferencesRepository
import com.closet.core.ui.theme.ClosetAccent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger
import kotlin.uuid.Uuid

// ---------------------------------------------------------------------------
// Intermediate grouping types — private to this file
// ---------------------------------------------------------------------------

private val log = Logger.withTag("SettingsViewModel")

private data class AppPrefs(
    val accent: ClosetAccent,
    val dynamicColor: Boolean,
    val weatherEnabled: Boolean,
    val weatherService: WeatherService,
    val googleApiKey: String,
    val temperatureUnit: TemperatureUnit,
)

private data class AiCorePrefs(
    val aiEnabled: Boolean,
    val styleVibe: StyleVibe,
    val selectedAiProvider: AiProvider,
    val nanoStatus: NanoStatus,
)

private data class OpenAiPrefs(
    val key: String,
    val baseUrl: String,
    val model: String,
    val models: List<String>,
    val modelsLoading: Boolean,
)

private data class AnthropicPrefs(
    val key: String,
    val model: String,
    val models: List<String>,
    val modelsLoading: Boolean,
)

private data class GeminiPrefs(
    val key: String,
    val model: String,
    val models: List<String>,
    val modelsLoading: Boolean,
)

private data class EmbeddingState(val workStatus: WorkStatus?, val indexSize: Int)

private data class CaptionState(
    val eligibleCount: Int,
    val progress: com.closet.core.data.ai.BatchCaptionProgress?,
    val result: com.closet.core.data.ai.BatchCaptionResult?,
    val lastHandledCaptionId: Uuid?,
)

private data class SegmentationState(
    val eligibleCount: Int,
    val workStatus: WorkStatus?,
    val lastHandledBatchId: String?,
)

private data class ColorRedetectState(
    val uncuratedCount: Int,
    val allCount: Int,
    val workStatus: WorkStatus?,
    val lastHandledId: String?,
)

private data class StorageState(val usedBytes: Long, val compressionWorkStatus: WorkStatus?)

/**
 * ViewModel for the Settings screen.
 */
@OptIn(FlowPreview::class)
class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val weatherPreferencesRepository: WeatherPreferencesRepository,
    private val clothingDao: ClothingDao,
    private val modelDiscovery: ModelDiscoveryRepository,
    private val nanoInitializer: NanoInitializer,
    private val embeddingScheduler: EmbeddingScheduler,
    private val embeddingIndex: EmbeddingIndex,
    private val batchSegmentationScheduler: BatchSegmentationScheduler,
    private val batchCaptioner: BatchCaptioner,
    private val imageCompressionScheduler: ImageCompressionScheduler,
    private val imageStorageStats: ImageStorageStats,
    private val colorRedetectScheduler: ColorRedetectScheduler,
) : ViewModel() {

    // ── Private mutable state ─────────────────────────────────────────────────

    private val _nanoStatus = MutableStateFlow<NanoStatus>(NanoStatus.Idle)
    private val _openAiModels = MutableStateFlow<List<String>>(emptyList())
    private val _openAiModelsLoading = MutableStateFlow(false)
    private val _anthropicModels = MutableStateFlow<List<String>>(emptyList())
    private val _anthropicModelsLoading = MutableStateFlow(false)
    private val _geminiModels = MutableStateFlow<List<String>>(emptyList())
    private val _geminiModelsLoading = MutableStateFlow(false)
    private val _storageUsedBytes = MutableStateFlow(-1L)
    private val _embeddingIndexSize = MutableStateFlow(embeddingIndex.size)
    private val _lastHandledCaptionId = MutableStateFlow<Uuid?>(null)

    // ── Intermediate flows (private, used to build uiState) ───────────────────

    private val appPrefsFlow = combine(
        combine(
            preferencesRepository.getAccent(),
            preferencesRepository.getDynamicColor(),
            weatherPreferencesRepository.getWeatherEnabled(),
        ) { accent, dynamic, weatherEnabled -> Triple(accent, dynamic, weatherEnabled) },
        combine(
            weatherPreferencesRepository.getWeatherService(),
            weatherPreferencesRepository.getGoogleApiKey(),
            weatherPreferencesRepository.getTemperatureUnit(),
        ) { service, apiKey, unit -> Triple(service, apiKey, unit) },
    ) { (accent, dynamic, weatherEnabled), (service, apiKey, unit) ->
        AppPrefs(accent, dynamic, weatherEnabled, service, apiKey, unit)
    }

    private val aiCorePrefsFlow = combine(
        aiPreferencesRepository.getAiEnabled(),
        aiPreferencesRepository.getStyleVibe(),
        aiPreferencesRepository.getSelectedProvider(),
        _nanoStatus,
    ) { enabled, vibe, provider, nano -> AiCorePrefs(enabled, vibe, provider, nano) }

    private val openAiPrefsFlow = combine(
        aiPreferencesRepository.getOpenAiApiKey(),
        aiPreferencesRepository.getOpenAiBaseUrl(),
        aiPreferencesRepository.getOpenAiModel(),
        _openAiModels,
        _openAiModelsLoading,
    ) { key, baseUrl, model, models, loading -> OpenAiPrefs(key, baseUrl, model, models, loading) }

    private val anthropicPrefsFlow = combine(
        aiPreferencesRepository.getAnthropicApiKey(),
        aiPreferencesRepository.getAnthropicModel(),
        _anthropicModels,
        _anthropicModelsLoading,
    ) { key, model, models, loading -> AnthropicPrefs(key, model, models, loading) }

    private val geminiPrefsFlow = combine(
        aiPreferencesRepository.getGeminiApiKey(),
        aiPreferencesRepository.getGeminiModel(),
        _geminiModels,
        _geminiModelsLoading,
    ) { key, model, models, loading -> GeminiPrefs(key, model, models, loading) }

    private val embeddingStateFlow = combine(
        embeddingScheduler.workStatus,
        _embeddingIndexSize,
    ) { workStatus, size -> EmbeddingState(workStatus, size) }

    private val captionStateFlow = combine(
        clothingDao.getCaptionEligibleCount(),
        batchCaptioner.progress,
        batchCaptioner.result,
        _lastHandledCaptionId,
    ) { eligible, progress, result, lastHandled -> CaptionState(eligible, progress, result, lastHandled) }

    private val segmentationStateFlow = combine(
        clothingDao.getSegmentationEligibleCount(),
        batchSegmentationScheduler.workStatus,
        preferencesRepository.lastHandledBatchId,
    ) { eligible, workStatus, batchId -> SegmentationState(eligible, workStatus, batchId) }

    private val colorRedetectStateFlow = combine(
        clothingDao.getColorRedetectEligibleCount(onlyUncurated = true),
        clothingDao.getColorRedetectEligibleCount(onlyUncurated = false),
        colorRedetectScheduler.workStatus,
        preferencesRepository.lastHandledColorRedetectId,
    ) { uncurated, all, workStatus, lastHandled ->
        ColorRedetectState(uncurated, all, workStatus, lastHandled)
    }

    private val storageStateFlow = combine(
        _storageUsedBytes,
        imageCompressionScheduler.workStatus,
    ) { bytes, workStatus -> StorageState(bytes, workStatus) }

    // ── Public single state flow ──────────────────────────────────────────────

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(appPrefsFlow, aiCorePrefsFlow, openAiPrefsFlow) { app, ai, openAi ->
            Triple(app, ai, openAi)
        },
        combine(anthropicPrefsFlow, geminiPrefsFlow, embeddingStateFlow) { anth, gem, emb ->
            Triple(anth, gem, emb)
        },
        combine(captionStateFlow, segmentationStateFlow, colorRedetectStateFlow) { cap, seg, color ->
            Triple(cap, seg, color)
        },
        storageStateFlow,
    ) { (app, ai, openAi), (anth, gem, emb), (cap, seg, color), storage ->
        SettingsUiState(
            accent = app.accent,
            dynamicColor = app.dynamicColor,
            weatherEnabled = app.weatherEnabled,
            weatherService = app.weatherService,
            googleApiKey = app.googleApiKey,
            temperatureUnit = app.temperatureUnit,
            aiEnabled = ai.aiEnabled,
            styleVibe = ai.styleVibe,
            selectedAiProvider = ai.selectedAiProvider,
            nanoStatus = ai.nanoStatus,
            openAiKey = openAi.key,
            openAiBaseUrl = openAi.baseUrl,
            openAiModel = openAi.model,
            openAiModels = openAi.models,
            openAiModelsLoading = openAi.modelsLoading,
            anthropicKey = anth.key,
            anthropicModel = anth.model,
            anthropicModels = anth.models,
            anthropicModelsLoading = anth.modelsLoading,
            geminiKey = gem.key,
            geminiModel = gem.model,
            geminiModels = gem.models,
            geminiModelsLoading = gem.modelsLoading,
            embeddingWorkStatus = emb.workStatus,
            embeddingIndexSize = emb.indexSize,
            captionSupported = batchCaptioner.isSupported,
            captionEligibleCount = cap.eligibleCount,
            batchCaptionProgress = cap.progress,
            captionResult = cap.result,
            lastHandledCaptionId = cap.lastHandledCaptionId,
            segmentationSupported = batchSegmentationScheduler.isSupported,
            segmentationEligibleCount = seg.eligibleCount,
            batchSegWorkStatus = seg.workStatus,
            lastHandledBatchId = seg.lastHandledBatchId,
            colorRedetectSupported = colorRedetectScheduler.isSupported,
            colorRedetectUncuratedCount = color.uncuratedCount,
            colorRedetectAllCount = color.allCount,
            colorRedetectWorkStatus = color.workStatus,
            lastHandledColorRedetectId = color.lastHandledId,
            storageUsedBytes = storage.usedBytes,
            compressionWorkStatus = storage.compressionWorkStatus,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(
            captionSupported = batchCaptioner.isSupported,
            segmentationSupported = batchSegmentationScheduler.isSupported,
            colorRedetectSupported = colorRedetectScheduler.isSupported,
        ),
    )

    // ── Initialization ────────────────────────────────────────────────────────

    init {
        // Track Gemini Nano init status.
        viewModelScope.launch {
            _nanoStatus.value = NanoStatus.Checking
            nanoInitializer.initNanoFlow().collect { result ->
                // Persist the outcome: chat, recommendations and the coherence scorer gate Nano on
                // getAiReady(); chat words its "not ready" error from the availability; the token
                // limit feeds the recommendations trim gate (0 = unknown).
                try {
                    nanoReadyFor(result)?.let { aiPreferencesRepository.setAiReady(it) }
                    aiPreferencesRepository.setNanoAvailability(nanoAvailabilityFor(result))
                    if (result is NanoInitResult.Success && result.tokenLimit > 0) {
                        aiPreferencesRepository.setTokenLimit(result.tokenLimit)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    log.w(e) { "failed to persist Nano readiness" }
                }
                _nanoStatus.value = when (result) {
                    is NanoInitResult.Downloading -> NanoStatus.Downloading(result.progressPct)
                    is NanoInitResult.Success -> NanoStatus.Ready
                    is NanoInitResult.Failed -> NanoStatus.Failed(result.reason)
                    NanoInitResult.NotSupported -> NanoStatus.NotSupported
                }
            }
        }

        // Fetch OpenAI models when key, base URL, or AI-enabled changes.
        viewModelScope.launch {
            combine(
                aiPreferencesRepository.getOpenAiApiKey(),
                aiPreferencesRepository.getOpenAiBaseUrl(),
                aiPreferencesRepository.getAiEnabled(),
            ) { key, url, enabled -> Triple(key, url, enabled) }
                .debounce(800)
                .collectLatest { (key, url, enabled) ->
                    if (!enabled || key.isBlank()) {
                        _openAiModels.value = emptyList()
                        return@collectLatest
                    }
                    _openAiModelsLoading.value = true
                    try {
                        modelDiscovery.fetchOpenAiModels(key, url)
                            .onSuccess { _openAiModels.value = it }
                            .onFailure { _openAiModels.value = emptyList() }
                    } finally {
                        _openAiModelsLoading.value = false
                    }
                }
        }

        // Fetch Anthropic models when key or AI-enabled changes.
        viewModelScope.launch {
            combine(
                aiPreferencesRepository.getAnthropicApiKey(),
                aiPreferencesRepository.getAiEnabled(),
            ) { key, enabled -> key to enabled }
                .debounce(800)
                .collectLatest { (key, enabled) ->
                    if (!enabled || key.isBlank()) {
                        _anthropicModels.value = emptyList()
                        return@collectLatest
                    }
                    _anthropicModelsLoading.value = true
                    try {
                        modelDiscovery.fetchAnthropicModels(key)
                            .onSuccess { _anthropicModels.value = it }
                            .onFailure { _anthropicModels.value = emptyList() }
                    } finally {
                        _anthropicModelsLoading.value = false
                    }
                }
        }

        // Fetch Gemini models when key or AI-enabled changes.
        viewModelScope.launch {
            combine(
                aiPreferencesRepository.getGeminiApiKey(),
                aiPreferencesRepository.getAiEnabled(),
            ) { key, enabled -> key to enabled }
                .debounce(800)
                .collectLatest { (key, enabled) ->
                    if (!enabled || key.isBlank()) {
                        _geminiModels.value = emptyList()
                        return@collectLatest
                    }
                    _geminiModelsLoading.value = true
                    try {
                        modelDiscovery.fetchGeminiModels(key)
                            .onSuccess { _geminiModels.value = it }
                            .onFailure { _geminiModels.value = emptyList() }
                    } finally {
                        _geminiModelsLoading.value = false
                    }
                }
        }

        // Refresh in-memory index size when a rebuild completes.
        viewModelScope.launch {
            embeddingScheduler.workStatus.collect { info ->
                if (info?.state == WorkState.SUCCEEDED || info?.state == WorkState.FAILED) {
                    try {
                        embeddingIndex.load()
                        _embeddingIndexSize.value = embeddingIndex.size
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        log.e(e) { "Failed to reload embedding index" }
                    }
                }
            }
        }

        // Compute closet_images/ storage size once on start, and refresh after each compression run.
        viewModelScope.launch {
            _storageUsedBytes.value = imageStorageStats.usedBytes()
            imageCompressionScheduler.workStatus.collect { info ->
                if (info?.state == WorkState.SUCCEEDED || info?.state == WorkState.FAILED) {
                    _storageUsedBytes.value = imageStorageStats.usedBytes()
                }
            }
        }
    }

    // ── App settings event handlers ───────────────────────────────────────────

    fun setAccent(accent: ClosetAccent) {
        viewModelScope.launch { preferencesRepository.setAccent(accent) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) }
    }

    fun setWeatherEnabled(enabled: Boolean) {
        viewModelScope.launch { weatherPreferencesRepository.setWeatherEnabled(enabled) }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { weatherPreferencesRepository.setTemperatureUnit(unit) }
    }

    fun setWeatherService(service: WeatherService) {
        viewModelScope.launch { weatherPreferencesRepository.setWeatherService(service) }
    }

    fun setGoogleApiKey(key: String) {
        viewModelScope.launch { weatherPreferencesRepository.setGoogleApiKey(key) }
    }

    fun clearForecastCache() {
        viewModelScope.launch { weatherPreferencesRepository.clearCache() }
    }

    // ── AI settings event handlers ────────────────────────────────────────────

    fun onAiToggled(enabled: Boolean) {
        viewModelScope.launch { aiPreferencesRepository.setAiEnabled(enabled) }
    }

    fun onStyleVibeSelected(vibe: StyleVibe) {
        viewModelScope.launch { aiPreferencesRepository.setStyleVibe(vibe) }
    }

    fun onAiProviderSelected(provider: AiProvider) {
        viewModelScope.launch { aiPreferencesRepository.setSelectedProvider(provider) }
    }

    // ── Provider key/model event handlers ────────────────────────────────────

    fun onOpenAiKeyChanged(key: String) {
        viewModelScope.launch { aiPreferencesRepository.setOpenAiApiKey(key) }
    }

    fun onOpenAiBaseUrlChanged(url: String) {
        viewModelScope.launch { aiPreferencesRepository.setOpenAiBaseUrl(url) }
    }

    fun onOpenAiModelChanged(model: String) {
        viewModelScope.launch { aiPreferencesRepository.setOpenAiModel(model) }
    }

    fun onAnthropicKeyChanged(key: String) {
        viewModelScope.launch { aiPreferencesRepository.setAnthropicApiKey(key) }
    }

    fun onAnthropicModelChanged(model: String) {
        viewModelScope.launch { aiPreferencesRepository.setAnthropicModel(model) }
    }

    fun onGeminiKeyChanged(key: String) {
        viewModelScope.launch { aiPreferencesRepository.setGeminiApiKey(key) }
    }

    fun onGeminiModelChanged(model: String) {
        viewModelScope.launch { aiPreferencesRepository.setGeminiModel(model) }
    }

    // ── Image compression event handlers ─────────────────────────────────────

    fun onCompressImages() {
        imageCompressionScheduler.runNow()
    }

    // ── Search index event handlers ───────────────────────────────────────────

    fun onRebuildEmbeddingIndex() {
        embeddingScheduler.runNow()
    }

    // ── Batch captioning event handlers ───────────────────────────────────────

    fun onStartBatchCaption() {
        batchCaptioner.startBatchEnrichment()
    }

    fun onCaptionResultHandled(id: Uuid) {
        _lastHandledCaptionId.value = id
    }

    fun onCaptionResultConsumed() {
        batchCaptioner.consumeResult()
    }

    // ── Batch segmentation event handlers ─────────────────────────────────────

    fun onStartBatchSegmentation() {
        batchSegmentationScheduler.schedule()
    }

    fun onBatchResultHandled(id: String) {
        viewModelScope.launch {
            preferencesRepository.setLastHandledBatchId(id)
                .onFailure { log.e(it) { "Failed to persist last handled batch ID" } }
        }
    }

    // ── Bulk colour re-detect event handlers (#128) ───────────────────────────

    /** Starts the bulk re-detect with the confirmation dialog's choice. */
    fun onStartColorRedetect(onlyUncurated: Boolean) {
        colorRedetectScheduler.schedule(onlyUncurated)
    }

    fun onColorRedetectResultHandled(id: String) {
        viewModelScope.launch {
            preferencesRepository.setLastHandledColorRedetectId(id)
                .onFailure { log.e(it) { "Failed to persist last handled colour re-detect ID" } }
        }
    }
}
