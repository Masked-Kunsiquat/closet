package com.closet.features.chat.ai

import com.closet.core.data.ai.ChatAiProvider
import com.closet.core.data.ai.ProviderNotConfiguredException
import com.closet.core.data.model.AiProvider
import com.closet.core.data.repository.AiPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Selects the active [ChatAiProvider] based on the user's configured provider and
 * its current readiness state.
 *
 * Mirrors the provider-selection logic in `OutfitCoherenceScorer` but returns a
 * [Result] rather than a nullable value, so callers can surface a human-readable
 * error message when the active provider is not configured.
 */
class ChatAiProviderSelector(
    private val nanoProvider: OnDeviceChatProvider,
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
                        Result.failure(ProviderNotConfiguredException("Gemini Nano is not ready — open Settings to set it up"))
                    } else {
                        Result.success(nanoProvider)
                    }
                }
                AiProvider.OpenAi -> {
                    val key = aiPreferencesRepository.getOpenAiApiKey().first()
                    if (key.isBlank()) {
                        Result.failure(ProviderNotConfiguredException("OpenAI-compatible API key is not configured"))
                    } else {
                        Result.success(openAiProvider)
                    }
                }
                AiProvider.Anthropic -> {
                    val key = aiPreferencesRepository.getAnthropicApiKey().first()
                    if (key.isBlank()) {
                        Result.failure(ProviderNotConfiguredException("Anthropic API key is not configured"))
                    } else {
                        Result.success(anthropicProvider)
                    }
                }
                AiProvider.Gemini -> {
                    val key = aiPreferencesRepository.getGeminiApiKey().first()
                    if (key.isBlank()) {
                        Result.failure(ProviderNotConfiguredException("Gemini API key is not configured"))
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

    /**
     * The header label for the currently selected provider: its [AiProvider.label], or for Nano
     * "On-device (<model>)" when the on-device provider reports a model label.
     */
    fun providerLabel(): Flow<String> = aiPreferencesRepository.getSelectedProvider()
        .map { provider ->
            val modelLabel = if (provider == AiProvider.Nano) nanoProvider.modelLabel() else null
            chatProviderLabel(provider, modelLabel)
        }
}

/**
 * Formats the chat header provider label. For [AiProvider.Nano] with a non-blank [modelLabel] it
 * is "On-device (<modelLabel>)"; otherwise the provider's own [AiProvider.label].
 */
internal fun chatProviderLabel(provider: AiProvider, modelLabel: String?): String {
    val model = modelLabel?.trim()
    return if (provider == AiProvider.Nano && !model.isNullOrEmpty()) "On-device ($model)" else provider.label
}
