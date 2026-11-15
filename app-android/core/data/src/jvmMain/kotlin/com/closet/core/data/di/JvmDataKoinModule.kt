package com.closet.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.getDatabaseBuilder
import com.closet.core.data.location.LocationProvider
import com.closet.core.data.location.NoLocationProvider
import com.closet.core.data.repository.AI_PREFS_NAME
import com.closet.core.data.repository.InMemorySecretStore
import com.closet.core.data.repository.SecretStore
import com.closet.core.data.repository.WEATHER_PREFS_NAME
import com.closet.core.data.repository.createPreferencesDataStore
import com.closet.core.data.repository.jvmPreferencesPath
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
    single<LocationProvider> { NoLocationProvider }
}
