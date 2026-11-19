package com.closet.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.closet.core.data.ai.NanoAvailability
import com.closet.core.data.model.AiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [AiPreferencesRepository] against a real file-backed DataStore built by
 * [createPreferencesDataStore], with [InMemorySecretStore] for the keys.
 */
class AiPreferencesRepositoryTest {

    private val dir: File = kotlin.io.path.createTempDirectory("ai-prefs-test").toFile()
    private val path: String = File(dir, "ai_prefs.preferences_pb").absolutePath

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    /**
     * Opens a DataStore on [path], runs [block], then cancels the DataStore's scope so the
     * file is released and a later instance may own it (one instance per file per process).
     */
    private suspend fun <T> withDataStore(block: suspend (DataStore<Preferences>) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            return block(createPreferencesDataStore(path, scope))
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun selectedProviderPersistsAcrossInstances() = runBlocking {
        withDataStore { store ->
            AiPreferencesRepository(store, InMemorySecretStore()).setSelectedProvider(AiProvider.Anthropic)
        }
        val read = withDataStore { store ->
            AiPreferencesRepository(store, InMemorySecretStore()).getSelectedProvider().first()
        }
        assertEquals(AiProvider.Anthropic, read)
    }

    @Test
    fun nanoAvailabilityDefaultsToUnknown() = runBlocking {
        withDataStore { store ->
            assertEquals(
                NanoAvailability.UNKNOWN,
                AiPreferencesRepository(store, InMemorySecretStore()).getNanoAvailability().first(),
            )
        }
    }

    @Test
    fun nanoAvailabilityPersistsAcrossInstances() = runBlocking {
        withDataStore { store ->
            AiPreferencesRepository(store, InMemorySecretStore()).setNanoAvailability(NanoAvailability.NOT_SUPPORTED)
        }
        val read = withDataStore { store ->
            AiPreferencesRepository(store, InMemorySecretStore()).getNanoAvailability().first()
        }
        assertEquals(NanoAvailability.NOT_SUPPORTED, read)
    }

    @Test
    fun readyFlagAloneDoesNotSetAvailability() = runBlocking {
        withDataStore { store ->
            val repository = AiPreferencesRepository(store, InMemorySecretStore())
            repository.setAiReady(true)
            assertEquals(NanoAvailability.UNKNOWN, repository.getNanoAvailability().first())
        }
    }

    @Test
    fun clearNanoStateResetsAvailability() = runBlocking {
        withDataStore { store ->
            val repository = AiPreferencesRepository(store, InMemorySecretStore())
            repository.setNanoAvailability(NanoAvailability.READY)
            repository.clearNanoState()
            assertEquals(NanoAvailability.UNKNOWN, repository.getNanoAvailability().first())
        }
    }

    @Test
    fun openAiKeyShowsUpInItsFlow() = runBlocking {
        withDataStore { store ->
            val repository = AiPreferencesRepository(store, InMemorySecretStore())
            repository.setOpenAiApiKey("k")
            assertEquals("k", repository.getOpenAiApiKey().first())
        }
    }

    @Test
    fun tasteWeightsAndWatermarkPersistAcrossInstances() = runBlocking {
        withDataStore { store ->
            val repository = AiPreferencesRepository(store, InMemorySecretStore())
            val res = repository.setTasteWeights("1.0,0.8,0.9,1.1,1.0,1.4,1.0,1.0", "v1:25:42")
            assertEquals(com.closet.core.data.util.DataResult.Success(Unit), res)
        }
        withDataStore { store ->
            val repository = AiPreferencesRepository(store, InMemorySecretStore())
            assertEquals("1.0,0.8,0.9,1.1,1.0,1.4,1.0,1.0", repository.getTasteWeightsRaw().first())
            assertEquals("v1:25:42", repository.getTasteWeightsWatermark().first())
        }
    }
}
