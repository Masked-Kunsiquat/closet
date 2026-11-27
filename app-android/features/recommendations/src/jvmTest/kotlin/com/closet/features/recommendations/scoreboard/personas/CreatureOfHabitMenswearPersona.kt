package com.closet.features.recommendations.scoreboard.personas

import com.closet.features.recommendations.scoreboard.Climate
import com.closet.features.recommendations.scoreboard.DayKind
import com.closet.features.recommendations.scoreboard.FixtureItem
import com.closet.features.recommendations.scoreboard.FixtureOutfit
import com.closet.features.recommendations.scoreboard.Persona
import java.time.DayOfWeek

/**
 * Curated scoreboard persona `creature-of-habit-menswear`: 16 items (2 hand-described), 11 outfits.
 *
 * Generated from local curation (#148). Descriptions are our own; colours were measured with the app's
 * `ColorExtractor` on garment crops of reference photos, except hand-described items, which carry a
 * hand-set colour. No source dataset content is included.
 */
object CreatureOfHabitMenswearPersona {
    fun build(): Persona = Persona(
        name = "creature-of-habit-menswear",
        seed = 303L,
        items = listOf(
            FixtureItem("blue-shirt", "Mid blue work shirt", "Tops", "Shirt",
                listOf("#7D97C1" to 0.547, "#C6D6EC" to 0.313),
                setOf("Cotton"), setOf("All Season"), setOf("Work/Business"), 0, false, 3),
            FixtureItem("white-shirt", "White work shirt", "Tops", "Shirt",
                listOf("#E6E4EA" to 0.95),
                setOf("Cotton"), setOf("All Season"), setOf("Work/Business"), 0, false, 4),
            FixtureItem("white-polo", "White polo shirt", "Tops", "Polo",
                listOf("#E5E4E9" to 0.903),
                setOf("Cotton"), setOf("Spring", "Summer", "Fall"), setOf("Casual"), 0, false, 2),
            FixtureItem("navy-tee", "Navy crew neck tee", "Tops", "T-Shirt",
                listOf("#3E424D" to 0.935),
                setOf("Cotton"), setOf("Spring", "Summer", "Fall"), setOf("Casual"), 0, false, 1),
            FixtureItem("beige-sweater", "Oatmeal half-zip sweater", "Tops", "Sweater",
                listOf("#EEE3DC" to 0.959),
                setOf("Wool"), setOf("Spring", "Fall", "Winter"), setOf("Casual", "Work/Business"), 1, false, 3),
            FixtureItem("tan-chinos", "Tan chinos", "Bottoms", "Chinos",
                listOf("#AD9676" to 0.717, "#8C7A5B" to 0.263),
                setOf("Cotton"), setOf("All Season"), setOf("Work/Business"), 0, false, 3),
            FixtureItem("grey-trousers", "Light grey trousers", "Bottoms", "Trousers/Slacks",
                listOf("#CBC5B6" to 0.647, "#A3A398" to 0.353),
                setOf("Polyester"), setOf("All Season"), setOf("Work/Business"), 0, false, 4),
            FixtureItem("navy-jeans", "Navy straight jeans", "Bottoms", "Jeans",
                listOf("#39434B" to 0.546, "#4E5E68" to 0.319),
                setOf("Denim"), setOf("All Season"), setOf("Casual"), 0, false, 2),
            FixtureItem("khaki-rain-jacket", "Khaki hooded rain jacket", "Outerwear", "Raincoat",
                listOf("#B49F78" to 0.48, "#988361" to 0.265, "#6B5A3F" to 0.254),
                setOf("Nylon"), setOf("Spring", "Summer", "Fall"), setOf("Casual", "Work/Business"), 1, true, 2),
            FixtureItem("grey-jacket", "Grey zip jacket", "Outerwear", "Jacket",
                listOf("#6D6C65" to 0.877),
                setOf("Polyester"), setOf("Spring", "Fall", "Winter"), setOf("Casual"), 2, false, 2),
            FixtureItem("navy-parka", "Navy padded waterproof parka", "Outerwear", "Coat",
                listOf("#1F2A44" to 1.0),
                setOf("Nylon"), setOf("Fall", "Winter"), setOf("Casual", "Work/Business"), 3, true, 2),
            FixtureItem("walking-boots", "Brown waterproof walking boots", "Footwear", "Boots",
                listOf("#6B4A2E" to 1.0),
                setOf("Leather"), setOf("All Season"), setOf("Casual", "Work/Business"), 0, true, 2),
            FixtureItem("tan-shoes", "Tan leather lace-ups", "Footwear", "Sneakers",
                listOf("#9F8762" to 0.597, "#3B393A" to 0.215),
                setOf("Leather"), setOf("All Season"), setOf("Casual", "Work/Business"), 0, false, 2),
            FixtureItem("white-sneakers", "White and green trainers", "Footwear", "Sneakers",
                listOf("#E3E0D4" to 0.527, "#CBC8BF" to 0.211),
                setOf("Leather"), setOf("All Season"), setOf("Casual"), 0, false, 1),
            FixtureItem("steel-watch", "Steel bracelet watch", "Accessories", "Watch",
                listOf("#DCDCDC" to 0.532, "#232226" to 0.209),
                setOf("Other"), setOf("All Season"), setOf("Work/Business"), 0, false, 3),
            FixtureItem("black-belt", "Black leather belt", "Accessories", "Belt",
                listOf("#202020" to 0.703, "#8E8C89" to 0.297),
                setOf("Leather"), setOf("All Season"), setOf("Work/Business"), 0, false, 3),
        ),
        outfits = listOf(
            FixtureOutfit("w-blue-chinos", listOf("blue-shirt", "tan-chinos", "tan-shoes", "black-belt", "steel-watch"),
                setOf("Work/Business"), 18.0, 24.0, false, 4.0, true),
            FixtureOutfit("w-white-grey", listOf("white-shirt", "grey-trousers", "tan-shoes", "black-belt", "steel-watch"),
                setOf("Work/Business"), 18.0, 24.0, false, 3.0, true),
            FixtureOutfit("w-sweater", listOf("blue-shirt", "beige-sweater", "tan-chinos", "tan-shoes", "steel-watch"),
                setOf("Work/Business"), 12.0, 16.0, false, 3.0, true),
            FixtureOutfit("w-rain-sweater", listOf("white-shirt", "beige-sweater", "grey-trousers", "khaki-rain-jacket", "walking-boots"),
                setOf("Work/Business"), 6.0, 16.0, true, 3.0, true),
            FixtureOutfit("w-rain", listOf("blue-shirt", "tan-chinos", "khaki-rain-jacket", "walking-boots", "steel-watch"),
                setOf("Work/Business"), 12.0, 20.0, true, 2.0, true),
            FixtureOutfit("w-parka", listOf("white-shirt", "beige-sweater", "grey-trousers", "navy-parka", "walking-boots"),
                setOf("Work/Business"), -4.0, 8.0, true, 3.0, true),
            FixtureOutfit("c-polo", listOf("white-polo", "navy-jeans", "white-sneakers"),
                setOf("Casual"), 18.0, 28.0, false, 3.0, true),
            FixtureOutfit("c-jacket", listOf("navy-tee", "navy-jeans", "grey-jacket", "white-sneakers"),
                setOf("Casual"), 6.0, 16.0, false, 2.0, true),
            FixtureOutfit("c-rain", listOf("beige-sweater", "navy-jeans", "khaki-rain-jacket", "walking-boots"),
                setOf("Casual"), 6.0, 14.0, true, 2.0, true),
            FixtureOutfit("c-parka", listOf("navy-tee", "beige-sweater", "navy-jeans", "navy-parka", "walking-boots"),
                setOf("Casual"), -4.0, 8.0, true, 2.0, true),
            FixtureOutfit("c-tee", listOf("navy-tee", "navy-jeans", "tan-shoes"),
                setOf("Casual"), 18.0, 28.0, false, 1.0, false),
        ),
        weekSchedule = listOf(DayKind.OFFICE, DayKind.OFFICE, DayKind.OFFICE, DayKind.OFFICE, DayKind.OFFICE, DayKind.ERRANDS, DayKind.ERRANDS),
        climate = Climate(
            monthlyHighC = listOf(8.0, 9.0, 12.0, 15.0, 18.0, 21.0, 24.0, 24.0, 21.0, 16.0, 11.0, 8.0),
            monthlyLowC = listOf(2.0, 2.0, 4.0, 6.0, 9.0, 12.0, 14.0, 14.0, 11.0, 8.0, 5.0, 3.0),
            monthlyRainChance = listOf(0.55, 0.5, 0.5, 0.45, 0.4, 0.3, 0.2, 0.2, 0.3, 0.45, 0.55, 0.55),
            windyChance = 0.2,
        ),
        wearsBeforeWash = 3,
        laundryDay = DayOfWeek.SUNDAY,
        swapChance = 0.03,
        homeNoShoesChance = 0.0,
    )
}
