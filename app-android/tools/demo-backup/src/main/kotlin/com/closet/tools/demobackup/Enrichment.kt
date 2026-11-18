package com.closet.tools.demobackup

import java.time.LocalDate
import java.util.Random

/**
 * Purchase, brand, material, pattern and status details for one demo item. A bare item has
 * none of them, so add/edit flows still have empty fields to fill in by hand.
 */
data class ItemDetails(
    val price: Double? = null,
    /** ISO `YYYY-MM-DD`. */
    val purchaseDate: String? = null,
    val brand: String? = null,
    /** Seeded material names. */
    val materials: List<String> = emptyList(),
    /** Seeded pattern names. */
    val patterns: List<String> = emptyList(),
    val favorite: Boolean = false,
    val dirty: Boolean = false,
    /** Retired items: "Sold" or "Donated" (a non-Active `ClothingStatus` label), else null. */
    val retired: String? = null,
) {
    val isBare: Boolean get() = this == ItemDetails()
}

/**
 * Deterministic per-item details. [share] of the items (0.0..1.0) get details; the rest stay
 * bare. Everything derives from [seed], so the same options give the same backup.
 */
class Enricher(seed: Long, private val today: LocalDate, private val share: Double, private val minAgeDays: Int) {

    private val random = Random(seed * 31 + 101)

    /** Call once per item, in a stable order. Always draws the same number of values per item. */
    fun detailsFor(item: DemoItem): ItemDetails {
        val roll = random.nextDouble()
        val priceRoll = random.nextDouble()
        val dateRoll = random.nextInt(MAX_AGE_DAYS - minAgeDays + 1)
        val brandRoll = random.nextInt(100)
        val brandPick = random.nextInt(BRANDS.size)
        val materialPick = random.nextInt(1000)
        val secondMaterialRoll = random.nextInt(100)
        val patternRoll = random.nextInt(100)
        val patternPick = random.nextInt(PRINTS.size)
        val favoriteRoll = random.nextInt(100)
        val dirtyRoll = random.nextInt(100)
        val retiredRoll = random.nextInt(100)

        if (roll >= share) return ItemDetails()

        val (low, high) = PRICE_RANGES[item.category] ?: (10 to 60)
        val price = (low + (priceRoll * (high - low)).toInt()) + 0.99

        val candidates = materialsFor(item)
        val first = candidates[materialPick % candidates.size]
        val materials = if (secondMaterialRoll < 25 && candidates.size > 1) {
            listOf(first, candidates[(materialPick + 1) % candidates.size])
        } else {
            listOf(first)
        }

        return ItemDetails(
            price = price,
            purchaseDate = today.minusDays((minAgeDays + dateRoll).toLong()).toString(),
            brand = if (brandRoll < 80) BRANDS[brandPick] else null,
            materials = materials,
            patterns = listOf(patternFor(item.name) ?: if (patternRoll < 70) "Solid" else PRINTS[patternPick]),
            favorite = favoriteRoll < 12,
            dirty = dirtyRoll < 15,
            retired = when {
                retiredRoll < 2 -> "Donated"
                retiredRoll < 3 -> "Sold"
                else -> null
            },
        )
    }

    private fun materialsFor(item: DemoItem): List<String> = when (item.subcategory) {
        "Jeans" -> listOf("Denim")
        "Sneakers" -> listOf("Mesh", "Leather", "Faux Leather")
        else -> MATERIALS[item.category] ?: listOf("Cotton", "Polyester")
    }

    companion object {
        /** Purchase dates fall up to two years back. */
        const val MAX_AGE_DAYS = 730

        /** Invented, so no real brand names land in the demo data. */
        val BRANDS = listOf(
            "Northline", "Harbor & Co", "Atlas Denim", "Fernwood", "Kestrel",
            "Maple Row", "Tidewater", "Copperleaf", "Ridgeway", "Linden",
        )

        /** Whole-currency price ranges per category; every price ends in .99. */
        val PRICE_RANGES: Map<String, Pair<Int, Int>> = mapOf(
            "Tops" to (12 to 60), "Bottoms" to (20 to 90), "Outerwear" to (40 to 220),
            "Dresses & Jumpsuits" to (25 to 150), "Footwear" to (30 to 160), "Accessories" to (8 to 120),
            "Bags" to (20 to 180), "Underwear & Intimates" to (5 to 30), "Swimwear" to (15 to 60),
        )

        /** Seeded material names per category (see `DatabaseSeeder`). */
        val MATERIALS: Map<String, List<String>> = mapOf(
            "Tops" to listOf("Cotton", "Polyester", "Linen", "Silk", "Jersey", "Rayon/Viscose"),
            "Bottoms" to listOf("Cotton", "Denim", "Polyester", "Wool", "Spandex/Elastane"),
            "Outerwear" to listOf("Wool", "Polyester", "Nylon", "Leather", "Fleece", "Cotton"),
            "Dresses & Jumpsuits" to listOf("Cotton", "Chiffon", "Satin", "Polyester", "Rayon/Viscose"),
            "Footwear" to listOf("Leather", "Suede", "Faux Leather", "Mesh"),
            "Accessories" to listOf("Leather", "Silk", "Cotton", "Other"),
            "Bags" to listOf("Leather", "Nylon", "Faux Leather", "Polyester"),
            "Underwear & Intimates" to listOf("Cotton", "Modal", "Spandex/Elastane"),
            "Swimwear" to listOf("Nylon", "Spandex/Elastane"),
        )

        /** Seeded non-solid patterns used when the product name gives no hint. */
        val PRINTS = listOf("Striped", "Plaid/Tartan", "Checkered", "Floral", "Geometric", "Graphic", "Polka Dot")

        /** A seeded pattern the product name names outright, e.g. "Green Check Shirt" → Checkered. */
        fun patternFor(name: String): String? {
            val lower = name.lowercase()
            return when {
                "check" in lower -> "Checkered"
                "stripe" in lower -> "Striped"
                "floral" in lower -> "Floral"
                "polka" in lower -> "Polka Dot"
                "camouflage" in lower || "camo " in lower -> "Camouflage"
                "animal print" in lower -> "Animal Print"
                "printed" in lower || "graphic" in lower -> "Graphic"
                else -> null
            }
        }

        /** Every material and pattern name this class can emit, for the seeded-name test. */
        fun allMaterials(): Set<String> = MATERIALS.values.flatten().toSet() + "Denim" + "Mesh" + "Leather" + "Faux Leather"

        fun allPatterns(): Set<String> = PRINTS.toSet() + "Solid" + "Camouflage" + "Animal Print"
    }
}
