package com.closet.features.wardrobe

/**
 * The progress value for a worker's API 36+ `Notification.ProgressStyle` Live Update: [processed]
 * of [total] on the style's default 0..100 scale (a style with no segments has a maximum of 100),
 * clamped to it; 0 when [total] is 0.
 */
internal fun liveUpdateProgress(processed: Int, total: Int): Int =
    if (total > 0) (processed * 100 / total).coerceIn(0, 100) else 0
