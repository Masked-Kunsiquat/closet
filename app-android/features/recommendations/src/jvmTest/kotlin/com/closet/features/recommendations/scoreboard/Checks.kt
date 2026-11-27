package com.closet.features.recommendations.scoreboard

import com.closet.features.recommendations.seasonFor
import kotlinx.datetime.Month

enum class CheckId { COMPLETE, WARM_ENOUGH, NOT_TOO_WARM, RAIN_READY, FORMALITY, CLEAN, NO_DOUBLED_SLOTS, THREE_DIFFER, NOT_ON_REPEAT }

enum class Outcome { PASS, FAIL, NOT_APPLICABLE }

data class CheckContext(
    val persona: Persona,
    val day: SimDay,
    val requestedOccasion: String?,
    /** Null when yesterday was not logged. */
    val yesterdayWornKeys: List<String>?,
)

/**
 * The nine common-sense checks, an independent rubric: they read only fixture keys, the
 * scoreboard-only tags (warmth, rainReady, formality) and the simulated day. They deliberately
 * share no constants or rules with the recommendation engine.
 */
object Checks {
    // Seeded category and subcategory names, exactly as DatabaseSeeder spells them.
    const val TOPS = "Tops"
    const val BOTTOMS = "Bottoms"
    const val ONE_PIECE = "Dresses & Jumpsuits"
    const val FOOTWEAR = "Footwear"
    const val OUTERWEAR = "Outerwear"
    const val BELT = "Belt"
    const val ALL_SEASON = "All Season"

    /** A hot day: from this high up the NOT_TOO_WARM check applies. */
    private const val HOT_HIGH_C = 24.0
    private const val MAX_FORMALITY_SPREAD = 2

    /**
     * Total item warmth (each item 0..3) an outfit needs for a day whose low is [lowC]:
     * 16 and above 0, 10 up to 16 needs 2, 5 up to 10 needs 3, 0 up to 5 needs 4, below 0 needs 5.
     */
    fun warmthNeeded(lowC: Double): Int = when {
        lowC >= 16.0 -> 0
        lowC >= 10.0 -> 2
        lowC >= 5.0 -> 3
        lowC >= 0.0 -> 4
        else -> 5
    }

    /** Inclusive formality (1..5) band an occasion allows, by seeded occasion name. */
    private val OCCASION_BANDS: Map<String, IntRange> = mapOf(
        "Casual" to 1..4,
        "Work/Business" to 3..5,
        "Formal" to 4..5,
        "Athletic" to 1..2,
        "Loungewear" to 1..2,
        "Date Night" to 2..5,
        "Vacation" to 1..3,
        "Outdoor/Hiking" to 1..2,
        "Special Occasion" to 3..5,
    )

    /** Per-suggestion checks (all but THREE_DIFFER); each suggestion is a list of fixture item keys. */
    fun perSuggestion(suggestion: List<String>, ctx: CheckContext): Map<CheckId, Outcome> {
        val byKey = ctx.persona.items.associateBy { it.key }
        fun item(key: String) = byKey[key] ?: error("Unknown fixture item key: $key")
        val items = suggestion.map(::item)
        val day = ctx.day
        val dirty = day.dirtyAtMorning

        return mapOf(
            CheckId.COMPLETE to complete(items, ctx),
            CheckId.WARM_ENOUGH to pass(items.sumOf { it.warmth } >= warmthNeeded(day.weather.lowC)),
            CheckId.NOT_TOO_WARM to if (day.weather.highC < HOT_HIGH_C) Outcome.NOT_APPLICABLE
            else pass(items.none { it.warmth == 3 }),
            CheckId.RAIN_READY to rainReady(items, ctx),
            CheckId.FORMALITY to formality(items, ctx.requestedOccasion),
            CheckId.CLEAN to pass(suggestion.none { it in dirty }),
            CheckId.NO_DOUBLED_SLOTS to pass(
                items.count { it.category == FOOTWEAR } <= 1 &&
                    items.count { it.category == OUTERWEAR } <= 1 &&
                    items.count { it.subcategory == BELT } <= 1,
            ),
            CheckId.NOT_ON_REPEAT to notOnRepeat(suggestion, ctx),
        )
    }

