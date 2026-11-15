package com.closet.core.data.di

import com.closet.core.data.BuildFlags
import com.closet.core.data.location.LocationProvider
import com.closet.core.data.location.NoLocationProvider
import com.closet.core.data.repository.InMemorySecretStore
import com.closet.core.data.repository.ModelDiscoveryRepository
import com.closet.core.data.repository.SecretStore
import com.closet.core.data.repository.WeatherRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
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
    }
}
