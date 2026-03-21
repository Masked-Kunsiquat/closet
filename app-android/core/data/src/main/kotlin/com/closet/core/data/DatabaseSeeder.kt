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
        val categories = listOf(
            Triple("Tops", "t-shirt", 1),
            Triple("Bottoms", "pants", 2),
            Triple("Outerwear", "hoodie", 3),
            Triple("Dresses & Jumpsuits", "dress", 4),
            Triple("Footwear", "sneaker", 5),
            Triple("Accessories", "watch", 6),
            Triple("Bags", "handbag", 7),
            Triple("Activewear", "person-simple-running", 8),
            Triple("Underwear & Intimates", "sock", 9),
            Triple("Swimwear", "goggles", 10)
        )
        categories.forEachIndexed { index, (name, icon, order) ->
            db.execSQL(
                "INSERT OR IGNORE INTO categories (id, name, icon, sort_order) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(index + 1, name, icon, order)
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

    /** Seeds the 5 seasons (Spring, Summer, Fall, Winter, All Season) with Phosphor icons. */
    private fun seedSeasons(db: SupportSQLiteDatabase) {
        val seasons = listOf(
            "Spring" to "flower",
            "Summer" to "sun",
            "Fall" to "leaf",
            "Winter" to "snowflake",
            "All Season" to "thermometer"
        )
        seasons.forEach { (name, icon) ->
            db.execSQL(
                "INSERT OR IGNORE INTO seasons (name, icon) VALUES (?, ?)",
                arrayOf<Any>(name, icon)
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
     * Seeds 18 named colors with hex codes.
     * Public so it can be called independently in migrations that backfill color data.
     */
    fun seedColors(db: SupportSQLiteDatabase) {
        val colors = listOf(
            "Black" to "#000000",
            "White" to "#FFFFFF",
            "Grey" to "#808080",
            "Beige" to "#F5F5DC",
            "Navy" to "#000080",
            "Red" to "#FF0000",
            "Royal Blue" to "#4169E1",
            "Sky Blue" to "#87CEEB",
            "Forest Green" to "#228B22",
            "Olive" to "#808000",
            "Mint" to "#98FF98",
            "Burgundy" to "#800020",
            "Pink" to "#FFC0CB",
            "Orange" to "#FFA500",
            "Yellow" to "#FFFF00",
            "Purple" to "#800080",
            "Brown" to "#A52A2A",
            "Tan" to "#D2B48C"
        )
        colors.forEach { (name, hex) ->
            db.execSQL(
                "INSERT OR IGNORE INTO colors (name, hex) VALUES (?, ?)",
                arrayOf<Any>(name, hex)
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
