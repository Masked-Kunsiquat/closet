package com.closet.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.closet.core.data.util.BitmapUtils
import com.closet.core.data.ai.NanoInitResult
import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.model.AiProvider
import com.closet.core.data.repository.CaptionEnrichmentProvider
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.EmbeddingIndex
import com.closet.core.data.worker.EmbeddingScheduler
import com.closet.core.data.worker.EmbeddingWork
import com.closet.core.data.model.StyleVibe
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherService
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.repository.ModelDiscoveryRepository
import com.closet.core.data.repository.WeatherPreferencesRepository
import com.closet.core.data.worker.BatchSegmentationScheduler
import com.closet.core.data.worker.BatchSegmentationWork
import com.closet.core.ui.preferences.PreferencesRepository
import com.closet.core.ui.theme.ClosetAccent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val weatherPreferencesRepository: WeatherPreferencesRepository,
    private val clothingDao: ClothingDao,
    private val storageRepository: StorageRepository,
    private val modelDiscovery: ModelDiscoveryRepository,
    private val nanoInitializer: NanoInitializer,
    private val embeddingScheduler: EmbeddingScheduler,
    private val embeddingIndex: EmbeddingIndex,
    private val batchSegmentationScheduler: BatchSegmentationScheduler,
    private val captionEnrichmentProvider: CaptionEnrichmentProvider,
) : ViewModel() {

    // ── App Settings ──────────────────────────────────────────────────────────

    val accent: StateFlow<ClosetAccent> = preferencesRepository.accent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClosetAccent.Blue)

    fun onAccentSelected(accent: ClosetAccent) {
        viewModelScope.launch {
            preferencesRepository.setAccent(accent)
        }
    }

    val temperatureUnit: StateFlow<TemperatureUnit> = weatherPreferencesRepository.unit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemperatureUnit.Celsius)

    fun onTemperatureUnitSelected(unit: TemperatureUnit) {
        viewModelScope.launch {
            weatherPreferencesRepository.setUnit(unit)
        }
    }

    val weatherService: StateFlow<WeatherService> = weatherPreferencesRepository.service
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeatherService.OpenWeatherMap)

    fun onWeatherServiceSelected(service: WeatherService) {
        viewModelScope.launch {
            weatherPreferencesRepository.setService(service)
        }
    }

    // ── AI Settings ───────────────────────────────────────────────────────────

    val aiEnabled: StateFlow<Boolean> = aiPreferencesRepository.aiEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onAiToggled(enabled: Boolean) {
        viewModelScope.launch {
            aiPreferencesRepository.setAiEnabled(enabled)
        }
    }

    val styleVibe: StateFlow<StyleVibe> = aiPreferencesRepository.styleVibe
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StyleVibe.SmartCasual)

    fun onStyleVibeSelected(vibe: StyleVibe) {
        viewModelScope.launch {
            aiPreferencesRepository.setStyleVibe(vibe)
        }
    }

    val selectedAiProvider: StateFlow<AiProvider> = aiPreferencesRepository.selectedProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiProvider.Gemini)

    fun onAiProviderSelected(provider: AiProvider) {
        viewModelScope.launch {
            aiPreferencesRepository.setSelectedProvider(provider)
        }
    }

    val nanoStatus: StateFlow<com.closet.core.data.ai.NanoStatus> = nanoInitializer.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.closet.core.data.ai.NanoStatus.Idle)

    // ── Provider Keys & Models ────────────────────────────────────────────────

    val openAiKey: StateFlow<String> = aiPreferencesRepository.openAiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun onOpenAiKeyChanged(key: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setOpenAiKey(key)
        }
    }

    val openAiBaseUrl: StateFlow<String> = aiPreferencesRepository.openAiBaseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun onOpenAiBaseUrlChanged(url: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setOpenAiBaseUrl(url)
        }
    }

    val openAiModel: StateFlow<String> = aiPreferencesRepository.openAiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun onOpenAiModelChanged(model: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setOpenAiModel(model)
        }
    }

    val anthropicKey: StateFlow<String> = aiPreferencesRepository.anthropicKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun onAnthropicKeyChanged(key: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setAnthropicKey(key)
        }
    }

    val anthropicModel: StateFlow<String> = aiPreferencesRepository.anthropicModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun onAnthropicModelChanged(model: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setAnthropicModel(model)
        }
    }

    val geminiKey: StateFlow<String> = aiPreferencesRepository.geminiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun onGeminiKeyChanged(key: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setGeminiKey(key)
        }
    }

    val geminiModel: StateFlow<String> = aiPreferencesRepository.geminiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun onGeminiModelChanged(model: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setGeminiModel(model)
        }
    }

    // ── Model Discovery ──────────────────────────────────────────────────────

    private val _openAiModels = MutableStateFlow<List<String>>(emptyList())
    val openAiModels: StateFlow<List<String>> = _openAiModels.asStateFlow()

    private val _openAiModelsLoading = MutableStateFlow(false)
    val openAiModelsLoading: StateFlow<Boolean> = _openAiModelsLoading.asStateFlow()

    private val _anthropicModels = MutableStateFlow<List<String>>(emptyList())
    val anthropicModels: StateFlow<List<String>> = _anthropicModels.asStateFlow()

    private val _anthropicModelsLoading = MutableStateFlow(false)
    val anthropicModelsLoading: StateFlow<Boolean> = _anthropicModelsLoading.asStateFlow()

    // ── Search Index ──────────────────────────────────────────────────────────

    /** Live status of the background embedding work. */
    val embeddingWorkInfo: StateFlow<WorkInfo?> = embeddingScheduler.workInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _embeddingIndexSize = MutableStateFlow(embeddingIndex.size)
    val embeddingIndexSize: StateFlow<Int> = _embeddingIndexSize.asStateFlow()

    fun onRebuildEmbeddingIndex() {
        embeddingScheduler.runNow()
    }

    // ── Batch Captioning ──────────────────────────────────────────────────────

    val captionSupported: Boolean = captionEnrichmentProvider.isSupported

    val captionEligibleCount: StateFlow<Int> = clothingDao.getCaptionEligibleCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val batchCaptionProgress: StateFlow<com.closet.core.data.ai.BatchCaptionProgress?> = captionEnrichmentProvider.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val captionResult: StateFlow<com.closet.core.data.ai.BatchCaptionResult?> = captionEnrichmentProvider.result
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onStartBatchCaption() {
        viewModelScope.launch {
            captionEnrichmentProvider.startBatchEnrichment()
        }
    }

    fun onCaptionResultConsumed() {
        captionEnrichmentProvider.consumeResult()
    }

    // ── Initialization ────────────────────────────────────────────────────────

    init {
        // Fetch OpenAI models when key or base URL changes.
        viewModelScope.launch {
            combine(openAiKey, openAiBaseUrl, aiEnabled) { key, url, enabled -> 
                Triple(key, url, enabled) 
            }
                .debounce(800)
                .collectLatest { (key, url, enabled) ->
                    if (!enabled || key.isBlank()) {
                        _openAiModels.value = emptyList()
                        return@collectLatest
                    }
                    _openAiModelsLoading.value = true
                    try {
                        modelDiscovery.fetchOpenAiModels(key, url.ifBlank { null })
                            .onSuccess { _openAiModels.value = it }
                            .onFailure { _openAiModels.value = emptyList() }
                    } finally {
                        _openAiModelsLoading.value = false
                    }
                }
        }

        // Fetch Anthropic models when key changes.
        viewModelScope.launch {
            combine(anthropicKey, aiEnabled) { key, enabled -> key to enabled }
                .debounce(800)
                .collectLatest { (key, shouldFetch) ->
                    if (!shouldFetch || key.isBlank()) {
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
        // Refresh in-memory index size when a user-triggered rebuild completes.
        viewModelScope.launch {
            embeddingWorkInfo.collect { info ->
                if (info?.state == WorkInfo.State.SUCCEEDED || info?.state == WorkInfo.State.FAILED) {
                    try {
                        embeddingIndex.load()
                        _embeddingIndexSize.value = embeddingIndex.size
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Timber.e(e, "Failed to reload embedding index")
                    }
                }
            }
        }
    }

    // ── Batch segmentation ────────────────────────────────────────────────────

    /** `false` on FOSS builds — hides the batch segmentation row entirely. */
    val segmentationSupported: Boolean = batchSegmentationScheduler.isSupported

    /**
     * Count of wardrobe items still eligible for background removal (non-PNG images).
     * Updates reactively whenever the clothing_items table changes.
     */
    val segmentationEligibleCount: StateFlow<Int> = clothingDao.getSegmentationEligibleCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Live [WorkInfo] for the unique batch segmentation job. `null` when no run has
     * been enqueued yet in this install. Use [WorkInfo.state] to drive UI and
     * [WorkInfo.progress] to show a progress bar.
     */
    val batchSegWorkInfo: StateFlow<WorkInfo?> = batchSegmentationScheduler.workInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The ID of the last segmentation job for which we showed a result snackbar.
     * Persisted in DataStore via [PreferencesRepository].
     */
    val lastHandledBatchId: StateFlow<java.util.UUID?> = preferencesRepository.lastHandledBatchId
        .map { it?.let { java.util.UUID.fromString(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onStartBatchSegmentation() {
        batchSegmentationScheduler.schedule()
    }

    /**
     * Updates the 'last handled' ID so we don't show the same success snackbar twice.
     */
    fun onBatchResultHandled(id: java.util.UUID) {
        viewModelScope.launch {
            preferencesRepository.setLastHandledBatchId(id.toString())
        }
    }
}
