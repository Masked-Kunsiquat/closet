package com.closet.features.wardrobe.di

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.closet.core.data.repository.CaptionEnrichmentProvider
import com.closet.core.data.worker.BatchSegmentationScheduler
import com.closet.core.data.worker.BatchSegmentationWork
import com.closet.features.wardrobe.BatchSegmentationWorker
import com.closet.features.wardrobe.repository.ImageCaptionRepository
import com.closet.features.wardrobe.repository.SegmentationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WardrobeModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideCaptionEnrichmentProvider(
        imageCaptionRepository: ImageCaptionRepository,
    ): CaptionEnrichmentProvider = imageCaptionRepository

    @Provides
    @Singleton
    fun provideBatchSegmentationScheduler(
        workManager: WorkManager,
        segmentationRepository: SegmentationRepository,
    ): BatchSegmentationScheduler =
        object : BatchSegmentationScheduler {
            override val isSupported: Boolean get() = segmentationRepository.isSupported
            override fun schedule() {
                workManager.enqueueUniqueWork(
                    BatchSegmentationWork.NAME,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<BatchSegmentationWorker>().build(),
                )
            }
        }
}