    /** Every pair of shown suggestions must differ in at least 2 items; N/A below two suggestions. */
    fun threeDiffer(page: List<List<String>>): Outcome {
        if (page.size < 2) return Outcome.NOT_APPLICABLE
        val sets = page.map { it.toSet() }
        for (i in sets.indices) {
            for (j in i + 1 until sets.size) {
                val differing = maxOf((sets[i] - sets[j]).size, (sets[j] - sets[i]).size) // a one-item swap differs in one item
                if (differing < 2) return Outcome.FAIL
            }
        }
        return Outcome.PASS
    }

    private fun pass(ok: Boolean) = if (ok) Outcome.PASS else Outcome.FAIL

    /**
     * COMPLETE: a core, plus footwear when the persona owns clean footwear suiting the day's calendar
     * season. A failure can come from the pipeline's candidate filter (the requested occasion or the
     * season removed every shoe), not only from ranking.
     */
    private fun complete(items: List<FixtureItem>, ctx: CheckContext): Outcome {
        val categories = items.map { it.category }.toSet()
        val body = ONE_PIECE in categories || (TOPS in categories && BOTTOMS in categories)
        if (!body) return Outcome.FAIL
        val shoesAvailable = ctx.persona.items.any {
            it.category == FOOTWEAR && it.key !in ctx.day.dirtyAtMorning && suitsSeason(it, ctx)
        }
        return pass(!shoesAvailable || FOOTWEAR in categories)
    }

    private fun suitsSeason(item: FixtureItem, ctx: CheckContext): Boolean {
        val season = seasonFor(Month(ctx.day.date.monthValue))
        return season in item.seasons || ALL_SEASON in item.seasons
    }

    /**
     * RAIN_READY: on a rainy day, when the persona owns clean rain-ready footwear or outerwear whose
     * seasons include the day's calendar season (or "All Season"), the outfit includes one. A failure
     * can come from the pipeline's candidate filter (the requested occasion removed the rain gear),
     * not only from ranking.
     */
    private fun rainReady(items: List<FixtureItem>, ctx: CheckContext): Outcome {
        if (!ctx.day.weather.raining) return Outcome.NOT_APPLICABLE
        val gearExists = ctx.persona.items.any {
            it.rainReady && (it.category == FOOTWEAR || it.category == OUTERWEAR) &&
                it.key !in ctx.day.dirtyAtMorning && suitsSeason(it, ctx)
        }
        if (!gearExists) return Outcome.NOT_APPLICABLE
        return pass(items.any { it.rainReady && (it.category == FOOTWEAR || it.category == OUTERWEAR) })
    }

    private fun formality(items: List<FixtureItem>, occasion: String?): Outcome {
        if (items.isEmpty()) return Outcome.PASS
        val levels = items.map { it.formality }
        if (levels.max() - levels.min() > MAX_FORMALITY_SPREAD) return Outcome.FAIL
        if (occasion == null) return Outcome.PASS
        val band = OCCASION_BANDS[occasion] ?: error("Unknown occasion: $occasion")
        return pass(levels.all { it in band })
    }

    private fun notOnRepeat(suggestion: List<String>, ctx: CheckContext): Outcome {
        val yesterday = ctx.yesterdayWornKeys ?: return Outcome.NOT_APPLICABLE
        val set = suggestion.toSet()
        if (set != yesterday.toSet()) return Outcome.PASS
        val weights = ctx.persona.outfits.map { it.favouriteWeight }.sorted()
        if (weights.isEmpty()) return Outcome.FAIL
        val median = if (weights.size % 2 == 1) weights[weights.size / 2]
        else (weights[weights.size / 2 - 1] + weights[weights.size / 2]) / 2.0
        val favourite = ctx.persona.outfits.any { it.itemKeys.toSet() == set && it.favouriteWeight >= median }
        return pass(favourite)
    }
}
