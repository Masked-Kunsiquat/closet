package com.closet.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.closet.core.data.migrations.MIGRATION_1_2
import com.closet.core.data.migrations.MIGRATION_2_3
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
     * Verifies that a fresh install (version 3 onCreate) produces the correct full schema.
     * Spot-checks every major structural element so regressions are caught early.
     */
    @Test
    fun freshInstallCreatesCorrectSchema() {
        val db = helper.createDatabase(TEST_DB, 3)

        // Clothing items
        assertTrue(columnExists(db, "clothing_items", "id"))
        assertTrue(columnExists(db, "clothing_items", "brand_id"))
        assertTrue(columnExists(db, "clothing_items", "is_favorite"))
        assertTrue(columnExists(db, "clothing_items", "wash_status"))

        // Brands
        assertTrue(columnExists(db, "brands", "id"))
        assertTrue(columnExists(db, "brands", "name"))
        assertTrue(columnExists(db, "brands", "normalized_name"))

        // Lookup tables — including v2 and v3 additions
        assertTrue(columnExists(db, "categories", "id"))
        assertTrue(columnExists(db, "categories", "warmth_layer"))
        assertTrue(columnExists(db, "categories", "outfit_role"))
        assertTrue(columnExists(db, "subcategories", "category_id"))
        assertTrue(columnExists(db, "colors", "hex"))
        assertTrue(columnExists(db, "colors", "color_family"))
        assertTrue(columnExists(db, "materials", "id"))
        assertTrue(columnExists(db, "patterns", "icon"))
        assertTrue(columnExists(db, "seasons", "id"))
        assertTrue(columnExists(db, "seasons", "temp_low_c"))
        assertTrue(columnExists(db, "seasons", "temp_high_c"))
        assertTrue(columnExists(db, "occasions", "id"))

        // Outfits
        assertTrue(columnExists(db, "outfits", "id"))
        assertTrue(columnExists(db, "outfit_items", "pos_x"))
        assertTrue(columnExists(db, "outfit_items", "z_index"))

        // Logs and snapshot table — including v2 additions
        assertTrue(columnExists(db, "outfit_logs", "is_ootd"))
        assertTrue(columnExists(db, "outfit_logs", "weather_condition"))
        assertTrue(columnExists(db, "outfit_logs", "precipitation_mm"))
        assertTrue(columnExists(db, "outfit_logs", "wind_speed_kmh"))
        assertTrue(columnExists(db, "outfit_log_items", "outfit_log_id"))
        assertTrue(columnExists(db, "outfit_log_items", "clothing_item_id"))
        assertTrue(columnExists(db, "outfit_log_items", "outfit_name"))

        db.close()
    }

    /**
     * Verifies that migrating from version 1 to 2 adds all expected columns and
     * the resulting schema matches the live entity definitions.
     */
    @Test
    fun migrate1To2() {
        // Build a v1 database, dropping the partial index first (AGENTS.md convention)
        var db = helper.createDatabase(TEST_DB, 1)
        db.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")
        db.close()

        // Run the migration and validate against current entity definitions
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // seasons — temperature bands
        assertTrue(columnExists(db, "seasons", "temp_low_c"))
        assertTrue(columnExists(db, "seasons", "temp_high_c"))

        // categories — warmth layer
        assertTrue(columnExists(db, "categories", "warmth_layer"))

        // outfit_logs — precip + wind
        assertTrue(columnExists(db, "outfit_logs", "precipitation_mm"))
        assertTrue(columnExists(db, "outfit_logs", "wind_speed_kmh"))

        db.close()
    }

    /**
     * Verifies that migrating from version 2 to 3 adds outfit_role on categories and
     * color_family on colors, backfills the known values correctly, and the resulting
     * schema matches the live entity definitions.
     */
    @Test
    fun migrate2To3() {
        // Build a v2 database with representative seed data, dropping the partial index
        // first (AGENTS.md convention — onOpen writes it during createDatabase).
        var db = helper.createDatabase(TEST_DB, 2)
        db.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")

        // Seed the categories and colors that the backfill UPDATE statements target,
        // so we can assert the backfill ran correctly after migration.
        db.execSQL("INSERT INTO categories (id, name, icon, sort_order, warmth_layer) VALUES (1, 'Tops', 't-shirt', 1, 'None')")
        db.execSQL("INSERT INTO categories (id, name, icon, sort_order, warmth_layer) VALUES (2, 'Bottoms', 'pants', 2, 'None')")
        db.execSQL("INSERT INTO categories (id, name, icon, sort_order, warmth_layer) VALUES (3, 'Outerwear', 'hoodie', 3, 'Outer')")
        db.execSQL("INSERT INTO categories (id, name, icon, sort_order, warmth_layer) VALUES (4, 'Dresses & Jumpsuits', 'dress', 4, 'None')")
        db.execSQL("INSERT INTO categories (id, name, icon, sort_order, warmth_layer) VALUES (5, 'Footwear', 'sneaker', 5, 'None')")
        db.execSQL("INSERT INTO categories (id, name, icon, sort_order, warmth_layer) VALUES (6, 'Accessories', 'watch', 6, 'None')")
        db.execSQL("INSERT INTO categories (id, name, icon, sort_order, warmth_layer) VALUES (7, 'Bags', 'handbag', 7, 'None')")

        db.execSQL("INSERT INTO colors (id, name, hex) VALUES (1, 'Black', '#000000')")
        db.execSQL("INSERT INTO colors (id, name, hex) VALUES (2, 'Navy', '#000080')")
        db.execSQL("INSERT INTO colors (id, name, hex) VALUES (3, 'Brown', '#A52A2A')")
        db.execSQL("INSERT INTO colors (id, name, hex) VALUES (4, 'Royal Blue', '#4169E1')")
        db.execSQL("INSERT INTO colors (id, name, hex) VALUES (5, 'Red', '#FF0000')")

        db.close()

        // Run the migration and validate against current entity definitions.
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        // Schema: new columns must exist.
        assertTrue(columnExists(db, "categories", "outfit_role"))
        assertTrue(columnExists(db, "colors", "color_family"))

        // Backfill: categories.outfit_role
        db.query("SELECT outfit_role FROM categories WHERE name = 'Tops'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "Top")
        }
        db.query("SELECT outfit_role FROM categories WHERE name = 'Bottoms'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "Bottom")
        }
        db.query("SELECT outfit_role FROM categories WHERE name = 'Outerwear'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "Outerwear")
        }
        db.query("SELECT outfit_role FROM categories WHERE name = 'Dresses & Jumpsuits'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "OnePiece")
        }
        db.query("SELECT outfit_role FROM categories WHERE name = 'Footwear'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "Footwear")
        }
        db.query("SELECT outfit_role FROM categories WHERE name = 'Accessories'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "Accessory")
        }
        // Unmapped category falls back to default 'Other'
        db.query("SELECT outfit_role FROM categories WHERE name = 'Bags'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "Other")
        }

        // Backfill: colors.color_family
        db.query("SELECT color_family FROM colors WHERE name = 'Black'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "Neutral")
        }
        db.query("SELECT color_family FROM colors WHERE name = 'Navy'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "Neutral")
        }
        db.query("SELECT color_family FROM colors WHERE name = 'Brown'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "Earth")
        }
        db.query("SELECT color_family FROM colors WHERE name = 'Royal Blue'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "Cool")
        }
        db.query("SELECT color_family FROM colors WHERE name = 'Red'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "Warm")
        }

        db.close()
    }

    /**
     * Verifies that the full migration chain from version 1 to the current version
     * runs without errors and the final schema matches entity definitions.
     */
    @Test
    fun migrateFullChain1ToLatest() {
        var db = helper.createDatabase(TEST_DB, 1)
        db.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        assertTrue(columnExists(db, "categories", "warmth_layer"))
        assertTrue(columnExists(db, "categories", "outfit_role"))
        assertTrue(columnExists(db, "colors", "color_family"))
        assertTrue(columnExists(db, "seasons", "temp_low_c"))
        assertTrue(columnExists(db, "outfit_logs", "precipitation_mm"))

        db.close()
    }
}
