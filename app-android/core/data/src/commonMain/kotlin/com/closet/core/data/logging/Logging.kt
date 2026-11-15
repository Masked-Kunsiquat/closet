package com.closet.core.data.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import com.closet.core.data.BuildFlags

/** Debug builds log everything to the platform writer; release builds log nowhere. */
fun configureLogging(flags: BuildFlags) {
    if (flags.isDebug) {
        Logger.setLogWriters(platformLogWriter())
        Logger.setMinSeverity(Severity.Verbose)
    } else {
        Logger.setLogWriters(emptyList())
    }
}
