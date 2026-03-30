package com.closet.features.settings

/**
 * UI state for the Nano (on-device) AI model init sequence shown in the Settings AI section.
 *
 * Lifecycle:
 *   Idle → Checking → Downloading(pct)* → Ready
 *                  → Failed(msg)
 *                  → NotSupported
 *
 * [Downloading] may be emitted zero or more times between [Checking] and the terminal state.
 */
sealed class NanoStatus {
    /** Initial state — no init has been attempted yet. */
    data object Idle : NanoStatus()

    /** checkStatus() is in progress. Show an indeterminate progress indicator. */
    data object Checking : NanoStatus()

    /**
     * Model download is in progress. [progressPct] is 0–100.
     * Show a determinate [LinearProgressIndicator].
     */
    data class Downloading(val progressPct: Int) : NanoStatus()

    /** Model is downloaded and ready for inference. Show a green checkmark. */
    data object Ready : NanoStatus()

    /** Init failed. Show [message] in [MaterialTheme.colorScheme.error]. */
    data class Failed(val message: String) : NanoStatus()

    /** The device does not meet hardware/software requirements for Gemini Nano. */
    data object NotSupported : NanoStatus()
}
