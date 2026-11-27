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
    /**
     * Recorded 2026-11-27 on engine commit b4826d13 (#148): the five curated personas, a year each
     * from 2025-01-06, default taste weights (learned taste paused), neighbour backoff off (#149).
     * Re-record in the PR that intends to move a metric, with before and after numbers.
     */
    val byPersona: Map<String, PersonaResult> = mapOf(
        "office-commuter-menswear" to PersonaResult(
            persona = "office-commuter-menswear", evaluatedDays = 68, hitsAt10 = 49,
            noResultDays = 0, layeredCoreDays = 18, unreachableCoreDays = 0,
            savedShare = 0.32,
            checks = mapOf(
                CheckId.COMPLETE to CheckRate(0, 204),
                CheckId.WARM_ENOUGH to CheckRate(86, 168),
                CheckId.NOT_TOO_WARM to CheckRate(0, 72),
                CheckId.RAIN_READY to CheckRate(29, 36),
                CheckId.FORMALITY to CheckRate(0, 204),
                CheckId.CLEAN to CheckRate(0, 204),
                CheckId.NO_DOUBLED_SLOTS to CheckRate(0, 204),
                CheckId.THREE_DIFFER to CheckRate(36, 68),
                CheckId.NOT_ON_REPEAT to CheckRate(0, 132),
            ),
        ),
        "office-commuter-womenswear" to PersonaResult(
            persona = "office-commuter-womenswear", evaluatedDays = 71, hitsAt10 = 58,
            noResultDays = 0, layeredCoreDays = 17, unreachableCoreDays = 0,
            savedShare = 0.30434782608695654,
            checks = mapOf(
                CheckId.COMPLETE to CheckRate(0, 212),
                CheckId.WARM_ENOUGH to CheckRate(70, 174),
                CheckId.NOT_TOO_WARM to CheckRate(0, 72),
                CheckId.RAIN_READY to CheckRate(16, 33),
                CheckId.FORMALITY to CheckRate(0, 212),
                CheckId.CLEAN to CheckRate(0, 212),
                CheckId.NO_DOUBLED_SLOTS to CheckRate(0, 212),
                CheckId.THREE_DIFFER to CheckRate(25, 71),
                CheckId.NOT_ON_REPEAT to CheckRate(0, 152),
            ),
        ),
        "creature-of-habit-menswear" to PersonaResult(
            persona = "creature-of-habit-menswear", evaluatedDays = 64, hitsAt10 = 56,
            noResultDays = 3, layeredCoreDays = 25, unreachableCoreDays = 0,
            savedShare = 0.9090909090909091,
            checks = mapOf(
                CheckId.COMPLETE to CheckRate(0, 183),
                CheckId.WARM_ENOUGH to CheckRate(80, 183),
                CheckId.NOT_TOO_WARM to CheckRate(5, 18),
                CheckId.RAIN_READY to CheckRate(36, 78),
                CheckId.FORMALITY to CheckRate(0, 183),
                CheckId.CLEAN to CheckRate(0, 183),
                CheckId.NO_DOUBLED_SLOTS to CheckRate(0, 183),
                CheckId.THREE_DIFFER to CheckRate(39, 61),
                CheckId.NOT_ON_REPEAT to CheckRate(0, 123),
            ),
        ),
        "mix-and-matcher-womenswear" to PersonaResult(
            persona = "mix-and-matcher-womenswear", evaluatedDays = 73, hitsAt10 = 49,
            noResultDays = 0, layeredCoreDays = 2, unreachableCoreDays = 0,
            savedShare = 0.06896551724137931,
            checks = mapOf(
                CheckId.COMPLETE to CheckRate(3, 219),
                CheckId.WARM_ENOUGH to CheckRate(48, 219),
                CheckId.NOT_TOO_WARM to CheckRate(0, 156),
                CheckId.RAIN_READY to CheckRate(1, 3),
                CheckId.FORMALITY to CheckRate(0, 219),
                CheckId.CLEAN to CheckRate(0, 219),
                CheckId.NO_DOUBLED_SLOTS to CheckRate(0, 219),
                CheckId.THREE_DIFFER to CheckRate(45, 73),
                CheckId.NOT_ON_REPEAT to CheckRate(0, 159),
            ),
        ),
        "active-student" to PersonaResult(
            persona = "active-student", evaluatedDays = 69, hitsAt10 = 51,
            noResultDays = 4, layeredCoreDays = 13, unreachableCoreDays = 7,
            savedShare = 0.25,
            checks = mapOf(
                CheckId.COMPLETE to CheckRate(2, 192),
                CheckId.WARM_ENOUGH to CheckRate(86, 129),
                CheckId.NOT_TOO_WARM to CheckRate(0, 0),
                CheckId.RAIN_READY to CheckRate(35, 75),
                CheckId.FORMALITY to CheckRate(0, 192),
                CheckId.CLEAN to CheckRate(0, 192),
                CheckId.NO_DOUBLED_SLOTS to CheckRate(0, 192),
                CheckId.THREE_DIFFER to CheckRate(41, 65),
                CheckId.NOT_ON_REPEAT to CheckRate(0, 119),
            ),
        ),
    )

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
