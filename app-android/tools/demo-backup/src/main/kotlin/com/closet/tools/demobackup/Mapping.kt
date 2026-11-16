package com.closet.tools.demobackup

/**
 * Dataset vocabulary to the app's seeded lookup names (see `DatabaseSeeder`). Every value here
 * must match a seeded row exactly; `MappingTest` checks the category/subcategory pairs.
 */
object Mapping {

    data class Target(val category: String, val subcategory: String)

    private val genders = setOf("Men", "Women", "Unisex")

    private fun targets(category: String, vararg pairs: Pair<String, String>): Map<String, Target> =
        pairs.associate { (articleType, sub) -> articleType to Target(category, sub) }

    private val articleTypes: Map<String, Target> =
        targets(
            "Tops",
            "Tshirts" to "T-Shirt", "Shirts" to "Shirt", "Tops" to "Blouse", "Tunics" to "Blouse",
            "Sweatshirts" to "Sweatshirt", "Sweaters" to "Sweater",
        ) + targets(
            "Bottoms",
            "Jeans" to "Jeans", "Trousers" to "Trousers/Slacks", "Capris" to "Trousers/Slacks",
            "Shorts" to "Shorts", "Skirts" to "Skirt", "Leggings" to "Leggings", "Jeggings" to "Leggings",
            "Track Pants" to "Joggers/Sweatpants",
        ) + targets(
            "Outerwear",
            "Jackets" to "Jacket", "Rain Jacket" to "Raincoat", "Blazers" to "Blazer", "Waistcoat" to "Vest",
        ) + targets(
            "Dresses & Jumpsuits",
            "Dresses" to "Dress", "Jumpsuit" to "Jumpsuit", "Rompers" to "Romper",
        ) + targets(
            "Footwear",
            "Casual Shoes" to "Sneakers", "Sports Shoes" to "Sneakers",
            "Formal Shoes" to "Dress Shoes", "Heels" to "Dress Shoes", "Flats" to "Dress Shoes",
            "Sandals" to "Sandals", "Flip Flops" to "Sandals", "Sports Sandals" to "Sandals",
        ) + targets(
            "Accessories",
            "Belts" to "Belt", "Caps" to "Hat/Cap", "Scarves" to "Scarf", "Stoles" to "Scarf",
            "Mufflers" to "Scarf", "Sunglasses" to "Sunglasses", "Watches" to "Watch",
            "Earrings" to "Jewelry", "Pendant" to "Jewelry", "Necklace and Chains" to "Jewelry",
            "Ring" to "Jewelry", "Bracelet" to "Jewelry", "Bangle" to "Jewelry",
            "Ties" to "Tie", "Cufflinks" to "Cufflinks",
        ) + targets(
            "Bags",
            "Backpacks" to "Backpack", "Rucksacks" to "Backpack", "Handbags" to "Tote",
            "Clutches" to "Crossbody", "Messenger Bag" to "Crossbody", "Duffel Bag" to "Duffel",
        ) + targets(
            "Underwear & Intimates",
            "Briefs" to "Underwear", "Trunk" to "Underwear", "Boxers" to "Underwear",
            "Bra" to "Bra/Bralette", "Socks" to "Socks", "Stockings" to "Tights", "Tights" to "Tights",
        ) + targets(
            "Swimwear",
            "Swimwear" to "Bikini/Trunks",
        )

    /** Seeded category names in sort order, for stable summary and sampling order. */
    val categoryOrder: List<String> = listOf(
        "Tops", "Bottoms", "Outerwear", "Dresses & Jumpsuits", "Footwear",
        "Accessories", "Bags", "Underwear & Intimates", "Swimwear",
    )

    private val colors: Map<String, String> = buildMap {
        fun to(color: String, vararg names: String) = names.forEach { put(it, color) }
        to("Black", "Black")
        to("White", "White", "Off White")
        to("Grey", "Grey", "Grey Melange", "Charcoal", "Steel", "Silver")
        to("Beige", "Beige", "Cream", "Skin", "Nude")
        to("Tan", "Khaki", "Tan")
        to("Navy", "Navy Blue")
        to("Royal Blue", "Blue")
        to("Sky Blue", "Turquoise Blue", "Teal")
        to("Red", "Red")
        to("Burgundy", "Maroon", "Burgundy")
        to("Forest Green", "Green", "Sea Green")
        to("Mint", "Lime Green", "Fluorescent Green")
        to("Olive", "Olive")
        to("Pink", "Pink", "Rose", "Magenta", "Peach")
        to("Orange", "Orange", "Rust", "Copper")
        to("Yellow", "Yellow", "Mustard", "Gold")
        to("Purple", "Purple", "Lavender", "Mauve")
        to("Brown", "Brown", "Coffee Brown", "Mushroom Brown", "Bronze", "Taupe")
    }

    private val seasons = setOf("Spring", "Summer", "Fall", "Winter")

    private val occasions = mapOf(
        "Casual" to "Casual",
        "Smart Casual" to "Casual",
        "Formal" to "Work/Business",
        "Sports" to "Athletic",
        "Party" to "Special Occasion",
        "Ethnic" to "Special Occasion",
        "Travel" to "Vacation",
        "Home" to "Loungewear",
    )

    fun genderKept(gender: String): Boolean = gender in genders

    /** The seeded category and subcategory for [articleType], or null when it has no mapping. */
    fun target(articleType: String): Target? = articleTypes[articleType]

    /** Every distinct category/subcategory pair the tool can produce. */
    fun allTargets(): Set<Target> = articleTypes.values.toSet()

    /** The seeded colour for [baseColour]; null for Multi, Metallic, NA, empty and anything unlisted. */
    fun color(baseColour: String): String? = colors[baseColour]

    fun season(season: String): String? = season.takeIf { it in seasons }

    fun occasion(usage: String): String? = occasions[usage]
}
