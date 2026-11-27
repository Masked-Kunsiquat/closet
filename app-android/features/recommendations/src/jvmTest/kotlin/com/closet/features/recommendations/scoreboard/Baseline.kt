package com.closet.features.recommendations.scoreboard

/**
 * The scoreboard baseline (spec §7) and the rule that guards it.
 *
 * [byPersona] holds each curated persona's committed [PersonaResult], keyed by [Persona.name].
 * No persona may get worse than its baseline: a check's fail rate may not rise, hit@10 may not
 * fall and the no-results rate may not rise. Rates are compared as exact fractions (by
 * cross-multiplication, tolerance 0), since the scoreboard is deterministic; a rate over 0
 * applicable, on either side, counts as unchanged.
 */
object Baseline {
    /** Empty until the baseline run is recorded (#148). */
    val byPersona: Map<String, PersonaResult> = emptyMap()

    /** One line per metric of [actual] worse than [baseline], naming persona, metric, baseline and actual. */
    fun regressions(actual: PersonaResult, baseline: PersonaResult): List<String> = buildList {
        fun line(metric: String, base: String, now: String) =
            "${actual.persona}: $metric regressed from $base to $now"

        if (compare(actual.hitsAt10, actual.evaluatedDays, baseline.hitsAt10, baseline.evaluatedDays).let { it != null && it < 0 }) {
            add(line("hit@10", "${baseline.hitsAt10}/${baseline.evaluatedDays}", "${actual.hitsAt10}/${actual.evaluatedDays}"))
        }
        if (compare(actual.noResultDays, actual.evaluatedDays, baseline.noResultDays, baseline.evaluatedDays).let { it != null && it > 0 }) {
            add(line("no results", "${baseline.noResultDays}/${baseline.evaluatedDays}", "${actual.noResultDays}/${actual.evaluatedDays}"))
        }
        for (id in CheckId.entries) {
            val now = actual.checks[id] ?: continue
            val base = baseline.checks[id] ?: continue
            if (compare(now.fails, now.applicable, base.fails, base.applicable).let { it != null && it > 0 }) {
                add(line("${id.name} fail rate", "${base.fails}/${base.applicable}", "${now.fails}/${now.applicable}"))
            }
        }
    }

    /** Whether count/of and otherCount/otherOf differ as fractions; a rate over 0 differs from any rate over more. */
    internal fun rateDiffers(count: Int, of: Int, otherCount: Int, otherOf: Int): Boolean =
        if (of == 0 || otherOf == 0) of != otherOf else compare(count, of, otherCount, otherOf) != 0

    /** The sign of a/b − c/d, exactly; null when either denominator is 0 (no rate to compare). */
    private fun compare(a: Int, b: Int, c: Int, d: Int): Int? {
        if (b == 0 || d == 0) return null
        return (a.toLong() * d).compareTo(c.toLong() * b)
    }
}
