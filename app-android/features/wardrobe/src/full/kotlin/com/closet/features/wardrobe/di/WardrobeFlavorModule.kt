package com.closet.features.wardrobe.di

import com.closet.core.data.repository.CaptionEnrichmentProvider
import com.closet.features.wardrobe.repository.ImageCaptionRepository
import com.closet.features.wardrobe.repository.SegmentationRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** full flavor: ImageCaptionRepository(context, clothingDao) doubles as the CaptionEnrichmentProvider. */
val wardrobeFlavorModule = module {
    single { ImageCaptionRepository(androidContext(), get()) } bind CaptionEnrichmentProvider::class
    singleOf(::SegmentationRepository)
}
