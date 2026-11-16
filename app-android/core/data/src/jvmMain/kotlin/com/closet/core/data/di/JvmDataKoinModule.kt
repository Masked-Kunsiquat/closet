package com.closet.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.ai.UnavailableNanoInitializer
import com.closet.core.data.getDatabaseBuilder
import com.closet.core.data.location.LocationProvider
import com.closet.core.data.location.NoLocationProvider
import com.closet.core.data.repository.AI_PREFS_NAME
import com.closet.core.data.repository.BatchCaptioner
import com.closet.core.data.repository.IMAGES_DIR_NAME
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.repository.ImageStorageStats
import com.closet.core.data.repository.InMemorySecretStore
import com.closet.core.data.repository.JvmImagePathResolver
import com.closet.core.data.repository.JvmImageStorageStats
import com.closet.core.data.repository.NoOpBatchCaptioner
import com.closet.core.data.repository.SecretStore
import com.closet.core.data.repository.WEATHER_PREFS_NAME
import com.closet.core.data.repository.createPreferencesDataStore
import com.closet.core.data.repository.jvmPreferencesPath
import com.closet.core.data.util.EmbeddingEncoder
import com.closet.core.data.util.UnavailableEmbeddingEncoder
import com.closet.core.data.worker.BatchSegmentationScheduler
import com.closet.core.data.worker.EmbeddingScheduler
import com.closet.core.data.worker.ImageCompressionScheduler
import com.closet.core.data.worker.NoOpBatchSegmentationScheduler
import com.closet.core.data.worker.NoOpEmbeddingScheduler
import com.closet.core.data.worker.NoOpImageCompressionScheduler
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

/**
 * JVM data-layer definitions, loaded alongside [commonDataModule]. `closet.db` and the
 * DataStore files live in [dataDirectory]. Not started anywhere yet.
 *
 * Stubs: keys live in an [InMemorySecretStore] (lost when the process ends) and
 * [NoLocationProvider] reports no location, so weather falls back to cached coordinates.
 * [UnavailableEmbeddingEncoder] always fails, and the no-op schedulers run nothing and
 * report no status (batch segmentation is unsupported).
 */
fun jvmDataModule(dataDirectory: File): Module = module {
    single<ClothingDatabase> { getDatabaseBuilder(File(dataDirectory, "closet.db")).build() }
    single<HttpClientEngineFactory<*>> { OkHttp }
    single<DataStore<Preferences>>(named(AI_PREFS_NAME)) {
        createPreferencesDataStore(jvmPreferencesPath(dataDirectory, AI_PREFS_NAME))
    }
    single<DataStore<Preferences>>(named(WEATHER_PREFS_NAME)) {
        createPreferencesDataStore(jvmPreferencesPath(dataDirectory, WEATHER_PREFS_NAME))
    }
    single<SecretStore> { InMemorySecretStore() }
    single<ImagePathResolver> { JvmImagePathResolver(File(dataDirectory, IMAGES_DIR_NAME)) }
    single<ImageStorageStats> { JvmImageStorageStats(File(dataDirectory, IMAGES_DIR_NAME)) }
    single<NanoInitializer> { UnavailableNanoInitializer }
    single<BatchCaptioner> { NoOpBatchCaptioner }
    single<LocationProvider> { NoLocationProvider }
    single<EmbeddingEncoder> { UnavailableEmbeddingEncoder }
    single<EmbeddingScheduler> { NoOpEmbeddingScheduler }
    single<ImageCompressionScheduler> { NoOpImageCompressionScheduler }
    single<BatchSegmentationScheduler> { NoOpBatchSegmentationScheduler }
}
