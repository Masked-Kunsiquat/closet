package com.closet.platform.gms.di

import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.ai.OutfitAiProvider
import com.closet.core.data.repository.CaptionEnrichmentProvider
import com.closet.features.chat.ChatDateParser
import com.closet.features.chat.ChatRouter
import com.closet.features.chat.LanguageDetector
import com.closet.features.chat.PatternChatRouter
import com.closet.features.chat.ai.OnDeviceChatProvider
import com.closet.features.recommendations.ai.OnDeviceRecommendationProvider
import com.closet.features.wardrobe.repository.SubjectSegmenter
import com.closet.platform.gms.chat.MlKitChatDateParser
import com.closet.platform.gms.chat.MlKitLanguageDetector
import com.closet.platform.gms.chat.NanoChatProvider
import com.closet.platform.gms.recommendations.NanoProvider
import com.closet.platform.gms.wardrobe.MlKitImageCaptioner
import com.closet.platform.gms.wardrobe.MlKitSubjectSegmenter
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * GMS / ML Kit implementations of the feature capability interfaces. Included only by the
 * full flavor's `flavorModule` in `app`; foss includes the features' `*NoOpModule`s instead.
 */
val gmsModule = module {
    // chat
    single<OnDeviceChatProvider> { NanoChatProvider(get()) }
    single<ChatDateParser> { MlKitChatDateParser() }
    single<LanguageDetector> { MlKitLanguageDetector() }
    single<ChatRouter> { PatternChatRouter(get(), get(), get(), get()) }

    // recommendations: one NanoProvider instance, bound to both core interfaces
    single<OnDeviceRecommendationProvider> { NanoProvider(get()) } binds arrayOf(OutfitAiProvider::class, NanoInitializer::class)

    // wardrobe
    single<CaptionEnrichmentProvider> { MlKitImageCaptioner(androidContext(), get()) }
    single<SubjectSegmenter> { MlKitSubjectSegmenter() }
}
