package com.closet.core.data.ai

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ChatResponseParseExceptionTest {

    @Test
    fun `parse failures are wrapped in ChatResponseParseException`() {
        val failure = ChatResponseParser.parse("no json here at all").exceptionOrNull()
        assertIs<ChatResponseParseException>(failure)
        assertNotNull(failure.cause)
    }

    @Test
    fun `structurally invalid responses are wrapped too`() {
        val failure = ChatResponseParser.parse("""{"type":"items","text":"here","item_ids":[]}""").exceptionOrNull()
        assertIs<ChatResponseParseException>(failure)
    }
}
