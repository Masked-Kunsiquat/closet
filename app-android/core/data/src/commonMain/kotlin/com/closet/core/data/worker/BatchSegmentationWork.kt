package com.closet.core.data.worker

import kotlinx.coroutines.flow.Flow

/**
 * Constants shared between `BatchSegmentationWorker` (features/wardrobe) and
 * `SettingsViewModel` (features/settings) so neither module needs to depend on the other.
 */
object BatchSegmentationWork {
    const val NAME = "batch_segmentation"
    const val KEY_DONE = WorkStatus.KEY_DONE
    const val KEY_TOTAL = WorkStatus.KEY_TOTAL
    const val KEY_FAILED = WorkStatus.KEY_FAILED
}

/**
 * Lets `SettingsViewModel` trigger batch segmentation without a direct dependency on
 * `features/wardrobe`. On Android it is bound by `wardrobeModule` in `features/wardrobe`;
 * JVM and iOS bind [NoOpBatchSegmentationScheduler].
 */
interface BatchSegmentationScheduler {
    /** `false` on FOSS builds where ML Kit segmentation is unavailable, and on JVM/iOS. */
    val isSupported: Boolean
    fun schedule()

    /** Live status of the unique batch segmentation job ([BatchSegmentationWork.NAME]); null when none. */
    val workStatus: Flow<WorkStatus?>
}
