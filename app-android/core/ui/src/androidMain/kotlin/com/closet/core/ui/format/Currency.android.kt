package com.closet.core.ui.format

import java.text.NumberFormat
import java.util.Locale

// Duplicated in Currency.jvm.kt: android and jvm share no intermediate source set.
actual fun formatCurrency(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.getDefault()).format(amount)
