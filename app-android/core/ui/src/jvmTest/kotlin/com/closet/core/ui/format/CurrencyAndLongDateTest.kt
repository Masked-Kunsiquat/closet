package com.closet.core.ui.format

import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyAndLongDateTest {
    private lateinit var original: Locale

    @BeforeTest fun setUp() {
        original = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @AfterTest fun tearDown() = Locale.setDefault(original)

    @Test fun usCurrency() {
        assertEquals("$12.50", formatCurrency(12.5))
    }

    @Test fun usLongDate() {
        assertEquals("April 4, 2026", formatDateLong(LocalDate(2026, 4, 4)))
    }
}
