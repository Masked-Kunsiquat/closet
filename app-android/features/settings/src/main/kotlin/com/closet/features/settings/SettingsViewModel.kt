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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepo: PreferencesRepository,
) : ViewModel() {

    val accent: StateFlow<ClosetAccent> = prefsRepo.getAccent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClosetAccent.Amber)

    fun setAccent(accent: ClosetAccent) {
        viewModelScope.launch { prefsRepo.setAccent(accent) }
    }
}
