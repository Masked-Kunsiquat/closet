package com.closet.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.closet.core.data.BuildFlags
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.repository.BatchCaptioner
import com.closet.core.data.repository.CaptionEnrichmentProvider
import io.mockk.mockk
import org.junit.Assert.assertSame
import com.closet.core.data.repository.SecretStore
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

class AppKoinGraphTest {
    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test fun allModulesVerify() {
        module { includes(allModules) }.verify(
            extraTypes = listOf(
                Context::class,
                SavedStateHandle::class,
                WorkerParameters::class,
                HttpClientEngine::class,
                HttpClientConfig::class,
            ),
            // BuildFlags(isDebug) is built from BuildConfig.DEBUG, not resolved from the graph.
            // Scoped to that one definition so a stray Boolean dependency elsewhere still fails.
            injections = injectedParameters(definition<BuildFlags>(Boolean::class)),
        )
    }

    @Test fun noDuplicateDefinitions() {
        val app = koinApplication {
            allowOverride(false)
            modules(allModules)
        }
        app.close()
    }

    @Test fun batchCaptionerIsTheCaptionEnrichmentProvider() {
        val overrides = module {
            single<Context> { mockk(relaxed = true) }
            single<ClothingDatabase> { mockk(relaxed = true) }
            single<SecretStore> { mockk(relaxed = true) }
            single<WorkManager> { mockk(relaxed = true) }
            single { BuildFlags(isDebug = false) }
        }
        val app = koinApplication {
            allowOverride(true)
            modules(allModules + overrides)
        }
        try {
            assertSame(app.koin.get<CaptionEnrichmentProvider>(), app.koin.get<BatchCaptioner>())
        } finally {
            app.close()
        }
    }
}
