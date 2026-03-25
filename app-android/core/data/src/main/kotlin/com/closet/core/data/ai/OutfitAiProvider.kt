package com.closet.core.data.ai

/**
 * Contract for AI providers that re-rank outfit candidates via coherence scoring.
 *
 * Modelled on the weather feature's [com.closet.core.data.weather.WeatherServiceClient]
 * pattern — lives in `core/data` so it can be injected from any module without creating
 * cross-feature dependencies.
 *
 * Implementations:
 * - `NanoProvider`       — on-device MLKit GenAI Prompt API; no API key; F-Droid default.
 *                          Lives in `features/recommendations/ai/` due to MLKit dependency.
 * - `OpenAiProvider`     — OpenAI-compatible HTTP endpoint (OpenAI, Gemini cloud, Ollama, Groq).
 * - `AnthropicProvider`  — Claude via Anthropic API (separate impl; different endpoint + header).
 *
 * The caller is responsible for:
 * - Checking [com.closet.core.data.repository.AiPreferencesRepository.getAiReady] (Nano)
 *   or key presence (cloud providers) before invoking.
 * - Validating [OutfitSuggestion.selectedIds] against the original candidate list.
 * - Discarding the result and falling back to the programmatic top-3 on any failure.
 */
interface OutfitAiProvider {
    /**
     * Ask the AI provider to select an outfit from [candidates].
     *
     * @param candidates Pre-filtered, programmatically ranked items (the payload). The
     *                   provider must not see items that haven't passed the hard-filter step.
     * @return [Result.success] with an [OutfitSuggestion] on a well-formed response, or
     *         [Result.failure] on network error, JSON parse failure, or unsupported device.
     *         The caller treats any [Result.failure] as a silent fallback to programmatic results.
     */
    suspend fun suggestOutfit(candidates: List<ClothingItemDto>): Result<OutfitSuggestion>
}
