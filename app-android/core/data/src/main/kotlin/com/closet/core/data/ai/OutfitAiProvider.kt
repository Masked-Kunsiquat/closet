package com.closet.core.data.ai

/**
 * Contract for AI providers that curate outfit recommendations from a ranked combo pool.
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
 * - Validating that all [OutfitSelection.comboId] values returned are within the range of
 *   the supplied [combos] list before mapping back to [OutfitComboPayload].
 * - Discarding the result and falling back to the programmatic top-3 on any failure.
 */
interface OutfitAiProvider {
    /**
     * Ask the AI to select the 3 best outfits from [combos] for the given [styleVibe].
     *
     * @param combos     Pool of outfit combos (up to 25) to curate from.
     * @param styleVibe  The requested aesthetic e.g. "Streetwear", "Minimalist".
     * @return [Result.success] with exactly 3 [OutfitSelection]s, or [Result.failure]
     *         on any error. Caller falls back to programmatic top-3 on failure.
     */
    suspend fun selectOutfits(
        combos: List<OutfitComboPayload>,
        styleVibe: String,
    ): Result<List<OutfitSelection>>
}
