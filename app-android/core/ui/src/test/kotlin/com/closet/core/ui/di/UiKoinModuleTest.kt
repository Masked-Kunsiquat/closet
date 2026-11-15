package com.closet.core.ui.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import com.closet.core.data.di.dataModule
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.verify.verify

class UiKoinModuleTest {
    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test fun uiModuleVerifies() {
        module { includes(dataModule, uiModule) }.verify(
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
