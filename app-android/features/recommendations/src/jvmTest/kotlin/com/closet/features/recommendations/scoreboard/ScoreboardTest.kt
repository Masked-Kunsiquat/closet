package com.closet.features.recommendations.scoreboard

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ScoreboardTest {

    @Test
    fun `mini persona run is deterministic`() = runTest(timeout = 5.minutes) {
        val first = Scoreboard.run(MiniPersona.build())
        val second = Scoreboard.run(MiniPersona.build())
        assertEquals(first, second)
        assertEquals(Report.markdown(listOf(first)), Report.markdown(listOf(second)))
    }

    @Test
    fun `mini persona produces evaluated days and sane rates`() = runTest(timeout = 5.minutes) {
        val result = Scoreboard.run(MiniPersona.build())
        assertTrue(result.evaluatedDays > 0, "no evaluated days")
        assertTrue(result.hitsAt10 <= result.evaluatedDays, "more hits than days")
        assertTrue(result.noResultDays <= result.evaluatedDays, "more no-result days than days")
        assertEquals(CheckId.entries.toSet(), result.checks.keys)
        for ((id, rate) in result.checks) {
            assertTrue(rate.applicable <= result.evaluatedDays * 3, "$id applicable ${rate.applicable}")
            assertTrue(rate.fails <= rate.applicable, "$id fails ${rate.fails} over ${rate.applicable}")
        }
    }

    @Test
    fun `pipeline sees only logs before the evaluation day`() = runTest(timeout = 5.minutes) {
        val persona = MiniPersona.build()
        val plan = Simulator(persona).plan(Scoreboard.DEFAULT_START, Scoreboard.DEFAULT_DAYS)
        val seen = ArrayList<Pair<SimDay, Int>>()
        Scoreboard.runObserved(persona, Scoreboard.DEFAULT_START, Scoreboard.DEFAULT_DAYS) { db, day ->
            seen += day to db.recommendationDao().getTotalLogCount()
        }
        val (firstDay, count) = assertNotNull(seen.firstOrNull(), "no evaluation day")
        val loggedBefore = plan.count { it.logged && it.date < firstDay.date }
        assertTrue(loggedBefore > 0)
        assertEquals(loggedBefore, count)
    }

    @Test
    fun `empty pipeline result counts as no results and a miss`() = runTest(timeout = 5.minutes) {
        val persona = MiniPersona.build()
        ScoreboardDatabase().use { env ->
            val fixture = FixtureWriter.write(env.db, persona)
            val worn = persona.outfits.first().itemKeys
            val allDirty = persona.items.map { it.key }.toSet()
            val day = SimDay(
                date = Scoreboard.DEFAULT_START.plusDays(90),
                kind = DayKind.OFFICE,
                occasion = "Work/Business",
                weather = DayWeather(lowC = 5.0, highC = 12.0, raining = false, windy = false),
                logged = true,
                wornItemKeys = worn,
                viaSavedOutfit = null,
                dirtyAtMorning = allDirty,
                dirtyAtNight = allDirty,
            )
            val evaluation = Scoreboard.evaluateDay(
                env.db, persona, fixture, day, requestedOccasion = null, yesterdayWornKeys = worn,
            )
            assertTrue(evaluation.noResults)
            assertFalse(evaluation.hitAt10)

            val result = Scoreboard.tally(persona, listOf(evaluation))
            assertEquals(1, result.evaluatedDays)
            assertEquals(1, result.noResultDays)
            assertEquals(0, result.hitsAt10)
            for ((id, rate) in result.checks) {
                assertEquals(CheckRate(fails = 0, applicable = 0), rate, "$id should be N/A")
            }
        }
    }

    @Test
    fun `report is written`() = runTest(timeout = 5.minutes) {
        val result = Scoreboard.run(MiniPersona.build())
        val file = Report.write(listOf(result))
        assertEquals(File(Report.reportDir(), "scoreboard.md").canonicalFile, file.canonicalFile)
        assertTrue(file.path.replace('\\', '/').endsWith("build/reports/scoreboard/scoreboard.md"), file.path)
        val text = file.readText()
        assertTrue("## mini" in text, text)
        assertTrue("neighbour backoff off: no JVM embeddings" in text, text)
    }
}
