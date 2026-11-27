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
    fun `a layered worn core is hit by a contained engine core and counts as layered`() = runTest(timeout = 5.minutes) {
        val persona = MiniPersona.build()
        ScoreboardDatabase().use { env ->
            val fixture = FixtureWriter.write(env.db, persona)
            val worn = listOf("white-oxford", "grey-sweater", "navy-chinos", "dress-shoes")
            // Every other top and bottom is dirty, so each engine core is one of those tops plus the chinos.
            val byKey = persona.items.associateBy { it.key }
            val dirty = persona.items
                .filter { it.category in setOf(Checks.TOPS, Checks.BOTTOMS) && it.key !in worn }
                .map { it.key }.toSet()
            val day = SimDay(
                date = Scoreboard.DEFAULT_START.plusDays(30),
                kind = DayKind.OFFICE,
                occasion = "Work/Business",
                weather = DayWeather(lowC = 0.0, highC = 6.0, raining = false, windy = false),
                logged = true,
                wornItemKeys = worn,
                viaSavedOutfit = null,
                dirtyAtMorning = dirty,
                dirtyAtNight = dirty,
            )
            assertTrue(worn.count { byKey.getValue(it).category == Checks.TOPS } == 2)
            val evaluation = Scoreboard.evaluateDay(
                env.db, persona, fixture, day, requestedOccasion = null, yesterdayWornKeys = null,
            )
            assertFalse(evaluation.noResults)
            assertTrue(evaluation.hitAt10, "a top-10 core of one worn top plus the chinos should be a hit")
            assertTrue(evaluation.layeredCore)

            val result = Scoreboard.tally(persona, listOf(evaluation))
            assertEquals(1, result.hitsAt10)
            assertEquals(1, result.layeredCoreDays)
        }
    }

    @Test
    fun `a worn core without a top and bottom pair is unreachable and a miss`() = runTest(timeout = 5.minutes) {
        val persona = MiniPersona.build()
        ScoreboardDatabase().use { env ->
            val fixture = FixtureWriter.write(env.db, persona)
            // Gym shorts are Activewear, so the worn core is the tee alone.
            val worn = listOf("gym-shorts", "blue-tee", "sneakers")
            val day = SimDay(
                date = Scoreboard.DEFAULT_START.plusDays(150),
                kind = DayKind.OFFICE,
                occasion = "Work/Business",
                weather = DayWeather(lowC = 15.0, highC = 24.0, raining = false, windy = false),
                logged = true,
                wornItemKeys = worn,
                viaSavedOutfit = null,
                dirtyAtMorning = emptySet(),
                dirtyAtNight = emptySet(),
            )
            val evaluation = Scoreboard.evaluateDay(
                env.db, persona, fixture, day, requestedOccasion = null, yesterdayWornKeys = null,
            )
            assertFalse(evaluation.noResults)
            assertTrue(evaluation.unreachableCore)
            assertFalse(evaluation.layeredCore)
            assertFalse(evaluation.hitAt10)

            val result = Scoreboard.tally(persona, listOf(evaluation))
            assertEquals(1, result.unreachableCoreDays)
            assertEquals(0, result.hitsAt10)
        }
    }

    @Test
    fun `report is written`() = runTest(timeout = 5.minutes) {
        val result = Scoreboard.run(MiniPersona.build())
        // The mini persona writes its own file, never the curated report.
        val file = Report.write(listOf(result), fileName = MINI_REPORT)
        assertEquals(File(Report.reportDir(), MINI_REPORT).canonicalFile, file.canonicalFile)
        assertTrue(file.path.replace('\\', '/').endsWith("build/reports/scoreboard/$MINI_REPORT"), file.path)
        assertTrue(MINI_REPORT != Report.REPORT_FILE)
        val text = file.readText()
        assertTrue("## mini" in text, text)
        assertTrue("neighbour backoff off: no JVM embeddings" in text, text)
        assertTrue(Report.RATES_NOTE in text, text)
        assertTrue("| Unreachable-core days |" in text, text)
        assertTrue(Report.CHECKS_HEADER in text, text)
    }

    @Test
    fun `the persona column is computed from the worn outfit`() = runTest(timeout = 5.minutes) {
        val persona = MiniPersona.build()
        ScoreboardDatabase().use { env ->
            val fixture = FixtureWriter.write(env.db, persona)
            val worn = persona.outfits.first().itemKeys
            // One worn item is dirty: the persona's own outfit fails CLEAN, while the engine avoids it.
            val dirty = setOf(worn.first())
            val day = SimDay(
                date = Scoreboard.DEFAULT_START.plusDays(90),
                kind = DayKind.OFFICE,
                occasion = "Work/Business",
                weather = DayWeather(lowC = 5.0, highC = 12.0, raining = false, windy = false),
                logged = true,
                wornItemKeys = worn,
                viaSavedOutfit = null,
                dirtyAtMorning = dirty,
                dirtyAtNight = dirty,
            )
            val evaluation = Scoreboard.evaluateDay(
                env.db, persona, fixture, day, requestedOccasion = "Work/Business", yesterdayWornKeys = null,
            )
            val ctx = CheckContext(persona, day, "Work/Business", yesterdayWornKeys = null)
            assertEquals(Checks.perSuggestion(worn, ctx), evaluation.personaOutcomes)
            assertEquals(Outcome.FAIL, evaluation.personaOutcomes[CheckId.CLEAN])

            val result = Scoreboard.tally(persona, listOf(evaluation))
            assertEquals(CheckRate(fails = 1, applicable = 1), result.personaChecks[CheckId.CLEAN])
            assertEquals(CheckRate(fails = 0, applicable = 0), result.personaChecks[CheckId.THREE_DIFFER])
            val engineClean = result.checks.getValue(CheckId.CLEAN)
            assertEquals(0, engineClean.fails)

            val cleanRow = Report.markdown(listOf(result)).lines().single { it.startsWith("| CLEAN |") }
            assertTrue(cleanRow.endsWith("| 100.0% (1/1) |"), cleanRow)
        }
    }

    private companion object {
        const val MINI_REPORT = "scoreboard-mini.md"
    }
}
