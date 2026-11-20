package com.closet.core.data.color

import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

/** One dominant colour of an item: its reference colour, representative hex, and share of the item's pixels. */
data class ExtractedColor(val colorId: Long, val hex: String, val share: Double)

/**
 * Pure colour-extraction pipeline: picks the item's pixels (alpha mask, uniform-border background
 * removal, or the centre box), clusters a fixed-stride sample with k-means++ in Oklab, merges near
 * clusters, and names each by the nearest [ReferenceShade]. Representative hexes are linear-RGB means.
 * Deterministic: the same input always gives the same output.
 */
class ColorExtractor(
    shades: List<ReferenceShade>,
    private val deltaEMerge: Double,
    private val minShare: Double,
) {
    /** Production configuration. */
    constructor(shades: List<ReferenceShade>) : this(shades, DELTA_E_MERGE, MIN_SHARE)

    private val namer = ColorNamer(shades)

    /**
     * [argb] is row-major, `width * height` long. Returns at most [MAX_COLORS] colours sorted by share
     * descending (ties by colorId); empty when the item has too few pixels or nothing reaches [MIN_SHARE].
     * Shares are fractions of the sampled item pixels and are not renormalized after filtering.
     * With no reference shades nothing can be named, so the result is empty.
     *
     * @throws IllegalArgumentException if `argb.size != width * height` or a dimension is not positive.
     */
    fun extract(argb: IntArray, width: Int, height: Int): List<ExtractedColor> {
        require(width > 0 && height > 0 && argb.size == width * height) {
            "Expected ${width}x$height pixels but got ${argb.size}"
        }
        val item = itemPixels(argb, width, height)
        if (item.size < MIN_ITEM_PIXELS) return emptyList()

        val samples = sample(item)
        val n = samples.size
        val labL = DoubleArray(n)
        val labA = DoubleArray(n)
        val labB = DoubleArray(n)
        for (i in 0 until n) {
            val lab = labOf(samples[i])
            labL[i] = lab.l
            labA[i] = lab.a
            labB[i] = lab.b
        }

        val clusters = merge(kMeans(samples, labL, labA, labB))
        return name(clusters, samples, n)
    }

    // --- 1. Item pixels -------------------------------------------------------------------------

    private fun itemPixels(argb: IntArray, width: Int, height: Int): IntArray {
        val total = argb.size
        val transparent = argb.count { alpha(it) < ALPHA_ITEM }
        if (transparent >= MASK_MIN_TRANSPARENT * total) {
            val opaque = IntArray(total - transparent)
            var count = 0
            for (p in argb) if (alpha(p) >= ALPHA_ITEM) opaque[count++] = p
            return opaque
        }

        val bw = max(1, ceil(width * BORDER_FRACTION).toInt())
        val bh = max(1, ceil(height * BORDER_FRACTION).toInt())
        val ringSize = total - max(0, width - 2 * bw) * max(0, height - 2 * bh)
        val borderL = DoubleArray(ringSize)
        val borderA = DoubleArray(ringSize)
        val borderB = DoubleArray(ringSize)
        var r = 0
        for (y in 0 until height) {
            val fullRow = y < bh || y >= height - bh
            for (x in 0 until width) {
                if (fullRow || x < bw || x >= width - bw) {
                    val lab = labOf(argb[y * width + x])
                    borderL[r] = lab.l; borderA[r] = lab.a; borderB[r] = lab.b
                    r++
                }
            }
        }
        val median = Lab(median(borderL), median(borderA), median(borderB))
        var near = 0
        for (i in 0 until ringSize) {
            if (Oklab.deltaE(Lab(borderL[i], borderA[i], borderB[i]), median) <= BG_DELTA_E) near++
        }
        if (near >= BORDER_UNIFORM * ringSize) {
            val foreground = IntArray(total)
            var count = 0
            for (p in argb) {
                if (Oklab.deltaE(labOf(p), median) > BG_DELTA_E) foreground[count++] = p
            }
            if (count >= MIN_ITEM_PIXELS) return foreground.copyOf(count)
        }
        return centreBox(argb, width, height)
    }

    private fun centreBox(argb: IntArray, width: Int, height: Int): IntArray {
        val cw = max(1, (width * CENTER_FRACTION).toInt())
        val ch = max(1, (height * CENTER_FRACTION).toInt())
        val x0 = (width - cw) / 2
        val y0 = (height - ch) / 2
        return IntArray(cw * ch) { i -> argb[(y0 + i / cw) * width + x0 + i % cw] }
    }

    // --- 2. Sample --------------------------------------------------------------------------------

    private fun sample(item: IntArray): IntArray {
        val stride = (item.size + MAX_SAMPLES - 1) / MAX_SAMPLES
        if (stride <= 1) return item
        return IntArray((item.size + stride - 1) / stride) { item[it * stride] }
    }

    // --- 3. Cluster -------------------------------------------------------------------------------

    private class Cluster(val members: IntArray, val l: Double, val a: Double, val b: Double) {
        val size: Int get() = members.size
        val first: Int get() = members[0]
    }

    private fun kMeans(samples: IntArray, labL: DoubleArray, labA: DoubleArray, labB: DoubleArray): List<Cluster> {
        val n = samples.size
        // k = distinct colours, capped at K. Alpha is masked off: two samples differing only in alpha
        // have the same Lab, so counting them twice could leave a seed with no point to pick.
        val distinct = HashSet<Int>()
        for (s in samples) {
            distinct += s and 0xFFFFFF
            if (distinct.size >= K) break
        }
        val k = distinct.size

        // k-means++ seeding.
        val random = Random(COLOR_SEED)
        val cl = DoubleArray(k)
        val ca = DoubleArray(k)
        val cb = DoubleArray(k)
        val first = random.nextInt(n)
        cl[0] = labL[first]; ca[0] = labA[first]; cb[0] = labB[first]
        val d2 = DoubleArray(n) { sq(labL[it] - cl[0], labA[it] - ca[0], labB[it] - cb[0]) }
        var chosen = 1
        while (chosen < k) {
            val total = d2.sum()
            if (total <= 0.0) break
            val target = random.nextDouble() * total
            var acc = 0.0
            var pick = -1
            for (i in 0 until n) {
                if (d2[i] <= 0.0) continue
                acc += d2[i]
                pick = i
                if (acc > target) break
            }
            cl[chosen] = labL[pick]; ca[chosen] = labA[pick]; cb[chosen] = labB[pick]
            for (i in 0 until n) {
                val d = sq(labL[i] - cl[chosen], labA[i] - ca[chosen], labB[i] - cb[chosen])
                if (d < d2[i]) d2[i] = d
            }
            chosen++
        }
        val kk = chosen

        // Lloyd iterations: assign by ΔE (squared, same argmin), update centroids as Lab means.
        val assignment = IntArray(n) { -1 }
        for (iteration in 0 until MAX_ITERATIONS) {
            var changed = false
            for (i in 0 until n) {
                var best = 0
                var bestD = Double.MAX_VALUE
                for (c in 0 until kk) {
                    val d = sq(labL[i] - cl[c], labA[i] - ca[c], labB[i] - cb[c])
                    if (d < bestD) {
                        bestD = d
                        best = c
                    }
                }
                if (assignment[i] != best) {
                    assignment[i] = best
                    changed = true
                }
            }
            if (!changed) break
            val sumL = DoubleArray(kk)
            val sumA = DoubleArray(kk)
            val sumB = DoubleArray(kk)
            val count = IntArray(kk)
            for (i in 0 until n) {
                val c = assignment[i]
                sumL[c] += labL[i]; sumA[c] += labA[i]; sumB[c] += labB[i]
                count[c]++
            }
            for (c in 0 until kk) {
                if (count[c] == 0) continue // keep an emptied centroid where it was
                cl[c] = sumL[c] / count[c]; ca[c] = sumA[c] / count[c]; cb[c] = sumB[c] / count[c]
            }
        }

        val buckets = Array(kk) { ArrayList<Int>() }
        for (i in 0 until n) buckets[assignment[i]] += i
        return buckets.mapNotNull { members ->
            if (members.isEmpty()) return@mapNotNull null
            var l = 0.0; var a = 0.0; var b = 0.0
            for (i in members) { l += labL[i]; a += labA[i]; b += labB[i] }
            val m = members.size.toDouble()
            Cluster(members.toIntArray(), l / m, a / m, b / m)
        }
    }

    // --- 4. Merge ---------------------------------------------------------------------------------

    private fun merge(initial: List<Cluster>): List<Cluster> {
        var clusters = initial.sortedWith(CLUSTER_ORDER)
        while (clusters.size > 1) {
            var bi = -1
            var bj = -1
            var bestD = Double.MAX_VALUE
            for (i in clusters.indices) {
                for (j in i + 1 until clusters.size) {
                    val x = clusters[i]
                    val y = clusters[j]
                    val d = Oklab.deltaE(Lab(x.l, x.a, x.b), Lab(y.l, y.a, y.b))
                    if (d < bestD) {
                        bestD = d; bi = i; bj = j
                    }
                }
            }
            if (bestD >= deltaEMerge) break
            val x = clusters[bi]
            val y = clusters[bj]
            val total = (x.size + y.size).toDouble()
            val merged = Cluster(
                members = (x.members + y.members).also { it.sort() },
                l = (x.l * x.size + y.l * y.size) / total,
                a = (x.a * x.size + y.a * y.size) / total,
                b = (x.b * x.size + y.b * y.size) / total,
            )
            clusters = (clusters.filterIndexed { idx, _ -> idx != bi && idx != bj } + merged).sortedWith(CLUSTER_ORDER)
        }
        return clusters
    }

    // --- 5-7. Represent, name, combine, filter -----------------------------------------------------

    private class Named(val colorId: Long, var r: Double, var g: Double, var b: Double, var count: Int)

    private fun name(clusters: List<Cluster>, samples: IntArray, n: Int): List<ExtractedColor> {
        val named = ArrayList<Named>() // insertion-ordered; clusters arrive in CLUSTER_ORDER
        for (cluster in clusters) {
            var r = 0.0; var g = 0.0; var b = 0.0
            for (i in cluster.members) {
                val s = samples[i]
                r += linR(s); g += linG(s); b += linB(s)
            }
            val m = cluster.size.toDouble()
            val hex = Oklab.hexFromLinear(r / m, g / m, b / m)
            val colorId = namer.nearest(Oklab.fromHex(hex)) ?: continue
            // Same colorId: shares add; the hex is the share-weighted mean of the linear means,
            // i.e. the linear mean over all members of both clusters.
            val existing = named.firstOrNull { it.colorId == colorId }
            if (existing == null) {
                named += Named(colorId, r, g, b, cluster.size)
            } else {
                existing.r += r; existing.g += g; existing.b += b; existing.count += cluster.size
            }
        }
        return named
            .map { e ->
                val c = e.count.toDouble()
                ExtractedColor(e.colorId, Oklab.hexFromLinear(e.r / c, e.g / c, e.b / c), e.count.toDouble() / n)
            }
            .filter { it.share >= minShare }
            .sortedWith(compareByDescending<ExtractedColor> { it.share }.thenBy { it.colorId })
            .take(MAX_COLORS)
    }

    companion object {
        internal const val MAX_SAMPLES = 10_000
        internal const val K = 4
        internal const val MAX_ITERATIONS = 20
        const val DELTA_E_MERGE = 0.07
        const val MIN_SHARE = 0.15
        internal const val MAX_COLORS = 3
        internal const val MIN_ITEM_PIXELS = 50
        internal const val ALPHA_ITEM = 128
        internal const val MASK_MIN_TRANSPARENT = 0.02
        internal const val BORDER_FRACTION = 0.03
        internal const val BORDER_UNIFORM = 0.80
        internal const val BG_DELTA_E = 0.05
        internal const val CENTER_FRACTION = 0.60
        internal const val COLOR_SEED = 0x5EED

        private val CLUSTER_ORDER: Comparator<Cluster> =
            compareByDescending<Cluster> { it.size }.thenBy { it.first }

        /** `Oklab.srgbToLinear(i / 255.0)` for every byte; avoids a `pow` per channel per pixel. */
        private val LINEAR: DoubleArray = DoubleArray(256) { Oklab.srgbToLinear(it / 255.0) }

        private fun linR(argb: Int): Double = LINEAR[(argb shr 16) and 0xFF]
        private fun linG(argb: Int): Double = LINEAR[(argb shr 8) and 0xFF]
        private fun linB(argb: Int): Double = LINEAR[argb and 0xFF]

        /** Same value as `Oklab.fromArgb`, via the [LINEAR] table. */
        private fun labOf(argb: Int): Lab = Oklab.fromLinear(linR(argb), linG(argb), linB(argb))

        private fun alpha(argb: Int): Int = (argb ushr 24) and 0xFF

        private fun sq(dl: Double, da: Double, db: Double): Double = dl * dl + da * da + db * db

        private fun median(values: DoubleArray): Double {
            val sorted = values.copyOf().also { it.sort() }
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }
}
