package com.closet.features.recommendations.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.closet.core.data.BuildFlags
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.ai.OutfitAiProvider
import com.closet.core.data.di.AiHttpClient
import com.closet.core.data.di.dataModule
import com.closet.features.recommendations.ai.AnthropicProvider
import com.closet.features.recommendations.ai.OpenAiProvider
import com.closet.features.recommendations.ai.OnDeviceRecommendationProvider
import io.ktor.client.HttpClient
import org.junit.Assert.assertNotSame
import com.closet.core.data.repository.EncryptedKeyStore
import com.closet.core.ui.di.uiModule
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import org.koin.test.verify.verify

class RecommendationsKoinTest {
    @After fun tearDown() = stopKoin()

    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test fun graphVerifies() {
        module { includes(dataModule, uiModule, recommendationsModule, recommendationsNoOpModule) }.verify(
            extraTypes = listOf(
                Context::class,
                SavedStateHandle::class,
                WorkerParameters::class,
                HttpClientEngine::class,
                HttpClientConfig::class,
            )
        )
    }

    @Test fun nanoProviderIsOneInstance() {
        startWithMockedLeaves()
        val koin = KoinPlatform.getKoin()
        val provider = koin.get<OnDeviceRecommendationProvider>()
        assertSame(provider, koin.get<OutfitAiProvider>())
        assertSame(provider, koin.get<NanoInitializer>())
    }

    private fun startWithMockedLeaves() {
        val overrides = module {
            single<Context> {
                mockk<Context>(relaxed = true) {
                    every { filesDir } returns java.nio.file.Files.createTempDirectory("recs").toFile()
                }
            }
            single<ClothingDatabase> { mockk(relaxed = true) }
            single<EncryptedKeyStore> { mockk(relaxed = true) }
            single<WorkManager> { mockk(relaxed = true) }
            single { BuildFlags(isDebug = false) }
        }
        startKoin {
            allowOverride(true)
            modules(dataModule, uiModule, recommendationsModule, recommendationsNoOpModule, overrides)
        }
    }

    private fun clientOf(provider: Any): Any? =
        provider.javaClass.getDeclaredField("client").apply { isAccessible = true }.get(provider)

    @Test fun anthropicProviderUsesAiHttpClient() {
        startWithMockedLeaves()
        val koin = KoinPlatform.getKoin()
        val client = clientOf(koin.get<AnthropicProvider>())
        assertSame(koin.get<AiHttpClient>().client, client)
        assertNotSame(koin.get<HttpClient>(), client)
    }

    @Test fun openAiProviderUsesAiHttpClient() {
        startWithMockedLeaves()
        val koin = KoinPlatform.getKoin()
        val client = clientOf(koin.get<OpenAiProvider>())
        assertSame(koin.get<AiHttpClient>().client, client)
        assertNotSame(koin.get<HttpClient>(), client)
    }
}
