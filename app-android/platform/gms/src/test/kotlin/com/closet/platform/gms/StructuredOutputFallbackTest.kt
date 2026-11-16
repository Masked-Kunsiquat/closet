package com.closet.platform.gms

import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.common.GenAiException.ErrorCode
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredOutputFallbackTest {

    private fun genAi(code: Int) = GenAiException("x", null, code)

    @Test
    fun `structured output error codes fall back`() {
        listOf(
            ErrorCode.STRUCTURED_OUTPUT_REQUEST_ERROR,
            ErrorCode.STRUCTURED_OUTPUT_RESPONSE_ERROR,
            ErrorCode.STRUCTURED_OUTPUT_MAX_TOKENS_ERROR,
        ).forEach { code -> assertTrue("code $code", shouldFallBackToPlainText(genAi(code))) }
    }

    @Test
    fun `other GenAiException codes do not fall back`() {
        listOf(
            ErrorCode.BUSY,
            ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED,
            ErrorCode.BACKGROUND_USE_BLOCKED,
            ErrorCode.REQUEST_TOO_LARGE,
            ErrorCode.NOT_AVAILABLE,
            ErrorCode.NEEDS_SYSTEM_UPDATE,
        ).forEach { code -> assertFalse("code $code", shouldFallBackToPlainText(genAi(code))) }
    }

    @Test
    fun `non GenAi exceptions fall back`() {
        assertTrue(shouldFallBackToPlainText(IllegalStateException("no GenerableProvider")))
        assertTrue(shouldFallBackToPlainText(IllegalArgumentException("bad json")))
        assertTrue(shouldFallBackToPlainText(ClassCastException()))
    }

    @Test
    fun `CancellationException never falls back`() {
        assertFalse(shouldFallBackToPlainText(CancellationException("cancelled")))
    }

    @Test
    fun `typedResponseOrNull returns the first response`() {
        assertEquals("a", typedResponseOrNull(listOf("a", "b")))
    }

    @Test
    fun `typedResponseOrNull is null for no candidates or a null response`() {
        assertNull(typedResponseOrNull(emptyList<String>()))
        assertNull(typedResponseOrNull(listOf<String?>(null)))
    }

    @Test
    fun `typedResponseOrNull is null for an empty response`() {
        assertNull(typedResponseOrNull(listOf(emptyList<Int>())) { it.isEmpty() })
        assertEquals(listOf(1), typedResponseOrNull(listOf(listOf(1))) { it.isEmpty() })
    }
}
