package com.closet.tools.demobackup

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeasureColorsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Writes a 40x40 white image as `<name>.jpg` (PNG bytes; ImageIO sniffs the content). */
    private fun writeImage(name: String, draw: (Graphics2D) -> Unit) {
        val image = BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 40, 40)
        draw(g)
        g.dispose()
        ImageIO.write(image, "png", File(tmp.root, "$name.jpg"))
    }

    /** White border background with a centred navy and red block, so the border mode drops the white. */
    private fun writeTwoColour(name: String) = writeImage(name) { g ->
        g.color = Color(0x1F, 0x2A, 0x5C)
        g.fillRect(8, 8, 12, 24)
        g.color = Color(0xC0, 0x1E, 0x1E)
        g.fillRect(20, 8, 12, 24)
    }

    @Test
    fun `a two colour image measures both colours with their shares`() {
        writeTwoColour("a")
        val m = MeasureColors.measure(tmp.root, listOf("a")).getValue("a")
        assertEquals("ok", m.status)
        assertEquals(2, m.colors.size)
        m.colors.forEach { assertEquals(0.5, it.share, 0.05) }
        assertTrue(m.colors.all { it.hex.matches(Regex("#[0-9A-F]{6}")) })
    }

    @Test
    fun `unreadable or colourless images are reported not skipped`() {
        File(tmp.root, "bad.jpg").writeText("not an image")
        // fully transparent: the alpha mask leaves no item pixels, so no colour (an all-white
        // photo would fall back to the centre box and read as white)
        ImageIO.write(BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB), "png", File(tmp.root, "blank.jpg"))
        val out = MeasureColors.measure(tmp.root, listOf("bad", "missing", "blank"))
        assertEquals(listOf("bad", "missing", "blank"), out.keys.toList())
        assertEquals("unreadable", out.getValue("bad").status)
        assertEquals("unreadable", out.getValue("missing").status)
        assertEquals("no-colours", out.getValue("blank").status)
        assertTrue(out.getValue("blank").colors.isEmpty())
    }

    @Test
    fun `output keeps input order`() {
        listOf("z", "m", "a").forEach { writeTwoColour(it) }
        val out = MeasureColors.measure(tmp.root, listOf("z", "m", "a"))
        assertEquals(listOf("z", "m", "a"), out.keys.toList())
    }
}
