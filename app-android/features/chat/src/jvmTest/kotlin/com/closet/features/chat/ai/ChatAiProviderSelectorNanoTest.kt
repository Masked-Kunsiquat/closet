package com.closet.features.chat.ai

import com.closet.core.data.ai.NanoAvailability
import com.closet.core.data.ai.ProviderNotConfiguredException
import com.closet.core.data.model.AiProvider
import com.closet.core.data.repository.AiPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test

class ChatAiProviderSelectorNanoTest {

    private val nano = mockk<OnDeviceChatProvider>()
    private val prefs = mockk<AiPreferencesRepository>()
    private val selector = ChatAiProviderSelector(nano, mockk(), mockk(), mockk(), prefs)

    private suspend fun failureMessage(availability: NanoAvailability): String? {
        every { prefs.getSelectedProvider() } returns flowOf(AiProvider.Nano)
        every { prefs.getNanoAvailability() } returns flowOf(availability)
        val error = selector.current().exceptionOrNull()
        assertTrue(error is ProviderNotConfiguredException)
        return error?.message
    }

    @Test
    fun `ready returns the on-device provider`() = runTest {
        every { prefs.getSelectedProvider() } returns flowOf(AiProvider.Nano)
        every { prefs.getNanoAvailability() } returns flowOf(NanoAvailability.READY)
        assertSame(nano, selector.current().getOrThrow())
    }

    @Test
    fun `not supported says the phone cannot run it`() = runTest {
        assertEquals(
            "On-device AI isn't supported on this phone. Choose another provider in Settings → AI.",
            failureMessage(NanoAvailability.NOT_SUPPORTED),
        )
    }

    @Test
    fun `downloading says to try again later`() = runTest {
        assertEquals(
            "The on-device model is still downloading. Try again in a few minutes, or open Settings → AI.",
            failureMessage(NanoAvailability.DOWNLOADING),
        )
    }

    @Test
    fun `failed says to check Settings`() = runTest {
        assertEquals(
            "On-device AI isn't ready. Open Settings → AI to check it.",
            failureMessage(NanoAvailability.FAILED),
        )
    }

    @Test
    fun `unknown says to check Settings`() = runTest {
        assertEquals(
            "On-device AI isn't ready. Open Settings → AI to check it.",
            failureMessage(NanoAvailability.UNKNOWN),
        )
    }
}
