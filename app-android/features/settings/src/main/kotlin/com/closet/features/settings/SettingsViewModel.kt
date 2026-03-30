package com.closet.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.ai.NanoInitResult
import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.model.AiProvider
import com.closet.core.data.model.StyleVibe
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherService
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.repository.ModelDiscoveryRepository
import com.closet.core.data.repository.WeatherPreferencesRepository
import com.closet.core.ui.preferences.PreferencesRepository
import com.closet.core.ui.theme.ClosetAccent
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Bridges [PreferencesRepository] (appearance), [WeatherPreferencesRepository] (weather),
 * and [AiPreferencesRepository] (AI suggestions) to the UI layer. All preferences are
 * exposed as [StateFlow]s; writes are fire-and-forget coroutines in [viewModelScope].
 *
 * Changes to appearance preferences propagate immediately to [ClosetTheme] via
 * [MainActivity][com.closet.MainActivity]'s collected flows, causing the entire
 * app theme to recompose.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepo: PreferencesRepository,
    private val weatherPrefsRepo: WeatherPreferencesRepository,
    private val aiPrefsRepo: AiPreferencesRepository,
    private val nanoInitializer: NanoInitializer,
    private val modelDiscovery: ModelDiscoveryRepository,
) : ViewModel() {

    // ── Appearance ────────────────────────────────────────────────────────────

    /** The current accent colour, persisted across app launches. */
    val accent: StateFlow<ClosetAccent> = prefsRepo.getAccent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClosetAccent.Amber)

    /**
     * Whether Material You dynamic color is enabled. Defaults to `false` so the
     * user-selected [accent] always applies unless explicitly opted in.
     */
    val dynamicColor: StateFlow<Boolean> = prefsRepo.getDynamicColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Persists [accent] to [PreferencesRepository]. */
    fun setAccent(accent: ClosetAccent) {
        viewModelScope.launch { prefsRepo.setAccent(accent) }
    }

    /** Persists the dynamic color [enabled] flag to [PreferencesRepository]. */
    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDynamicColor(enabled) }
    }

    // ── Weather ───────────────────────────────────────────────────────────────

    /**
     * Whether the weather feature is enabled. In Phase 2 this toggle will gate
     * the location permission request; for now it writes directly to DataStore.
     */
    val weatherEnabled: StateFlow<Boolean> = weatherPrefsRepo.getWeatherEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The selected weather service provider. */
    val weatherService: StateFlow<WeatherService> = weatherPrefsRepo.getWeatherService()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeatherService.OpenMeteo)

    /** The stored Google Weather API key (empty string when not set). */
    val googleApiKey: StateFlow<String> = weatherPrefsRepo.getGoogleApiKey()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** The preferred temperature display unit. Storage is always °C. */
    val temperatureUnit: StateFlow<TemperatureUnit> = weatherPrefsRepo.getTemperatureUnit()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemperatureUnit.Celsius)

    fun setWeatherEnabled(enabled: Boolean) {
        viewModelScope.launch { weatherPrefsRepo.setWeatherEnabled(enabled) }
    }

    fun setWeatherService(service: WeatherService) {
        viewModelScope.launch { weatherPrefsRepo.setWeatherService(service) }
    }

    fun setGoogleApiKey(key: String) {
        viewModelScope.launch { weatherPrefsRepo.setGoogleApiKey(key) }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { weatherPrefsRepo.setTemperatureUnit(unit) }
    }

    fun clearForecastCache() {
        viewModelScope.launch { weatherPrefsRepo.clearCache() }
    }

    // ── AI suggestions ────────────────────────────────────────────────────────

    /**
     * Whether the AI coherence scoring feature is enabled. Defaults to OFF.
     *
     * When toggled ON with [AiProvider.Nano] selected, immediately starts the Nano
     * init sequence via [nanoInitializer]. When toggled OFF, cancels any in-progress
     * init and clears the Nano ready state.
     */
    val aiEnabled: StateFlow<Boolean> = aiPrefsRepo.getAiEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The currently selected AI provider. */
    val selectedAiProvider: StateFlow<AiProvider> = aiPrefsRepo.getSelectedProvider()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiProvider.Nano)

    /** The user's selected style vibe. Defaults to [StyleVibe.SmartCasual]. */
    val styleVibe: StateFlow<StyleVibe> = aiPrefsRepo.getStyleVibe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StyleVibe.SmartCasual)

    fun onStyleVibeSelected(vibe: StyleVibe) {
        viewModelScope.launch { aiPrefsRepo.setStyleVibe(vibe) }
    }

    // ── Nano init status ──────────────────────────────────────────────────────

    private val _nanoStatus = MutableStateFlow<NanoStatus>(NanoStatus.Idle)
    val nanoStatus: StateFlow<NanoStatus> = _nanoStatus.asStateFlow()

    /** Tracks the in-progress Nano init coroutine so it can be cancelled on toggle-off. */
    private var nanoInitJob: Job? = null

    /**
     * Serializes concurrent AI state mutations.
     *
     * [onAiToggled] and [onAiProviderSelected] each read persisted state after writing it.
     * Without a lock a rapid toggle-then-provider-switch could interleave: the toggle coroutine
     * reads the old provider (Nano) after the provider write has already switched it to OpenAI,
     * causing a spurious [startNanoInit] for the wrong provider. The mutex ensures only one
     * mutation/check runs at a time without blocking the main thread.
     */
    private val aiMutex = Mutex()

    // ── OpenAI-compatible fields ───────────────────────────────────────────────

    /** API key for OpenAI-compatible providers. Stored encrypted via [EncryptedKeyStore]. */
    val openAiKey: StateFlow<String> = aiPrefsRepo.getOpenAiApiKey()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Base URL override for OpenAI-compatible providers (e.g. Ollama, Groq). Empty = default. */
    val openAiBaseUrl: StateFlow<String> = aiPrefsRepo.getOpenAiBaseUrl()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Model identifier override. Empty = gpt-4o-mini default. */
    val openAiModel: StateFlow<String> = aiPrefsRepo.getOpenAiModel()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    // ── Anthropic fields ──────────────────────────────────────────────────────

    /** API key for the Anthropic provider. */
    val anthropicKey: StateFlow<String> = aiPrefsRepo.getAnthropicApiKey()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Model identifier for the Anthropic provider. Empty = default (Haiku). */
    val anthropicModel: StateFlow<String> = aiPrefsRepo.getAnthropicModel()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    // ── Model discovery ───────────────────────────────────────────────────────

    private val _openAiModels = MutableStateFlow<List<String>>(emptyList())
    /** Available OpenAI-compatible model IDs fetched from the configured endpoint. */
    val openAiModels: StateFlow<List<String>> = _openAiModels.asStateFlow()

    private val _anthropicModels = MutableStateFlow<List<String>>(emptyList())
    /** Available Anthropic model IDs fetched from the Anthropic API. */
    val anthropicModels: StateFlow<List<String>> = _anthropicModels.asStateFlow()

    private val _openAiModelsLoading = MutableStateFlow(false)
    val openAiModelsLoading: StateFlow<Boolean> = _openAiModelsLoading.asStateFlow()

    private val _anthropicModelsLoading = MutableStateFlow(false)
    val anthropicModelsLoading: StateFlow<Boolean> = _anthropicModelsLoading.asStateFlow()

    // ── AI event handlers ─────────────────────────────────────────────────────

    /**
     * Called when the master AI toggle is switched.
     *
     * - ON + Nano selected → cancel any previous init, then start the Nano init sequence.
     * - ON + other provider → just persist the enabled state (key-based providers are
     *   ready as soon as a key is configured; no download step needed).
     * - OFF → cancel any in-progress Nano init, clear Nano state, reset [nanoStatus].
     */
    fun onAiToggled(enabled: Boolean) {
        if (!enabled) {
            nanoInitJob?.cancel()
            nanoInitJob = null
            _nanoStatus.value = NanoStatus.Idle
            viewModelScope.launch {
                aiMutex.withLock {
                    aiPrefsRepo.setAiEnabled(false)
                    aiPrefsRepo.clearNanoState()
                }
            }
            return
        }

        viewModelScope.launch {
            aiMutex.withLock {
                aiPrefsRepo.setAiEnabled(true)
                // Read the persisted provider inside the lock — onAiProviderSelected cannot
                // interleave between setAiEnabled and this read, so the provider state is stable.
                if (aiPrefsRepo.getSelectedProvider().first() == AiProvider.Nano) {
                    startNanoInit()
                }
                // Cloud providers are ready as soon as a key is present — no download step.
            }
        }
    }

    /**
     * Called when the user selects a different AI provider.
     *
     * Cancels any in-progress Nano init if switching away from Nano, and starts a
     * fresh init if switching to Nano while the master toggle is ON.
     */
    fun onAiProviderSelected(provider: AiProvider) {
        viewModelScope.launch {
            aiMutex.withLock {
                aiPrefsRepo.setSelectedProvider(provider)
                // Cancel Nano init if we're leaving Nano
                if (provider != AiProvider.Nano) {
                    nanoInitJob?.cancel()
                    nanoInitJob = null
                    _nanoStatus.value = NanoStatus.Idle
                }
                // Start Nano init if switching to Nano while the master toggle is already on.
                // Read within the lock — setAiEnabled from onAiToggled cannot interleave.
                if (provider == AiProvider.Nano && aiPrefsRepo.getAiEnabled().first()) {
                    startNanoInit()
                }
            }
        }
    }

    fun onOpenAiKeyChanged(key: String) {
        viewModelScope.launch { aiPrefsRepo.setOpenAiApiKey(key) }
    }

    fun onOpenAiBaseUrlChanged(url: String) {
        viewModelScope.launch { aiPrefsRepo.setOpenAiBaseUrl(url) }
    }

    fun onOpenAiModelChanged(model: String) {
        viewModelScope.launch { aiPrefsRepo.setOpenAiModel(model) }
    }

    fun onAnthropicKeyChanged(key: String) {
        viewModelScope.launch { aiPrefsRepo.setAnthropicApiKey(key) }
    }

    fun onAnthropicModelChanged(model: String) {
        viewModelScope.launch { aiPrefsRepo.setAnthropicModel(model) }
    }

    // ── Nano init sequence ────────────────────────────────────────────────────

    /**
     * Launches the Nano init sequence in [viewModelScope].
     *
     * - Collects [NanoInitializer.initNanoFlow], mapping each [NanoInitResult] to [NanoStatus].
     * - On [NanoInitResult.Success]: persists aiReady + tokenLimit, updates status to [NanoStatus.Ready].
     * - On [NanoInitResult.NotSupported] or [NanoInitResult.Failed]: flips the master toggle
     *   back OFF (clears aiReady), updates [nanoStatus] with the failure state.
     */
    private fun startNanoInit() {
        nanoInitJob?.cancel()
        _nanoStatus.value = NanoStatus.Checking
        nanoInitJob = viewModelScope.launch {
            nanoInitializer.initNanoFlow().collect { result ->
                when (result) {
                    is NanoInitResult.Downloading -> {
                        _nanoStatus.value = NanoStatus.Downloading(result.progressPct)
                    }
                    is NanoInitResult.Success -> {
                        aiPrefsRepo.setAiReady(true)
                        aiPrefsRepo.setTokenLimit(result.tokenLimit)
                        _nanoStatus.value = NanoStatus.Ready
                    }
                    is NanoInitResult.NotSupported -> {
                        aiPrefsRepo.setAiEnabled(false)
                        aiPrefsRepo.clearNanoState()
                        _nanoStatus.value = NanoStatus.NotSupported
                    }
                    is NanoInitResult.Failed -> {
                        aiPrefsRepo.setAiEnabled(false)
                        aiPrefsRepo.clearNanoState()
                        _nanoStatus.value = NanoStatus.Failed(result.reason)
                    }
                }
            }
        }
    }

    init {
        // Debounced model discovery: fetch models 800ms after any upstream input settles.
        // Gated by aiEnabled + selectedAiProvider so no network requests fire when AI is
        // off or when the other provider's panel is active. collectLatest cancels in-flight
        // fetches when a newer emission arrives.
        viewModelScope.launch {
            combine(openAiKey, openAiBaseUrl, aiEnabled, selectedAiProvider) { key, url, enabled, provider ->
                Triple(key, url, enabled && provider == AiProvider.OpenAi)
            }
                .debounce(800)
                .collectLatest { (key, url, shouldFetch) ->
                    if (!shouldFetch || key.isBlank()) {
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
        viewModelScope.launch {
            combine(anthropicKey, aiEnabled, selectedAiProvider) { key, enabled, provider ->
                Pair(key, enabled && provider == AiProvider.Anthropic)
            }
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
    }

    private companion object {
        /** Minimum API key length before triggering a model discovery fetch. */
        const val MIN_KEY_LENGTH = 20
    }
}
