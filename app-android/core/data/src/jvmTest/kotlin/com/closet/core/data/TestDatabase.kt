package com.closet.core.data

import java.io.File

/**
 * A [ClothingDatabase] file in a fresh temp directory, opened with the production builder
 * ([getDatabaseBuilder], so the bundled driver and the `configureClosetDatabase` callbacks run).
 * [close] closes the database and deletes the directory, WAL and SHM files included.
 */
class TestDatabase : AutoCloseable {
    private val dir: File = kotlin.io.path.createTempDirectory("closet-db-test").toFile()
    val file: File = File(dir, "closet.db")
    val db: ClothingDatabase = getDatabaseBuilder(file).build()

    override fun close() {
        db.close()
        dir.deleteRecursively()
    }
}

/**
 * The committed Room schema export for [version]. The `jvmTest` task sets `closet.schemaDir`
 * to `core/data/schemas`; the fallback is that path relative to the module directory, which is
 * Gradle's working directory for tests.
 */
fun committedSchemaFile(version: Int): File {
    val schemaDir = System.getProperty("closet.schemaDir") ?: "schemas"
    return File(schemaDir, "com.closet.core.data.ClothingDatabase/$version.json")
}
