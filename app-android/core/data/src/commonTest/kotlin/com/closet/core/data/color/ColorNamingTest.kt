package com.closet.core.data.color

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColorNamingTest {
    private val namer = ColorNamer(SeedShadesFixture.shades)

    private val heldOut: Map<String, List<String>> = mapOf(
        "Yellow" to listOf("#FAF0B4", "#F9E79F", "#F3E5AB", "#D4A017", "#C9A227"),
        "Tan" to listOf("#C9A97E", "#B5895A"),
        "Denim" to listOf("#5677A0", "#3E5F8A"),
        "Charcoal" to listOf("#333B40", "#404040"),
        "Navy" to listOf("#23304A"),
        "Grey" to listOf("#C8C8C8", "#9A9A9A"),
        "Burgundy" to listOf("#722F37"),
        "Teal" to listOf("#367588"),
        "Olive" to listOf("#6B8E23"),
        "White" to listOf("#FAF9F6"),
        "Cream" to listOf("#FFF8E7"),
        "Beige" to listOf("#E1D3B8"),
        "Brown" to listOf("#4B3621"),
        "Purple" to listOf("#C3A6D9"),
        "Orange" to listOf("#F88379"),
        "Pink" to listOf("#FF69B4"),
        "Black" to listOf("#141414"),
    )

    @Test
    fun `held out colours get the expected names`() {
        val failures = mutableListOf<String>()
        for ((name, hexes) in heldOut) {
            val expected = SeedShadesFixture.idOf(name)
            for (hex in hexes) {
                val got = namer.nearest(Oklab.fromHex(hex))
                if (got != expected) {
                    val gotName = got?.let { SeedShadesFixture.names[(it - 1).toInt()] }
                    failures += "$hex expected $name but was $gotName"
                }
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `empty namer returns null`() {
        assertNull(ColorNamer(emptyList()).nearest(Oklab.fromHex("#FF0000")))
    }

    @Test
    fun `ties go to the lower colour id`() {
        val tied = ColorNamer(listOf(ReferenceShade(7, "#123456"), ReferenceShade(3, "#123456")))
        assertEquals(3L, tied.nearest(Oklab.fromHex("#123456")))
    }
}
