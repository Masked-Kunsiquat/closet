package com.closet.features.stats

import java.text.NumberFormat

internal actual fun formatCurrency(amount: Double): String =
    NumberFormat.getCurrencyInstance().apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(amount)
