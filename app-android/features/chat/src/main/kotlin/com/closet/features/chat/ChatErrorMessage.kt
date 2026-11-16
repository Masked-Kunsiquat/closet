package com.closet.features.chat

import com.closet.core.data.ai.ChatResponseParseException
import com.closet.core.data.ai.OnDeviceAiException
import com.closet.core.data.ai.ProviderNotConfiguredException

private const val GENERIC_CHAT_ERROR = "Something went wrong. Please try again."

/** User-facing copy for a failed chat query. Never exposes raw exception text except known-safe cases. */
internal fun chatErrorMessage(error: Throwable): String = when (error) {
    is OnDeviceAiException -> error.reason.userMessage
    is ChatResponseParseException -> "The assistant's reply couldn't be read. Try asking another way."
    // ChatAiProviderSelector (safe, user-facing text): "… is not configured", "On-device AI isn't ready. Open Settings → AI …"
    is ProviderNotConfiguredException -> error.message ?: GENERIC_CHAT_ERROR
    else -> GENERIC_CHAT_ERROR
}
