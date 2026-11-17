package com.closet.features.chat.ai

import com.closet.core.data.model.AiProvider
import kotlin.test.assertEquals
import kotlin.test.Test

class ChatProviderLabelTest {

    @Test
    fun `Nano with a model label shows the label`() {
        assertEquals("On-device (Gemini Nano v3)", chatProviderLabel(AiProvider.Nano, "Gemini Nano v3"))
    }

    @Test
    fun `Nano without a model label keeps the default label`() {
        assertEquals("On-device (Nano)", chatProviderLabel(AiProvider.Nano, null))
        assertEquals("On-device (Nano)", chatProviderLabel(AiProvider.Nano, "  "))
    }

    @Test
    fun `model label is trimmed`() {
        assertEquals("On-device (nano-v2)", chatProviderLabel(AiProvider.Nano, " nano-v2 \n"))
    }

    @Test
    fun `cloud providers ignore any model label`() {
        AiProvider.entries.filter { it != AiProvider.Nano }.forEach { provider ->
            assertEquals(provider.label, chatProviderLabel(provider, "something"))
            assertEquals(provider.label, chatProviderLabel(provider, null))
        }
    }
}
