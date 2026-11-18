package com.closet.shared

import com.closet.shared.resources.Res
import com.closet.shared.resources.nav_chat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString

class SharedStringsTest {
    @Test
    fun chatTabLabelIsAssistant() = runTest {
        assertEquals("Assistant", getString(Res.string.nav_chat))
    }
}
