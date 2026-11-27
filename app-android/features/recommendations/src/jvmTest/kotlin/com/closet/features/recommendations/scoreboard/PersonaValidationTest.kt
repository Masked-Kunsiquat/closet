package com.closet.features.recommendations.scoreboard

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PersonaValidationTest {
    private val base = MiniPersona.build()

    private fun assertRejected(persona: Persona, fragment: String) {
        val error = assertFailsWith<IllegalArgumentException> { Simulator(persona) }
        assertTrue(fragment in error.message.orEmpty(), error.message)
    }

    @Test
    fun `the mini persona is valid`() {
        Simulator(base)
    }

    @Test
    fun `a climate list without twelve months is rejected`() =
        assertRejected(base.copy(climate = base.climate.copy(monthlyHighC = List(11) { 10.0 })), "monthlyHighC")

    @Test
    fun `a week schedule without seven days is rejected`() =
        assertRejected(base.copy(weekSchedule = List(6) { DayKind.OFFICE }), "weekSchedule")

    @Test
    fun `a zero favourite weight is rejected`() =
        assertRejected(base.copy(outfits = base.outfits.map { it.copy(favouriteWeight = 0.0) }), "favouriteWeight")

    @Test
    fun `an unsaved outfit with an unknown item key is rejected`() {
        val outfit = base.outfits.first { !it.saved }
        val bad = outfit.copy(key = "bad", itemKeys = outfit.itemKeys + "no-such-item")
        assertRejected(base.copy(outfits = base.outfits + bad), "no-such-item")
    }

    @Test
    fun `a duplicate item key is rejected`() =
        assertRejected(base.copy(items = base.items + base.items.first()), "duplicate item key")

    @Test
    fun `a duplicate outfit key is rejected`() =
        assertRejected(base.copy(outfits = base.outfits + base.outfits.first()), "duplicate outfit key")

    @Test
    fun `an outfit with no items is rejected`() =
        assertRejected(base.copy(outfits = base.outfits + base.outfits.first().copy(key = "empty", itemKeys = emptyList())), "has no items")

    @Test
    fun `wears before wash below one is rejected`() =
        assertRejected(base.copy(wearsBeforeWash = 0), "wearsBeforeWash")

    @Test
    fun `a chance outside zero to one is rejected`() {
        assertRejected(base.copy(swapChance = 1.5), "swapChance")
        assertRejected(base.copy(homeNoShoesChance = -0.1), "homeNoShoesChance")
        assertRejected(base.copy(climate = base.climate.copy(windyChance = 2.0)), "windyChance")
        assertRejected(base.copy(climate = base.climate.copy(monthlyRainChance = List(12) { 1.2 })), "monthlyRainChance")
    }

    @Test
    fun `the fixture writer rejects a bad persona`() = runTest {
        ScoreboardDatabase().use { env ->
            val error = assertFailsWith<IllegalArgumentException> {
                FixtureWriter.write(env.db, base.copy(weekSchedule = emptyList()))
            }
            assertTrue("weekSchedule" in error.message.orEmpty(), error.message)
        }
    }
}
