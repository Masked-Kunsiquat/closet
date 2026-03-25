package com.closet.core.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Seeds all static lookup tables (categories, subcategories, seasons, occasions, colors,
 * materials, patterns, size systems) on first database creation.
 *
 * Mirror of seed files in hangr/db/seeds. Canonical values sourced from the
 * closet-migrations skill. All inserts use `INSERT OR IGNORE` so seeds are idempotent
 * and safe to re-run without duplicating rows.
 */
object DatabaseSeeder {

    /**
     * Runs all seed functions inside a single transaction.
     * Called by [com.closet.core.data.ClothingDatabase] from [androidx.room.RoomDatabase.Callback.onCreate].
     */
    fun seedAll(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            seedCategories(db)
            seedSubcategories(db)
            seedSeasons(db)
            seedOccasions(db)
            seedColors(db)
            seedMaterials(db)
            seedPatterns(db)
            seedSizeSystems(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Seeds the 10 top-level clothing categories with stable IDs (1–10). */
    private fun seedCategories(db: SupportSQLiteDatabase) {
        // name, icon, sort_order, warmth_layer, outfit_role
        val categories = listOf(
            listOf("Tops",                  "t-shirt",               1, "None",  "Top"),
            listOf("Bottoms",               "pants",                 2, "None",  "Bottom"),
            listOf("Outerwear",             "hoodie",                3, "Outer", "Outerwear"),
            listOf("Dresses & Jumpsuits",   "dress",                 4, "None",  "OnePiece"),
            listOf("Footwear",              "sneaker",               5, "None",  "Footwear"),
            listOf("Accessories",           "watch",                 6, "None",  "Accessory"),
            listOf("Bags",                  "handbag",               7, "None",  "Other"),
            listOf("Activewear",            "person-simple-running", 8, "None",  "Other"),
            listOf("Underwear & Intimates", "sock",                  9, "Base",  "Other"),
            listOf("Swimwear",              "goggles",               10, "None", "Other"),
        )
        categories.forEachIndexed { index, row ->
            db.execSQL(
                "INSERT OR IGNORE INTO categories (id, name, icon, sort_order, warmth_layer, outfit_role) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(index + 1, row[0], row[1], row[2], row[3], row[4])
            )
        }
    }

    /** Seeds subcategories mapped to their parent category IDs (1–10). */
    private fun seedSubcategories(db: SupportSQLiteDatabase) {
        val mapping = mapOf(
            1L to listOf("T-Shirt", "Tank Top", "Blouse", "Shirt", "Polo", "Sweater", "Hoodie", "Sweatshirt", "Cardigan", "Bodysuit"),
            2L to listOf("Jeans", "Trousers/Slacks", "Chinos", "Shorts", "Skirt", "Leggings", "Joggers/Sweatpants"),
            3L to listOf("Jacket", "Coat", "Blazer", "Vest", "Raincoat"),
            4L to listOf("Dress", "Romper", "Jumpsuit"),
            5L to listOf("Sneakers", "Boots", "Sandals", "Dress Shoes", "Slippers"),
            6L to listOf("Belt", "Hat/Cap", "Scarf", "Sunglasses", "Watch", "Jewelry", "Tie", "Cufflinks"),
            7L to listOf("Backpack", "Tote", "Crossbody", "Duffel"),
            8L to listOf("Sports Bra", "Athletic Shorts", "Track Jacket"),
            9L to listOf("Underwear", "Bra/Bralette", "Socks", "Tights"),
            10L to listOf("One-Piece", "Bikini/Trunks", "Rash Guard")
        )
        mapping.forEach { (catId, subs) ->
            subs.forEachIndexed { index, name ->
                db.execSQL(
                    "INSERT OR IGNORE INTO subcategories (category_id, name, sort_order) VALUES (?, ?, ?)",
                    arrayOf<Any>(catId, name, index + 1)
                )
            }
        }
    }

    /** Seeds the 5 seasons with Phosphor icons and temperature bands (°C). All Season has null bounds. */
    private fun seedSeasons(db: SupportSQLiteDatabase) {
        // name, icon, temp_low_c, temp_high_c — All Season intentionally null (always applicable)
        val seasons = listOf(
            listOf<Any?>("Spring",     "flower",      10.0,  20.0),
            listOf<Any?>("Summer",     "sun",         22.0,  35.0),
            listOf<Any?>("Fall",       "leaf",         5.0,  18.0),
            listOf<Any?>("Winter",     "snowflake",  -10.0,   5.0),
            listOf<Any?>("All Season", "thermometer",  null,  null),
        )
        seasons.forEach { row ->
            db.execSQL(
                "INSERT OR IGNORE INTO seasons (name, icon, temp_low_c, temp_high_c) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(row[0], row[1], row[2], row[3])
            )
        }
    }

    /** Seeds the 9 occasion types (Casual, Work/Business, Formal, etc.) with Phosphor icons. */
    private fun seedOccasions(db: SupportSQLiteDatabase) {
        val occasions = listOf(
            "Casual" to "coffee",
            "Work/Business" to "briefcase",
            "Formal" to "crown-simple",
            "Athletic" to "barbell",
            "Loungewear" to "couch",
            "Date Night" to "heart",
            "Vacation" to "island",
            "Outdoor/Hiking" to "mountains",
            "Special Occasion" to "cheers"
        )
        occasions.forEach { (name, icon) ->
            db.execSQL(
                "INSERT OR IGNORE INTO occasions (name, icon) VALUES (?, ?)",
                arrayOf<Any>(name, icon)
            )
        }
    }

    /**
     * Seeds 18 named colors with hex codes and color families.
     * Public so it can be called independently in migrations that backfill color data.
     *
     * color_family values: Neutral | Earth | Cool | Warm | Bright.
     * Added in Migration 2→3; the column has DEFAULT 'Neutral' so this insert always
     * supplies the correct family explicitly.
     */
    fun seedColors(db: SupportSQLiteDatabase) {
        // name, hex, color_family
        val colors = listOf(
            Triple("Black",        "#000000", "Neutral"),
            Triple("White",        "#FFFFFF", "Neutral"),
            Triple("Grey",         "#808080", "Neutral"),
            Triple("Beige",        "#F5F5DC", "Neutral"),
            Triple("Navy",         "#000080", "Neutral"),
            Triple("Red",          "#FF0000", "Warm"),
            Triple("Royal Blue",   "#4169E1", "Cool"),
            Triple("Sky Blue",     "#87CEEB", "Cool"),
            Triple("Forest Green", "#228B22", "Cool"),
            Triple("Olive",        "#808000", "Earth"),
            Triple("Mint",         "#98FF98", "Cool"),
            Triple("Burgundy",     "#800020", "Warm"),
            Triple("Pink",         "#FFC0CB", "Warm"),
            Triple("Orange",       "#FFA500", "Warm"),
            Triple("Yellow",       "#FFFF00", "Warm"),
            Triple("Purple",       "#800080", "Cool"),
            Triple("Brown",        "#A52A2A", "Earth"),
            Triple("Tan",          "#D2B48C", "Earth"),
        )
        colors.forEach { (name, hex, family) ->
            db.execSQL(
                "INSERT OR IGNORE INTO colors (name, hex, color_family) VALUES (?, ?, ?)",
                arrayOf<Any>(name, hex, family)
            )
        }
    }

    /** Seeds 23 fabric/material types including natural fibers, synthetics, and "Other". */
    private fun seedMaterials(db: SupportSQLiteDatabase) {
        val materials = listOf(
            "Cotton", "Polyester", "Wool", "Linen", "Silk", "Denim", "Leather",
            "Faux Leather", "Suede", "Velvet", "Cashmere", "Nylon", "Spandex/Elastane",
            "Rayon/Viscose", "Fleece", "Chiffon", "Satin", "Corduroy", "Jersey", "Mesh",
            "Modal", "Bamboo", "Other"
        )
        materials.forEach { name ->
            db.execSQL("INSERT OR IGNORE INTO materials (name) VALUES (?)", arrayOf<Any>(name))
        }
    }

    /** Seeds 17 visual patterns (Solid, Striped, Floral, etc.) including "Other". */
    private fun seedPatterns(db: SupportSQLiteDatabase) {
        val patterns = listOf(
            "Solid" to "solid",
            "Striped" to "striped",
            "Plaid/Tartan" to "plaid_tartan",
            "Checkered" to "checkered",
            "Floral" to "floral",
            "Geometric" to "geometric",
            "Animal Print" to "animal_print",
            "Abstract" to "abstract",
            "Tie-Dye" to "tie_dye",
            "Camouflage" to "camouflage",
            "Paisley" to "paisley",
            "Polka Dot" to "polka_dot",
            "Houndstooth" to "houndstooth",
            "Graphic" to "graphic",
            "Color Block" to "color_block",
            "Ombré" to "ombre",
            "Other" to "other"
        )
        patterns.forEach { (name, icon) ->
            db.execSQL("INSERT OR IGNORE INTO patterns (name, icon) VALUES (?, ?)", arrayOf<Any>(name, icon))
        }
    }

    /**
     * Seeds a starter set of common brands. Called only from MIGRATION_3_4 — not from seedAll.
     * Uses INSERT OR IGNORE so user-backfilled data from the migration takes priority.
     */
    internal fun seedBrands(db: SupportSQLiteDatabase) {
        val brands = listOf("Adidas", "Gap", "H&M", "Levi's", "Mango", "Nike", "Uniqlo", "Zara")
        brands.forEach { name ->
            db.execSQL("INSERT OR IGNORE INTO brands (name) VALUES (?)", arrayOf<Any>(name))
        }
    }

    /**
     * Seeds 4 size systems (Letter, Women's Numeric, Shoes (US Men's), One Size) with
     * their associated size values. System IDs are hardcoded starting at 1.
     */
    private fun seedSizeSystems(db: SupportSQLiteDatabase) {
        val systems = mapOf(
            "Letter" to listOf("XS", "S", "M", "L", "XL", "XXL", "XXXL"),
            "Women's Numeric" to listOf("00", "0", "2", "4", "6", "8", "10", "12", "14", "16"),
            "Shoes (US Men's)" to listOf("6", "6.5", "7", "7.5", "8", "8.5", "9", "9.5", "10", "10.5", "11", "11.5", "12", "13", "14", "15"),
            "One Size" to listOf("One Size")
        )
        
        var sysId = 1L
        systems.forEach { (name, values) ->
            db.execSQL("INSERT OR IGNORE INTO size_systems (id, name) VALUES (?, ?)", arrayOf<Any>(sysId, name))
            values.forEachIndexed { index, v ->
                db.execSQL(
                    "INSERT OR IGNORE INTO size_values (size_system_id, value, sort_order) VALUES (?, ?, ?)",
                    arrayOf<Any>(sysId, v, index + 1)
                )
            }
            sysId++
        }
    }
}
