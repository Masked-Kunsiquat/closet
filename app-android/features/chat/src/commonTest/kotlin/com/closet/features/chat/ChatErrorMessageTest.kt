package com.closet.features.chat

import com.closet.core.data.ai.ChatResponseParseException
import com.closet.core.data.ai.OnDeviceAiException
import com.closet.core.data.ai.ProviderNotConfiguredException
import kotlin.test.assertEquals
import kotlin.test.Test

class ChatErrorMessageTest {

    @Test
    fun `OnDeviceAiException uses the reason copy`() {
        val error = OnDeviceAiException(OnDeviceAiException.Reason.BUSY)
        assertEquals(OnDeviceAiException.Reason.BUSY.userMessage, chatErrorMessage(error))
    }

    @Test
    fun `ChatResponseParseException gets the unreadable-reply copy`() {
        assertEquals(
            "The assistant's reply couldn't be read. Try asking another way.",
            chatErrorMessage(ChatResponseParseException(IllegalArgumentException("bad json"))),
        )
    }

    @Test
    fun `ProviderNotConfiguredException surfaces its message`() {
        assertEquals(
            "On-device AI isn't ready. Open Settings → AI to check it.",
            chatErrorMessage(ProviderNotConfiguredException("On-device AI isn't ready. Open Settings → AI to check it.")),
        )
    }

    @Test
    fun `unrelated IllegalStateException gets the generic copy`() {
        assertEquals(
            "Something went wrong. Please try again.",
            chatErrorMessage(IllegalStateException("OpenAiChatProvider inference failed (model=x)")),
        )
    }

    @Test
    fun `anything else gets the generic copy`() {
        assertEquals("Something went wrong. Please try again.", chatErrorMessage(RuntimeException("x")))
    }
}
