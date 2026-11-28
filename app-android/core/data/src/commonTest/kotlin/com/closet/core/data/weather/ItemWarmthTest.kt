package com.closet.core.data.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemWarmthTest {

    private fun warmth(category: String?, sub: String?, vararg materials: String) =
        ItemWarmth.derivedWarmth(category, sub, materials.toSet())

    private fun rain(category: String?, sub: String?, vararg materials: String) =
        ItemWarmth.derivedRainReady(category, sub, materials.toSet())

    @Test
    fun `a tee derives none`() {
        assertEquals(WarmthLevel.NONE, warmth("Tops", "T-Shirt", "Cotton"))
    }

    @Test
    fun `a wool sweater derives warm`() {
        assertEquals(WarmthLevel.WARM, warmth("Tops", "Sweater", "Wool"))
    }

    @Test
    fun `a coat derives warm and a wool coat heavy`() {
        assertEquals(WarmthLevel.WARM, warmth("Outerwear", "Coat"))
        assertEquals(WarmthLevel.HEAVY, warmth("Outerwear", "Coat", "Wool"))
    }

    @Test
    fun `a raincoat is rain ready`() {
        assertTrue(rain("Outerwear", "Raincoat"))
    }

    @Test
    fun `a nylon jacket is rain ready and a cotton jacket is not`() {
        assertTrue(rain("Outerwear", "Jacket", "Nylon"))
        assertFalse(rain("Outerwear", "Jacket", "Cotton"))
    }

    @Test
    fun `boots are rain ready`() {
        assertTrue(rain("Footwear", "Boots"))
    }

    @Test
    fun `material modifiers stay within the scale`() {
        assertEquals(WarmthLevel.HEAVY, warmth("Outerwear", "Coat", "Wool", "Cashmere", "Fleece"))
        assertEquals(WarmthLevel.NONE, warmth("Tops", "T-Shirt", "Linen", "Mesh"))
    }

    @Test
    fun `an item with no subcategory or materials derives none and not rain ready`() {
        assertEquals(WarmthLevel.NONE, warmth(null, null))
        assertFalse(rain(null, null))
    }

    @Test
    fun `linen lowers a light item to none`() {
        assertEquals(WarmthLevel.NONE, warmth("Outerwear", "Vest", "Linen"))
    }

    @Test
    fun `wool does not warm an item that starts at none`() {
        assertEquals(WarmthLevel.NONE, warmth("Tops", "T-Shirt", "Wool"))
    }

    @Test
    fun `suede boots are not rain ready`() {
        assertFalse(rain("Footwear", "Boots", "Suede"))
    }

    @Test
    fun `a nylon jacket with cashmere is not rain ready`() {
        assertFalse(rain("Outerwear", "Jacket", "Nylon", "Cashmere"))
    }

    @Test
    fun `a raincoat stays rain ready whatever the material`() {
        assertTrue(rain("Outerwear", "Raincoat", "Silk", "Linen"))
    }

    @Test
    fun `an outerwear item without a known subcategory derives light`() {
        assertEquals(WarmthLevel.LIGHT, warmth("Outerwear", "Poncho"))
        assertEquals(WarmthLevel.LIGHT, warmth("Outerwear", null))
    }

    @Test
    fun `several warming materials raise warmth only once`() {
        assertEquals(WarmthLevel.WARM, warmth("Outerwear", "Jacket", "Wool", "Cashmere"))
    }

    @Test
    fun `wool and linen cancel out`() {
        assertEquals(WarmthLevel.MEDIUM, warmth("Outerwear", "Jacket", "Wool", "Linen"))
    }

    @Test
    fun `warmth levels keep their stored order`() {
        assertEquals(listOf("NONE", "LIGHT", "MEDIUM", "WARM", "HEAVY"), WarmthLevel.entries.map { it.name })
    }
}
