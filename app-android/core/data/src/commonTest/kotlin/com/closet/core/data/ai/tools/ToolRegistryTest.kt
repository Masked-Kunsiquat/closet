package com.closet.core.data.ai.tools

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ToolRegistryTest {

    private class StubTool(name: String) : WardrobeTool {
        override val spec = ToolSpec(name, "stub", emptyList())
        override suspend fun run(args: JsonObject) = ToolResult(JsonNull, emptySet())
    }

    @Test
    fun findReturnsToolByNameAndSpecsListAll() {
        val a = StubTool("tool_a")
        val b = StubTool("tool_b")
        val registry = ToolRegistry(listOf(a, b))
        assertSame(b, registry.find("tool_b"))
        assertNull(registry.find("missing"))
        assertEquals(listOf("tool_a", "tool_b"), registry.specs.map { it.name })
    }
}
