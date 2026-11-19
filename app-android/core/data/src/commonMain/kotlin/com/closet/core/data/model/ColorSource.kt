package com.closet.core.data.model

/**
 * Who set an item's colour row (`clothing_item_colors.source`).
 *
 * - [AUTO]: measured from the photo by the colour extractor; a later extraction may replace it.
 * - [USER]: picked or kept by the user; extraction never overwrites an item that has one.
 * - [NANO]: suggested by on-device Gemini Nano.
 */
object ColorSource {
    const val AUTO = "auto"
    const val USER = "user"
    const val NANO = "nano"
}
