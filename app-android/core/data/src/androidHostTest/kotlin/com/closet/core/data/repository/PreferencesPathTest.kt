package com.closet.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * [androidPreferencesPath] must resolve to the file `preferencesDataStore(name)` used before the
 * KMP move (`filesDir/datastore/<name>.preferences_pb`), so existing settings survive the upgrade.
 */
class PreferencesPathTest {
    @Test fun matchesLegacyPreferencesDataStorePath() {
        val filesDir = File("/data/x/files")
        for (name in listOf("ai_prefs", "weather_prefs")) {
            assertEquals(
                File(filesDir, "datastore/$name.preferences_pb").absolutePath,
                androidPreferencesPath(filesDir, name),
            )
        }
    }
}
