package com.closet.core.data.repository

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmImageStorageStatsTest {
    @Test fun sumsFilesRecursively() = runTest {
        val tmp = Files.createTempDirectory("stats").toFile()
        try {
            File(tmp, "a.jpg").writeBytes(ByteArray(3))
            File(tmp, "sub").mkdirs()
            File(tmp, "sub/b.jpg").writeBytes(ByteArray(5))
            assertEquals(8L, JvmImageStorageStats(tmp).usedBytes())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test fun missingDirectoryIsZero() = runTest {
        val tmp = Files.createTempDirectory("stats").toFile()
        try {
            assertEquals(0L, JvmImageStorageStats(File(tmp, "nope")).usedBytes())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test fun emptyDirectoryIsZero() = runTest {
        val tmp = Files.createTempDirectory("stats").toFile()
        try {
            assertEquals(0L, JvmImageStorageStats(tmp).usedBytes())
        } finally {
            tmp.deleteRecursively()
        }
    }
}
