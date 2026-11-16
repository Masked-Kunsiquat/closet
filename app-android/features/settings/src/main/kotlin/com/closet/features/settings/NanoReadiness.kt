package com.closet.features.settings

import com.closet.core.data.ai.NanoInitResult

/**
 * The `aiReady` value to persist for a Nano init [result]: `true` once Nano is ready,
 * `false` when it failed or isn't supported, and `null` (leave unchanged) while downloading.
 */
internal fun nanoReadyFor(result: NanoInitResult): Boolean? = when (result) {
    is NanoInitResult.Success -> true
    is NanoInitResult.Failed -> false
    NanoInitResult.NotSupported -> false
    is NanoInitResult.Downloading -> null
}
