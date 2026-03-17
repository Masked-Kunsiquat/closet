package com.closet.core.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Mirror of seed files in hangr/db/seeds.
 * Canonical values sourced from closet-migrations skills and reference.md.
 */
object DatabaseSeeder {

    fun seedAll(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            seedCategories(db)
            seedSeasons(db)
            seedOccasions(db)
            seedMaterials(db)
            seedPatterns(db)
            seedSizeSystems(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

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
        categories.forEach { (name, icon, order) ->
            db.execSQL(
                "INSERT OR IGNORE INTO categories (name, icon, sort_order) VALUES (?, ?, ?)",
                arrayOf(name, icon, order)
            )
        }
    }

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
                arrayOf(name, icon)
            )
        }
    }

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
                arrayOf(name, icon)
            )
        }
    }

    private fun seedMaterials(db: SupportSQLiteDatabase) {
        val materials = listOf(
            "Cotton", "Polyester", "Wool", "Linen", "Silk", "Denim", "Leather",
            "Faux Leather", "Suede", "Velvet", "Cashmere", "Nylon", "Spandex/Elastane",
            "Rayon/Viscose", "Fleece", "Chiffon", "Satin", "Corduroy", "Jersey", "Mesh",
            "Modal", "Bamboo", "Other"
        )
        materials.forEach { name ->
            db.execSQL("INSERT OR IGNORE INTO materials (name) VALUES (?)", arrayOf(name))
        }
    }

    private fun seedPatterns(db: SupportSQLiteDatabase) {
        val patterns = listOf(
            "Solid", "Striped", "Plaid/Tartan", "Checkered", "Floral", "Geometric",
            "Animal Print", "Abstract", "Tie-Dye", "Camouflage", "Paisley", "Polka Dot",
            "Houndstooth", "Graphic", "Color Block", "Ombre", "Other"
        )
        patterns.forEach { name ->
            db.execSQL("INSERT OR IGNORE INTO patterns (name) VALUES (?)", arrayOf(name))
        }
    }

    private fun seedSizeSystems(db: SupportSQLiteDatabase) {
        // Size System: Letter
        db.execSQL("INSERT OR IGNORE INTO size_systems (id, name) VALUES (1, 'Letter')")
        val letterSizes = listOf("XS", "S", "M", "L", "XL", "XXL", "XXXL")
        letterSizes.forEachIndexed { index, value ->
            db.execSQL(
                "INSERT OR IGNORE INTO size_values (size_system_id, value, sort_order) VALUES (1, ?, ?)",
                arrayOf(value, index + 1)
            )
        }
    }
}
