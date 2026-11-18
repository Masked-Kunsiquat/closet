package com.closet.core.ui.format

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.currentLocale

actual fun formatCurrency(amount: Double): String {
    val formatter = NSNumberFormatter()
    formatter.locale = NSLocale.currentLocale
    formatter.numberStyle = NSNumberFormatterCurrencyStyle
    return formatter.stringFromNumber(NSNumber(amount)) ?: amount.toString()
}
