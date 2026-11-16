package com.closet.features.stats

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsFormatTest {

    private lateinit var originalLocale: Locale

    @BeforeTest
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun formatCurrencyUsesTwoFractionDigitsAndGrouping() {
        assertEquals("$1,234.50", formatCurrency(1234.5))
    }

    @Test
    fun formatCurrencyFormatsZero() {
        assertEquals("$0.00", formatCurrency(0.0))
    }
}
