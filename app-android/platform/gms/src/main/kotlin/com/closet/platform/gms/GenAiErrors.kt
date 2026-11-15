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
    else -> Reason.FAILED
}

/** Converts any failure from the on-device stack into an [OnDeviceAiException]. */
internal fun Throwable.toOnDeviceAiException(): OnDeviceAiException = when (this) {
    is OnDeviceAiException -> this
    is GenAiException -> OnDeviceAiException(mapGenAiErrorCode(errorCode), this)
    else -> OnDeviceAiException(Reason.FAILED, this)
}
