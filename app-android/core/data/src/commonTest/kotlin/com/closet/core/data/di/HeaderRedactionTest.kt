package com.closet.core.data.di

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeaderRedactionTest {
    @Test fun redactsEverySecretHeaderCaseInsensitively() {
        val msg = "REQUEST\nAuthorization: Bearer sk-123\nx-api-key: abc\nX-Goog-Api-Key: g-1\nAccept: application/json"
        val out = redactSensitiveHeaders(msg)
        assertFalse(out.contains("sk-123")); assertFalse(out.contains("abc")); assertFalse(out.contains("g-1"))
        assertTrue(out.contains("Accept: application/json"))
        assertTrue(out.contains("Authorization: <REDACTED>"))
    }

    // The shape Ktor 3.1.3's Logging plugin produces (LoggingUtils.logHeader: "-> $key: $value";
    // Logging.logRequest: "REQUEST: ${Url(request.url)}" / "METHOD: ${request.method}").
    @Test fun redactsKtorFormattedHeaderLinesAndKeepsThePrefix() {
        val msg = listOf(
            "REQUEST: https://api.openai.com/v1/models",
            "METHOD: HttpMethod(value=GET)",
            "COMMON HEADERS",
            "-> Accept: application/json",
            "-> Authorization: Bearer sk-123",
            "-> X-Goog-Api-Key: g-1",
            "-> x-api-key: abc",
        ).joinToString("\n")
        val out = redactSensitiveHeaders(msg)
        assertFalse(out.contains("sk-123")); assertFalse(out.contains("abc")); assertFalse(out.contains("g-1"))
        assertTrue(out.contains("-> Accept: application/json"))
        assertTrue(out.contains("-> Authorization: <REDACTED>"))
        assertTrue(out.contains("-> X-Goog-Api-Key: <REDACTED>"))
        assertTrue(out.contains("-> x-api-key: <REDACTED>"))
    }

    @Test fun redactsResponseHeaderPrefixAndLeadingWhitespace() {
        val out = redactSensitiveHeaders("<- Authorization: Bearer sk-1\n   -> x-api-key: abc")
        assertFalse(out.contains("sk-1")); assertFalse(out.contains("abc"))
        assertTrue(out.contains("<- Authorization: <REDACTED>"))
        assertTrue(out.contains("   -> x-api-key: <REDACTED>"))
    }

    @Test fun redactsKeyQueryParameterInLoggedUrls() {
        val msg = "REQUEST: https://example.com/v1/forecast?key=secret&lat=1\n" +
            "FROM: https://example.com/v1/forecast?lat=1&key=secret2"
        val out = redactSensitiveHeaders(msg)
        assertFalse(out.contains("secret"))
        assertTrue(out.contains("REQUEST: https://example.com/v1/forecast?key=<REDACTED>&lat=1"))
        assertTrue(out.contains("FROM: https://example.com/v1/forecast?lat=1&key=<REDACTED>"))
    }
}
