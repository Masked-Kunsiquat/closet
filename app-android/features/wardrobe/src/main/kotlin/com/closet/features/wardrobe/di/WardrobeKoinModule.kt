package com.closet.features.wardrobe.di

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.closet.core.data.worker.BatchSegmentationScheduler
import com.closet.core.data.worker.BatchSegmentationWork
import com.closet.features.wardrobe.BatchSegmentationWorker
import com.closet.features.wardrobe.BrandManagementViewModel
import com.closet.features.wardrobe.BulkWashViewModel
import com.closet.features.wardrobe.ClosetViewModel
import com.closet.features.wardrobe.ClothingDetailViewModel
import com.closet.features.wardrobe.ClothingFormViewModel
import com.closet.features.wardrobe.repository.SubjectSegmenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

private val activeStates = setOf(
    WorkInfo.State.ENQUEUED,
    WorkInfo.State.RUNNING,
    WorkInfo.State.BLOCKED,
)

/**
 * Koin module for the wardrobe feature. SubjectSegmenter and the
 * CaptionEnrichmentProvider binding live in `wardrobeFlavorModule` (src/full, src/foss).
 * WorkManager is provided by `dataModule`.
 */
val wardrobeModule = module {
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
            override val workInfo: Flow<WorkInfo?> =
                workManager.getWorkInfosForUniqueWorkFlow(BatchSegmentationWork.NAME)
                    .map { list ->
                        list.firstOrNull { it.state in activeStates } ?: list.firstOrNull()
                    }
        }
    }
    workerOf(::BatchSegmentationWorker)
    viewModelOf(::BrandManagementViewModel)
    viewModelOf(::BulkWashViewModel)
    viewModelOf(::ClosetViewModel)
    viewModelOf(::ClothingDetailViewModel)
    viewModelOf(::ClothingFormViewModel)
}
