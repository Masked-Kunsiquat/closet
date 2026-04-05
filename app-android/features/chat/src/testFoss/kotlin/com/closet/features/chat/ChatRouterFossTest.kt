package com.closet.features.chat

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Smoke test for the FOSS-flavor [ChatRouter] stub.
 * The FOSS stub has no GMS dependencies and always returns [ChatRouter.RouterResult.Unrouted].
 */
class ChatRouterFossTest {

    @Test
    fun `FOSS router always returns Unrouted regardless of input`() = runTest {
        val router = ChatRouter()
        assertEquals(ChatRouter.RouterResult.Unrouted, router.route("anything"))
        assertEquals(ChatRouter.RouterResult.Unrouted, router.route(""))
        assertEquals(ChatRouter.RouterResult.Unrouted, router.route("how many items do I have?"))
        assertEquals(ChatRouter.RouterResult.Unrouted, router.route("what have i never worn"))
    }
}