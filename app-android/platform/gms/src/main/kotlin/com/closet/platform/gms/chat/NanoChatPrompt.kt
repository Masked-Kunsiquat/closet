package com.closet.platform.gms.chat

import com.closet.core.data.ai.ConversationTurn

/** One multi-turn content entry, before it becomes an ML Kit `Content`. */
internal data class NanoTurn(val isUser: Boolean, val text: String)

/**
 * Drops the oldest entries of [turns] until [fits] accepts the rest. If even one turn doesn't
 * fit, returns an empty list without asking again: the caller sends the request anyway and lets
 * it fail through the normal error mapping (`REQUEST_TOO_LARGE`).
 *
 * `inline` so [fits] can call suspend functions (`countTokens`).
 */
internal inline fun <T> trimOldest(turns: List<T>, fits: (List<T>) -> Boolean): List<T> {
    var kept = turns
    while (kept.isNotEmpty() && !fits(kept)) kept = kept.drop(1)
    return kept
}

/**
 * Builds the multi-turn contents for a Nano chat request: [history] oldest first, then a final
 * user turn holding the context block and [userMessage].
 *
 * A leading assistant turn (left behind when trimming drops an odd number of turns) is dropped so
 * the conversation starts with the user. When [inlineSystemPrompt] is non-null (the model has no
 * system-instruction support), it is prepended to the first user turn.
 */
internal fun nanoTurns(
    history: List<ConversationTurn>,
    context: String,
    userMessage: String,
    inlineSystemPrompt: String?,
): List<NanoTurn> {
    val turns = history
        .dropWhile { it.role == ConversationTurn.Role.Assistant }
        .map { NanoTurn(isUser = it.role == ConversationTurn.Role.User, text = it.text) } +
        NanoTurn(isUser = true, text = "Context:\n$context\n\n$userMessage")
    if (inlineSystemPrompt == null) return turns
    // The final turn is always a user turn, so there is always a first user turn.
    val first = turns.indexOfFirst { it.isUser }
    return turns.mapIndexed { i, turn ->
        if (i == first) turn.copy(text = "$inlineSystemPrompt\n\n${turn.text}") else turn
    }
}
