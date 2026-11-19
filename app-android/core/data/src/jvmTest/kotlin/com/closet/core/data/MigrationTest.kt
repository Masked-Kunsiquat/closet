package com.closet.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.closet.core.data.migrations.MIGRATION_1_2
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Migration tests for the real migrations after the version-1 baseline, on the JVM with Room's
 * [MigrationTestHelper] against the committed schema exports (`core/data/schemas`).
 *
 * The helper creates the v1 schema without the app's callbacks, so the v1 colour seeds are
 * inserted here by hand, exactly as the v1 seeder wrote them.
 */
class MigrationTest {

    private val dir: File = createTempDirectory("closet-migration-test").toFile()
    private val connections = mutableListOf<SQLiteConnection>()

    private val helper = MigrationTestHelper(
        schemaDirectoryPath = Path(System.getProperty("closet.schemaDir") ?: "schemas"),
        databasePath = File(dir, "migration.db").toPath(),
        driver = BundledSQLiteDriver(),
        databaseClass = ClothingDatabase::class,
        databaseFactory = { ClothingDatabaseConstructor.initialize() },
    )

    @AfterTest
    fun tearDown() {
        connections.forEach { it.close() }
        dir.deleteRecursively()
    }

    @Test
    fun `v1 to v2 keeps item colours as user rows`() {
        val v1 = createV1()
        v1.execSQL(
            "INSERT INTO clothing_items (id, name, is_favorite, created_at, updated_at) " +
                "VALUES (1, 'Navy tee', 0, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')"
        )
        v1.execSQL("INSERT INTO clothing_item_colors (clothing_item_id, color_id) VALUES (1, 5)") // Navy
        v1.execSQL("INSERT INTO clothing_item_colors (clothing_item_id, color_id) VALUES (1, 2)") // White
        v1.execSQL("INSERT INTO outfit_logs (date, is_ootd, created_at) VALUES ('2026-01-02', 1, '2026-01-02T08:00:00Z')")
        v1.close()

        val db = migrate()

        val rows = db.rows(
            "SELECT color_id, source, hex IS NULL, share IS NULL FROM clothing_item_colors " +
                "WHERE clothing_item_id = 1 ORDER BY color_id"
        ) { listOf(it.getLong(0), it.getText(1), it.getLong(2), it.getLong(3)) }
        assertEquals(listOf(listOf(2L, "user", 1L, 1L), listOf(5L, "user", 1L, 1L)), rows)

        val navy = db.rows("SELECT basic_term, wears_as_neutral FROM colors WHERE name = 'Navy'") {
            it.getText(0) to it.getLong(1)
        }
        assertEquals(listOf("blue" to 0L), navy)

        for (name in listOf("Charcoal", "Cream", "Denim", "Teal")) {
            assertEquals(1L, db.single("SELECT COUNT(*) FROM colors WHERE name = '$name'"), "$name exists")
        }
        assertEquals(22L, db.single("SELECT COUNT(*) FROM colors"))
        assertEquals(83L, db.single("SELECT COUNT(*) FROM color_shades"))
        assertEquals(
            0L,
            db.single("SELECT COUNT(*) FROM colors c WHERE NOT EXISTS (SELECT 1 FROM color_shades s WHERE s.color_id = c.id)"),
            "every colour has at least one shade",
        )
        assertEquals(1L, db.single("SELECT COUNT(*) FROM outfit_logs"), "the outfit log survives")
    }

    @Test
    fun `migrated and fresh databases have identical colours`() {
        createV1().close()
        val migrated = migrate()
        val migratedColors = migrated.rows(COLORS_SQL, ::colorRow)
        val migratedShades = migrated.rows(SHADES_SQL, ::shadeRow)

        val fresh = TestDatabase()
        try {
            val (freshColors, freshShades) = runBlocking {
                fresh.db.useReaderConnection { connection ->
                    connection.usePrepared(COLORS_SQL) { it.collect(::colorRow) } to
                        connection.usePrepared(SHADES_SQL) { it.collect(::shadeRow) }
                }
            }
            assertEquals(freshColors, migratedColors)
            assertEquals(freshShades, migratedShades)
            assertEquals(22, freshColors.size)
            assertEquals(83, freshShades.size)
        } finally {
            fresh.close()
        }
    }

    /** A v1 database with the v1 colour seeds; the partial index is dropped per migrations/AGENTS.md. */
    private fun createV1(): SQLiteConnection {
        val v1 = helper.createDatabase(1)
        v1.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")
        V1_COLORS.forEachIndexed { i, (name, hex, family) ->
            v1.execSQL("INSERT INTO colors (id, name, hex, color_family) VALUES (${i + 1}, '$name', '$hex', '$family')")
        }
        return v1
    }

    private fun migrate(): SQLiteConnection =
        helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2)).also { connections += it }

    private fun <T> SQLiteConnection.rows(sql: String, map: (SQLiteStatement) -> T): List<T> =
        prepare(sql).use { it.collect(map) }

    private fun SQLiteConnection.single(sql: String): Long =
        prepare(sql).use { it.step(); it.getLong(0) }

    private fun <T> SQLiteStatement.collect(map: (SQLiteStatement) -> T): List<T> =
        buildList { while (step()) add(map(this@collect)) }

    private fun colorRow(s: SQLiteStatement): List<Any> =
        listOf(s.getText(0), s.getText(1), s.getText(2), s.getText(3), s.getLong(4))

    private fun shadeRow(s: SQLiteStatement): Pair<String, String> = s.getText(0) to s.getText(1)

    private companion object {
        const val COLORS_SQL =
            "SELECT name, hex, color_family, basic_term, wears_as_neutral FROM colors ORDER BY name, hex"
        const val SHADES_SQL =
            "SELECT c.name, s.hex FROM color_shades s JOIN colors c ON c.id = s.color_id ORDER BY c.name, s.hex"

        /** The colours the v1 seeder inserted, in its order (ids 1–18). */
        val V1_COLORS = listOf(
            Triple("Black", "#000000", "Neutral"),
            Triple("White", "#FFFFFF", "Neutral"),
            Triple("Grey", "#808080", "Neutral"),
            Triple("Beige", "#F5F5DC", "Neutral"),
            Triple("Navy", "#000080", "Neutral"),
            Triple("Red", "#FF0000", "Warm"),
            Triple("Royal Blue", "#4169E1", "Cool"),
            Triple("Sky Blue", "#87CEEB", "Cool"),
            Triple("Forest Green", "#228B22", "Cool"),
            Triple("Olive", "#808000", "Earth"),
            Triple("Mint", "#98FF98", "Cool"),
            Triple("Burgundy", "#800020", "Warm"),
            Triple("Pink", "#FFC0CB", "Warm"),
            Triple("Orange", "#FFA500", "Warm"),
            Triple("Yellow", "#FFFF00", "Warm"),
            Triple("Purple", "#800080", "Cool"),
            Triple("Brown", "#A52A2A", "Earth"),
            Triple("Tan", "#D2B48C", "Earth"),
        )
    }
}
