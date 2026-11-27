package com.closet.features.recommendations.scoreboard

import com.closet.core.data.ClothingDatabase
import com.closet.core.data.getDatabaseBuilder
import java.io.File

/**
 * A real [ClothingDatabase] in a fresh temp directory, opened with the production builder
 * ([getDatabaseBuilder]) so the seeded lookup tables are present. [close] closes the database and
 * deletes the directory, WAL and SHM files included.
 */
class ScoreboardDatabase : AutoCloseable {
    private val dir: File = kotlin.io.path.createTempDirectory("closet-scoreboard").toFile()
    val db: ClothingDatabase = getDatabaseBuilder(File(dir, "closet.db")).build()

    override fun close() {
        db.close()
        dir.deleteRecursively()
    }
}
