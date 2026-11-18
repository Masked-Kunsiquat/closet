package com.closet.core.ui.format

/** [amount] in the device locale's currency format, e.g. "$12.50". */
expect fun formatCurrency(amount: Double): String
