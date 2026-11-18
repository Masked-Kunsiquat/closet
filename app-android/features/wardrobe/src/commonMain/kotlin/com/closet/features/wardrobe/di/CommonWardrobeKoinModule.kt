package com.closet.features.wardrobe.di

import com.closet.features.wardrobe.BrandManagementViewModel
import com.closet.features.wardrobe.BulkWashViewModel
import com.closet.features.wardrobe.ClosetViewModel
import com.closet.features.wardrobe.ClothingDetailViewModel
import com.closet.features.wardrobe.ClothingFormViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The wardrobe ViewModels. Each platform binds `ItemPhotoProcessor` and
 * `CategoryShortcutPinner` (Android: `wardrobeModule`); the app binds `BuildFlags`.
 */
val commonWardrobeModule = module {
    viewModelOf(::BrandManagementViewModel)
    viewModelOf(::BulkWashViewModel)
    viewModelOf(::ClosetViewModel)
    viewModelOf(::ClothingDetailViewModel)
    viewModelOf(::ClothingFormViewModel)
}
