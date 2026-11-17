package com.closet.features.chat.di

import androidx.lifecycle.SavedStateHandle
import com.closet.core.data.di.commonDataModule
import com.closet.core.data.di.jvmDataModule
import com.closet.core.ui.di.jvmUiModule
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.module
import org.koin.test.verify.verify
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test

class ChatJvmKoinGraphTest {
    private val tmp = Files.createTempDirectory("chat-jvm").toFile()

    @AfterTest fun tearDown() {
        tmp.deleteRecursively()
    }

    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test fun verifies() {
        module {
            includes(
                jvmDataModule(tmp),
                commonDataModule,
                jvmUiModule(tmp),
                chatModule,
                chatNoOpModule,
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
