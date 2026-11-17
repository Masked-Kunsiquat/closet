package com.closet.core.data.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A [NanoInitializer] for platforms without Gemini Nano: reports [NanoInitResult.NotSupported] once. */
object UnavailableNanoInitializer : NanoInitializer {
    override fun initNanoFlow(): Flow<NanoInitResult> = flowOf(NanoInitResult.NotSupported)
}
