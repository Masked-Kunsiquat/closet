package com.closet.core.ui.di

import com.closet.core.data.BuildFlags
import com.closet.core.data.di.commonDataModule
import com.closet.core.data.di.jvmDataModule
import com.closet.core.ui.preferences.PreferencesRepository
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull

class JvmUiKoinModuleTest {
    private val tmp = Files.createTempDirectory("jvm-ui").toFile()

    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test fun verifies() {
        module { includes(jvmDataModule(tmp), jvmUiModule(tmp)) }.verify(
            extraTypes = listOf(HttpClientEngine::class, HttpClientConfig::class),
        )
    }

    @Test fun resolvesPreferencesRepository() {
        val app = koinApplication {
            modules(
                commonDataModule,
                jvmDataModule(tmp),
                jvmUiModule(tmp),
                module { single { BuildFlags(isDebug = false) } },
            )
        }
        try {
            assertNotNull(app.koin.get<PreferencesRepository>())
        } finally {
            app.close()
        }
    }
}
