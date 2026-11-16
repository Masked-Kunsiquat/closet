package com.closet.platform.gms.chat

import com.closet.core.data.ai.ChatAiProvider
import com.closet.core.data.ai.ChatPromptPrefix
import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ChatResponseParser
import com.closet.core.data.ai.ConversationTurn
import com.closet.core.data.ai.OnDeviceAiException
import com.closet.core.data.ai.tools.ToolCallingChatProvider
import com.closet.core.data.ai.tools.ToolSpec
import com.closet.core.data.ai.tools.ToolStep
import com.closet.core.data.ai.tools.ToolTurn
import com.closet.features.chat.ai.OnDeviceChatProvider
import com.closet.platform.gms.shouldFallBackToPlainText
import com.closet.platform.gms.toOnDeviceAiException
import com.closet.platform.gms.typedResponseOrNull
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Content
import com.google.mlkit.genai.prompt.Feature
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.content
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import com.google.mlkit.genai.schema.tools.FunctionDeclaration
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * [ChatAiProvider] backed by the MLKit GenAI Prompt API (Gemini Nano on-device).
 *
 * No API key required. Runs entirely on-device via Android AICore. Available on
 * supported devices (Pixel 6+, Galaxy S24+, etc.) once the Nano model is downloaded.
 *
 * Returns [Result.failure] immediately if [Generation.getClient] reports a status
 * other than [FeatureStatus.AVAILABLE] — callers should check
 * [com.closet.core.data.repository.AiPreferencesRepository.getAiReady] before invoking.
 *
 * The request is multi-turn: history turns as user/model contents (oldest first), then a user
 * content holding the context block and the message. [ChatPromptPrefix.SYSTEM_PROMPT] goes in
 * the system instruction when the model supports one, otherwise it is prepended to the first
 * user content. Oldest history turns are dropped until the request fits the model's token limit.
 *
 * When the model supports structured output the reply is decoded into [NanoChatReply]; if it
 * doesn't, or the structured call fails in a way [shouldFallBackToPlainText] accepts or returns no
 * reply, the call falls back to plain text. Both paths end in [ChatResponseParser].
 *
 * **Tool calling.** When the model reports [Feature.FUNCTION_CALLING], [supportsTools] is true and
 * `ChatRepository` drives [step] through `ToolChatRunner` instead of [chat]. Each step sends the
 * tool conversation (see [toContents]) with the tools declared; a candidate with function calls
 * becomes [ToolStep.Calls], otherwise its text is the [ToolStep.Final] reply. The forced final
 * step (tools disabled) uses the structured [NanoChatReply] path when the model supports it.
 */
