package com.closet.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherService
import com.closet.core.data.repository.WeatherPreferencesRepository
import com.closet.core.ui.preferences.PreferencesRepository
import com.closet.core.ui.theme.ClosetAccent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Bridges [PreferencesRepository] (appearance) and [WeatherPreferencesRepository]
 * (weather) to the UI layer. All preferences are exposed as [StateFlow]s; writes
 * are fire-and-forget coroutines in [viewModelScope].
 *
 * Changes to appearance preferences propagate immediately to [ClosetTheme] via
 * [MainActivity][com.closet.MainActivity]'s collected flows, causing the entire
 * app theme to recompose.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepo: PreferencesRepository,
    private val weatherPrefsRepo: WeatherPreferencesRepository,
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
}
