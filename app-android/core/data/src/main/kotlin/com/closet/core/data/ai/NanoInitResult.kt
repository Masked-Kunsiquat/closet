package com.closet.core.data.ai

/**
 * Result type returned by [NanoInitializer.initNanoFlow].
 *
 * The init sequence is: isAvailable() → download() (streaming progress) → getTokenLimit().
 * Progress events are emitted as a [kotlinx.coroutines.flow.Flow] of [Downloading] from
 * the implementation; the terminal event is always [Success], [Failed], or [NotSupported].
 */
sealed class NanoInitResult {
    /** Nano is downloaded, ready, and the token limit has been fetched. */
    data class Success(val tokenLimit: Int) : NanoInitResult()

    /**
     * Model download is in progress. [progressPct] is 0–100.
     * Emitted as intermediate events from [NanoInitializer.initNanoFlow]; never returned
     * as the terminal result of [NanoInitializer.initNano].
     */
    data class Downloading(val progressPct: Int) : NanoInitResult()

    /** Init failed for an unexpected reason. [reason] is a human-readable description. */
    data class Failed(val reason: String) : NanoInitResult()

    /** The device does not meet the hardware/software requirements for Gemini Nano. */
    data object NotSupported : NanoInitResult()
}
