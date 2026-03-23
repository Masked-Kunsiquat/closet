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
 * Bridges [PreferencesRepository] to the UI layer, exposing the persisted
 * [ClosetAccent] as a [StateFlow] and providing a single write path via [setAccent].
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepo: PreferencesRepository,
) : ViewModel() {

    /** The current accent color, persisted across app launches. */
    val accent: StateFlow<ClosetAccent> = prefsRepo.getAccent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClosetAccent.Amber)

    /**
     * Persists [accent] to [PreferencesRepository]. The change propagates
     * immediately to [ClosetTheme] via [MainActivity][com.closet.MainActivity]'s
     * collected flow, causing the entire app theme to recompose.
     */
    fun setAccent(accent: ClosetAccent) {
        viewModelScope.launch { prefsRepo.setAccent(accent) }
    }
}
