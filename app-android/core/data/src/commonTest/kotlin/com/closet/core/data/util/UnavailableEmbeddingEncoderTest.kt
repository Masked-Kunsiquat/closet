package com.closet.core.data.util

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnavailableEmbeddingEncoderTest {
    private val encoder: EmbeddingEncoder = UnavailableEmbeddingEncoder

    @Test fun isNotAvailable() {
        assertFalse(encoder.isAvailable)
    }

    @Test fun reportsTheStoredModelVersion() {
        assertEquals("arctic-embed-xs-q8-v1", encoder.modelVersion)
    }

    @Test fun encodeFails() = runTest {
        assertTrue(encoder.encode("x").isFailure)
    }
}
