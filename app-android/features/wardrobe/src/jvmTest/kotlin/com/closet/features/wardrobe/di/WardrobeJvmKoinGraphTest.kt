package com.closet.features.wardrobe.di

import androidx.lifecycle.SavedStateHandle
import com.closet.core.data.BuildFlags
import com.closet.core.data.di.commonDataModule
import com.closet.core.data.di.jvmDataModule
import com.closet.core.ui.di.jvmUiModule
import com.closet.features.wardrobe.CategoryShortcutPinner
import com.closet.features.wardrobe.FakeItemPhotoProcessor
import com.closet.features.wardrobe.photo.ItemPhotoProcessor
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.module
import org.koin.test.verify.verify
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test

class WardrobeJvmKoinGraphTest {
    private val tmp = Files.createTempDirectory("wardrobe-jvm").toFile()

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
                commonWardrobeModule,
                // Android binds these in wardrobeModule (androidMain); the JVM has no implementation yet.
                module {
                    single<ItemPhotoProcessor> { FakeItemPhotoProcessor() }
                    single<CategoryShortcutPinner> { CategoryShortcutPinner { _, _ -> } }
                },
            )
        }.verify(
            extraTypes = listOf(
                SavedStateHandle::class,
                HttpClientEngine::class,
                HttpClientConfig::class,
                // Bound by the app module from BuildConfig.DEBUG; this module can't depend on app.
                BuildFlags::class,
            ),
        )
    }
}
