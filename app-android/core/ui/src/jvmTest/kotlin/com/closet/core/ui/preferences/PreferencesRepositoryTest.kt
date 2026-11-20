package com.closet.core.ui.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.closet.core.data.repository.createPreferencesDataStore
import com.closet.core.ui.theme.ClosetAccent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreferencesRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore: DataStore<Preferences> = createPreferencesDataStore(
        Files.createTempDirectory("ui-prefs").resolve("closet_prefs.preferences_pb").toString(),
        scope,
    )
    private val repo = PreferencesRepository(dataStore)

    @AfterTest fun tearDown() = scope.cancel()

    @Test fun defaults() = runBlocking {
        assertEquals(ClosetAccent.Amber, repo.getAccent().first())
        assertFalse(repo.getDynamicColor().first())
        assertNull(repo.lastHandledBatchId.first())
        assertNull(repo.lastHandledColorRedetectId.first())
    }

    @Test fun accentRoundTrips() = runBlocking {
        repo.setAccent(ClosetAccent.Coral)
        assertEquals(ClosetAccent.Coral, repo.getAccent().first())
    }

    @Test fun dynamicColorRoundTrips() = runBlocking {
        repo.setDynamicColor(true)
        assertTrue(repo.getDynamicColor().first())
    }

    @Test fun unknownStoredAccentFallsBackToAmber() = runBlocking {
        dataStore.edit { it[stringPreferencesKey("accent")] = "Teal" }
        assertEquals(ClosetAccent.Amber, repo.getAccent().first())
    }

    @Test fun lastHandledBatchIdRoundTrips() = runBlocking {
        assertTrue(repo.setLastHandledBatchId("b1").isSuccess)
        assertEquals("b1", repo.lastHandledBatchId.first())
    }

    @Test fun lastHandledColorRedetectIdRoundTripsSeparatelyFromBatch() = runBlocking {
        assertTrue(repo.setLastHandledColorRedetectId("c1").isSuccess)
        assertEquals("c1", repo.lastHandledColorRedetectId.first())
        assertNull(repo.lastHandledBatchId.first())
    }
}
