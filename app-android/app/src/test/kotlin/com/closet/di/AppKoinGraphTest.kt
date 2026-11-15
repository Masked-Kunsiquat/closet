package com.closet.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module
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
            )
        )
    }

    @Test fun noDuplicateDefinitions() {
        val app = koinApplication {
            allowOverride(false)
            modules(allModules)
        }
        app.close()
    }
}
