package com.closet.features.settings.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.di.dataModule
import com.closet.core.data.repository.CaptionEnrichmentProvider
import com.closet.core.data.worker.BatchSegmentationScheduler
import com.closet.core.ui.di.uiModule
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.verify.verify

class SettingsKoinModuleTest {
    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test fun settingsModuleVerifies() {
        module { includes(dataModule, uiModule, settingsModule) }.verify(
            extraTypes = listOf(
                Context::class,
                SavedStateHandle::class,
                WorkerParameters::class,
                HttpClientEngine::class,
                HttpClientConfig::class,
                // Bound by the recommendations and wardrobe Koin modules. Settings can't depend on
                // those flavored sibling Gradle modules, so AppKoinGraphTest (both flavors, no whitelist)
                // is what proves these bindings exist.
                NanoInitializer::class,
                CaptionEnrichmentProvider::class,
                BatchSegmentationScheduler::class,
            )
        )
    }
}
