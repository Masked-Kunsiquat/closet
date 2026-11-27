package com.closet.features.recommendations.scoreboard

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimulatorTest {
    private val start = LocalDate.of(2026, 1, 1)

    private fun year(persona: Persona) = Simulator(persona).plan(start, 365)

    /** One outfit, a constant climate and an all-office week, to drive the fallback ladder. */
    private fun single(outfit: FixtureOutfit, rainChance: Double): Persona {
        val base = MiniPersona.build()
        return base.copy(
            outfits = listOf(outfit),
            weekSchedule = List(7) { DayKind.OFFICE },
            swapChance = 0.0,
            homeNoShoesChance = 0.0,
            wearsBeforeWash = 2,
            climate = Climate(
                monthlyHighC = List(12) { 20.0 },
                monthlyLowC = List(12) { 10.0 },
                monthlyRainChance = List(12) { rainChance },
                windyChance = 0.0,
            ),
        )
    }

    private val officeMild = FixtureOutfit(
        "only", listOf("white-oxford", "navy-chinos", "dress-shoes", "brown-belt"),
        setOf("Work/Business"), 8.0, 18.0, rainOk = false, favouriteWeight = 1.0, saved = true,
    )

    private fun assertNeverDirty(persona: Persona, outfit: FixtureOutfit, plan: List<SimDay>) {
        val logged = plan.filter { it.logged }
        assertTrue(logged.isNotEmpty(), "no logged days at all")
        for (day in logged) {
            assertEquals(outfit.itemKeys.toSet(), day.wornItemKeys.toSet(), "${day.date}")
            assertTrue(day.wornItemKeys.none { it in day.dirtyAtMorning }, "dirty item worn on ${day.date}")
        }
        // Days on which the only outfit is dirty cannot be logged, however the dice fall.
        val blocked = plan.filter { d -> outfit.itemKeys.any { it in d.dirtyAtMorning } }
        assertTrue(blocked.isNotEmpty(), "persona never ran out of clean clothes")
        assertTrue(blocked.none { it.logged }, "logged a day with nothing clean")
    }

    @Test
    fun `same persona gives the same plan`() {
        val persona = MiniPersona.build()
        assertEquals(year(persona), year(persona))
        assertEquals(year(persona), Simulator(MiniPersona.build()).plan(start, 365))
        assertTrue(year(persona) != year(persona.copy(seed = 2L)))
    }

    @Test
    fun `about seventy percent of days are logged`() {
        val share = year(MiniPersona.build()).count { it.logged } / 365.0
        assertTrue(share in 0.60..0.80, "logged share was $share")
    }

    @Test
    fun `raining days pick a rain ok outfit when one is clean`() {
        val persona = MiniPersona.build()
        var checked = 0
        for (day in year(persona)) {
            if (!day.logged || !day.weather.raining) continue
            val worn = day.wornItemKeys.toSet()
            val eligible = persona.outfits.filter { o ->
                day.occasion in o.occasions &&
                    o.minTempC <= day.weather.highC && day.weather.lowC <= o.maxTempC &&
                    o.itemKeys.none { it in day.dirtyAtMorning }
            }
            val rainOk = eligible.filter { it.rainOk }
            if (rainOk.isEmpty()) continue
            checked++
            // A swap or a dropped pair of shoes changes at most one item each.
            val close = rainOk.any { o ->
                (o.itemKeys.toSet() - worn).size <= 1 && (worn - o.itemKeys.toSet()).size <= 1
            }
            assertTrue(close, "rain day ${day.date} wore ${day.wornItemKeys}")
        }
        assertTrue(checked > 0, "no rainy day had a clean rain ok outfit")
    }

    @Test
    fun `fallback relaxes rain then temperature and never logs dirty items`() {
        // Rain: always raining, outfit not rain ok.
        val rain = single(officeMild, rainChance = 1.0)
        val rainPlan = year(rain)
        assertTrue(rainPlan.all { it.weather.raining })
        assertNeverDirty(rain, officeMild, rainPlan)

        // Temperature: far outside the outfit's band.
        val cold = officeMild.copy(minTempC = 40.0, maxTempC = 50.0)
        val tempPlan = year(single(cold, rainChance = 0.0))
        assertNeverDirty(single(cold, 0.0), cold, tempPlan)

        // Occasion: the outfit suits a different occasion altogether.
        val wrong = officeMild.copy(occasions = setOf("Athletic"))
        val occPlan = year(single(wrong, rainChance = 1.0))
        assertNeverDirty(single(wrong, 1.0), wrong, occPlan)
    }

    @Test
    fun `home days sometimes drop footwear`() {
        val footwear = MiniPersona.build().items.filter { it.category == "Footwear" }.map { it.key }.toSet()
        fun shoeless(persona: Persona) = year(persona)
            .filter { it.kind == DayKind.HOME && it.logged }
            .count { d -> d.wornItemKeys.none { it in footwear } }

        val withChance = MiniPersona.build().copy(homeNoShoesChance = 0.6)
        assertTrue(shoeless(withChance) > 0)
        assertEquals(0, shoeless(MiniPersona.build().copy(homeNoShoesChance = 0.0)))
    }

    @Test
    fun `apply writes saved and ad hoc logs through the right paths`() = runTest {
        ScoreboardDatabase().use { env ->
            val persona = MiniPersona.build()
            val written = FixtureWriter.write(env.db, persona)
            val plan = Simulator(persona).plan(start, 120)
            val simulator = Simulator(persona)
            val logs = env.db.logDao()
            var saved = 0
            var adHoc = 0
            for (day in plan) {
                simulator.apply(env.db, written, day)
                val rows = logs.getLogsForDateOnce(day.date.toString())
                if (!day.logged) {
                    assertTrue(rows.isEmpty(), "unlogged day ${day.date} has a log")
                } else {
                    assertEquals(1, rows.size, "${day.date}")
                    val row = rows.single()
                    val ids = logs.getItemIdsForLog(row.id).toSet()
                    assertEquals(day.wornItemKeys.map { written.itemIds.getValue(it) }.toSet(), ids)
                    assertEquals(day.weather.lowC, row.temperatureLow)
                    assertEquals(day.weather.highC, row.temperatureHigh)
                    assertEquals(if (day.weather.raining) 5.0 else 0.0, row.precipitationMm)
                    assertEquals(if (day.weather.windy) 40.0 else 10.0, row.windSpeedKmh)
                    if (day.viaSavedOutfit != null) {
                        saved++
                        assertEquals(written.savedOutfitIds.getValue(day.viaSavedOutfit!!), row.outfitId)
                    } else {
                        adHoc++
                        assertNull(row.outfitId)
                        assertTrue(ids.isNotEmpty())
                    }
                }
                val dirtyIds = env.db.clothingDao().getItemsNeedingWash().map { it.id }.toSet()
                assertEquals(day.dirtyAtNight.map { written.itemIds.getValue(it) }.toSet(), dirtyIds, "${day.date}")
            }
            assertTrue(saved > 0, "no saved-path logs")
            assertTrue(adHoc > 0, "no ad hoc logs")
        }
    }
}
