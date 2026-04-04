package com.closet.core.data.ai

/**
 * Contract for AI providers that answer free-form wardrobe questions.
 *
 * Parallel to [OutfitAiProvider] but designed for open-ended chat: the caller
 * passes a user message and a pre-built wardrobe context block, and the provider
 * returns a structured [ChatResponse] describing what the UI should render.
 *
 * Implementations live in `features/chat/`:
 * - `NanoChatProvider`     — on-device Gemini Nano (full flavor only)
 * - `AnthropicChatProvider`— Claude via Anthropic Messages API
 * - `OpenAiChatProvider`   — any OpenAI Chat Completions-compatible endpoint
 * - `GeminiChatProvider`   — Google Gemini via native generateContent API
 *
 * All providers instruct the model to return a structured JSON object matching
 * one of the [ChatResponse] subtypes. [ChatResponseParser] handles parsing.
 */
interface ChatAiProvider {
    /**
     * Sends [userMessage] to the model with [context] as the wardrobe context block.
     *
     * [context] is a formatted prose block built by `ChatRepository.buildContextBlock`
     * listing the top-K retrieved items with their IDs, names, wear counts, and metadata.
     * The system prompt instructs the model to reference only IDs present in this block.
     *
     * [history] is an optional rolling list of prior turns (capped to the last 3 exchanges
     * by the caller). Providers map this to their native message-array format; Nano
     * flattens the last 1 exchange into the single-string prompt.
     *
     * @return [Result.success] with a parsed [ChatResponse], or [Result.failure] on any
     *         error (key missing, HTTP error, JSON parse failure). Never throws.
     */
    suspend fun chat(
        userMessage: String,
        context: String,
        history: List<ConversationTurn> = emptyList(),
    ): Result<ChatResponse>
}

/** Structured response returned by every [ChatAiProvider] implementation. */
sealed interface ChatResponse {
    /** Plain conversational answer — no specific items to surface in the UI. */
    data class Text(val text: String) : ChatResponse

    /**
     * Answer references a set of specific wardrobe items (e.g. "haven't worn lately").
     * [itemIds] are resolved DB item IDs from the context block — always from the wardrobe.
     */
    data class WithItems(val text: String, val itemIds: List<Long>) : ChatResponse

    /**
     * Answer is an outfit suggestion.
     * [itemIds] are the 2–4 items forming the outfit.
     * [reason] is the AI's one-sentence rationale.
     */
    data class WithOutfit(val text: String, val itemIds: List<Long>, val reason: String) : ChatResponse
}
