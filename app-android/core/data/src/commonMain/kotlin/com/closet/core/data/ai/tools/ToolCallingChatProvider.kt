package com.closet.core.data.ai.tools

/** A chat backend that can call [ToolSpec]s. Implementations map to each provider's native API. */
interface ToolCallingChatProvider {
    /** Whether this provider can currently do tool calling. */
    suspend fun supportsTools(): Boolean

    /**
     * Runs one model step over [turns]. With [allowTools] false the model must answer ([ToolStep.Final]).
     * Never throws except for cancellation; failures come back as [Result.failure].
     */
    suspend fun step(
        system: String,
        turns: List<ToolTurn>,
        tools: List<ToolSpec>,
        allowTools: Boolean,
    ): Result<ToolStep>
}
