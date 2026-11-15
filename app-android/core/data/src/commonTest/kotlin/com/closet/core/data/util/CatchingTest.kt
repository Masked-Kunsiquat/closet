package com.closet.core.data.util

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatchingTest {
    @Test
    fun wrapsOrdinaryFailures() {
        assertTrue(catchingNonCancellation { error("x") }.isFailure)
    }

    @Test
    fun returnsSuccessValue() {
        assertEquals(42, catchingNonCancellation { 42 }.getOrNull())
    }

    @Test
    fun rethrowsCancellation() {
        assertFailsWith<CancellationException> {
            catchingNonCancellation { throw CancellationException("c") }
        }
    }
}
