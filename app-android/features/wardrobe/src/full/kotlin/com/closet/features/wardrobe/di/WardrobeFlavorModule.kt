package com.closet.features.wardrobe.di

import com.closet.core.data.repository.CaptionEnrichmentProvider
import com.closet.features.wardrobe.repository.MlKitImageCaptioner
import com.closet.features.wardrobe.repository.MlKitSubjectSegmenter
import com.closet.features.wardrobe.repository.SubjectSegmenter
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** full flavor: ML Kit implementations. */
val wardrobeFlavorModule = module {
    single<CaptionEnrichmentProvider> { MlKitImageCaptioner(androidContext(), get()) }
    single<SubjectSegmenter> { MlKitSubjectSegmenter() }
}
