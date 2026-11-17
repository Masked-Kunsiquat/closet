package com.closet.features.chat.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAiCompletionsUrlTest {

    @Test
    fun `blank base uses the OpenAI default with the v1 path`() {
        assertEquals("https://api.openai.com/v1/chat/completions", buildCompletionsUrl(""))
    }

    @Test
    fun `host only base gets the v1 path`() {
        assertEquals("http://localhost:11434/v1/chat/completions", buildCompletionsUrl("http://localhost:11434"))
    }

    @Test
    fun `host only base with a trailing slash gets the v1 path`() {
        assertEquals("http://localhost:11434/v1/chat/completions", buildCompletionsUrl("http://localhost:11434/"))
    }

    @Test
    fun `base that already has a path only gets chat completions appended`() {
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            buildCompletionsUrl("https://api.groq.com/openai/v1"),
        )
    }
}
