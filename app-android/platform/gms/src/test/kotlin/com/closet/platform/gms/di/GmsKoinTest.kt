package com.closet.platform.gms.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.ai.OutfitAiProvider
import com.closet.core.data.di.dataModule
import com.closet.core.data.repository.EncryptedKeyStore
import com.closet.core.ui.di.uiModule
import com.closet.features.chat.ai.OnDeviceChatProvider
import com.closet.features.chat.di.chatModule
import com.closet.features.recommendations.ai.OnDeviceRecommendationProvider
import com.closet.features.recommendations.di.recommendationsModule
import com.closet.features.wardrobe.di.wardrobeModule
import com.closet.platform.gms.chat.NanoChatProvider
import com.closet.platform.gms.recommendations.NanoProvider
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import org.koin.test.verify.verify

/**
 * Verifies [gmsModule] against the feature modules it binds for.
 *
 * Only classes whose ML Kit client is `lazy` ([NanoProvider], [NanoChatProvider]) are resolved
 * here. The other GMS classes create their ML Kit clients in the constructor and need a real
 * Android runtime; [graphVerifies] covers their constructor dependencies without instantiating.
 */
class GmsKoinTest {
    @After fun tearDown() = stopKoin()

    // Copied from RecommendationsKoinTest.startWithMockedLeaves(); a shared fixture is deferred.
    private fun startWithMockedLeaves() {
        val overrides = module {
            single<Context> {
                mockk<Context>(relaxed = true) {
                    every { filesDir } returns java.nio.file.Files.createTempDirectory("gms").toFile()
                }
            }
            single<ClothingDatabase> { mockk(relaxed = true) }
            single<EncryptedKeyStore> { mockk(relaxed = true) }
            single<WorkManager> { mockk(relaxed = true) }
        }
        startKoin {
            allowOverride(true)
            modules(dataModule, uiModule, chatModule, recommendationsModule, wardrobeModule, gmsModule, overrides)
        }
    }

    @Test fun onDeviceRecommendationProviderIsOneInstance() {
        startWithMockedLeaves()
        val koin = KoinPlatform.getKoin()
        val provider = koin.get<OnDeviceRecommendationProvider>()
        assertSame(provider, koin.get<OutfitAiProvider>())
        assertSame(provider, koin.get<NanoInitializer>())
        assertTrue(provider is NanoProvider)
    }

    @Test fun onDeviceChatProviderIsNano() {
        startWithMockedLeaves()
        val koin = KoinPlatform.getKoin()
        assertTrue(koin.get<OnDeviceChatProvider>() is NanoChatProvider)
    }

    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test fun graphVerifies() {
        module { includes(dataModule, uiModule, chatModule, recommendationsModule, wardrobeModule, gmsModule) }.verify(
            extraTypes = listOf(
                Context::class,
                SavedStateHandle::class,
                WorkerParameters::class,
                HttpClientEngine::class,
                HttpClientConfig::class,
            )
        )
    }
}
