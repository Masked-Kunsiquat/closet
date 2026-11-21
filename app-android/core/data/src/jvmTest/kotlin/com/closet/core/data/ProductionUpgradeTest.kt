package com.closet.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A real v2 -> v4 upgrade through the production builder ([getDatabaseBuilder], so
 * `configureClosetDatabase` supplies the migrations and the `onOpen` callback), on the JVM.
 * [MigrationTest] checks the migrations alone with [MigrationTestHelper]; this checks that the app's
 * own builder upgrades an existing v2 file, backfills `colors_curated` and recreates the partial index.
 */
class ProductionUpgradeTest {

    private val dir: File = createTempDirectory("closet-upgrade-test").toFile()
    private val file = File(dir, "closet.db")
    private var db: ClothingDatabase? = null

    private val helper = MigrationTestHelper(
        schemaDirectoryPath = Path(System.getProperty("closet.schemaDir") ?: "schemas"),
        databasePath = file.toPath(),
        driver = BundledSQLiteDriver(),
        databaseClass = ClothingDatabase::class,
        databaseFactory = { ClothingDatabaseConstructor.initialize() },
    )

    @AfterTest
    fun tearDown() {
        db?.close()
        dir.deleteRecursively()
    }

    @Test
    fun `the production builder upgrades a v2 file to v4`() {
        val v2 = helper.createDatabase(2)
        v2.execSQL("DROP INDEX IF EXISTS one_ootd_per_day") // per migrations/AGENTS.md
        v2.execSQL("INSERT INTO colors (id, name, hex) VALUES (2, 'White', '#FFFFFF')")
        v2.execSQL("INSERT INTO colors (id, name, hex) VALUES (5, 'Navy', '#1F2A44')")
        for (id in 1..2) {
            v2.execSQL(
                "INSERT INTO clothing_items (id, name, is_favorite, created_at, updated_at) " +
                    "VALUES ($id, 'Item $id', 0, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')"
            )
        }
        // Item 1 has a user row; item 2 only auto rows.
        v2.execSQL("INSERT INTO clothing_item_colors (clothing_item_id, color_id, source) VALUES (1, 5, 'user')")
        v2.execSQL("INSERT INTO clothing_item_colors (clothing_item_id, color_id, source) VALUES (2, 5, 'auto')")
        v2.execSQL("INSERT INTO clothing_item_colors (clothing_item_id, color_id, source) VALUES (2, 2, 'auto')")
        v2.close()

        val upgraded = getDatabaseBuilder(file).build().also { db = it }

        assertEquals(4, runBlocking { upgraded.databaseVersion() })
        val curated = upgraded.rows("SELECT id, colors_curated FROM clothing_items ORDER BY id") {
            it.getLong(0) to it.getLong(1)
        }
        assertEquals(listOf(1L to 1L, 2L to 0L), curated)
        val ootdIndex = upgraded.rows(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'one_ootd_per_day'"
        ) { it.getText(0) }
        assertTrue(ootdIndex.isNotEmpty(), "onOpen recreates one_ootd_per_day")
    }

    private fun <T> ClothingDatabase.rows(sql: String, map: (SQLiteStatement) -> T): List<T> = runBlocking {
        useReaderConnection { connection ->
            connection.usePrepared(sql) { s -> buildList { while (s.step()) add(map(s)) } }
        }
    }
}
