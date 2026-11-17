package com.closet.features.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatDisplayDateTest {

    @Test
    fun `iso date renders as month name day and year`() {
        assertEquals("April 4, 2026", formatDisplayDate("2026-04-04"))
    }

    @Test
    fun `two digit day is not padded or truncated`() {
        assertEquals("December 25, 2026", formatDisplayDate("2026-12-25"))
    }

    @Test
    fun `non iso input is returned unchanged`() {
        assertEquals("garbage", formatDisplayDate("garbage"))
    }
}
