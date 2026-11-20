package com.closet.tools.demobackup

import com.closet.core.data.DatabaseSeeder
import com.closet.core.data.color.ColorExtractor
import com.closet.core.data.color.ReferenceShade
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random

/** What `color-check` measured over a sample of dataset photos. */
class ColorCheckReport(
    /** Photos that were decoded and run through the extractor. */
    val checked: Int,
    /** Sampled photos that could not be decoded; not part of [checked]. */
    val unreadable: Int,
    /** Top extracted colour name equals the mapped `baseColour` name. */
    val exact: Int,
    /** Top extracted colour has the same `basic_term` as the mapped name. */
    val basicTerm: Int,
    /** Photos where the extractor returned no colour. */
    val empty: Int,
    /**
     * Baseline: the hex of the extractor's top colour (highest share, after same-name clusters are
     * combined) re-named by [LegacyHslMatcher], compared with the mapped name.
     */
    val baselineExact: Int,
    val baselineBasicTerm: Int,
    /** Top (mapped, got) pairs that disagree, with counts, most frequent first. */
    val confusion: List<Triple<String, String, Int>>,
    val mergeDeltaE: Double?,
    val minShare: Double?,
) {
    private fun pct(n: Int) = if (checked == 0) "n/a" else "%.1f%%".format(java.util.Locale.ROOT, 100.0 * n / checked)

    fun render(): String = buildString {
        appendLine("Checked:          $checked" + if (unreadable > 0) " ($unreadable unreadable)" else "")
        if (mergeDeltaE != null || minShare != null) {
            appendLine("Overrides:        merge=${mergeDeltaE ?: "default"} min-share=${minShare ?: "default"}")
        }
        appendLine("Empty rate:       ${pct(empty)} ($empty)")
        appendLine("Exact agreement:  ${pct(exact)} ($exact)")
        appendLine("Basic-term:       ${pct(basicTerm)} ($basicTerm)")
        appendLine("Baseline (legacy HSL matcher on the same clusters):")
        appendLine("  Exact agreement: ${pct(baselineExact)} ($baselineExact)")
        appendLine("  Basic-term:      ${pct(baselineBasicTerm)} ($baselineBasicTerm)")
        appendLine("Top confusions (mapped -> got):")
        if (confusion.isEmpty()) {
            append("  none")
        } else {
            confusion.forEachIndexed { i, (m, g, n) ->
                if (i > 0) appendLine()
                append("  $m -> $g: $n")
            }
        }
    }
}

/**
 * Compares the shared [ColorExtractor] with the dataset's `baseColour` labels.
 *
 * The baseline does not name every cluster. It takes only the extractor's top colour (the highest
 * share after clusters with the same reference name are combined), and names that colour's hex
 * with [LegacyHslMatcher]. So it measures the old matching rule on the new pixels, not the old
 * pipeline, since Palette's swatch selection cannot run on the JVM.
 *
 * The baseline matches against the base hexes of all 22 current seeds, whereas the old app had
 * only 18 (no Charcoal, Cream, Denim or Teal). Its numbers are therefore not exactly what the old
 * app would have produced.
 */
object ColorCheck {

    private val seeds = DatabaseSeeder.SEEDED_COLORS
    private val termByName = seeds.associate { it.name to it.basicTerm }

    /** Seed ids are 1..22 in list order. */
    private val shades: List<ReferenceShade> = seeds.flatMapIndexed { index, c ->
        c.shades.map { ReferenceShade(colorId = index + 1L, hex = it) }
    }

    fun run(
        datasetDir: File,
        limit: Int = 300,
        seed: Long = 42,
        mergeDeltaE: Double? = null,
        minShare: Double? = null,
    ): ColorCheckReport {
        val extractor = if (mergeDeltaE != null || minShare != null) {
            ColorExtractor(shades, mergeDeltaE ?: ColorExtractor.DELTA_E_MERGE, minShare ?: ColorExtractor.MIN_SHARE)
        } else {
            ColorExtractor(shades)
        }
        val sample = Dataset.load(datasetDir).items
            .filter { it.color != null }
            .sortedBy { it.id }
            .shuffled(Random(seed))
            .take(limit)

        var checked = 0
        var unreadable = 0
        var exact = 0
        var term = 0
        var empty = 0
        var baseExact = 0
        var baseTerm = 0
        val confusion = HashMap<Pair<String, String>, Int>()

        for (item in sample) {
            val mapped = item.color ?: continue
            val image = runCatching { ImageIO.read(item.image) }.getOrNull()
            if (image == null) {
                unreadable++
                continue
            }
            val w = image.width
            val h = image.height
            val colors = extractor.extract(image.getRGB(0, 0, w, h, null, 0, w), w, h)
            checked++

            if (colors.isEmpty()) {
                empty++
                confusion.merge(mapped to "(none)", 1, Int::plus)
                continue
            }
            val got = seeds[(colors.first().colorId - 1).toInt()].name
            if (got == mapped) exact++ else confusion.merge(mapped to got, 1, Int::plus)
            if (termByName[got] == termByName[mapped]) term++

            val legacy = LegacyHslMatcher.nearestName(colors.maxBy { it.share }.hex)
            if (legacy == mapped) baseExact++
            if (termByName[legacy] == termByName[mapped]) baseTerm++
        }

        val top = confusion.entries
            .sortedWith(
                compareByDescending<Map.Entry<Pair<String, String>, Int>> { it.value }
                    .thenBy { it.key.first }.thenBy { it.key.second },
            )
            .take(10)
            .map { Triple(it.key.first, it.key.second, it.value) }
        return ColorCheckReport(checked, unreadable, exact, term, empty, baseExact, baseTerm, top, mergeDeltaE, minShare)
    }
}
