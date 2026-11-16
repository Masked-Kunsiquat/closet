package com.closet.core.data.ai

/**
 * Platform-neutral failure from on-device AI (Gemini Nano). The GMS layer maps ML Kit's
 * `GenAiException` codes onto [Reason] so features can show an actionable message without
 * importing any GMS type. [message] is [Reason.userMessage].
 */
class OnDeviceAiException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(reason.userMessage, cause) {

    enum class Reason(val userMessage: String) {
        NOT_AVAILABLE("On-device AI isn't available right now. Check Settings → AI."),
        BUSY("On-device AI is busy. Try again in a moment."),
        QUOTA_EXCEEDED("On-device AI has hit its daily usage limit. Try again later or switch providers in Settings."),
        BACKGROUND_BLOCKED("On-device AI only works while hangr is open on screen."),
        REQUEST_TOO_LARGE("That request is too long for on-device AI. Try a shorter question."),
        NEEDS_UPDATE("On-device AI needs a system update (AICore / Play services)."),
        FAILED("On-device AI couldn't answer. Try again."),
    }
}
