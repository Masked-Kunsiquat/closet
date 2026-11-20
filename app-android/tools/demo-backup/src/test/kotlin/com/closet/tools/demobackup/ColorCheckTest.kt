package com.closet.tools.demobackup

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorCheckTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** A white 120x120 JPEG with a centred [fill] square covering about 44% of it. */
    private fun writeJpeg(file: File, fill: Int) {
        val image = BufferedImage(120, 120, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 120, 120)
        g.color = Color(fill)
        g.fillRect(20, 20, 80, 80)
        g.dispose()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val param = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = 1.0f
        }
        file.outputStream().use { out ->
            ImageIO.createImageOutputStream(out).use { ios ->
                writer.output = ios
                writer.write(null, IIOImage(image, null, null), param)
            }
        }
        writer.dispose()
    }

    private fun dataset(): File {
        val dir = tmp.newFolder("dataset")
        File(dir, "images").mkdirs()
        writeJpeg(File(dir, "images/1.jpg"), 0xB3202A)
        writeJpeg(File(dir, "images/2.jpg"), 0x1F2A44)
        writeJpeg(File(dir, "images/3.jpg"), 0xF6E7A5)
        File(dir, "styles.csv").writeText(
            """
            id,gender,masterCategory,subCategory,articleType,baseColour,season,year,usage,productDisplayName
            1,Men,Apparel,Topwear,Tshirts,Red,Summer,2012,Casual,Red Tee
            2,Men,Apparel,Topwear,Tshirts,Navy Blue,Summer,2012,Casual,Navy Tee
            3,Men,Apparel,Topwear,Tshirts,Yellow,Summer,2012,Casual,Pale Yellow Tee
            """.trimIndent() + "\n",
        )
        return dir
    }

    @Test
    fun reportsExactAgreementOnKnownColours() {
        val report = ColorCheck.run(dataset())

        assertEquals(3, report.checked)
        assertEquals(3, report.exact)
        assertEquals(3, report.basicTerm)
        assertEquals(0, report.empty)
        val text = report.render()
        assertTrue("Exact agreement:  100.0% (3)" in text, text)
        assertTrue("Empty rate:       0.0% (0)" in text, text)
    }

    @Test
    fun limitCapsTheSample() {
        assertEquals(2, ColorCheck.run(dataset(), limit = 2).checked)
    }
}
