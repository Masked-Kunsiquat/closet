package com.closet.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * Bridges [PreferencesRepository] to the UI layer, exposing persisted preferences
 * as [StateFlow]s and providing write paths for each. Changes propagate immediately
 * to [ClosetTheme] via [MainActivity][com.closet.MainActivity]'s collected flows,
 * causing the entire app theme to recompose.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepo: PreferencesRepository,
) : ViewModel() {

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
}
