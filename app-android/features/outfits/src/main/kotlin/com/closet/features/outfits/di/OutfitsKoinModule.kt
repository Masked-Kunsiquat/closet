package com.closet.features.outfits.di

import com.closet.features.outfits.JournalViewModel
import com.closet.features.outfits.OutfitBuilderViewModel
import com.closet.features.outfits.OutfitsViewModel
import com.closet.features.outfits.WardrobePickerViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val outfitsModule = module {
    viewModelOf(::JournalViewModel)
    viewModelOf(::OutfitBuilderViewModel) // SavedStateHandle supplied by Koin
    viewModelOf(::OutfitsViewModel)
    viewModelOf(::WardrobePickerViewModel)
}
