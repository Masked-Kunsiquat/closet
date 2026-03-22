package com.closet.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.closet.core.data.migrations.MIGRATION_1_2
import com.closet.core.data.migrations.MIGRATION_2_3
import com.closet.core.data.migrations.MIGRATION_3_4
import com.closet.core.data.migrations.MIGRATION_4_5
import com.closet.core.data.migrations.MIGRATION_5_6
import com.closet.core.data.migrations.MIGRATION_6_7
import com.closet.core.data.migrations.columnExists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    // ─── Individual migrations ─────────────────────────────────────────────────

    /**
     * 1→2: Adds layout columns to outfit_items and seeds colors.
     */
    @Test
    fun migrate1To2() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        assertTrue(columnExists(db, "outfit_items", "pos_x"))
        assertTrue(columnExists(db, "outfit_items", "pos_y"))
        assertTrue(columnExists(db, "outfit_items", "scale"))
        assertTrue(columnExists(db, "outfit_items", "z_index"))

        // Colors should have been seeded
        val colorCount = db.query("SELECT COUNT(*) FROM colors").use { c ->
            c.moveToFirst(); c.getInt(0)
        }
        assertTrue("Colors should be seeded after migration 1→2", colorCount > 0)

        db.close()
    }

    /**
     * 2→3: Adds the unique index enforcing one wear-log per outfit per day.
     */
    @Test
    fun migrate2To3() {
        helper.createDatabase(TEST_DB, 2).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        // Verify the unique index exists
        val indexExists = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_outfit_logs_outfit_id_date'"
        ).use { it.moveToFirst() }
        assertTrue("Unique index on (outfit_id, date) should exist after migration 2→3", indexExists)

        db.close()
    }

    /**
     * 3→4: Introduces the brands table and migrates free-text brand to a FK.
     * Seeds a clothing item with a brand name and verifies it is backfilled.
     */
    @Test
    fun migrate3To4() {
        val db3 = helper.createDatabase(TEST_DB, 3)

        // Insert a category so the clothing item FK is valid
        db3.execSQL("INSERT INTO categories (id, name, sort_order) VALUES (1, 'Tops', 1)")

        // Insert a clothing item with a free-text brand
        db3.execSQL(
            "INSERT INTO clothing_items (name, brand, category_id, status, wash_status, is_favorite, created_at, updated_at) " +
            "VALUES ('Test Jacket', 'Patagonia', 1, 'Active', 'Clean', 0, '2024-01-01T00:00:00Z', '2024-01-01T00:00:00Z')"
        )
        db3.close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        // brands table should exist and contain the backfilled brand
        val brandName = db.query("SELECT name FROM brands WHERE name = 'Patagonia'").use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        assertEquals("Patagonia", brandName)

        // clothing_items should have brand_id populated
        val brandId = db.query(
            "SELECT brand_id FROM clothing_items WHERE name = 'Test Jacket'"
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else null }
        assertNotNull("brand_id should be set after backfill", brandId)

        assertTrue(columnExists(db, "clothing_items", "brand_id"))

        db.close()
    }

    /**
     * 4→5: Adds case-insensitive normalized_name to brands.
     * Seeds a brand and verifies normalized_name is backfilled.
     */
    @Test
    fun migrate4To5() {
        val db4 = helper.createDatabase(TEST_DB, 4)
        db4.execSQL("INSERT INTO brands (name) VALUES ('Nike')")
        db4.execSQL("INSERT INTO brands (name) VALUES ('Adidas')")
        db4.close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        assertTrue(columnExists(db, "brands", "normalized_name"))

        val normalizedName = db.query(
            "SELECT normalized_name FROM brands WHERE name = 'Nike'"
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        assertEquals("nike", normalizedName)

        db.close()
    }

    /**
     * 5→6: Adds icon to patterns, renames Ombre → Ombré, backfills all 17 icons.
     * Also drops one_ootd_per_day so Room's schema validator passes.
     */
    @Test
    fun migrate5To6() {
        val db5 = helper.createDatabase(TEST_DB, 5)

        // Seed a subset of patterns manually (the seeder doesn't run in test helper)
        db5.execSQL("INSERT INTO patterns (name) VALUES ('Ombre')")
        db5.execSQL("INSERT INTO patterns (name) VALUES ('Floral')")
        db5.execSQL("INSERT INTO patterns (name) VALUES ('Solid')")

        db5.close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        assertTrue(columnExists(db, "patterns", "icon"))

        // Ombre should have been renamed to Ombré
        val ombreCount = db.query("SELECT COUNT(*) FROM patterns WHERE name = 'Ombre'")
            .use { c -> c.moveToFirst(); c.getInt(0) }
        assertEquals("'Ombre' (no accent) should no longer exist", 0, ombreCount)

        val ombreAccentCount = db.query("SELECT COUNT(*) FROM patterns WHERE name = 'Ombré'")
            .use { c -> c.moveToFirst(); c.getInt(0) }
        assertEquals("'Ombré' (with accent) should exist", 1, ombreAccentCount)

        // Icons should be backfilled
        val floralIcon = db.query("SELECT icon FROM patterns WHERE name = 'Floral'")
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }
        assertEquals("floral", floralIcon)

        val solidIcon = db.query("SELECT icon FROM patterns WHERE name = 'Solid'")
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }
        assertEquals("solid", solidIcon)

        // one_ootd_per_day should have been dropped (Room validates this via entity schema)
        val ootdIndexExists = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='one_ootd_per_day'"
        ).use { it.moveToFirst() }
        assertTrue("one_ootd_per_day should be absent after migration so Room validates clean", !ootdIndexExists)

        db.close()
    }

    /**
     * 6→7: Adds outfit_log_items snapshot table and backfills from existing logs.
     * Seeds an outfit, a clothing item, an outfit_item link, and a log, then verifies
     * the backfill wrote a snapshot row with the correct outfit name.
     */
    @Test
    fun migrate6To7() {
        val db6 = helper.createDatabase(TEST_DB, 6)

        // Seed prerequisite rows
        db6.execSQL("INSERT INTO categories (id, name, sort_order) VALUES (1, 'Tops', 1)")
        db6.execSQL(
            "INSERT INTO clothing_items (id, name, category_id, status, wash_status, is_favorite, created_at, updated_at) " +
            "VALUES (1, 'Blue Tee', 1, 'Active', 'Clean', 0, '2024-01-01T00:00:00Z', '2024-01-01T00:00:00Z')"
        )
        db6.execSQL(
            "INSERT INTO outfits (id, name, created_at, updated_at) " +
            "VALUES (1, 'Casual Friday', '2024-01-01T00:00:00Z', '2024-01-01T00:00:00Z')"
        )
        db6.execSQL(
            "INSERT INTO outfit_items (outfit_id, clothing_item_id) VALUES (1, 1)"
        )
        db6.execSQL(
            "INSERT INTO outfit_logs (id, outfit_id, date, is_ootd, created_at) " +
            "VALUES (1, 1, '2024-01-01', 0, '2024-01-01T00:00:00Z')"
        )
        db6.close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)

        // Table should exist
        val tableExists = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='outfit_log_items'"
        ).use { it.moveToFirst() }
        assertTrue("outfit_log_items table should exist after migration 6→7", tableExists)

        // Backfill should have written a snapshot row
        val snapshotRow = db.query(
            "SELECT outfit_name FROM outfit_log_items WHERE outfit_log_id = 1 AND clothing_item_id = 1"
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        assertEquals("Casual Friday", snapshotRow)

        db.close()
    }

    // ─── Full upgrade path ─────────────────────────────────────────────────────

    /**
     * Full path 1→7: simulates a user who has never updated the app.
     * Validates the schema at every step and confirms the final state is correct.
     */
    @Test
    fun migrateFullPath1To7() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 7, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
        )

        // Spot-check columns added across the full migration chain
        assertTrue(columnExists(db, "outfit_items", "pos_x"))
        assertTrue(columnExists(db, "clothing_items", "brand_id"))
        assertTrue(columnExists(db, "brands", "normalized_name"))
        assertTrue(columnExists(db, "patterns", "icon"))
        val outfitLogItemsExists = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='outfit_log_items'"
        ).use { it.moveToFirst() }
        assertTrue(outfitLogItemsExists)

        db.close()
    }
}
