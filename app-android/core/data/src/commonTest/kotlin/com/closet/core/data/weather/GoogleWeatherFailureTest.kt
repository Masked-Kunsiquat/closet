package com.closet.core.data.weather

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleWeatherFailureTest {
    // The shape of Ktor's HttpRequestTimeoutException message, which embeds the full request URL.
    private val timeout = RuntimeException(
        "Request timeout has expired [url=https://x/?key=abc, request_timeout=30000 ms]",
    )

    @Test fun sanitizedFailureHasNoKeyInMessageOrToString() {
        val out = sanitizeWeatherFailure(timeout)
        assertFalse(out.message.orEmpty().contains("abc"))
        assertFalse(out.toString().contains("abc"))
        assertTrue(out.message.orEmpty().contains("Google Weather fetch failed"))
    }

    @Test fun sanitizedFailureDropsTheOriginalCause() {
        val wrapped = RuntimeException("outer", timeout)
        val out = sanitizeWeatherFailure(wrapped)
        assertNull(out.cause)
    }
}
