package com.closet.core.data.repository

import com.closet.core.data.BuildFlags
import com.closet.core.data.di.commonDataModule
import com.closet.core.data.di.jvmDataModule
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmImagePathResolverTest {
    @Test fun resolvesAgainstTheImagesDirectory() {
        val tmp = Files.createTempDirectory("images").toFile()
        try {
            assertEquals(File(tmp, "a/b.jpg").absolutePath, JvmImagePathResolver(tmp).absolutePath("a/b.jpg"))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test fun jvmDataModuleBindsTheResolverUnderTheImagesDirectory() {
        val dataDir = Files.createTempDirectory("jvm-data").toFile()
        val app = koinApplication {
            modules(
                commonDataModule,
                jvmDataModule(dataDir),
                module { single { BuildFlags(isDebug = false) } },
            )
        }
        try {
            val path = app.koin.get<ImagePathResolver>().absolutePath("x.jpg")
            assertTrue(path.endsWith("$IMAGES_DIR_NAME${File.separator}x.jpg"), path)
        } finally {
            app.close()
            dataDir.deleteRecursively()
        }
    }
}
