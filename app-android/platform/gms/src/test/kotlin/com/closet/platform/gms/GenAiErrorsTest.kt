package com.closet.platform.gms

import com.closet.core.data.ai.OnDeviceAiException
import com.closet.core.data.ai.OnDeviceAiException.Reason
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.common.GenAiException.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GenAiErrorsTest {

    @Test
    fun `mapGenAiErrorCode maps every known code`() {
        val expected = mapOf(
            ErrorCode.NOT_AVAILABLE to Reason.NOT_AVAILABLE,
            ErrorCode.NOT_SUPPORTED to Reason.NOT_AVAILABLE,
            ErrorCode.AICORE_INCOMPATIBLE to Reason.NOT_AVAILABLE,
            ErrorCode.BUSY to Reason.BUSY,
            ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED to Reason.QUOTA_EXCEEDED,
            ErrorCode.BACKGROUND_USE_BLOCKED to Reason.BACKGROUND_BLOCKED,
            ErrorCode.REQUEST_TOO_LARGE to Reason.REQUEST_TOO_LARGE,
            ErrorCode.NEEDS_SYSTEM_UPDATE to Reason.NEEDS_UPDATE,
        )
        expected.forEach { (code, reason) ->
            assertEquals("code $code", reason, mapGenAiErrorCode(code))
        }
    }

    @Test
    fun `mapGenAiErrorCode maps unmapped codes to FAILED`() {
        assertEquals(Reason.FAILED, mapGenAiErrorCode(ErrorCode.UNKNOWN))
        assertEquals(Reason.FAILED, mapGenAiErrorCode(ErrorCode.RESPONSE_GENERATION_ERROR))
    }

    @Test
    fun `structured output codes map to FAILED`() {
        assertEquals(-104, ErrorCode.STRUCTURED_OUTPUT_REQUEST_ERROR)
        assertEquals(-105, ErrorCode.STRUCTURED_OUTPUT_RESPONSE_ERROR)
        assertEquals(-106, ErrorCode.STRUCTURED_OUTPUT_MAX_TOKENS_ERROR)
        assertEquals(Reason.FAILED, mapGenAiErrorCode(ErrorCode.STRUCTURED_OUTPUT_REQUEST_ERROR))
        assertEquals(Reason.FAILED, mapGenAiErrorCode(ErrorCode.STRUCTURED_OUTPUT_RESPONSE_ERROR))
        assertEquals(Reason.FAILED, mapGenAiErrorCode(ErrorCode.STRUCTURED_OUTPUT_MAX_TOKENS_ERROR))
    }

    @Test
    fun `isStructuredOutputError matches only the structured output codes`() {
        listOf(
            ErrorCode.STRUCTURED_OUTPUT_REQUEST_ERROR,
            ErrorCode.STRUCTURED_OUTPUT_RESPONSE_ERROR,
            ErrorCode.STRUCTURED_OUTPUT_MAX_TOKENS_ERROR,
        ).forEach { code ->
            assertTrue("code $code", GenAiException("x", null, code).isStructuredOutputError())
        }
        assertFalse(GenAiException("x", null, ErrorCode.BUSY).isStructuredOutputError())
        assertFalse(GenAiException("x", null, ErrorCode.REQUEST_TOO_LARGE).isStructuredOutputError())
        assertFalse(IllegalStateException("x").isStructuredOutputError())
    }

    @Test
    fun `GenAiException maps via its error code and keeps the cause`() {
        val original = GenAiException("busy", null, ErrorCode.BUSY)
        val mapped = original.toOnDeviceAiException()
        assertEquals(Reason.BUSY, mapped.reason)
        assertSame(original, mapped.cause)
    }

    @Test
    fun `existing OnDeviceAiException passes through`() {
        val original = OnDeviceAiException(Reason.QUOTA_EXCEEDED)
        assertSame(original, original.toOnDeviceAiException())
    }

    @Test
    fun `other throwables become FAILED with the original as cause`() {
        val original = IllegalStateException("boom")
        val mapped = original.toOnDeviceAiException()
        assertEquals(Reason.FAILED, mapped.reason)
        assertSame(original, mapped.cause)
    }
}
