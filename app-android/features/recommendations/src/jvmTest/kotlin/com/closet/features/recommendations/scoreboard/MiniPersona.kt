package com.closet.features.recommendations.scoreboard

import java.time.DayOfWeek

/** A small hand-written menswear persona (14 items, 8 outfits, 3 saved) for harness tests. */
object MiniPersona {
    private val work = setOf("Work/Business")
    private val casual = setOf("Casual")
    private val allSeasons = setOf("All Season")
    private val warm = setOf("Spring", "Summer", "Fall")
    private val cold = setOf("Fall", "Winter")

    private fun item(
        key: String, description: String, category: String, subcategory: String?, hex: String,
        material: String, seasons: Set<String>, occasions: Set<String>,
        warmth: Int = 1, rainReady: Boolean = false, formality: Int = 2,
    ) = FixtureItem(
        key = key, description = description, category = category, subcategory = subcategory,
        colors = listOf(hex to 1.0), materials = setOf(material), seasons = seasons,
        occasions = occasions, warmth = warmth, rainReady = rainReady, formality = formality,
    )

    fun build(): Persona {
        val items = listOf(
            item("white-oxford", "White oxford shirt", "Tops", "Shirt", "#F4F4F2", "Cotton", allSeasons, work, 1, false, 4),
            item("blue-tee", "Blue t-shirt", "Tops", "T-Shirt", "#4169E1", "Cotton", warm, casual, 0, false, 1),
            item("grey-sweater", "Grey wool sweater", "Tops", "Sweater", "#808080", "Wool", cold, work + casual, 3, false, 3),
            item("navy-chinos", "Navy chinos", "Bottoms", "Chinos", "#1F2A44", "Cotton", allSeasons, work + casual, 1, false, 3),
            item("dark-jeans", "Dark jeans", "Bottoms", "Jeans", "#000080", "Denim", allSeasons, casual, 1, false, 2),
            item("wool-coat", "Charcoal wool coat", "Outerwear", "Coat", "#2B2B2B", "Wool", setOf("Winter"), work + casual, 3, true, 4),
            item("light-jacket", "Tan light jacket", "Outerwear", "Jacket", "#D9C8A9", "Cotton", setOf("Spring", "Fall"), casual, 2, false, 2),
            item("dress-shoes", "Black dress shoes", "Footwear", "Dress Shoes", "#000000", "Leather", allSeasons, work, 1, false, 4),
            item("rain-boots", "Brown waterproof boots", "Footwear", "Boots", "#6E4B2A", "Leather", allSeasons, casual + work, 2, true, 3),
            item("brown-belt", "Brown leather belt", "Accessories", "Belt", "#6E4B2A", "Leather", allSeasons, work + casual, 0, false, 3),
            item("gym-shorts", "Black athletic shorts", "Activewear", "Athletic Shorts", "#1A1A1A", "Polyester", setOf("Summer"), setOf("Athletic"), 0, false, 1),
            item("white-polo", "White polo", "Tops", "Polo", "#FFFFFF", "Cotton", warm, work + casual, 0, false, 3),
            item("khaki-shorts", "Beige shorts", "Bottoms", "Shorts", "#E8DCC4", "Cotton", setOf("Summer"), casual, 0, false, 2),
            item("sneakers", "White sneakers", "Footwear", "Sneakers", "#FFFFFF", "Leather", allSeasons, casual, 1, false, 1),
        )
        fun outfit(key: String, keys: List<String>, occ: Set<String>, lo: Double, hi: Double, rain: Boolean, saved: Boolean) =
            FixtureOutfit(key, keys, occ, lo, hi, rain, 1.0, saved)
        val outfits = listOf(
            outfit("office-winter", listOf("white-oxford", "grey-sweater", "navy-chinos", "dress-shoes", "brown-belt", "wool-coat"), work, -10.0, 5.0, true, true),
            outfit("office-mild", listOf("white-oxford", "navy-chinos", "dress-shoes", "brown-belt", "light-jacket"), work, 8.0, 18.0, false, true),
            outfit("office-warm", listOf("white-polo", "navy-chinos", "dress-shoes", "brown-belt"), work, 18.0, 32.0, false, true),
            outfit("weekend-cold", listOf("grey-sweater", "dark-jeans", "rain-boots", "wool-coat"), casual, -5.0, 8.0, true, false),
            outfit("weekend-mild", listOf("blue-tee", "dark-jeans", "sneakers", "light-jacket"), casual, 8.0, 20.0, false, false),
            outfit("weekend-warm", listOf("blue-tee", "khaki-shorts", "sneakers"), casual, 20.0, 35.0, false, false),
            outfit("rainy-day", listOf("white-polo", "dark-jeans", "rain-boots", "light-jacket"), casual, 5.0, 18.0, true, false),
            outfit("gym", listOf("gym-shorts", "blue-tee", "sneakers"), setOf("Athletic"), 10.0, 35.0, false, false),
        )
        return Persona(
            name = "mini",
            seed = 1L,
            items = items,
            outfits = outfits,
            weekSchedule = List(5) { DayKind.OFFICE } + DayKind.WEEKEND + DayKind.HOME,
            climate = Climate(
                monthlyHighC = listOf(2.0, 4.0, 9.0, 15.0, 21.0, 26.0, 29.0, 28.0, 23.0, 16.0, 9.0, 4.0),
                monthlyLowC = listOf(-5.0, -4.0, 0.0, 5.0, 11.0, 16.0, 19.0, 18.0, 13.0, 7.0, 2.0, -2.0),
                monthlyRainChance = listOf(0.3, 0.3, 0.35, 0.35, 0.35, 0.3, 0.3, 0.3, 0.3, 0.3, 0.35, 0.35),
                windyChance = 0.2,
            ),
            wearsBeforeWash = 3,
            laundryDay = DayOfWeek.SUNDAY,
            swapChance = 0.1,
            homeNoShoesChance = 0.6,
        )
    }
}
