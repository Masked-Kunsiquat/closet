package com.closet.features.recommendations.scoreboard

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

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
        // Equal as fractions over different counts: 3/30 equals 10/100 and 61/122 equals 30/60.
        val equalFractions = result(hits = 61, days = 122, noResults = 4)
            .withCheck(CheckId.FORMALITY, CheckRate(fails = 3, applicable = 30))
        assertEquals(emptyList(), Baseline.regressions(equalFractions, baseline))
        // A rate on 0 applicable is unchanged, on either side.
        val nothingApplicable = baseline.withCheck(CheckId.RAIN_READY, CheckRate(fails = 0, applicable = 0))
        assertEquals(emptyList(), Baseline.regressions(nothingApplicable, baseline))
        assertEquals(emptyList(), Baseline.regressions(baseline.withCheck(CheckId.RAIN_READY, CheckRate(50, 100)), nothingApplicable))
    }

    @Test
    fun `no persona regresses against the baseline`() = runTest(timeout = 10.minutes) {
        val problems = ArrayList<String>()
        for (persona in AllPersonas.list) {
            val baseline = Baseline.byPersona[persona.name]
            if (baseline == null) {
                problems += "no baseline recorded for ${persona.name}"
                continue
            }
            problems += Baseline.regressions(Scoreboard.run(persona), baseline)
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
