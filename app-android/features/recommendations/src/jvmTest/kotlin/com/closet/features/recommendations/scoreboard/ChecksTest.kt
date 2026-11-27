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
        kind: DayKind = DayKind.OFFICE,
    ) = SimDay(
        date = date, kind = kind, occasion = "Work/Business",
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
    fun `warmth needed follows the day mean table`() {
        val table = listOf(
            30.0 to 0, 18.0 to 0, 17.9 to 1, 12.0 to 1, 11.9 to 2, 6.0 to 2, 5.9 to 3,
            0.0 to 3, -0.1 to 4, -6.0 to 4, -6.1 to 5, -20.0 to 5,
        )
        for ((mean, needed) in table) assertEquals(needed, Checks.warmthNeeded(mean), "mean $mean")
    }

    @Test
    fun `warm enough reads the day mean not the low`() {
        // Low 0 high 20 has mean 10 and needs 2: basic (3) passes. Low -8 high 0 has mean -4 and needs 4.
        assertEquals(Outcome.PASS, run(basic, ctx(day(low = 0.0, high = 20.0)))[CheckId.WARM_ENOUGH])
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

    @Test
    fun `home days do not require footwear`() {
        val shoeless = listOf("white-oxford", "navy-chinos")
        assertEquals(Outcome.PASS, run(shoeless, ctx(day(kind = DayKind.HOME)))[CheckId.COMPLETE])
        assertEquals(Outcome.FAIL, run(shoeless, ctx(day(kind = DayKind.ERRANDS)))[CheckId.COMPLETE])
        // The core requirement still applies at home.
        assertEquals(Outcome.FAIL, run(listOf("white-oxford", "dress-shoes"), ctx(day(kind = DayKind.HOME)))[CheckId.COMPLETE])
    }

    @Test
    fun `rain ready is not applicable at home or the gym`() {
        for (kind in listOf(DayKind.HOME, DayKind.GYM)) {
            assertEquals(Outcome.NOT_APPLICABLE, run(basic, ctx(day(raining = true, kind = kind)))[CheckId.RAIN_READY], "$kind")
        }
        assertEquals(Outcome.FAIL, run(basic, ctx(day(raining = true, kind = DayKind.ERRANDS)))[CheckId.RAIN_READY])
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
    fun `the work band only applies to core items`() {
        val c = ctx(occasion = "Work/Business")
        // An informal sneaker-free outfit with a casual belt-like accessory and a rain jacket is fine:
        // gym-shorts (activewear) is outside the core, so only the oxford and chinos face the band.
        assertEquals(Outcome.PASS, run(listOf("white-oxford", "navy-chinos", "dress-shoes", "gym-shorts"), c)[CheckId.FORMALITY])
        // Business casual: a level 2 top is inside the widened 2..5 band.
        assertEquals(Outcome.PASS, run(listOf("white-oxford", "dark-jeans", "dress-shoes"), c)[CheckId.FORMALITY])
        // Outerwear is excluded from coherence: a formality 2 jacket over formal clothes is no clash.
        assertEquals(Outcome.PASS, run(listOf("white-oxford", "navy-chinos", "dress-shoes", "light-jacket", "brown-belt"), c)[CheckId.FORMALITY])
        // Footwear counts for coherence though not for the band: oxford 4 with sneakers 1 is a spread of 3.
        assertEquals(Outcome.FAIL, run(listOf("white-oxford", "navy-chinos", "sneakers"), c)[CheckId.FORMALITY])
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
    fun `outerwear layering is not a doubled slot`() {
        assertEquals(Outcome.PASS, run(basic + "wool-coat" + "light-jacket")[CheckId.NO_DOUBLED_SLOTS])
        assertEquals(Outcome.FAIL, run(basic + "rain-boots")[CheckId.NO_DOUBLED_SLOTS])
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
