package com.closet.features.settings

private const val KIB = 1_024L
private const val MIB = 1_048_576L
private const val GIB = 1_073_741_824L

/** "x.y GB" / "x.y MB" / "x.y KB" / "N B"; one decimal, half up, '.' separator in every locale. */
internal fun formatBytes(bytes: Long): String = when {
    bytes >= GIB -> "${oneDecimal(bytes, GIB)} GB"
    bytes >= MIB -> "${oneDecimal(bytes, MIB)} MB"
    bytes >= KIB -> "${oneDecimal(bytes, KIB)} KB"
    else -> "$bytes B"
}

// Integer math, no locale-dependent formatting: tenths = round-half-up(bytes * 10 / unit).
private fun oneDecimal(bytes: Long, unit: Long): String {
    val tenths = (bytes * 20 / unit + 1) / 2
    return "${tenths / 10}.${tenths % 10}"
}