class NanoChatProvider(
    private val json: Json,
) : OnDeviceChatProvider, ToolCallingChatProvider {

    private val model by lazy { Generation.getClient() }

    // Cached per client. Volatile only for visibility; a racing duplicate lookup is harmless.
    @Volatile private var cachedTokenLimit: Int? = null
    @Volatile private var cachedModelLabel: String? = null
    @Volatile private var cachedSupportsTools: Boolean? = null

    companion object {
        private const val TAG = "NanoChatProvider"
        private const val MAX_OUTPUT_TOKENS = 1024
        private const val MAX_HISTORY_TURNS = 6
    }

    override suspend fun chat(
        userMessage: String,
        context: String,
        history: List<ConversationTurn>,
    ): Result<ChatResponse> {
        val status = try {
            model.checkStatus()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).w(e, "NanoChatProvider: checkStatus threw — AICore unavailable")
            return Result.failure(e.toOnDeviceAiException())
        }

        if (status != FeatureStatus.AVAILABLE) {
            Timber.tag(TAG).w("NanoChatProvider: Nano not available (status=%d)", status)
            return Result.failure(OnDeviceAiException(OnDeviceAiException.Reason.NOT_AVAILABLE))
        }

        return try {
            val systemInstruction = supports("isSystemPromptAvailable") { model.isSystemPromptAvailable() }
            val structured = supports("isStructuredOutputFeatureAvailable") {
                model.isStructuredOutputFeatureAvailable()
            }
            val build = { turns: List<ConversationTurn> ->
                buildRequest(turns, context, userMessage, systemInstruction)
            }
            val turns = fitHistory(history.takeLast(MAX_HISTORY_TURNS), build) { request ->
                if (structured) {
                    model.countTokens(generateTypedContentRequest(request, NanoChatReply::class)).totalTokens
                } else {
                    model.countTokens(request).totalTokens
                }
            }
            val request = build(turns)
            if (structured) {
                generateStructured(request)?.let { return it }
            }
            generatePlain(request)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).w(e, "NanoChatProvider inference failed")
            Result.failure(e.toOnDeviceAiException())
        }
    }

    /**
     * Whether the on-device model supports function calling, cached once known. A failed lookup
     * (e.g. AICore unavailable) returns false without caching, so a later query can retry.
     */
    override suspend fun supportsTools(): Boolean {
        cachedSupportsTools?.let { return it }
        return try {
            model.getAvailableFeatures().contains(Feature.FUNCTION_CALLING).also { cachedSupportsTools = it }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).d(e, "getAvailableFeatures failed — tools unsupported")
            false
        }
    }

    override suspend fun step(
        system: String,
        turns: List<ToolTurn>,
        tools: List<ToolSpec>,
        allowTools: Boolean,
    ): Result<ToolStep> {
        val status = try {
            model.checkStatus()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).w(e, "NanoChatProvider: checkStatus threw — AICore unavailable")
            return Result.failure(e.toOnDeviceAiException())
        }
        if (status != FeatureStatus.AVAILABLE) {
            Timber.tag(TAG).w("NanoChatProvider: Nano not available (status=%d)", status)
            return Result.failure(OnDeviceAiException(OnDeviceAiException.Reason.NOT_AVAILABLE))
        }

        return try {
            val systemInstruction = supports("isSystemPromptAvailable") { model.isSystemPromptAvailable() }
            // Typed output only for the forced final answer; a tool round must be free to call tools.
            val structured = !allowTools && supports("isStructuredOutputFeatureAvailable") {
                model.isStructuredOutputFeatureAvailable()
            }
            val declarations = if (allowTools) tools.map { it.toFunctionDeclaration() } else null
            val build = { kept: List<ToolTurn> ->
                buildToolRequest(kept, system, systemInstruction, declarations)
            }
            val kept = fitToolTurns(turns, build) { request ->
                if (structured) {
                    model.countTokens(generateTypedContentRequest(request, NanoChatReply::class)).totalTokens
                } else {
                    model.countTokens(request).totalTokens
                }
            }
            val request = build(kept)
            if (structured) {
                generateStructuredJson(request)?.let { return Result.success(ToolStep.Final(it)) }
            }
            val candidate = model.generateContent(request).candidates.firstOrNull()
                ?: throw OnDeviceAiException(OnDeviceAiException.Reason.FAILED)
            val step = candidateStep(candidate.text, candidate.functionCalls, round = toolRound(turns))
                ?: throw OnDeviceAiException(OnDeviceAiException.Reason.FAILED)
            Result.success(step)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).w(e, "NanoChatProvider tool step failed")
            Result.failure(e.toOnDeviceAiException())
        }
    }

    /** The base model AICore serves (e.g. a Nano version), cached; null if unknown. */
    override suspend fun modelLabel(): String? {
        cachedModelLabel?.let { return it }
        return try {
            model.getBaseModelName().trim().takeIf { it.isNotEmpty() }?.also { cachedModelLabel = it }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).d(e, "getBaseModelName failed")
            null
        }
    }

    private fun buildRequest(
        history: List<ConversationTurn>,
        context: String,
        userMessage: String,
        systemInstruction: Boolean,
    ): GenerateContentRequest {
        val contents = nanoTurns(
            history = history,
            context = context,
            userMessage = userMessage,
            inlineSystemPrompt = if (systemInstruction) null else ChatPromptPrefix.SYSTEM_PROMPT,
        ).map { turn ->
            content(if (turn.isUser) Content.Role.USER else Content.Role.MODEL) { text(turn.text) }
        }
        return GenerateContentRequest.Builder(contents).apply {
            if (systemInstruction) this.systemInstruction = SystemInstruction(ChatPromptPrefix.SYSTEM_PROMPT)
            temperature = 0.2f
            topK = 40
            maxOutputTokens = MAX_OUTPUT_TOKENS
        }.build()
    }

    /**
     * Drops the oldest history turns while the request is over `tokenLimit - MAX_OUTPUT_TOKENS`.
     * Skips trimming if the limit is unknown (≤ 0); stops trimming if counting fails.
     */
    private suspend inline fun fitHistory(
        history: List<ConversationTurn>,
        build: (List<ConversationTurn>) -> GenerateContentRequest,
        countTokens: (GenerateContentRequest) -> Int,
    ): List<ConversationTurn> {
        val budget = tokenLimit() - MAX_OUTPUT_TOKENS
        if (budget <= 0 || history.isEmpty()) return history
        return trimOldest(history) { turns ->
            try {
                countTokens(build(turns)) <= budget
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.tag(TAG).d(e, "countTokens failed — history not trimmed")
                true
            }
        }
    }

    private fun buildToolRequest(
        turns: List<ToolTurn>,
        system: String,
        systemInstruction: Boolean,
        declarations: List<FunctionDeclaration>?,
    ): GenerateContentRequest {
        val contents = turns.toContents(inlineSystemPrompt = if (systemInstruction) null else system)
        return GenerateContentRequest.Builder(contents).apply {
            if (systemInstruction) this.systemInstruction = SystemInstruction(system)
            temperature = 0.2f
            topK = 40
            maxOutputTokens = MAX_OUTPUT_TOKENS
            if (declarations != null) setTools(declarations)
        }.build()
    }

    /**
     * Fits the tool conversation into the token budget:
     * 1. The prior conversation (every turn before the last [ToolTurn.User], the current message)
     *    is trimmed the way [chat] trims history: at most [MAX_HISTORY_TURNS], no leading
     *    assistant turn, then oldest first.
     * 2. If the request is still over budget with no history left, this turn's oldest tool rounds
     *    are dropped whole ([trimToolRounds]); the current message and the latest round stay.
     *    The runner already recorded the dropped results' item ids, so validation is unaffected.
     */
    private suspend inline fun fitToolTurns(
        turns: List<ToolTurn>,
        build: (List<ToolTurn>) -> GenerateContentRequest,
        countTokens: (GenerateContentRequest) -> Int,
    ): List<ToolTurn> {
        val current = turns.indexOfLast { it is ToolTurn.User }
        if (current < 0) return turns
        val tail = turns.subList(current, turns.size)
        val history = turns.subList(0, current).takeLast(MAX_HISTORY_TURNS).dropWhile { it !is ToolTurn.User }
        val budget = tokenLimit() - MAX_OUTPUT_TOKENS
        if (budget <= 0) return history + tail
        val keptHistory = trimOldest(history) { candidate ->
            withinBudget(budget) { countTokens(build(candidate.dropWhile { it !is ToolTurn.User } + tail)) }
        }.dropWhile { it !is ToolTurn.User }
        if (keptHistory.isNotEmpty()) return keptHistory + tail
        return trimToolRounds(tail) { candidate -> withinBudget(budget) { countTokens(build(candidate)) } }
    }

    /** True when [count] is within [budget]; a failed count counts as fitting (nothing trimmed). */
    private inline fun withinBudget(budget: Int, count: () -> Int): Boolean = try {
        count() <= budget
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.tag(TAG).d(e, "countTokens failed — request not trimmed")
        true
    }

    /**
     * The structured final answer as parser JSON, or null to fall back to plain text (same rules
     * as [generateStructured]).
     */
    private suspend fun generateStructuredJson(request: GenerateContentRequest): String? {
        val reply = try {
            val response = model.generateContent(generateTypedContentRequest(request, NanoChatReply::class))
            typedResponseOrNull(response.candidates.map { it.response }) { it.isEmpty() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!shouldFallBackToPlainText(e)) throw e
            Timber.tag(TAG).d(e, "Structured tool answer failed — falling back to plain text")
            return null
        }
        if (reply == null) Timber.tag(TAG).d("Structured tool answer empty — falling back to plain text")
        return reply?.toParserJson()
    }

    /**
     * Typed reply, or null to fall back to plain text (see [shouldFallBackToPlainText]; also when
     * the typed response is missing).
     */
    private suspend fun generateStructured(request: GenerateContentRequest): Result<ChatResponse>? {
        val reply = try {
            val response = model.generateContent(generateTypedContentRequest(request, NanoChatReply::class))
            // A blank reply counts as no reply, so the plain-text fallback below runs.
            typedResponseOrNull(response.candidates.map { it.response }) { it.isEmpty() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!shouldFallBackToPlainText(e)) throw e
            Timber.tag(TAG).d(e, "Structured output failed — falling back to plain text")
            return null
        } ?: run {
            Timber.tag(TAG).d("Structured output returned no reply — falling back to plain text")
            return null
        }
        // A parse failure stays a ChatResponseParseException (not an on-device failure).
        return reply.toChatResponse()
    }

    private suspend fun generatePlain(request: GenerateContentRequest): Result<ChatResponse> {
        val responseText = model.generateContent(request).candidates.firstOrNull()?.text
            ?: throw OnDeviceAiException(OnDeviceAiException.Reason.FAILED)
        // A parse failure stays a ChatResponseParseException (not an on-device failure).
        return ChatResponseParser.parse(responseText)
    }

    /** The model's input token limit, cached once known; 0 if ML Kit can't report it. */
    private suspend fun tokenLimit(): Int {
        cachedTokenLimit?.let { return it }
        return try {
            model.getTokenLimit().also { cachedTokenLimit = it }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).d(e, "getTokenLimit failed — history not trimmed")
            0
        }
    }

    /** Feature check that treats a failure as "not supported". */
    private suspend inline fun supports(name: String, check: () -> Boolean): Boolean = try {
        check()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.tag(TAG).d(e, "%s failed — treating as unsupported", name)
        false
    }
}
