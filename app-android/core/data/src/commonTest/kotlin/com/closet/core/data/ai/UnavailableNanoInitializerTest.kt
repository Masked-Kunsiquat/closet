package com.closet.core.data.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UnavailableNanoInitializerTest {
    @Test fun emitsNotSupportedOnce() = runTest {
        assertEquals(listOf(NanoInitResult.NotSupported), UnavailableNanoInitializer.initNanoFlow().toList())
    }
}
