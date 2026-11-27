package com.closet.features.recommendations.scoreboard

import java.io.File
import java.util.Locale

/**
 * Renders [PersonaResult]s as Markdown: per persona (and in total when there are several), each
 * check's fail rate over its applicable outcomes, hit@10, the no-results rate, the layered-core
 * days (on which hit@10's subset rule is generous), the evaluated days and the saved share.
 */
object Report {
    const val EMBEDDINGS_NOTE = "neighbour backoff off: no JVM embeddings"

    fun markdown(results: List<PersonaResult>): String = buildString {
        appendLine("# Recommendation scoreboard")
        appendLine()
        appendLine(EMBEDDINGS_NOTE)
        for (result in results) section(result.persona, result)
        if (results.size > 1) section("Total", total(results))
    }

    /** The directory the `scoreboard` Gradle task passes in, else `build/reports/scoreboard` here. */
    fun reportDir(): File = File(System.getProperty("scoreboard.reportDir") ?: "build/reports/scoreboard")

    /** Writes [markdown] to `scoreboard.md` in [reportDir], prints it, and returns the file. */
    fun write(results: List<PersonaResult>): File {
        val text = markdown(results)
        val file = File(reportDir(), "scoreboard.md")
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
        for (id in CheckId.entries) {
            val check = r.checks.getValue(id)
            appendLine("| ${id.name} fails | ${rate(check.fails, check.applicable)} |")
        }
    }

    private fun total(results: List<PersonaResult>): PersonaResult {
        val meanSavedShare = results.map { it.savedShare }.average()
        return PersonaResult(
            persona = "Total",
            evaluatedDays = results.sumOf { it.evaluatedDays },
            hitsAt10 = results.sumOf { it.hitsAt10 },
            noResultDays = results.sumOf { it.noResultDays },
            layeredCoreDays = results.sumOf { it.layeredCoreDays },
            savedShare = meanSavedShare,
            checks = CheckId.entries.associateWith { id ->
                CheckRate(
                    fails = results.sumOf { it.checks.getValue(id).fails },
                    applicable = results.sumOf { it.checks.getValue(id).applicable },
                )
            },
        )
    }

    private fun percent(share: Double) = String.format(Locale.ROOT, "%.1f%%", share * 100.0)

    private fun rate(count: Int, of: Int) = if (of == 0) "N/A (0/0)" else "${percent(count.toDouble() / of)} ($count/$of)"
}
