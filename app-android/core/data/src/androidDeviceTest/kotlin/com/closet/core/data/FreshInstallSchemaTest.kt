package com.closet.core.data

import android.content.Context
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteStatement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fresh-install schema test for the version-1 baseline.
 *
 * Opens the database exactly as the app does ([ClothingDatabase.getDatabase], so the real
 * `onCreate`/`onOpen` callbacks run) and checks it against the committed schema export
 * `core/data/schemas/com.closet.core.data.ClothingDatabase/1.json`, which is packaged as an
 * androidTest asset.
 *
 * Replaces the old `MigrationTest`: the 1→6 migration chain was squashed into this baseline
 * (see `migrations/AGENTS.md`). When a real migration is added again, add a
 * `MigrationTestHelper` test for it alongside this one.
 */
@RunWith(AndroidJUnit4::class)
class FreshInstallSchemaTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ClothingDatabase
    private lateinit var schema: JSONObject

    @Before
    fun setUp() {
        ClothingDatabase.closeAndReset()
        context.deleteDatabase(DATABASE_NAME)
        db = ClothingDatabase.getDatabase(context)
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.closet.core.data.ClothingDatabase/1.json")
            .bufferedReader().use { it.readText() }
        schema = JSONObject(json).getJSONObject("database")
    }

    @After
    fun tearDown() {
        ClothingDatabase.closeAndReset()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun freshInstall_isVersionOne_andMatchesCommittedSchemaHash() {
        assertEquals(1, schema.getInt("version"))
        assertEquals(1, runBlocking { db.databaseVersion() })
        val storedHash = query("SELECT identity_hash FROM room_master_table") {
            it.step()
            it.getText(0)
        }
        // A mismatch means 1.json is stale: rerun `./gradlew kspDebugKotlin` and commit it.
        assertEquals(schema.getString("identityHash"), storedHash)
    }

    @Test
    fun freshInstall_hasEveryTableColumnAndIndexIn1Json() {
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")

            val expectedColumns = entity.getJSONArray("fields").let { fields ->
                (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }.toSet()
            }
            val actualColumns = query("PRAGMA table_info(`$table`)") { c ->
                val name = c.getColumnNames().indexOf("name")
                buildSet { while (c.step()) add(c.getText(name)) }
            }
            assertEquals("columns of $table", expectedColumns, actualColumns)

            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                val index = indices.getJSONObject(j).getString("name")
                assertTrue("index $index on $table", sqliteObjectExists("index", index))
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
            assertTrue("$table should be seeded", count(table) > 0)
        }
        // Brands were only ever seeded by the old MIGRATION_3_4, never on a fresh install.
        assertEquals(0, count("brands"))
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
            assertTrue("trigger $trigger", sqliteObjectExists("trigger", trigger))
        }
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

    private companion object {
        const val DATABASE_NAME = "closet.db"
    }
}
