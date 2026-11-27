package com.closet.features.recommendations.scoreboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class BaselineTest {

    private fun result(
        hits: Int = 30,
        days: Int = 60,
        noResults: Int = 2,
        checks: Map<CheckId, CheckRate> = CheckId.entries.associateWith { CheckRate(fails = 10, applicable = 100) },
    ) = PersonaResult(
        persona = "synthetic",
        evaluatedDays = days,
        hitsAt10 = hits,
        noResultDays = noResults,
        layeredCoreDays = 0,
        unreachableCoreDays = 0,
        savedShare = 0.5,
        checks = checks,
    )

    private fun PersonaResult.withCheck(id: CheckId, rate: CheckRate) = copy(checks = checks + (id to rate))

    @Test
    fun `a worse metric fails the baseline check`() {
        val baseline = result()
        val worse = listOf(
            "hit@10" to result(hits = 29),
            "no results" to result(noResults = 3),
            CheckId.RAIN_READY.name to baseline.withCheck(CheckId.RAIN_READY, CheckRate(fails = 11, applicable = 100)),
            // 1 in 9 is worse than the baseline's 10 in 100, over a different number of suggestions.
            CheckId.CLEAN.name to baseline.withCheck(CheckId.CLEAN, CheckRate(fails = 1, applicable = 9)),
        )
        for ((metric, actual) in worse) {
            val lines = Baseline.regressions(actual, baseline)
            assertEquals(1, lines.size, "$metric: $lines")
            val line = lines.single()
            assertTrue("synthetic" in line, line)
            assertTrue(metric in line, line)
        }
        // The line names the baseline and the actual value.
        val line = Baseline.regressions(result(hits = 29), baseline).single()
        assertTrue("30/60" in line && "29/60" in line, line)
    }

    @Test
    fun `an equal or better result passes the baseline check`() {
        val baseline = result()
        assertEquals(emptyList(), Baseline.regressions(baseline, baseline))
        val better = result(
            hits = 31,
            noResults = 1,
            checks = CheckId.entries.associateWith { CheckRate(fails = 9, applicable = 100) },
        )
        assertEquals(emptyList(), Baseline.regressions(better, baseline))
        // Equal as fractions over a different number of suggestions: 3/30 equals 10/100.
        val equalFractions = baseline.withCheck(CheckId.FORMALITY, CheckRate(fails = 3, applicable = 30))
        assertEquals(emptyList(), Baseline.regressions(equalFractions, baseline))
        assertEquals(emptyList(), Baseline.improvements(equalFractions, baseline))
        // A rate on 0 applicable is unchanged, on either side.
        val nothingApplicable = baseline.withCheck(CheckId.RAIN_READY, CheckRate(fails = 0, applicable = 0))
        assertEquals(emptyList(), Baseline.regressions(nothingApplicable, baseline))
        assertEquals(emptyList(), Baseline.regressions(baseline.withCheck(CheckId.RAIN_READY, CheckRate(50, 100)), nothingApplicable))
    }

    @Test
    fun `a missing check is a regression on either side`() {
        val full = result()
        val partial = full.copy(checks = full.checks - CheckId.RAIN_READY)
        assertEquals(listOf("synthetic: RAIN_READY missing from baseline"), Baseline.regressions(full, partial))
        assertEquals(listOf("synthetic: RAIN_READY missing from result"), Baseline.regressions(partial, full))
    }

    @Test
    fun `a change in evaluated days marks the baseline stale`() {
        // 61 of 122 equals 30 of 60 as a rate, but a different number of days means the simulation changed.
        val lines = Baseline.regressions(result(hits = 61, days = 122, noResults = 4), result())
        assertEquals(listOf("synthetic: baseline stale, evaluated days 60 → 122, re-record"), lines)
    }

    @Test
    fun `an improvement is listed and is not a regression`() {
        val baseline = result()
        val better = result(hits = 31, noResults = 1).withCheck(CheckId.WARM_ENOUGH, CheckRate(fails = 9, applicable = 100))
        assertEquals(emptyList(), Baseline.regressions(better, baseline))
        assertEquals(
            listOf(
                "synthetic: hit@10 improved from 30/60 to 31/60",
                "synthetic: no results improved from 2/60 to 1/60",
                "synthetic: WARM_ENOUGH fail rate improved from 10/100 to 9/100",
            ),
            Baseline.improvements(better, baseline),
        )
        // A mixed result lists each side once.
        val mixed = better.copy(hitsAt10 = 29)
        assertEquals(listOf("synthetic: hit@10 regressed from 30/60 to 29/60"), Baseline.regressions(mixed, baseline))
        assertEquals(2, Baseline.improvements(mixed, baseline).size)
        assertEquals(emptyList(), Baseline.improvements(baseline, baseline))
    }

    @Test
    fun `no persona regresses against the baseline`() {
        val problems = ArrayList<String>()
        val improvements = ArrayList<String>()
        val results = CuratedRun.results.associateBy { it.persona }
        for (persona in AllPersonas.list) {
            val baseline = Baseline.byPersona[persona.name]
            if (baseline == null) {
                problems += "no baseline recorded for ${persona.name}"
                continue
            }
            val actual = results.getValue(persona.name)
            problems += Baseline.regressions(actual, baseline)
            improvements += Baseline.improvements(actual, baseline).map { "$it (consider re-recording)" }
        }
        if (improvements.isNotEmpty()) println("Scoreboard improvements:\n" + improvements.joinToString("\n"))
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
