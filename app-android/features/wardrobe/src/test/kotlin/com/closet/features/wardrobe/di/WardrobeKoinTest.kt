package com.closet.features.wardrobe.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.di.dataModule
import com.closet.core.data.repository.CaptionEnrichmentProvider
import com.closet.core.data.repository.EncryptedKeyStore
import com.closet.core.ui.di.uiModule
import com.closet.features.wardrobe.repository.ImageCaptionRepository
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
        module { includes(dataModule, uiModule, wardrobeModule, wardrobeFlavorModule) }.verify(
            extraTypes = listOf(
                Context::class,
                SavedStateHandle::class,
                WorkerParameters::class,
                HttpClientEngine::class,
                HttpClientConfig::class,
            )
        )
    }

    @Test fun captionProviderIsImageCaptionRepository() {
        val overrides = module {
            single<Context> {
                mockk<Context>(relaxed = true) {
                    every { filesDir } returns java.nio.file.Files.createTempDirectory("wardrobe").toFile()
                }
            }
            single<ClothingDatabase> { mockk(relaxed = true) }
            single<EncryptedKeyStore> { mockk(relaxed = true) }
            single<WorkManager> { mockk(relaxed = true) }
        }
        startKoin {
            modules(dataModule, uiModule, wardrobeModule, wardrobeFlavorModule, overrides)
            allowOverride(true)
        }
        assertTrue(KoinPlatform.getKoin().get<CaptionEnrichmentProvider>() is ImageCaptionRepository)
    }
}
