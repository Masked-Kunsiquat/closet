package com.closet.core.data.di

import com.closet.core.data.BuildFlags
import com.closet.core.data.location.LocationProvider
import com.closet.core.data.location.NoLocationProvider
import com.closet.core.data.repository.InMemorySecretStore
import com.closet.core.data.repository.ModelDiscoveryRepository
import com.closet.core.data.repository.SecretStore
import com.closet.core.data.repository.WeatherRepository
import com.closet.core.data.util.EmbeddingEncoder
import com.closet.core.data.util.EmbeddingIndex
import com.closet.core.data.util.UnavailableEmbeddingEncoder
import com.closet.core.data.worker.BatchSegmentationScheduler
import com.closet.core.data.worker.EmbeddingScheduler
import com.closet.core.data.worker.ImageCompressionScheduler
import com.closet.core.data.worker.NoOpBatchSegmentationScheduler
import com.closet.core.data.worker.NoOpEmbeddingScheduler
import com.closet.core.data.worker.NoOpImageCompressionScheduler
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class JvmDataKoinModuleTest {
    private val app = koinApplication {
        modules(
            commonDataModule,
            jvmDataModule(Files.createTempDirectory("jvm-data").toFile()),
            module { single { BuildFlags(isDebug = false) } },
        )
    }
    private val koin = app.koin

    @AfterTest fun tearDown() = app.close()

    @Test fun resolvesTheNetworkGraph() {
        assertSame(koin.get<WeatherRepository>(), koin.get<WeatherRepository>())
        koin.get<ModelDiscoveryRepository>()
        assertNotSame(koin.get<HttpClient>(), koin.get<AiHttpClient>().client)
    }

    @Test fun bindsTheStubs() = runBlocking {
        assertIs<InMemorySecretStore>(koin.get<SecretStore>())
        val location = koin.get<LocationProvider>()
        assertSame(NoLocationProvider, location)
        assertNull(location.getLocation())

        assertSame(UnavailableEmbeddingEncoder, koin.get<EmbeddingEncoder>())
        assertSame(koin.get<EmbeddingIndex>(), koin.get<EmbeddingIndex>())

        assertSame(NoOpEmbeddingScheduler, koin.get<EmbeddingScheduler>())
        assertSame(NoOpImageCompressionScheduler, koin.get<ImageCompressionScheduler>())
        val segmentation = koin.get<BatchSegmentationScheduler>()
        assertSame(NoOpBatchSegmentationScheduler, segmentation)
        assertFalse(segmentation.isSupported)
        assertNull(koin.get<EmbeddingScheduler>().workStatus.first())
        assertNull(koin.get<ImageCompressionScheduler>().workStatus.first())
        assertNull(segmentation.workStatus.first())
    }
}
