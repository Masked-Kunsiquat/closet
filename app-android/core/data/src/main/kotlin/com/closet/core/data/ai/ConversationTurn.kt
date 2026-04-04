package com.closet.core.data.ai

/**
 * A single turn in a multi-turn conversation, passed as rolling history to
 * [ChatAiProvider] implementations.
 *
 * Each provider maps [Role] to its own API terminology:
 * - Anthropic / OpenAI: [Role.User] → "user", [Role.Assistant] → "assistant"
 * - Gemini native:      [Role.User] → "user", [Role.Assistant] → "model"
 * - Nano:               flattened into a formatted text prefix (single-prompt API)
 */
data class ConversationTurn(val role: Role, val text: String) {
    enum class Role { User, Assistant }
}
