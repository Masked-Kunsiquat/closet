package com.closet.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorSpace
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.closet.core.data.util.BitmapUtils
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Colour extraction reads pixels as sRGB. A Display P3 photo (modern phone cameras) must therefore
 * be converted on decode, or its measured shades would be skewed (#128).
 */
@RunWith(AndroidJUnit4::class)
class BitmapDecodeColorSpaceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "p3_decode_test.png")

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun displayP3Photo_decodesToSrgb_withConvertedPixels() {
        val p3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
        val srgb = ColorSpace.get(ColorSpace.Named.SRGB)

        val source = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888, true, p3)
        source.eraseColor(Color.pack(0.85f, 0.30f, 0.20f, 1f, p3))
        file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        source.recycle()

        // Self-check: the PNG really carries P3 (decoding without a preferred space keeps it).
        val raw = BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull(raw)
        assertEquals("fixture PNG must carry Display P3", p3, raw!!.colorSpace)
        raw.recycle()

        val decoded = BitmapUtils.decodeSampledBitmap(file.absolutePath, 512)
        assertNotNull(decoded)
        assertEquals(srgb, decoded!!.colorSpace)

        val expected = ColorSpace.connect(p3, srgb).transform(0.85f, 0.30f, 0.20f)
            .map { (it * 255f).roundToInt() }
        val px = decoded.getPixel(decoded.width / 2, decoded.height / 2)
        val actual = listOf(Color.red(px), Color.green(px), Color.blue(px))
        expected.zip(actual).forEachIndexed { i, (e, a) ->
            assertTrue("channel $i expected $e but was $a", abs(e - a) <= 2)
        }
    }
}
