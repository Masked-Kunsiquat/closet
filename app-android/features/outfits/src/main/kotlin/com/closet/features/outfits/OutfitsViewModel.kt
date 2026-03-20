package com.closet.features.outfits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.OutfitWithItems
import com.closet.core.data.repository.OutfitRepository
import com.closet.core.data.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

@HiltViewModel
class OutfitsViewModel @Inject constructor(
    private val outfitRepository: OutfitRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    val outfits: StateFlow<List<OutfitWithItems>> = outfitRepository.getAllOutfitsWithItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun resolveImagePath(path: String?): File? = path?.let { storageRepository.getFile(it) }
}
