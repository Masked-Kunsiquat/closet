package com.closet.platform.gms

import com.closet.core.data.ai.OnDeviceAiException
import com.closet.core.data.ai.OnDeviceAiException.Reason
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.common.GenAiException.ErrorCode

/** Maps a [GenAiException.ErrorCode] to a platform-neutral [Reason]. */
internal fun mapGenAiErrorCode(code: Int): Reason = when (code) {
    ErrorCode.NOT_AVAILABLE,
    ErrorCode.NOT_SUPPORTED,
    ErrorCode.AICORE_INCOMPATIBLE -> Reason.NOT_AVAILABLE
    ErrorCode.BUSY -> Reason.BUSY
    ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> Reason.QUOTA_EXCEEDED
    ErrorCode.BACKGROUND_USE_BLOCKED -> Reason.BACKGROUND_BLOCKED
    ErrorCode.REQUEST_TOO_LARGE -> Reason.REQUEST_TOO_LARGE
    ErrorCode.NEEDS_SYSTEM_UPDATE -> Reason.NEEDS_UPDATE
    // Structured output errors normally trigger a plain-text retry (see isStructuredOutputError);
    // if one ever escapes, it is a generic failure.
    ErrorCode.STRUCTURED_OUTPUT_REQUEST_ERROR,
    ErrorCode.STRUCTURED_OUTPUT_RESPONSE_ERROR,
    ErrorCode.STRUCTURED_OUTPUT_MAX_TOKENS_ERROR -> Reason.FAILED
    else -> Reason.FAILED
}

/**
 * True when this is a [GenAiException] from the structured-output path (-104..-106). Callers
 * retry those once on the plain-text path instead of failing.
 */
internal fun Throwable.isStructuredOutputError(): Boolean =
    this is GenAiException && when (errorCode) {
        ErrorCode.STRUCTURED_OUTPUT_REQUEST_ERROR,
        ErrorCode.STRUCTURED_OUTPUT_RESPONSE_ERROR,
        ErrorCode.STRUCTURED_OUTPUT_MAX_TOKENS_ERROR -> true
        else -> false
    }

/** Converts any failure from the on-device stack into an [OnDeviceAiException]. */
internal fun Throwable.toOnDeviceAiException(): OnDeviceAiException = when (this) {
    is OnDeviceAiException -> this
    is GenAiException -> OnDeviceAiException(mapGenAiErrorCode(errorCode), this)
    else -> OnDeviceAiException(Reason.FAILED, this)
}
