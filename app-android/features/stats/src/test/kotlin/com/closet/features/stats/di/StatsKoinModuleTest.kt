package com.closet.features.stats.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import com.closet.core.data.di.dataModule
import com.closet.core.ui.di.uiModule
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.verify.verify

class StatsKoinModuleTest {
    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test fun statsModuleVerifies() {
        module { includes(dataModule, uiModule, statsModule) }.verify(
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
