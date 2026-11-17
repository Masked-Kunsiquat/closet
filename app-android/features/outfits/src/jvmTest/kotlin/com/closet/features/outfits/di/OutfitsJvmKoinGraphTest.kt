package com.closet.features.outfits.di

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
import kotlin.test.AfterTest
import kotlin.test.Test

class OutfitsJvmKoinGraphTest {
    private val tmp = Files.createTempDirectory("outfits-jvm").toFile()

    @AfterTest fun tearDown() {
        tmp.deleteRecursively()
    }

    // No ShortcutUsageReporter whitelist: the JVM no-op in jvmDataModule must provide it.
    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test fun verifies() {
        module {
            includes(commonDataModule, jvmDataModule(tmp), commonUiModule, jvmUiModule(tmp), outfitsModule)
        }.verify(
            extraTypes = listOf(
                SavedStateHandle::class,
                HttpClientEngine::class,
                HttpClientConfig::class,
            ),
        )
    }
}
