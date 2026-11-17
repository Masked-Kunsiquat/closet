package com.closet.features.chat.ai

import com.closet.core.data.ai.ChatAiProvider

/** On-device (Gemini Nano) [ChatAiProvider] capability. */
interface OnDeviceChatProvider : ChatAiProvider {

    /**
     * A short name for the on-device model (e.g. the base model AICore serves), shown in the chat
     * header. Null when unknown or unavailable.
     */
    suspend fun modelLabel(): String? = null
}
