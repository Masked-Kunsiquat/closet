package com.closet.core.data.color

data class ReferenceShade(val colorId: Long, val hex: String)

/** Names a colour by the nearest reference shade (Oklab distance). */
class ColorNamer(shades: List<ReferenceShade>) {
    private val entries: List<Pair<Long, Lab>> =
        shades.map { it.colorId to Oklab.fromHex(it.hex) }

    /** The colorId of the closest shade, ties to the lower colorId; null when there are no shades. */
    fun nearest(lab: Lab): Long? {
        var bestId: Long? = null
        var bestDist = Double.MAX_VALUE
        for ((id, shade) in entries) {
            val d = Oklab.deltaE(lab, shade)
            if (d < bestDist || (d == bestDist && bestId != null && id < bestId)) {
                bestDist = d
                bestId = id
            }
        }
        return bestId
    }
}
