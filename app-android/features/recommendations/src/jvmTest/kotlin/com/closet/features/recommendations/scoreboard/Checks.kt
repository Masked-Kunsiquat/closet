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
    private const val WARM_RAIN_MEAN_C = 20.0
    private val CORE_CATEGORIES = setOf(TOPS, BOTTOMS, ONE_PIECE)

    /**
     * Total item warmth (each item 0..3) an outfit needs for a day whose mean temperature
     * (`(low + high) / 2`) is [meanC]: 18 and above 0, 12 up to 18 needs 1, 6 up to 12 needs 2,
     * 0 up to 6 needs 3, -6 up to 0 needs 4, below -6 needs 5. The mean, not the low, because people
     * dress for the day and the personas' own choices follow it. WARM_ENOUGH is not applicable on
     * [DayKind.HOME] and [DayKind.GYM] days (indoors).
     */
    fun warmthNeeded(meanC: Double): Int = when {
        meanC >= 18.0 -> 0
        meanC >= 12.0 -> 1
        meanC >= 6.0 -> 2
        meanC >= 0.0 -> 3
        meanC >= -6.0 -> 4
        else -> 5
    }

    /**
     * Inclusive formality (1..5) band an occasion allows, by seeded occasion name. It applies to core
     * items only. Work/Business is 2..5 because business casual is normal office wear.
     */
    private val OCCASION_BANDS: Map<String, IntRange> = mapOf(
        "Casual" to 1..4,
        "Work/Business" to 2..5,
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
            // Indoors (home, gym) the weather does not decide what to wear, so warmth is not judged.
            CheckId.WARM_ENOUGH to if (indoors(day)) Outcome.NOT_APPLICABLE
            else pass(items.sumOf { it.warmth } >= warmthNeeded(meanC(day))),
            CheckId.NOT_TOO_WARM to if (day.weather.highC < HOT_HIGH_C) Outcome.NOT_APPLICABLE
            else pass(items.none { it.warmth == 3 }),
            CheckId.RAIN_READY to rainReady(items, ctx),
            CheckId.FORMALITY to formality(items, ctx.requestedOccasion),
            CheckId.CLEAN to pass(suggestion.none { it in dirty }),
            // Only footwear and belts: outerwear may layer (a blazer under a coat).
            CheckId.NO_DOUBLED_SLOTS to pass(
                items.count { it.category == FOOTWEAR } <= 1 && items.count { it.subcategory == BELT } <= 1,
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

    private fun meanC(day: SimDay) = (day.weather.lowC + day.weather.highC) / 2

    /** Home and gym days are spent indoors. */
    private fun indoors(day: SimDay) = day.kind == DayKind.HOME || day.kind == DayKind.GYM

    private fun pass(ok: Boolean) = if (ok) Outcome.PASS else Outcome.FAIL

    /**
     * COMPLETE: a core, plus footwear when the persona owns clean footwear suiting the day's calendar
     * season. Footwear is not required on [DayKind.HOME] days (people go barefoot or in socks at
     * home); the core still is. A failure can come from the pipeline's candidate filter (the requested occasion or the
     * season removed every shoe), not only from ranking.
     */
    private fun complete(items: List<FixtureItem>, ctx: CheckContext): Outcome {
        val categories = items.map { it.category }.toSet()
        val body = ONE_PIECE in categories || (TOPS in categories && BOTTOMS in categories)
        if (!body) return Outcome.FAIL
        val shoesAvailable = ctx.persona.items.any {
            it.category == FOOTWEAR && it.key !in ctx.day.dirtyAtMorning && suitsSeason(it, ctx)
        }
        return pass(!shoesAvailable || ctx.day.kind == DayKind.HOME || FOOTWEAR in categories)
    }

    private fun suitsSeason(item: FixtureItem, ctx: CheckContext): Boolean {
        val season = seasonFor(Month(ctx.day.date.monthValue))
        return season in item.seasons || ALL_SEASON in item.seasons
    }

    /**
     * RAIN_READY: on a rainy day, when the persona owns clean rain-ready footwear or outerwear whose
     * seasons include the day's calendar season (or "All Season"), the outfit includes one. A failure
     * can come from the pipeline's candidate filter (the requested occasion removed the rain gear),
     * not only from ranking. Not applicable on [DayKind.HOME] and [DayKind.GYM] days (indoors), nor when
     * the day's mean temperature is [WARM_RAIN_MEAN_C] or more: in warm rain people carry an umbrella
     * rather than wear rain gear.
     */
    private fun rainReady(items: List<FixtureItem>, ctx: CheckContext): Outcome {
        if (!ctx.day.weather.raining) return Outcome.NOT_APPLICABLE
        if (indoors(ctx.day)) return Outcome.NOT_APPLICABLE
        if (meanC(ctx.day) >= WARM_RAIN_MEAN_C) return Outcome.NOT_APPLICABLE
        val gearExists = ctx.persona.items.any {
            it.rainReady && (it.category == FOOTWEAR || it.category == OUTERWEAR) &&
                it.key !in ctx.day.dirtyAtMorning && suitsSeason(it, ctx)
        }
        if (!gearExists) return Outcome.NOT_APPLICABLE
        return pass(items.any { it.rainReady && (it.category == FOOTWEAR || it.category == OUTERWEAR) })
    }

    /**
     * FORMALITY: the occasion band applies to core items (tops, bottoms, one-pieces) only, and the
     * coherence rule (max minus min at most [MAX_FORMALITY_SPREAD]) to core items plus footwear only.
     * Outerwear and accessories are excluded: a rain jacket or a watch over office clothes is not a
     * formality clash.
     */
    private fun formality(items: List<FixtureItem>, occasion: String?): Outcome {
        val core = items.filter { it.category in CORE_CATEGORIES }
        val coherent = items.filter { it.category in CORE_CATEGORIES || it.category == FOOTWEAR }
        if (coherent.isEmpty()) return Outcome.PASS
        val levels = coherent.map { it.formality }
        if (levels.max() - levels.min() > MAX_FORMALITY_SPREAD) return Outcome.FAIL
        if (occasion == null) return Outcome.PASS
        val band = OCCASION_BANDS[occasion] ?: error("Unknown occasion: $occasion")
        return pass(core.all { it.formality in band })
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
