package com.closet.features.recommendations.scoreboard

import java.io.File
import java.util.Locale

/**
 * Renders [PersonaResult]s as Markdown: per persona (and in total when there are several), each
 * check's fail rate over its applicable outcomes, hit@10, the no-results rate, the layered-core
 * days (on which hit@10's subset rule is generous), the unreachable-core days (which can never be
 * hits), the evaluated days and the saved share. Beside each check's engine fail rate it prints the
 * persona's own fail rate ([PersonaResult.personaChecks]), for reference only.
 *
 * The curated run ([CuratedReportTest]) owns [REPORT_FILE]; other runs write to another file.
 */
object Report {
    const val EMBEDDINGS_NOTE = "neighbour backoff off: no JVM embeddings"
    const val RATES_NOTE = "Check fail rates are over shown suggestions (up to 3 per evaluation day), " +
        "except THREE_DIFFER, which is per evaluation day."
    const val PERSONA_NOTE = "Persona's own fails apply the same checks to the outfit the persona actually " +
        "wore on each evaluation day (one per day; THREE_DIFFER does not apply). They are for reference " +
        "only, never asserted on or baselined."
    const val CHECKS_HEADER = "| Check | Engine fails | Persona's own fails |"

    /** The curated personas' report, written only by [CuratedReportTest]. */
    const val REPORT_FILE = "scoreboard.md"

    fun markdown(results: List<PersonaResult>): String = buildString {
        appendLine("# Recommendation scoreboard")
        appendLine()
        appendLine(EMBEDDINGS_NOTE)
        appendLine()
        appendLine(RATES_NOTE)
        appendLine()
        appendLine(PERSONA_NOTE)
        for (result in results) section(result.persona, result)
        if (results.size > 1) section("Total", total(results))
    }

    /** The directory the `scoreboard` Gradle task passes in, else `build/reports/scoreboard` here. */
    fun reportDir(): File = File(System.getProperty("scoreboard.reportDir") ?: "build/reports/scoreboard")

    /** Writes [markdown] to [fileName] in [reportDir], prints it, and returns the file. */
    fun write(results: List<PersonaResult>, fileName: String = REPORT_FILE): File {
        val text = markdown(results)
        val file = File(reportDir(), fileName)
        file.parentFile.mkdirs()
        file.writeText(text)
        println(text)
        println("Scoreboard report: ${file.absolutePath}")
        return file
    }

    private fun StringBuilder.section(title: String, r: PersonaResult) {
        appendLine()
        appendLine("## $title")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|---|---|")
        appendLine("| Evaluated days | ${r.evaluatedDays} |")
        appendLine("| Saved share | ${percent(r.savedShare)} |")
        appendLine("| Hit@10 | ${rate(r.hitsAt10, r.evaluatedDays)} |")
        appendLine("| No results | ${rate(r.noResultDays, r.evaluatedDays)} |")
        appendLine("| Layered-core days | ${rate(r.layeredCoreDays, r.evaluatedDays)} |")
        appendLine("| Unreachable-core days | ${rate(r.unreachableCoreDays, r.evaluatedDays)} |")
        appendLine()
        appendLine(CHECKS_HEADER)
        appendLine("|---|---|---|")
        for (id in CheckId.entries) {
            val check = r.checks.getValue(id)
            val own = r.personaChecks[id] ?: CheckRate(0, 0)
            appendLine("| ${id.name} | ${rate(check.fails, check.applicable)} | ${rate(own.fails, own.applicable)} |")
        }
    }

    private fun total(results: List<PersonaResult>): PersonaResult {
        val meanSavedShare = results.map { it.savedShare }.average()
        fun sum(rates: (PersonaResult) -> Map<CheckId, CheckRate>) = CheckId.entries.associateWith { id ->
            CheckRate(
                fails = results.sumOf { rates(it)[id]?.fails ?: 0 },
                applicable = results.sumOf { rates(it)[id]?.applicable ?: 0 },
            )
        }
        return PersonaResult(
            persona = "Total",
            evaluatedDays = results.sumOf { it.evaluatedDays },
            hitsAt10 = results.sumOf { it.hitsAt10 },
            noResultDays = results.sumOf { it.noResultDays },
            layeredCoreDays = results.sumOf { it.layeredCoreDays },
            unreachableCoreDays = results.sumOf { it.unreachableCoreDays },
            savedShare = meanSavedShare,
            checks = sum { it.checks },
            personaChecks = sum { it.personaChecks },
        )
    }

    private fun percent(share: Double) = String.format(Locale.ROOT, "%.1f%%", share * 100.0)

    private fun rate(count: Int, of: Int) = if (of == 0) "N/A (0/0)" else "${percent(count.toDouble() / of)} ($count/$of)"
}
