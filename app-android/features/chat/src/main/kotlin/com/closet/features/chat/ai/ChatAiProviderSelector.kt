package com.closet.features.chat.ai

import com.closet.core.data.ai.ChatAiProvider
import com.closet.core.data.model.AiProvider
import com.closet.core.data.repository.AiPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects the active [ChatAiProvider] based on the user's configured provider and
 * its current readiness state.
 *
 * Mirrors the provider-selection logic in `OutfitCoherenceScorer` but returns a
 * [Result] rather than a nullable value, so callers can surface a human-readable
 * error message when the active provider is not configured.
 */
@Singleton
class ChatAiProviderSelector @Inject constructor(
    private val nanoProvider: NanoChatProvider,
    private val openAiProvider: OpenAiChatProvider,
    private val anthropicProvider: AnthropicChatProvider,
    private val geminiProvider: GeminiChatProvider,
    private val aiPreferencesRepository: AiPreferencesRepository,
) {

    /**
     * Returns the active [ChatAiProvider] based on the user's selected provider and
     * the current readiness state. Returns [Result.failure] if the active provider is
     * not configured (key missing or Nano not ready).
     */
    suspend fun current(): Result<ChatAiProvider> {
        return try {
            when (val provider = aiPreferencesRepository.getSelectedProvider().first()) {
                AiProvider.Nano -> {
                    val ready = aiPreferencesRepository.getAiReady().first()
                    if (!ready) {
                        Result.failure(IllegalStateException("Gemini Nano is not ready — open Settings to set it up"))
                    } else {
                        Result.success(nanoProvider)
                    }
                }
                AiProvider.OpenAi -> {
                    val key = aiPreferencesRepository.getOpenAiApiKey().first()
                    if (key.isBlank()) {
                        Result.failure(IllegalStateException("OpenAI-compatible API key is not configured"))
                    } else {
                        Result.success(openAiProvider)
                    }
                }
                AiProvider.Anthropic -> {
                    val key = aiPreferencesRepository.getAnthropicApiKey().first()
                    if (key.isBlank()) {
                        Result.failure(IllegalStateException("Anthropic API key is not configured"))
                    } else {
                        Result.success(anthropicProvider)
                    }
                }
                AiProvider.Gemini -> {
                    val key = aiPreferencesRepository.getGeminiApiKey().first()
                    if (key.isBlank()) {
                        Result.failure(IllegalStateException("Gemini API key is not configured"))
                    } else {
                        Result.success(geminiProvider)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    /** The [AiProvider.label] of the currently selected provider. */
    fun providerLabel(): Flow<String> = aiPreferencesRepository.getSelectedProvider()
        .map { it.label }
}
