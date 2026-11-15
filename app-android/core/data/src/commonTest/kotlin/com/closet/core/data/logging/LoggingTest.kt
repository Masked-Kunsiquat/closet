package com.closet.core.data.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.closet.core.data.BuildFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoggingTest {
    @Test
    fun releaseHasNoWriters() {
        configureLogging(BuildFlags(isDebug = false))
        assertTrue(Logger.config.logWriterList.isEmpty())
    }

    @Test
    fun debugLogsVerbose() {
        configureLogging(BuildFlags(isDebug = true))
        assertTrue(Logger.config.logWriterList.isNotEmpty())
        assertEquals(Severity.Verbose, Logger.config.minSeverity)
    }
}
