package com.closet.features.recommendations.di

import androidx.lifecycle.SavedStateHandle
import com.closet.core.data.di.commonDataModule
import com.closet.core.data.di.jvmDataModule
import com.closet.core.ui.di.jvmUiModule
import com.closet.features.recommendations.ai.OnDeviceRecommendationProvider
import com.closet.features.recommendations.ai.UnavailableOnDeviceRecommendationProvider
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.module
import org.koin.test.verify.verify
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test

class RecommendationsJvmKoinGraphTest {
    private val tmp = Files.createTempDirectory("recommendations-jvm").toFile()

    @AfterTest fun tearDown() {
        tmp.deleteRecursively()
    }

    // recommendationsNoOpModule is left out: its NanoInitializer binding would clash with the
    // UnavailableNanoInitializer that jvmDataModule already binds.
    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test fun verifies() {
        module {
            includes(
                jvmDataModule(tmp),
                commonDataModule,
                jvmUiModule(tmp),
                recommendationsModule,
                module { single<OnDeviceRecommendationProvider> { UnavailableOnDeviceRecommendationProvider() } },
            )
        }.verify(
            extraTypes = listOf(
                SavedStateHandle::class,
                HttpClientEngine::class,
                HttpClientConfig::class,
            ),
        )
    }
}
