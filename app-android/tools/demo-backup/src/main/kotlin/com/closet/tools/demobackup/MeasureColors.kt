package com.closet.tools.demobackup

import com.closet.core.data.color.ColorExtractor
import com.closet.core.data.color.ExtractedColor
import java.io.File
import javax.imageio.ImageIO

/** One photo's measurement: [status] is `ok`, `unreadable` or `no-colours`. */
data class Measured(val status: String, val colors: List<ExtractedColor>)

/**
 * Measures photos with the app's [ColorExtractor] (default constants, the seeded reference shades
 * [ColorCheck] uses), so colour names match `color-check` and the app.
 */
object MeasureColors {

    fun measure(imagesDir: File, ids: List<String>): Map<String, Measured> {
        val extractor = ColorExtractor(ColorCheck.shades)
        val out = LinkedHashMap<String, Measured>()
        for (id in ids) {
            val image = runCatching { ImageIO.read(File(imagesDir, "$id.jpg")) }.getOrNull()
            out[id] = if (image == null) {
                Measured("unreadable", emptyList())
            } else {
                val w = image.width
                val h = image.height
                val colors = extractor.extract(image.getRGB(0, 0, w, h, null, 0, w), w, h)
                Measured(if (colors.isEmpty()) "no-colours" else "ok", colors)
            }
        }
        return out
    }

    /** Colour name for an [ExtractedColor], from the same seeded list `color-check` uses. */
    fun name(color: ExtractedColor): String = ColorCheck.seeds[(color.colorId - 1).toInt()].name

    fun toJson(measured: Map<String, Measured>): String = buildString {
        append("{")
        measured.entries.forEachIndexed { i, (id, m) ->
            if (i > 0) append(",")
            append("\n  ").append(quote(id)).append(": {\"colors\": [")
            m.colors.forEachIndexed { j, c ->
                if (j > 0) append(", ")
                append("{\"hex\": ").append(quote(c.hex))
                append(", \"share\": ").append(c.share.toString())
                append(", \"colorName\": ").append(quote(name(c))).append("}")
            }
            append("], \"status\": ").append(quote(m.status)).append("}")
        }
        append("\n}\n")
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (ch in s) when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch < ' ' -> append("\\u%04x".format(ch.code))
            else -> append(ch)
        }
        append('"')
    }

    fun run(imagesDir: File, idsFile: File, outJson: File) {
        val ids = idsFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        outJson.writeText(toJson(measure(imagesDir, ids)))
    }
}
