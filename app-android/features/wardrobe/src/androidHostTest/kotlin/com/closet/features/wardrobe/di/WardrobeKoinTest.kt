package com.closet.features.wardrobe.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.closet.core.data.BuildFlags
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.di.dataModule
import com.closet.core.data.repository.CaptionEnrichmentProvider
import com.closet.core.data.repository.SecretStore
import com.closet.core.ui.di.uiModule
import com.closet.features.wardrobe.repository.SubjectSegmenter
import com.closet.features.wardrobe.repository.UnsupportedImageCaptioner
import com.closet.features.wardrobe.repository.UnsupportedSubjectSegmenter
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import org.koin.test.verify.verify

class WardrobeKoinTest {
    @After fun tearDown() = stopKoin()

    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test fun graphVerifies() {
        module { includes(dataModule, uiModule, wardrobeModule, wardrobeNoOpModule) }.verify(
            extraTypes = listOf(
                Context::class,
                SavedStateHandle::class,
                WorkerParameters::class,
                HttpClientEngine::class,
                HttpClientConfig::class,
            )
        )
    }

    @Test fun captionerAndSegmenterAreUnsupportedStubs() {
        val overrides = module {
            single<Context> {
                mockk<Context>(relaxed = true) {
                    every { filesDir } returns java.nio.file.Files.createTempDirectory("wardrobe").toFile()
                }
            }
            single<ClothingDatabase> { mockk(relaxed = true) }
            single<SecretStore> { mockk(relaxed = true) }
            single<WorkManager> { mockk(relaxed = true) }
            single { BuildFlags(isDebug = false) }
        }
        startKoin {
            allowOverride(true)
            modules(dataModule, uiModule, wardrobeModule, wardrobeNoOpModule, overrides)
        }
        val koin = KoinPlatform.getKoin()
        assertTrue(koin.get<CaptionEnrichmentProvider>() is UnsupportedImageCaptioner)
        assertTrue(koin.get<SubjectSegmenter>() is UnsupportedSubjectSegmenter)
    }
}
