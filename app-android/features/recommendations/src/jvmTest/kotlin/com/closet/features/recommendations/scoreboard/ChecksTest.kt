package com.closet.features.recommendations.scoreboard

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChecksTest {
    private val persona = MiniPersona.build()

    private fun day(
        low: Double = 10.0,
        high: Double = 18.0,
        raining: Boolean = false,
        dirty: Set<String> = emptySet(),
        date: LocalDate = LocalDate.of(2026, 4, 14),
    ) = SimDay(
        date = date, kind = DayKind.OFFICE, occasion = "Work/Business",
        weather = DayWeather(low, high, raining, windy = false),
        logged = false, wornItemKeys = emptyList(), viaSavedOutfit = null,
        dirtyAtMorning = dirty, dirtyAtNight = dirty,
    )

    private fun ctx(
        day: SimDay = day(),
        occasion: String? = null,
        yesterday: List<String>? = null,
        persona: Persona = this.persona,
    ) = CheckContext(persona, day, occasion, yesterday)

    private fun run(keys: List<String>, ctx: CheckContext = ctx()) = Checks.perSuggestion(keys, ctx)

    private val basic = listOf("white-oxford", "navy-chinos", "dress-shoes")

    // COMPLETE
    @Test
    fun `complete passes with top bottom and shoes`() {
        assertEquals(Outcome.PASS, run(basic)[CheckId.COMPLETE])
    }

    @Test
    fun `complete fails without a bottom`() {
        assertEquals(Outcome.FAIL, run(listOf("white-oxford", "dress-shoes"))[CheckId.COMPLETE])
    }

    @Test
    fun `complete fails without shoes when clean footwear exists`() {
        assertEquals(Outcome.FAIL, run(listOf("white-oxford", "navy-chinos"))[CheckId.COMPLETE])
    }

    @Test
    fun `complete passes without shoes when no clean footwear exists`() {
        val allShoes = persona.items.filter { it.category == "Footwear" }.map { it.key }.toSet()
        val c = ctx(day = day(dirty = allShoes))
        assertEquals(Outcome.PASS, run(listOf("white-oxford", "navy-chinos"), c)[CheckId.COMPLETE])
    }

    @Test
    fun `complete passes without shoes when no footwear suits the season`() {
        val summerOnly = persona.copy(items = persona.items.map {
            if (it.category == "Footwear") it.copy(seasons = setOf("Summer")) else it
        })
        val c = ctx(persona = summerOnly, day = day(date = LocalDate.of(2026, 1, 15)))
        assertEquals(Outcome.PASS, run(listOf("white-oxford", "navy-chinos"), c)[CheckId.COMPLETE])
    }

    @Test
    fun `complete accepts a one piece in place of top and bottom`() {
        val dress = FixtureItem(
            "sun-dress", "Dress", "Dresses & Jumpsuits", "Dress", listOf("#FF0000" to 1.0), setOf("Cotton"),
            setOf("All Season"), setOf("Casual"), warmth = 1, rainReady = false, formality = 2,
        )
        val p = persona.copy(items = persona.items + dress)
        assertEquals(Outcome.PASS, run(listOf("sun-dress", "dress-shoes"), ctx(persona = p))[CheckId.COMPLETE])
    }

    // WARM_ENOUGH
    @Test
    fun `warm enough passes when warmth meets the need`() {
        assertEquals(Outcome.PASS, run(basic, ctx(day(low = 12.0)))[CheckId.WARM_ENOUGH])
    }

    @Test
    fun `warm enough fails when too light for a freezing day`() {
        assertEquals(Outcome.FAIL, run(basic, ctx(day(low = -8.0, high = 0.0)))[CheckId.WARM_ENOUGH])
    }

    @Test
    fun `warmth needed grows as the low falls`() {
        val needs = listOf(25.0, 15.0, 8.0, 2.0, -5.0).map { Checks.warmthNeeded(it) }
        assertEquals(needs.sorted(), needs)
        assertEquals(0, needs.first())
        assertTrue(needs.last() > needs.first())
    }

    // NOT_TOO_WARM
    @Test
    fun `not too warm is not applicable below twenty four degrees`() {
        val heavy = listOf("white-oxford", "navy-chinos", "dress-shoes", "wool-coat")
        assertEquals(Outcome.NOT_APPLICABLE, run(heavy, ctx(day(low = 10.0, high = 23.9)))[CheckId.NOT_TOO_WARM])
    }

    @Test
    fun `not too warm fails with a heaviest item on a hot day`() {
        val heavy = listOf("white-oxford", "navy-chinos", "dress-shoes", "wool-coat")
        assertEquals(Outcome.FAIL, run(heavy, ctx(day(low = 20.0, high = 30.0)))[CheckId.NOT_TOO_WARM])
    }

    @Test
    fun `not too warm passes with light items on a hot day`() {
        assertEquals(Outcome.PASS, run(basic, ctx(day(low = 20.0, high = 30.0)))[CheckId.NOT_TOO_WARM])
    }

    // RAIN_READY
    @Test
    fun `rain ready is not applicable on a dry day`() {
        assertEquals(Outcome.NOT_APPLICABLE, run(basic)[CheckId.RAIN_READY])
    }

    @Test
    fun `rain ready is not applicable when no clean rain gear exists`() {
        val gear = persona.items.filter { it.rainReady }.map { it.key }.toSet()
        val c = ctx(day(raining = true, dirty = gear))
        assertEquals(Outcome.NOT_APPLICABLE, run(basic, c)[CheckId.RAIN_READY])
    }

    @Test
    fun `rain ready is not applicable when the only clean rain gear is out of season`() {
        // April is Spring: the wool coat is Winter only, so with the boots dirty no gear suits the day.
        val c = ctx(day(raining = true, dirty = setOf("rain-boots")))
        assertEquals(Outcome.NOT_APPLICABLE, run(basic, c)[CheckId.RAIN_READY])
    }

    @Test
    fun `rain ready passes with waterproof boots`() {
        val c = ctx(day(raining = true))
        assertEquals(Outcome.PASS, run(listOf("white-oxford", "navy-chinos", "rain-boots"), c)[CheckId.RAIN_READY])
    }

    @Test
    fun `rain ready fails without any rain gear on a wet day`() {
        assertEquals(Outcome.FAIL, run(basic, ctx(day(raining = true)))[CheckId.RAIN_READY])
    }

    // FORMALITY
    @Test
    fun `formality passes for a coherent outfit`() {
        assertEquals(Outcome.PASS, run(basic)[CheckId.FORMALITY])
    }

    @Test
    fun `formality fails when the spread is wider than two`() {
        val mixed = listOf("white-oxford", "dark-jeans", "sneakers")
        assertEquals(Outcome.FAIL, run(mixed)[CheckId.FORMALITY])
    }

    @Test
    fun `formality fails when an item is below the occasion band`() {
        val c = ctx(occasion = "Work/Business")
        assertEquals(Outcome.FAIL, run(listOf("blue-tee", "dark-jeans", "sneakers"), c)[CheckId.FORMALITY])
    }

    @Test
    fun `formality passes when every item is in the occasion band`() {
        val c = ctx(occasion = "Work/Business")
        assertEquals(Outcome.PASS, run(basic, c)[CheckId.FORMALITY])
    }

    @Test
    fun `formality fails when an item is above the occasion band`() {
        val c = ctx(occasion = "Athletic")
        assertEquals(Outcome.FAIL, run(listOf("gym-shorts", "white-oxford"), c)[CheckId.FORMALITY])
    }

    // CLEAN
    @Test
    fun `clean passes when nothing is dirty`() {
        assertEquals(Outcome.PASS, run(basic)[CheckId.CLEAN])
    }

    @Test
    fun `clean fails when an item is dirty`() {
        assertEquals(Outcome.FAIL, run(basic, ctx(day(dirty = setOf("navy-chinos"))))[CheckId.CLEAN])
    }

    // NO_DOUBLED_SLOTS
    @Test
    fun `no doubled slots passes with one of each`() {
        assertEquals(Outcome.PASS, run(basic + "brown-belt" + "wool-coat")[CheckId.NO_DOUBLED_SLOTS])
    }

    @Test
    fun `no doubled slots fails with two pairs of shoes`() {
        assertEquals(Outcome.FAIL, run(basic + "sneakers")[CheckId.NO_DOUBLED_SLOTS])
    }

    @Test
    fun `no doubled slots fails with two coats`() {
        assertEquals(Outcome.FAIL, run(basic + "wool-coat" + "light-jacket")[CheckId.NO_DOUBLED_SLOTS])
    }

    @Test
    fun `no doubled slots fails with two belts`() {
        assertEquals(Outcome.FAIL, run(basic + "brown-belt" + "black-belt")[CheckId.NO_DOUBLED_SLOTS])
    }

    // THREE_DIFFER
    @Test
    fun `three differ passes when every pair differs in two items`() {
        val page = listOf(
            listOf("white-oxford", "navy-chinos", "dress-shoes"),
            listOf("blue-oxford", "khaki-chinos", "dress-shoes"),
            listOf("white-polo", "dark-jeans", "sneakers"),
        )
        assertEquals(Outcome.PASS, Checks.threeDiffer(page))
    }

    @Test
    fun `three differ fails when two suggestions differ in one item`() {
        val page = listOf(
            listOf("white-oxford", "navy-chinos", "dress-shoes"),
            listOf("white-oxford", "navy-chinos", "sneakers"),
        )
        assertEquals(Outcome.FAIL, Checks.threeDiffer(page))
    }

    @Test
    fun `three differ is not applicable with fewer than two suggestions`() {
        assertEquals(Outcome.NOT_APPLICABLE, Checks.threeDiffer(emptyList()))
        assertEquals(Outcome.NOT_APPLICABLE, Checks.threeDiffer(listOf(basic)))
    }

    // NOT_ON_REPEAT
    @Test
    fun `not on repeat is not applicable when yesterday was not logged`() {
        assertEquals(Outcome.NOT_APPLICABLE, run(basic, ctx(yesterday = null))[CheckId.NOT_ON_REPEAT])
    }

    @Test
    fun `not on repeat passes when the outfit differs from yesterday`() {
        val c = ctx(yesterday = listOf("blue-tee", "dark-jeans", "sneakers"))
        assertEquals(Outcome.PASS, run(basic, c)[CheckId.NOT_ON_REPEAT])
    }

    @Test
    fun `not on repeat fails when an ad hoc outfit repeats yesterday`() {
        assertEquals(Outcome.FAIL, run(basic, ctx(yesterday = basic.reversed()))[CheckId.NOT_ON_REPEAT])
    }

    @Test
    fun `not on repeat passes for a favourite catalogue outfit`() {
        val weighted = persona.copy(outfits = persona.outfits.mapIndexed { i, o ->
            if (i == 0) o.copy(favouriteWeight = 5.0) else o
        })
        val keys = weighted.outfits.first().itemKeys
        assertEquals(Outcome.PASS, run(keys, ctx(persona = weighted, yesterday = keys))[CheckId.NOT_ON_REPEAT])
    }

    @Test
    fun `not on repeat fails for a below median catalogue outfit`() {
        val weighted = persona.copy(outfits = persona.outfits.mapIndexed { i, o ->
            if (i == 0) o.copy(favouriteWeight = 0.1) else o
        })
        val keys = weighted.outfits.first().itemKeys
        assertEquals(Outcome.FAIL, run(keys, ctx(persona = weighted, yesterday = keys))[CheckId.NOT_ON_REPEAT])
    }

    // Programming errors
    @Test
    fun `unknown fixture key throws`() {
        assertFailsWith<IllegalStateException> { run(listOf("no-such-item")) }
    }
}
