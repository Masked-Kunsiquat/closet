package com.closet.core.data.ai

/**
 * The persisted outcome of the last Gemini Nano init (see [NanoInitResult]), so chat can say why
 * on-device AI isn't usable. [UNKNOWN] means no init has run yet (Settings → AI runs it).
 */
enum class NanoAvailability { READY, DOWNLOADING, NOT_SUPPORTED, FAILED, UNKNOWN }
