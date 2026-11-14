package com.closet.features.wardrobe.di

import com.closet.core.data.repository.CaptionEnrichmentProvider
import com.closet.features.wardrobe.repository.ImageCaptionRepository
import com.closet.features.wardrobe.repository.SegmentationRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** foss flavor: no-op stubs. ImageCaptionRepository() doubles as the CaptionEnrichmentProvider. */
val wardrobeFlavorModule = module {
    singleOf(::ImageCaptionRepository) bind CaptionEnrichmentProvider::class
    singleOf(::SegmentationRepository)
}
