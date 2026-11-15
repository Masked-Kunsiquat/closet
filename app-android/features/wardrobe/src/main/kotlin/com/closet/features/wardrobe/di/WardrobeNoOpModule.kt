package com.closet.features.wardrobe.di

import com.closet.core.data.repository.CaptionEnrichmentProvider
import com.closet.features.wardrobe.repository.SubjectSegmenter
import com.closet.features.wardrobe.repository.UnsupportedImageCaptioner
import com.closet.features.wardrobe.repository.UnsupportedSubjectSegmenter
import org.koin.dsl.module

/** No-op capability bindings for builds without Play Services. */
val wardrobeNoOpModule = module {
    single<CaptionEnrichmentProvider> { UnsupportedImageCaptioner() }
    single<SubjectSegmenter> { UnsupportedSubjectSegmenter() }
}
