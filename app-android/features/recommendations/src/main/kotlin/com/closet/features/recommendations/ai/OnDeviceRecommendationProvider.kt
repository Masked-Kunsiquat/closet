package com.closet.features.recommendations.ai

import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.ai.OutfitAiProvider

/**
 * On-device (Gemini Nano) outfit provider. The full flavor backs it with ML Kit GenAI;
 * builds without Play Services bind [UnavailableOnDeviceRecommendationProvider].
 */
interface OnDeviceRecommendationProvider : OutfitAiProvider, NanoInitializer {
    /** Token count for [prompt]; [Int.MAX_VALUE] keeps the scorer's token-trim gate inactive. */
    suspend fun countTokens(prompt: String): Int
}
