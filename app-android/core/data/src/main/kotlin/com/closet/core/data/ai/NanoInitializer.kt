package com.closet.core.data.ai

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the Gemini Nano init sequence used by [SettingsViewModel].
 *
 * Defined in core/data so that features/settings can inject it without depending
 * on features/recommendations (which owns the MLKit surface).
 *
 * Implementation: [com.closet.features.recommendations.ai.NanoProvider].
 * Binding: [com.closet.features.recommendations.di.RecommendationModule].
 *
 * Init sequence:
 *   1. checkStatus() — isAvailable() gate
 *   2. download()    — streaming progress
 *   3. getTokenLimit()
 *   4. Set aiReady = true
 *
 * [initNanoFlow] emits [NanoInitResult.Downloading] zero or more times during
 * download, then emits exactly one terminal [NanoInitResult] ([Success], [Failed],
 * or [NotSupported]) and completes.
 */
interface NanoInitializer {
    /**
     * Runs the full Nano init sequence and emits progress + terminal result as a [Flow].
     *
     * - Caller is responsible for collecting in a coroutine scope (e.g. viewModelScope).
     * - The flow completes after emitting the terminal result.
     * - [NanoInitResult.Downloading] events carry a 0–100 percent value.
     */
    fun initNanoFlow(): Flow<NanoInitResult>
}
