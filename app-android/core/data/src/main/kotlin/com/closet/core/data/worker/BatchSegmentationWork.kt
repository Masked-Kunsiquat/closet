package com.closet.core.data.worker

import androidx.work.WorkInfo
import kotlinx.coroutines.flow.Flow

/**
 * Constants shared between `BatchSegmentationWorker` (features/wardrobe) and
 * `SettingsViewModel` (features/settings) so neither module needs to depend on the other.
 */
object BatchSegmentationWork {
    const val NAME = "batch_segmentation"
    const val KEY_DONE = "done"
    const val KEY_TOTAL = "total"
    const val KEY_FAILED = "failed"
}

/**
 * Abstraction that lets [SettingsViewModel] trigger batch segmentation without
 * a direct dependency on `features/wardrobe`. Bound to its concrete implementation
 * by `WardrobeModule` in `features/wardrobe`.
 */
interface BatchSegmentationScheduler {
    /** `false` on FOSS builds where ML Kit segmentation is unavailable. */
    val isSupported: Boolean
    fun schedule()

    /** Live [WorkInfo] for the unique batch segmentation job ([BatchSegmentationWork.NAME]). */
    val workInfo: Flow<WorkInfo?>
}
