package com.closet.features.wardrobe.di

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.closet.core.data.worker.BatchSegmentationScheduler
import com.closet.core.data.worker.BatchSegmentationWork
import com.closet.core.data.worker.ColorRedetectScheduler
import com.closet.core.data.worker.ColorRedetectWork
import com.closet.core.data.worker.WorkStatus
import com.closet.core.data.worker.toWorkStatus
import com.closet.features.wardrobe.AndroidCategoryShortcutPinner
import com.closet.features.wardrobe.BatchSegmentationWorker
import com.closet.features.wardrobe.CategoryShortcutPinner
import com.closet.features.wardrobe.ColorRedetectWorker
import com.closet.features.wardrobe.photo.AndroidItemPhotoProcessor
import com.closet.features.wardrobe.photo.ItemPhotoProcessor
import com.closet.features.wardrobe.repository.SubjectSegmenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

private val activeStates = setOf(
    WorkInfo.State.ENQUEUED,
    WorkInfo.State.RUNNING,
    WorkInfo.State.BLOCKED,
)

/** The unique job [name]'s status: the active run if any, else the most recent one. */
private fun WorkManager.uniqueWorkStatus(name: String): Flow<WorkStatus?> =
    getWorkInfosForUniqueWorkFlow(name).map { list ->
        (list.firstOrNull { it.state in activeStates } ?: list.firstOrNull())?.toWorkStatus()
    }

/**
 * Koin module for the wardrobe feature on Android: [commonWardrobeModule] (the ViewModels) plus
 * the Android photo pipeline, launcher shortcut pinner, and the batch segmentation and bulk colour
 * re-detect schedulers and workers.
 * SubjectSegmenter and the
 * CaptionEnrichmentProvider bindings are per flavor: `wardrobeNoOpModule` (foss) or
 * `gmsModule` in `platform/gms` (full).
 * WorkManager is provided by `dataModule`.
 */
val wardrobeModule = module {
    includes(commonWardrobeModule)
    single<ItemPhotoProcessor> { AndroidItemPhotoProcessor(get(), get(), get()) }
    single<CategoryShortcutPinner> { AndroidCategoryShortcutPinner(androidContext()) }
    single<BatchSegmentationScheduler> {
        val workManager: WorkManager = get()
        val segmentationRepository: SubjectSegmenter = get()
        object : BatchSegmentationScheduler {
            override val isSupported: Boolean get() = segmentationRepository.isSupported
            override fun schedule() {
                workManager.enqueueUniqueWork(
                    BatchSegmentationWork.NAME,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<BatchSegmentationWorker>().build(),
                )
            }
            override val workStatus: Flow<WorkStatus?> =
                workManager.uniqueWorkStatus(BatchSegmentationWork.NAME)
        }
    }
    workerOf(::BatchSegmentationWorker)
    // Both flavors: colour extraction needs no Play Services (#128).
    single<ColorRedetectScheduler> {
        val workManager: WorkManager = get()
        object : ColorRedetectScheduler {
            override val isSupported: Boolean = true
            override fun schedule(onlyUncurated: Boolean) {
                // KEEP: a second tap while a run is pending or running doesn't restart it.
                workManager.enqueueUniqueWork(
                    ColorRedetectWork.NAME,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ColorRedetectWorker>()
                        .setInputData(workDataOf(ColorRedetectWork.KEY_ONLY_UNCURATED to onlyUncurated))
                        .build(),
                )
            }
            override val workStatus: Flow<WorkStatus?> =
                workManager.uniqueWorkStatus(ColorRedetectWork.NAME)
        }
    }
    workerOf(::ColorRedetectWorker)
}
