package com.closet.core.data.di

import android.content.Context
import androidx.work.WorkManager
import com.closet.core.data.BuildFlags
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.repository.ImageStorageStats
import com.closet.core.data.repository.SecretStore
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.shortcuts.AndroidShortcutUsageReporter
import com.closet.core.data.shortcuts.ShortcutUsageReporter
import com.closet.core.data.util.EmbeddingEncoder
import com.closet.core.data.util.EmbeddingIndex
import io.ktor.client.HttpClient
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

class SingletonParityTest {
    @Before fun setUp() {
        val overrides = module {
            single<Context> {
                mockk<Context>(relaxed = true) {
                    every { filesDir } returns java.nio.file.Files.createTempDirectory("parity").toFile()
                }
            }
            single<ClothingDatabase> { mockk(relaxed = true) }
            single<SecretStore> { mockk(relaxed = true) }
            single<WorkManager> { mockk(relaxed = true) }
            single { BuildFlags(isDebug = false) }
        }
        startKoin { allowOverride(true); modules(dataModule, overrides) }
    }

    @After fun tearDown() = stopKoin()

    private inline fun <reified T : Any> assertSingleton() {
        val koin = KoinPlatform.getKoin()
        assertSame(koin.get<T>(), koin.get<T>())
    }

    @Test fun singletonsAreShared() {
        assertSingleton<EmbeddingIndex>()
        assertSingleton<EmbeddingEncoder>()
        assertSingleton<AiPreferencesRepository>()
        assertSingleton<ClothingRepository>()
        assertSingleton<StorageRepository>()
    }

    @Test fun imagePathResolverIsTheStorageRepository() {
        val koin = KoinPlatform.getKoin()
        assertSame(koin.get<StorageRepository>(), koin.get<ImagePathResolver>())
    }

    @Test fun imageStorageStatsIsTheStorageRepository() {
        val koin = KoinPlatform.getKoin()
        assertSame(koin.get<StorageRepository>(), koin.get<ImageStorageStats>())
    }

    @Test fun shortcutUsageReporterIsAndroid() {
        org.junit.Assert.assertTrue(KoinPlatform.getKoin().get<ShortcutUsageReporter>() is AndroidShortcutUsageReporter)
    }

    @Test fun aiHttpClientIsDistinct() {
        val koin = KoinPlatform.getKoin()
        assertNotSame(koin.get<HttpClient>(), koin.get<AiHttpClient>().client)
    }
}
