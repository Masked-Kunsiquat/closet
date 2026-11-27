package com.closet.features.recommendations.scoreboard

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The one owner of the curated report, `build/reports/scoreboard/scoreboard.md` (spec §7). */
class CuratedReportTest {

    @Test
    fun `the curated report is written`() {
        val file = Report.write(CuratedRun.results)
        assertEquals(File(Report.reportDir(), Report.REPORT_FILE).canonicalFile, file.canonicalFile)
        assertTrue(file.path.replace('\\', '/').endsWith("build/reports/scoreboard/scoreboard.md"), file.path)
        val text = file.readText()
        for (persona in AllPersonas.list) assertTrue("## ${persona.name}\n" in text.replace("\r\n", "\n"), persona.name)
        assertTrue("## Total" in text, text)
        assertEquals(AllPersonas.list.size + 1, text.lines().count { it == Report.CHECKS_HEADER })
        assertTrue(Report.PERSONA_NOTE in text, text)
    }
}
