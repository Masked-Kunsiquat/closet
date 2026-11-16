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

/**
 * Whether a failure on the structured-output (typed) path should be retried once on the
 * plain-text path:
 * - structured-output [GenAiException]s (-104..-106): yes;
 * - any other [GenAiException] (busy, quota, background, too large, not available, …): no, the
 *   plain path would fail the same way;
 * - anything else (missing generated provider, decoding/serialization problems): yes;
 * - [kotlinx.coroutines.CancellationException]: never; callers rethrow it.
 */
internal fun shouldFallBackToPlainText(error: Throwable): Boolean = when (error) {
    is kotlinx.coroutines.CancellationException -> false
    is GenAiException -> error.isStructuredOutputError()
    else -> true
}

/**
 * The first typed response, or null (fall back to plain text) when there are no candidates, the
 * response is null, or [isEmpty] says it carries nothing.
 */
internal inline fun <T : Any> typedResponseOrNull(
    responses: List<T?>,
    isEmpty: (T) -> Boolean = { false },
): T? = responses.firstOrNull()?.takeUnless(isEmpty)

/** Converts any failure from the on-device stack into an [OnDeviceAiException]. */
internal fun Throwable.toOnDeviceAiException(): OnDeviceAiException = when (this) {
    is OnDeviceAiException -> this
    is GenAiException -> OnDeviceAiException(mapGenAiErrorCode(errorCode), this)
    else -> OnDeviceAiException(Reason.FAILED, this)
}
