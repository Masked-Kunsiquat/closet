package com.closet.core.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

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
     * Runs all seed functions. Called by [com.closet.core.data.configureClosetDatabase] from
     * [androidx.room.RoomDatabase.Callback.onCreate], which Room runs inside its own
     * `BEGIN EXCLUSIVE TRANSACTION`, so the seeds commit or roll back together with the schema.
     * (A connection can't nest `BEGIN`, so this no longer opens a transaction itself.)
     */
    fun seedAll(db: SQLiteConnection) {
        seedCategories(db)
        seedSubcategories(db)
        seedSeasons(db)
        seedOccasions(db)
        seedColors(db)
        seedMaterials(db)
        seedPatterns(db)
        seedSizeSystems(db)
    }

    /**
     * Runs one statement with bound [args], binding each the way `SupportSQLiteDatabase.execSQL`
     * did: integers as INTEGER, doubles as REAL, strings as TEXT and null as NULL.
     */
    private fun SQLiteConnection.execSQL(sql: String, args: Array<out Any?>) {
        prepare(sql).use { statement ->
            args.forEachIndexed { i, arg -> statement.bindArg(i + 1, arg) }
            statement.step()
        }
    }

    private fun SQLiteStatement.bindArg(index: Int, arg: Any?) {
        when (arg) {
            null -> bindNull(index)
            is Int -> bindLong(index, arg.toLong())
            is Long -> bindLong(index, arg)
            is Double -> bindDouble(index, arg)
            is String -> bindText(index, arg)
            else -> error("Unsupported seed argument type: ${arg::class}")
        }
    }

    /** Seeds the 10 top-level clothing categories with stable IDs (1–10). */
    private fun seedCategories(db: SQLiteConnection) {
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
    private fun seedSubcategories(db: SQLiteConnection) {
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
    private fun seedSeasons(db: SQLiteConnection) {
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
    private fun seedOccasions(db: SQLiteConnection) {
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
     * Seeds the 22 named colors ([SEEDED_COLORS], in order, so a fresh install gets ids 1–22),
     * then their reference shades into `color_shades`.
     *
     * Migrations must not call this (see `migrations/AGENTS.md`); `MIGRATION_1_2` inlines the
     * same values as literal SQL, and `MigrationTest` checks the two agree.
     */
    fun seedColors(db: SQLiteConnection) {
        SEEDED_COLORS.forEach { c ->
            db.execSQL(
                "INSERT OR IGNORE INTO colors (name, hex, color_family, basic_term, wears_as_neutral) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(c.name, c.hex, c.family, c.basicTerm, if (c.wearsAsNeutral) 1 else 0)
            )
        }
        SEEDED_COLORS.forEach { c ->
            c.shades.forEach { shade ->
                db.execSQL(
                    "INSERT INTO color_shades (color_id, hex) SELECT id, ? FROM colors WHERE name = ? " +
                        "AND NOT EXISTS (SELECT 1 FROM color_shades s WHERE s.color_id = colors.id AND s.hex = ?)",
                    arrayOf<Any>(shade, c.name, shade)
                )
            }
        }
    }

    /**
     * A seeded color.
     *
     * @property hex the legacy display hex, which the recommendation engine's hue terms read.
     * @property family the `color_family`: Neutral | Earth | Cool | Warm | Bright.
     * @property basicTerm the basic colour term (`colors.basic_term`).
     * @property wearsAsNeutral `colors.wears_as_neutral`.
     * @property shades the reference shades (`color_shades.hex`) the colour namer matches
     *   against; [hex] is not automatically one of them.
     */
    data class SeedColor(
        val name: String,
        val hex: String,
        val family: String,
        val basicTerm: String,
        val wearsAsNeutral: Boolean,
        val shades: List<String>,
    )

    /**
     * The seeded colors, in insert order (Black = 1 … Teal = 22 on a fresh install). Public so the
     * recommendation engine's hue thresholds and the colour namer can be tested against the real
     * palette. The first 18 rows are the v1 palette, unchanged; v2 appended the last 4.
     */
    val SEEDED_COLORS: List<SeedColor> = listOf(
        SeedColor("Black",        "#000000", "Neutral", "black",  true,  listOf("#000000", "#1A1A1A", "#2B2B2B")),
        SeedColor("White",        "#FFFFFF", "Neutral", "white",  true,  listOf("#FFFFFF", "#F4F4F2", "#EDEDE8")),
        SeedColor("Grey",         "#808080", "Neutral", "gray",   true,  listOf("#808080", "#A0A0A0", "#6E6E6E", "#BEBEBE")),
        SeedColor("Beige",        "#F5F5DC", "Neutral", "brown",  true,  listOf("#F5F5DC", "#E8DCC4", "#D9C8A9")),
        SeedColor("Navy",         "#000080", "Neutral", "blue",   false, listOf("#000080", "#1F2A44", "#202A5A", "#2C3E63")),
        SeedColor("Red",          "#FF0000", "Warm",    "red",    false, listOf("#FF0000", "#B3202A", "#D62B2B", "#C41E3A")),
        SeedColor("Royal Blue",   "#4169E1", "Cool",    "blue",   false, listOf("#4169E1", "#2E50A8", "#3A5FCD")),
        SeedColor("Sky Blue",     "#87CEEB", "Cool",    "blue",   false, listOf("#87CEEB", "#8DB8DE", "#A7C7E7")),
        SeedColor("Forest Green", "#228B22", "Cool",    "green",  false, listOf("#228B22", "#2E5E3A", "#355E3B", "#4F7942")),
        SeedColor("Olive",        "#808000", "Earth",   "green",  false, listOf("#808000", "#6B6B3A", "#708238")),
        SeedColor("Mint",         "#98FF98", "Cool",    "green",  false, listOf("#98FF98", "#A8DCC0", "#B2E0C8")),
        SeedColor("Burgundy",     "#800020", "Warm",    "red",    false, listOf("#800020", "#6D1A2B", "#7B2D3A")),
        SeedColor("Pink",         "#FFC0CB", "Warm",    "pink",   false, listOf("#FFC0CB", "#F2A7B8", "#E75480", "#F4C2C2")),
        SeedColor("Orange",       "#FFA500", "Warm",    "orange", false, listOf("#FFA500", "#E8742A", "#F28C28", "#FF7F50")),
        SeedColor("Yellow",       "#FFFF00", "Warm",    "yellow", false, listOf("#FFFF00", "#F2CF3A", "#FFFACD", "#FFF59D", "#E1AD01", "#FFD300", "#F6E7A5")),
        SeedColor("Purple",       "#800080", "Cool",    "purple", false, listOf("#800080", "#6A3D8F", "#9B59B6", "#B57EDC", "#CDB4E0", "#E6E6FA")),
        SeedColor("Brown",        "#A52A2A", "Earth",   "brown",  true,  listOf("#6B4423", "#5C4033", "#7B5B3A", "#4E342E")),
        SeedColor("Tan",          "#D2B48C", "Earth",   "brown",  true,  listOf("#D2B48C", "#C8A47E", "#C3B091", "#C19A6B")),
        SeedColor("Charcoal",     "#36454F", "Neutral", "gray",   true,  listOf("#36454F", "#3C3C3C", "#4A4A4A")),
        SeedColor("Cream",        "#F3EBD3", "Neutral", "white",  true,  listOf("#FFFDD0", "#F3EBD3", "#FAF3E0", "#FFF5DC")),
        SeedColor("Denim",        "#4F6D8F", "Neutral", "blue",   true,  listOf("#3B5A80", "#4F6D8F", "#5B7BA6", "#2F4A6B")),
        SeedColor("Teal",         "#1F7A7A", "Cool",    "blue",   false, listOf("#008080", "#1F7A7A", "#2A9D8F")),
    )

    /** Seeds 23 fabric/material types including natural fibers, synthetics, and "Other". */
    private fun seedMaterials(db: SQLiteConnection) {
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
    private fun seedPatterns(db: SQLiteConnection) {
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
     * Seeds 4 size systems (Letter, Women's Numeric, Shoes (US Men's), One Size) with
     * their associated size values. System IDs are hardcoded starting at 1.
     */
    private fun seedSizeSystems(db: SQLiteConnection) {
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
