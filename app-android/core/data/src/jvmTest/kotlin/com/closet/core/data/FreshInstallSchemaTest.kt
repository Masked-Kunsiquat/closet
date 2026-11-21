package com.closet.core.data

import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fresh-install schema test for the newest schema version ([LATEST_VERSION]), on the JVM.
 *
 * Opens a new database through the production builder ([getDatabaseBuilder], so the bundled
 * driver and the real `configureClosetDatabase` `onCreate`/`onOpen` callbacks run) and checks it
 * against the committed schema export `core/data/schemas/com.closet.core.data.ClothingDatabase/<LATEST_VERSION>.json`
 * (located by [committedSchemaFile]). Upgrades from older versions are covered by [MigrationTest].
 */
class FreshInstallSchemaTest {

    private lateinit var testDb: TestDatabase
    private val db: ClothingDatabase get() = testDb.db
    private lateinit var schema: JsonObject

    @BeforeTest
    fun setUp() {
        testDb = TestDatabase()
        val json = committedSchemaFile(LATEST_VERSION).readText()
        schema = Json.parseToJsonElement(json).jsonObject.getValue("database").jsonObject
    }

    @AfterTest
    fun tearDown() {
        testDb.close()
    }

    @Test
    fun freshInstall_isLatestVersion_andMatchesCommittedSchemaHash() {
        assertEquals(LATEST_VERSION, schema.getValue("version").jsonPrimitive.int)
        assertEquals(LATEST_VERSION, runBlocking { db.databaseVersion() })
        val storedHash = query("SELECT identity_hash FROM room_master_table") {
            it.step()
            it.getText(0)
        }
        // A mismatch means the newest schema JSON is stale: rerun `./gradlew :core:data:kspAndroidMain` and commit it.
        assertEquals(schema.getValue("identityHash").jsonPrimitive.content, storedHash)
    }

    @Test
    fun freshInstall_hasEveryTableColumnAndIndexInLatestJson() {
        for (element in schema.getValue("entities").jsonArray) {
            val entity = element.jsonObject
            val table = entity.getValue("tableName").jsonPrimitive.content

            val expectedColumns = entity.getValue("fields").jsonArray
                .map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
                .toSet()
            val actualColumns = query("PRAGMA table_info(`$table`)") { c ->
                val name = c.getColumnNames().indexOf("name")
                buildSet { while (c.step()) add(c.getText(name)) }
            }
            assertEquals(expectedColumns, actualColumns, "columns of $table")

            val indices = entity["indices"]?.jsonArray ?: continue
            for (indexElement in indices) {
                val index = indexElement.jsonObject.getValue("name").jsonPrimitive.content
                assertTrue(sqliteObjectExists("index", index), "index $index on $table")
            }
        }
    }

    @Test
    fun freshInstall_enforcesForeignKeys() {
        val enabled = query("PRAGMA foreign_keys") { it.step(); it.getInt(0) }
        assertEquals(1, enabled)
    }

    @Test
    fun freshInstall_seedsLookupTables_butNotBrands() {
        assertEquals(10, count("categories"))
        assertEquals(4, count("size_systems"))
        for (table in listOf("subcategories", "seasons", "occasions", "colors", "materials", "patterns", "size_values")) {
            assertTrue(count(table) > 0, "$table should be seeded")
        }
        // Brands were only ever seeded by the old MIGRATION_3_4, never on a fresh install.
        assertEquals(0, count("brands"))
        assertEquals(22, count("colors"))
        assertEquals(83, count("color_shades"))
    }

    @Test
    fun freshInstall_createsOotdPartialIndexAndCategoryTriggers() {
        assertTrue(sqliteObjectExists("index", "one_ootd_per_day"))
        for (trigger in listOf(
            "check_clothing_item_category_consistency_insert",
            "verify_subcategory_parent_category_insert",
            "check_clothing_item_category_consistency_update",
            "verify_subcategory_parent_category_update",
        )) {
            assertTrue(sqliteObjectExists("trigger", trigger), "trigger $trigger")
        }
    }

    private companion object {
        /** The newest schema version; its export is `<LATEST_VERSION>.json`. */
        const val LATEST_VERSION = 3
    }

    private fun count(table: String): Int =
        query("SELECT COUNT(*) FROM `$table`") { it.step(); it.getInt(0) }

    private fun sqliteObjectExists(type: String, name: String): Boolean =
        query("SELECT 1 FROM sqlite_master WHERE type = ? AND name = ?") {
            it.bindText(1, type)
            it.bindText(2, name)
            it.step()
        }

    /** Runs [sql] on Room's writer connection, the one the app's writes (and foreign keys) use. */
    private fun <T> query(sql: String, block: (SQLiteStatement) -> T): T = runBlocking {
        db.useWriterConnection { connection -> connection.usePrepared(sql, block) }
    }
}
