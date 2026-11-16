package com.closet.features.settings.di

import androidx.lifecycle.SavedStateHandle
import com.closet.core.data.di.commonDataModule
import com.closet.core.data.di.jvmDataModule
import com.closet.core.ui.di.commonUiModule
import com.closet.core.ui.di.jvmUiModule
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.module
import org.koin.test.verify.verify
import java.nio.file.Files
import kotlin.test.Test

class SettingsJvmKoinGraphTest {
    private val tmp = Files.createTempDirectory("settings-jvm").toFile()

    // No NanoInitializer / BatchCaptioner whitelist: the JVM stubs in jvmDataModule must provide them.
    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test fun verifies() {
        module {
            includes(commonDataModule, jvmDataModule(tmp), commonUiModule, jvmUiModule(tmp), settingsModule)
        }.verify(
            extraTypes = listOf(
                SavedStateHandle::class,
                HttpClientEngine::class,
                HttpClientConfig::class,
            ),
        )
    }
}
