package com.closet.features.recommendations.ai

import com.closet.core.data.ai.NanoInitResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnavailableOnDeviceRecommendationProviderTest {
    private val provider = UnavailableOnDeviceRecommendationProvider()

    @Test fun countTokensIsMaxValue() = runTest {
        assertEquals(Int.MAX_VALUE, provider.countTokens("anything"))
    }

    @Test fun initNanoFlowEmitsNotSupported() = runTest {
        assertEquals(listOf<NanoInitResult>(NanoInitResult.NotSupported), provider.initNanoFlow().toList())
    }

    @Test fun selectOutfitsFailsWithUnsupported() = runTest {
        val result = provider.selectOutfits(emptyList(), "casual")
        val e = result.exceptionOrNull()
        assertTrue(e is UnsupportedOperationException)
        assertEquals("On-device AI is not available in the FOSS build", e.message)
    }
}
