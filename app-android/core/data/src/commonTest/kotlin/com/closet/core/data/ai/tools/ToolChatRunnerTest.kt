package com.closet.core.data.ai.tools

import com.closet.core.data.ai.ChatAction
import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ConversationTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolChatRunnerTest {

    private class FakeTool(
        name: String,
        private val ids: Set<Long> = emptySet(),
        private val onRun: () -> Unit = {},
    ) : WardrobeTool {
        override val spec = ToolSpec(name, "fake", emptyList())
        override suspend fun run(args: JsonObject): ToolResult {
            onRun()
            return ToolResult(buildJsonObject { put("ok", true) }, ids)
        }
    }

    private class ScriptedProvider(private val script: List<ToolStep>) : ToolCallingChatProvider {
        data class Seen(val turns: List<ToolTurn>, val allowTools: Boolean)

        val seen = mutableListOf<Seen>()
        override suspend fun supportsTools() = true
        override suspend fun step(
            system: String,
            turns: List<ToolTurn>,
            tools: List<ToolSpec>,
            allowTools: Boolean,
        ): Result<ToolStep> {
            seen += Seen(turns.toList(), allowTools)
            return Result.success(script[minOf(seen.size - 1, script.lastIndex)])
        }
    }

    private fun call(name: String) = ToolStep.Calls(listOf(ToolCall("c1", name, JsonObject(emptyMap()))))

    private fun runner(vararg tools: WardrobeTool) = ToolChatRunner(ToolRegistry(tools.toList()))

    @Test
    fun finalItemsReplyIsFilteredToIdsSeenInToolResults() = runTest {
        val provider = ScriptedProvider(
            listOf(
                call("search_items"),
                ToolStep.Final("""{"type":"items","text":"Here","item_ids":[2,1]}"""),
            ),
        )
        val result = runner(FakeTool("search_items", setOf(2L))).run(provider, "hi", emptyList())
        val response = assertIs<ChatResponse.WithItems>(result.getOrThrow())
        assertEquals(listOf(2L), response.itemIds)
    }

    @Test
    fun outfitFilteredToOneItemBecomesItemsAndDropsLogOutfit() = runTest {
        val provider = ScriptedProvider(
            listOf(
                call("search_items"),
                ToolStep.Final(
                    """{"type":"outfit","text":"Wear these","item_ids":[2,5,6],"reason":"Warm",""" +
                        """"action":{"type":"log_outfit","item_ids":[2,5,6]}}""",
                ),
            ),
        )
        val result = runner(FakeTool("search_items", setOf(2L))).run(provider, "hi", emptyList())
        val response = assertIs<ChatResponse.WithItems>(result.getOrThrow())
        assertEquals(listOf(2L), response.itemIds)
        assertNull(response.action)
    }

    @Test
    fun outfitKeepsTwoSeenItemsButDropsActionReferencingFilteredIds() = runTest {
        val provider = ScriptedProvider(
            listOf(
                call("search_items"),
                ToolStep.Final(
                    """{"type":"outfit","text":"Wear these","item_ids":[2,3,9],"reason":"Warm",""" +
                        """"action":{"type":"log_outfit","item_ids":[2,3,9]}}""",
                ),
            ),
        )
        val result = runner(FakeTool("search_items", setOf(2L, 3L))).run(provider, "hi", emptyList())
        val response = assertIs<ChatResponse.WithOutfit>(result.getOrThrow())
        assertEquals(listOf(2L, 3L), response.itemIds)
        assertNull(response.action)
    }

    @Test
    fun itemsReplyWithNoSeenIdsBecomesText() = runTest {
        val provider = ScriptedProvider(
            listOf(ToolStep.Final("""{"type":"items","text":"Here","item_ids":[7]}""")),
        )
        val result = runner().run(provider, "hi", emptyList())
        assertEquals(ChatResponse.Text("Here"), result.getOrThrow())
    }

    @Test
    fun outfitReplyWithNoValidIdsBecomesText() = runTest {
        val provider = ScriptedProvider(
            listOf(ToolStep.Final("""{"type":"outfit","text":"Wear","item_ids":[7,8],"reason":"r"}""")),
        )
        val result = runner().run(provider, "hi", emptyList())
        assertEquals(ChatResponse.Text("Wear"), result.getOrThrow())
    }

    @Test
    fun actionWithUnseenIdsIsDropped() = runTest {
        val provider = ScriptedProvider(
            listOf(
                call("search_items"),
                ToolStep.Final(
                    """{"type":"items","text":"x","item_ids":[2],"action":{"type":"open_item","item_id":9}}""",
                ),
            ),
        )
        val result = runner(FakeTool("search_items", setOf(2L))).run(provider, "hi", emptyList())
        val response = assertIs<ChatResponse.WithItems>(result.getOrThrow())
        assertNull(response.action)
    }

    @Test
    fun actionWithSeenIdsIsKept() = runTest {
        val provider = ScriptedProvider(
            listOf(
                call("search_items"),
                ToolStep.Final(
                    """{"type":"items","text":"x","item_ids":[2],"action":{"type":"open_item","item_id":2}}""",
                ),
            ),
        )
        val result = runner(FakeTool("search_items", setOf(2L))).run(provider, "hi", emptyList())
        val response = assertIs<ChatResponse.WithItems>(result.getOrThrow())
        assertEquals(ChatAction.OpenItem(2L), response.action)
    }

    @Test
    fun fifthStepDisallowsToolsAfterFourRounds() = runTest {
        val provider = ScriptedProvider(listOf(call("search_items")))
        runner(FakeTool("search_items")).run(provider, "hi", emptyList())
        assertEquals(5, provider.seen.size)
        assertEquals(listOf(true, true, true, true, false), provider.seen.map { it.allowTools })
    }

    @Test
    fun thrownToolExceptionIsHiddenFromTheModel() = runTest {
        val provider = ScriptedProvider(
            listOf(call("boom"), ToolStep.Final("""{"type":"text","text":"ok"}""")),
        )
        runner(FakeTool("boom") { throw IllegalStateException("db secret") })
            .run(provider, "hi", emptyList())
        val results = provider.seen[1].turns.filterIsInstance<ToolTurn.ToolResults>().single()
        val rendered = results.results.single().result.toString()
        assertEquals("""{"error":"tool failed"}""", rendered)
        assertFalse(rendered.contains("db secret"))
    }

    @Test
    fun cancellationFromToolPropagates() = runTest {
        val provider = ScriptedProvider(listOf(call("boom")))
        assertFailsWith<CancellationException> {
            runner(FakeTool("boom") { throw CancellationException("stop") })
                .run(provider, "hi", emptyList())
        }
    }

    @Test
    fun unknownToolYieldsErrorAndLoopContinues() = runTest {
        val provider = ScriptedProvider(
            listOf(call("nope"), ToolStep.Final("""{"type":"text","text":"done"}""")),
        )
        val result = runner().run(provider, "hi", emptyList())
        assertEquals(ChatResponse.Text("done"), result.getOrThrow())
        val results = provider.seen[1].turns.filterIsInstance<ToolTurn.ToolResults>().single()
        assertEquals("""{"error":"tool failed"}""", results.results.single().result.toString())
    }

    @Test
    fun turnsStartWithHistoryThenUserMessage() = runTest {
        val provider = ScriptedProvider(listOf(ToolStep.Final("""{"type":"text","text":"a"}""")))
        runner().run(
            provider,
            "now",
            listOf(
                ConversationTurn(ConversationTurn.Role.User, "q1"),
                ConversationTurn(ConversationTurn.Role.Assistant, "a1"),
            ),
        )
        assertEquals(
            listOf(
                ToolTurn.User("q1"),
                ToolTurn.Assistant("a1"),
                ToolTurn.User("now"),
            ),
            provider.seen.single().turns,
        )
    }

    @Test
    fun providerFailureIsReturnedAsFailure() = runTest {
        val provider = object : ToolCallingChatProvider {
            override suspend fun supportsTools() = true
            override suspend fun step(
                system: String,
                turns: List<ToolTurn>,
                tools: List<ToolSpec>,
                allowTools: Boolean,
            ): Result<ToolStep> = Result.failure(IllegalStateException("x"))
        }
        assertTrue(runner().run(provider, "hi", emptyList()).isFailure)
    }
}
