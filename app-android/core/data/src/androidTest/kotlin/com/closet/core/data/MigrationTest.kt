package com.closet.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.closet.core.data.migrations.columnExists
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ClothingDatabase::class.java
    )

    /**
     * Verifies that a fresh install (version 1 onCreate) produces the correct full schema.
     * Spot-checks every major structural element so regressions are caught early.
     */
    @Test
    fun freshInstallCreatesCorrectSchema() {
        val db = helper.createDatabase(TEST_DB, 1)

        // Clothing items
        assertTrue(columnExists(db, "clothing_items", "id"))
        assertTrue(columnExists(db, "clothing_items", "brand_id"))
        assertTrue(columnExists(db, "clothing_items", "is_favorite"))
        assertTrue(columnExists(db, "clothing_items", "wash_status"))

        // Brands
        assertTrue(columnExists(db, "brands", "id"))
        assertTrue(columnExists(db, "brands", "name"))
        assertTrue(columnExists(db, "brands", "normalized_name"))

        // Lookup tables
        assertTrue(columnExists(db, "categories", "id"))
        assertTrue(columnExists(db, "subcategories", "category_id"))
        assertTrue(columnExists(db, "colors", "hex"))
        assertTrue(columnExists(db, "materials", "id"))
        assertTrue(columnExists(db, "patterns", "icon"))
        assertTrue(columnExists(db, "seasons", "id"))
        assertTrue(columnExists(db, "occasions", "id"))

        // Outfits
        assertTrue(columnExists(db, "outfits", "id"))
        assertTrue(columnExists(db, "outfit_items", "pos_x"))
        assertTrue(columnExists(db, "outfit_items", "z_index"))

        // Logs and snapshot table
        assertTrue(columnExists(db, "outfit_logs", "is_ootd"))
        assertTrue(columnExists(db, "outfit_logs", "weather_condition"))
        assertTrue(columnExists(db, "outfit_log_items", "outfit_log_id"))
        assertTrue(columnExists(db, "outfit_log_items", "clothing_item_id"))
        assertTrue(columnExists(db, "outfit_log_items", "outfit_name"))

        db.close()
    }
}
