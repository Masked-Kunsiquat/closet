package com.closet.tools.demobackup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MappingTest {

    private fun target(articleType: String) = Mapping.target(articleType)?.let { it.category to it.subcategory }

    @Test
    fun articleTypesMapToSeededCategoryAndSubcategory() {
        assertEquals("Tops" to "T-Shirt", target("Tshirts"))
        assertEquals("Tops" to "Shirt", target("Shirts"))
        assertEquals("Tops" to "Blouse", target("Tunics"))
        assertEquals("Tops" to "Sweatshirt", target("Sweatshirts"))
        assertEquals("Tops" to "Sweater", target("Sweaters"))
        assertEquals("Bottoms" to "Jeans", target("Jeans"))
        assertEquals("Bottoms" to "Trousers/Slacks", target("Capris"))
        assertEquals("Bottoms" to "Leggings", target("Jeggings"))
        assertEquals("Bottoms" to "Joggers/Sweatpants", target("Track Pants"))
        assertEquals("Outerwear" to "Raincoat", target("Rain Jacket"))
        assertEquals("Outerwear" to "Vest", target("Waistcoat"))
        assertEquals("Dresses & Jumpsuits" to "Romper", target("Rompers"))
        assertEquals("Footwear" to "Sneakers", target("Sports Shoes"))
        assertEquals("Footwear" to "Dress Shoes", target("Heels"))
        assertEquals("Footwear" to "Sandals", target("Flip Flops"))
        assertEquals("Accessories" to "Hat/Cap", target("Caps"))
        assertEquals("Accessories" to "Scarf", target("Mufflers"))
        assertEquals("Accessories" to "Jewelry", target("Necklace and Chains"))
        assertEquals("Accessories" to "Cufflinks", target("Cufflinks"))
        assertEquals("Bags" to "Tote", target("Handbags"))
        assertEquals("Bags" to "Crossbody", target("Messenger Bag"))
        assertEquals("Underwear & Intimates" to "Tights", target("Stockings"))
        assertEquals("Swimwear" to "Bikini/Trunks", target("Swimwear"))
    }

    @Test
    fun unknownArticleTypeIsSkipped() {
        assertNull(Mapping.target("Perfume and Body Mist"))
        assertNull(Mapping.target(""))
    }

    @Test
    fun everyMappedTargetExistsInTheSeededLookupTables() {
        // The seeded names, copied from DatabaseSeeder; a typo here would make an insert fail.
        val seeded = mapOf(
            "Tops" to listOf("T-Shirt", "Tank Top", "Blouse", "Shirt", "Polo", "Sweater", "Hoodie", "Sweatshirt", "Cardigan", "Bodysuit"),
            "Bottoms" to listOf("Jeans", "Trousers/Slacks", "Chinos", "Shorts", "Skirt", "Leggings", "Joggers/Sweatpants"),
            "Outerwear" to listOf("Jacket", "Coat", "Blazer", "Vest", "Raincoat"),
            "Dresses & Jumpsuits" to listOf("Dress", "Romper", "Jumpsuit"),
            "Footwear" to listOf("Sneakers", "Boots", "Sandals", "Dress Shoes", "Slippers"),
            "Accessories" to listOf("Belt", "Hat/Cap", "Scarf", "Sunglasses", "Watch", "Jewelry", "Tie", "Cufflinks"),
            "Bags" to listOf("Backpack", "Tote", "Crossbody", "Duffel"),
            "Underwear & Intimates" to listOf("Underwear", "Bra/Bralette", "Socks", "Tights"),
            "Swimwear" to listOf("One-Piece", "Bikini/Trunks", "Rash Guard"),
        )
        for (t in Mapping.allTargets()) {
            assertTrue(seeded[t.category]?.contains(t.subcategory) == true, "unseeded target: $t")
        }
    }

    @Test
    fun baseColoursMapToSeededColors() {
        assertEquals("Grey", Mapping.color("Grey Melange"))
        assertEquals("White", Mapping.color("Off White"))
        assertEquals("Navy", Mapping.color("Navy Blue"))
        assertEquals("Royal Blue", Mapping.color("Blue"))
        assertEquals("Sky Blue", Mapping.color("Teal"))
        assertEquals("Burgundy", Mapping.color("Maroon"))
        assertEquals("Mint", Mapping.color("Fluorescent Green"))
        assertEquals("Tan", Mapping.color("Khaki"))
        assertEquals("Beige", Mapping.color("Nude"))
        assertEquals("Brown", Mapping.color("Taupe"))
        assertEquals("Yellow", Mapping.color("Gold"))
    }

    @Test
    fun unmappedColoursKeepTheItemWithNoColor() {
        assertNull(Mapping.color("Multi"))
        assertNull(Mapping.color("Metallic"))
        assertNull(Mapping.color("NA"))
        assertNull(Mapping.color(""))
    }

    @Test
    fun seasonsMapToTheSameNamesAndEmptyIsNone() {
        assertEquals("Fall", Mapping.season("Fall"))
        assertEquals("Winter", Mapping.season("Winter"))
        assertNull(Mapping.season(""))
    }

    @Test
    fun usageMapsToOccasions() {
        assertEquals("Casual", Mapping.occasion("Smart Casual"))
        assertEquals("Work/Business", Mapping.occasion("Formal"))
        assertEquals("Athletic", Mapping.occasion("Sports"))
        assertEquals("Special Occasion", Mapping.occasion("Ethnic"))
        assertEquals("Vacation", Mapping.occasion("Travel"))
        assertEquals("Loungewear", Mapping.occasion("Home"))
        assertNull(Mapping.occasion("NA"))
        assertNull(Mapping.occasion(""))
    }

    @Test
    fun onlyMenWomenAndUnisexAreKept() {
        assertTrue(Mapping.genderKept("Men"))
        assertTrue(Mapping.genderKept("Women"))
        assertTrue(Mapping.genderKept("Unisex"))
        assertFalse(Mapping.genderKept("Boys"))
        assertFalse(Mapping.genderKept("Girls"))
    }
}
