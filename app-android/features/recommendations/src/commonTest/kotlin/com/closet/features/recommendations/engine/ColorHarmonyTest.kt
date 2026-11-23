package com.closet.features.recommendations.engine

import com.closet.core.data.DatabaseSeeder
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ColorHarmonyTest {

    private fun seeded(name: String) = DatabaseSeeder.SEEDED_COLORS.single { it.name == name }

    /** A tag of the seeded colour [name], at its seeded hex, family and neutrality. */
    private fun tag(name: String, share: Double? = 1.0): ColorHex = seeded(name).let {
        ColorHex(hex = it.hex, family = it.family, share = share, wearsAsNeutral = it.wearsAsNeutral)
    }

    private var nextId = 1L

    private fun itemOf(vararg tags: ColorHex) = item(
        id = nextId++,
        colorFamilies = tags.map { it.family }.toSet(),
        colorHexes = tags.toList(),
    )

    private fun pairOf(a: String, b: String) = ColorHarmony.factors(listOf(itemOf(tag(a)), itemOf(tag(b))))

    // =========================================================================
    // Bands
    // =========================================================================

    @Test
    fun `the re-picked complements score 1_05`() {
        for ((a, b) in listOf(
            "Red" to "Forest Green",
            "Yellow" to "Purple",
            "Orange" to "Royal Blue",
            "Navy" to "Orange",  // Navy is Cool, not neutral
        )) {
            val factors = pairOf(a, b)
            assertEquals(1.05, factors.combined, 1e-9, "$a + $b")
            assertEquals(1.05, factors.hueMultiplier, 1e-9, "$a + $b")
            assertEquals(1.0, factors.familyMultiplier, 1e-9, "$a + $b")
        }
    }

    @Test
    fun `a same-family pair takes the family rule when it beats the hue`() {
        // Red + Burgundy, both Warm: analogous (1_05) loses to the same-family 1_10.
        val factors = pairOf("Red", "Burgundy")
        assertEquals(1.10, factors.combined, 1e-9)
        assertEquals(1.10, factors.familyMultiplier, 1e-9)
        assertEquals(1.0, factors.hueMultiplier, 1e-9)
    }

    @Test
    fun `the hue bands are analogous up to 60 and complementary from 150`() {
        assertEquals(1.0, ColorHarmony.hueMultiplier(90.0))
        assertEquals(1.05, ColorHarmony.hueMultiplier(0.0))
        assertEquals(1.05, ColorHarmony.hueMultiplier(60.0))
        assertEquals(1.0, ColorHarmony.hueMultiplier(60.5))
        assertEquals(1.0, ColorHarmony.hueMultiplier(149.5))
        assertEquals(1.05, ColorHarmony.hueMultiplier(150.0))
        assertEquals(1.05, ColorHarmony.hueMultiplier(180.0))
    }

    @Test
    fun `there is no clash band`() {
        // About 85 to 98 degrees apart, which the old 80-100 band scored 0_90.
        assertEquals(1.0, pairOf("Forest Green", "Yellow").combined, 1e-9)
        assertEquals(1.0, pairOf("Royal Blue", "Pink").combined, 1e-9)
    }

    // =========================================================================
    // Neutrals
    // =========================================================================

    @Test
    fun `an all-neutral outfit has no colour effect`() {
        val items = listOf(itemOf(tag("Grey")), itemOf(tag("Denim")), itemOf(tag("Brown")))
        assertEquals(ColorHarmonyFactors(1.0, 1.0), ColorHarmony.factors(items))
        // Lavender is a Purple shade below C_FLOOR: neutral without wears_as_neutral.
        assertNull(ColorHarmony.harmonyColor(itemOf(ColorHex("#E6E6FA", "Cool", 1.0, wearsAsNeutral = false))))
    }

    @Test
    fun `a wears-as-neutral tag never becomes the harmony colour`() {
        // Denim (chroma above C_FLOOR, wears as neutral) has the larger share; Red still leads.
        val color = assertNotNull(ColorHarmony.harmonyColor(itemOf(tag("Denim", 0.6), tag("Red", 0.4))))
        assertEquals("Warm", color.family)
        assertEquals(0.4, color.share, 1e-12)
    }

    @Test
    fun `a neutral item sits out and leaves the other pairs alone`() {
        val withGrey = ColorHarmony.factors(listOf(itemOf(tag("Red")), itemOf(tag("Forest Green")), itemOf(tag("Grey"))))
        assertEquals(1.05, withGrey.combined, 1e-9)
    }

    // =========================================================================
    // Harmony colour and shares
    // =========================================================================

    @Test
    fun `the largest-share colour is the harmony colour and ties go to the earlier tag`() {
        assertEquals("Cool", ColorHarmony.harmonyColor(itemOf(tag("Red", 0.3), tag("Forest Green", 0.7)))?.family)
        assertEquals("Warm", ColorHarmony.harmonyColor(itemOf(tag("Red", 0.5), tag("Forest Green", 0.5)))?.family)
        assertEquals("Cool", ColorHarmony.harmonyColor(itemOf(tag("Forest Green", 0.5), tag("Red", 0.5)))?.family)
    }

    @Test
    fun `a 30 percent accent counts to the power 0_3`() {
        val greyTeeRedPrint = itemOf(tag("Grey", 0.7), tag("Red", 0.3))
        val greenChinos = itemOf(tag("Forest Green", 1.0))
        val factors = ColorHarmony.factors(listOf(greyTeeRedPrint, greenChinos))
        assertEquals(1.05.pow(0.3), factors.combined, 1e-9)
    }

    @Test
    fun `shares are clamped and missing shares split evenly`() {
        val split = assertNotNull(ColorHarmony.harmonyColor(itemOf(tag("Red", null), tag("Grey", null))))
        assertEquals(0.5, split.share, 1e-12)
        // One missing share is enough to split every tag.
        val oneMissing = assertNotNull(ColorHarmony.harmonyColor(itemOf(tag("Red", 0.9), tag("Grey", null))))
        assertEquals(0.5, oneMissing.share, 1e-12)
        assertEquals(1.0, ColorHarmony.harmonyColor(itemOf(tag("Red", 1.7)))?.share)
        assertEquals(0.0, ColorHarmony.harmonyColor(itemOf(tag("Red", -0.2)))?.share)
        // NaN counts as missing.
        val nan = assertNotNull(ColorHarmony.harmonyColor(itemOf(tag("Red", Double.NaN), tag("Grey", 0.5))))
        assertEquals(0.5, nan.share, 1e-12)
        // Clamped shares weight the pair: 1_7 acts as 1_0.
        val clamped = ColorHarmony.factors(listOf(itemOf(tag("Red", 1.7)), itemOf(tag("Forest Green", 1.0))))
        assertEquals(1.05, clamped.combined, 1e-9)
    }

    // =========================================================================
    // Malformed hexes (Review Focus 1)
    // =========================================================================

    @Test
    fun `a malformed measured hex falls back to the name hex`() {
        val red = seeded("Red")
        val plain = assertNotNull(ColorHarmony.harmonyColor(itemOf(tag("Red"))))
        val fallback = assertNotNull(
            ColorHarmony.harmonyColor(itemOf(ColorHex("#ZZ1E3A", "Warm", 1.0, fallbackHex = red.hex))),
        )
        assertEquals(plain, fallback)
        // A tag with no parseable hex at all is skipped, and nothing throws.
        val broken = ColorHex("not a hex", "Warm", 0.9, fallbackHex = "#12")
        assertEquals("Cool", ColorHarmony.harmonyColor(itemOf(broken, tag("Forest Green", 0.1)))?.family)
        assertNull(ColorHarmony.harmonyColor(itemOf(broken)))
        assertNull(ColorHarmony.harmonyColor(itemOf(ColorHex("", "Warm"), ColorHex("#ZZ00FFFF", "Warm"))))
        assertEquals(ColorHarmonyFactors(1.0, 1.0), ColorHarmony.factors(listOf(itemOf(broken), itemOf(tag("Red")))))
    }

    @Test
    fun `lowercase, padded and ARGB hexes parse`() {
        val plain = assertNotNull(ColorHarmony.harmonyColor(itemOf(tag("Red"))))
        assertEquals(plain, ColorHarmony.harmonyColor(itemOf(ColorHex(" #c41e3a ", "Warm", 1.0))))
        assertEquals(plain, ColorHarmony.harmonyColor(itemOf(ColorHex("#FFC41E3A", "Warm", 1.0))))
    }
}
