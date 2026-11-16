package com.closet.features.stats

/** Formats [amount] in the device's default currency with exactly 2 fraction digits. */
internal expect fun formatCurrency(amount: Double): String
