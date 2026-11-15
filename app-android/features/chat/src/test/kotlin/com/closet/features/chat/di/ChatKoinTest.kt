package com.closet.features.chat.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.di.AiHttpClient
import com.closet.core.data.di.dataModule
import com.closet.core.data.repository.EncryptedKeyStore
import com.closet.core.ui.di.uiModule
import com.closet.features.chat.ai.AnthropicChatProvider
import com.closet.features.chat.ai.GeminiChatProvider
import com.closet.features.chat.ChatRouter
import com.closet.features.chat.UnroutedChatRouter
import com.closet.features.chat.ai.OnDeviceChatProvider
import com.closet.features.chat.ai.OpenAiChatProvider
import com.closet.features.chat.ai.UnavailableOnDeviceChatProvider
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import org.koin.test.verify.verify

class ChatKoinTest {
    @After fun tearDown() = stopKoin()

    private fun startWithMockedLeaves() {
        val overrides = module {
            single<Context> {
                mockk<Context>(relaxed = true) {
                    every { filesDir } returns java.nio.file.Files.createTempDirectory("chat").toFile()
                }
            }
            single<ClothingDatabase> { mockk(relaxed = true) }
            single<EncryptedKeyStore> { mockk(relaxed = true) }
            single<WorkManager> { mockk(relaxed = true) }
        }
        startKoin {
            allowOverride(true)
            modules(dataModule, uiModule, chatModule, chatNoOpModule, overrides)
        }
    }

    private fun clientOf(provider: Any): Any? =
        provider.javaClass.getDeclaredField("client").apply { isAccessible = true }.get(provider)

    private fun assertAiClient(provider: Any) {
        val koin = KoinPlatform.getKoin()
        val client = clientOf(provider)
        assertSame(koin.get<AiHttpClient>().client, client)
        assertNotSame(koin.get<HttpClient>(), client)
    }

    @Test fun anthropicChatProviderUsesAiHttpClient() {
        startWithMockedLeaves()
        assertAiClient(KoinPlatform.getKoin().get<AnthropicChatProvider>())
    }

    @Test fun geminiChatProviderUsesAiHttpClient() {
        startWithMockedLeaves()
        assertAiClient(KoinPlatform.getKoin().get<GeminiChatProvider>())
    }

    @Test fun openAiChatProviderUsesAiHttpClient() {
        startWithMockedLeaves()
        assertAiClient(KoinPlatform.getKoin().get<OpenAiChatProvider>())
    }

    @Test fun onDeviceChatProviderIsUnavailableStub() {
        startWithMockedLeaves()
        val koin = KoinPlatform.getKoin()
        assertTrue(koin.get<OnDeviceChatProvider>() is UnavailableOnDeviceChatProvider)
        assertTrue(koin.get<ChatRouter>() is UnroutedChatRouter)
    }

    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test fun graphVerifies() {
        module { includes(dataModule, uiModule, chatModule, chatNoOpModule) }.verify(
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
