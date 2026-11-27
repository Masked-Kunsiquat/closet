package com.closet.features.recommendations.scoreboard

import com.closet.core.data.ClothingDatabase
import com.closet.core.data.model.WashStatus
import com.closet.core.data.repository.RecommendationRepository
import com.closet.features.recommendations.PipelineRequest
import com.closet.features.recommendations.RecommendationPipeline
import com.closet.features.recommendations.engine.OutfitCombo
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.engine.learning.TasteWeights
import com.closet.features.recommendations.model.WeatherConditions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate
import kotlin.random.Random

/** Fails over applicable suggestions (or days, for THREE_DIFFER); N/A outcomes are not counted. */
data class CheckRate(val fails: Int, val applicable: Int)

/**
 * One persona's scoreboard numbers.
 *
 * @property savedShare the share of the persona's catalogue outfits that are saved in the app.
 */
data class PersonaResult(
    val persona: String,
    val evaluatedDays: Int,
    val hitsAt10: Int,
    val noResultDays: Int,
    val savedShare: Double,
    val checks: Map<CheckId, CheckRate>,
)

/** One evaluation day's outcome: per check, one [Outcome] per shown suggestion (one for THREE_DIFFER). */
internal data class DayEvaluation(
    val noResults: Boolean,
    val hitAt10: Boolean,
    val outcomes: Map<CheckId, List<Outcome>>,
)

/**
 * Runs a persona's simulated year through the production [RecommendationPipeline] and scores it.
 *
 * Day by day: on an evaluation day (every [EVAL_EVERY]th logged day from [WARM_UP_DAYS] days after
 * the start) the morning's wash states are written, the pipeline runs twice, and the result is
 * scored; then [Simulator.apply] writes that day's log and laundry. So the pipeline only ever sees
 * strictly earlier history.
 *
 * Taste weights are [TasteWeights.DEFAULT], which mirrors production while
 * `RecommendationViewModel.LEARNED_TASTE_ENABLED` is false. The pipeline reads the date only from
 * the request, so no clock is needed. The JVM has no item embeddings, so the neighbour backoff is
 * off (see [Report]).
 */
object Scoreboard {
    val DEFAULT_START: LocalDate = LocalDate.of(2025, 1, 6)
    const val DEFAULT_DAYS = 365

    private const val WARM_UP_DAYS = 60L
    private const val EVAL_EVERY = 3
    private const val OCCASION_REQUEST_CHANCE = 0.70
    private const val PAGE_SIZE = 3
    private const val HIT_DEPTH = 10
    private const val UPDATED_AT = "2026-01-01T00:00:00Z"
    private val CORE_CATEGORIES = setOf(Checks.TOPS, Checks.BOTTOMS, Checks.ONE_PIECE)

    suspend fun run(persona: Persona, start: LocalDate = DEFAULT_START, days: Int = DEFAULT_DAYS): PersonaResult =
        runObserved(persona, start, days) { _, _ -> }

    /** [run], calling [beforeEvaluating] on each evaluation day after its wash states are set. */
    internal suspend fun runObserved(
        persona: Persona,
        start: LocalDate,
        days: Int,
        beforeEvaluating: suspend (ClothingDatabase, SimDay) -> Unit,
    ): PersonaResult = ScoreboardDatabase().use { env ->
        val db = env.db
        val fixture = FixtureWriter.write(db, persona)
        val simulator = Simulator(persona)
        val plan = simulator.plan(start, days)
        val occasionDraws = Random(persona.seed)
        val warmUpEnd = start.plusDays(WARM_UP_DAYS)
        val evaluations = ArrayList<DayEvaluation>()
        var loggedAfterWarmUp = 0

        plan.forEachIndexed { index, day ->
            if (day.logged && !day.date.isBefore(warmUpEnd)) {
                loggedAfterWarmUp++
                if (loggedAfterWarmUp % EVAL_EVERY == 0) {
                    val requested = if (occasionDraws.nextDouble() < OCCASION_REQUEST_CHANCE) day.occasion else null
                    val yesterday = plan.getOrNull(index - 1)?.takeIf { it.logged }?.wornItemKeys
                    evaluations += evaluateDay(db, persona, fixture, day, requested, yesterday) {
                        beforeEvaluating(db, day)
                    }
                }
            }
            simulator.apply(db, fixture, day)
        }
        tally(persona, evaluations)
    }

    /**
     * One day's evaluation: sets every item's `wash_status` from [SimDay.dirtyAtMorning], runs the
     * pipeline for the page the app shows (`topN = 3`, scored by the checks) and for hit@10
     * (`topN = 10`), each with a new `Random` seeded by the date. An empty page is a no-results day:
     * a miss, with every check N/A.
     *
     * A hit means some top-10 combo's core (its Tops, Bottoms and Dresses & Jumpsuits items) is
     * contained in the core the persona actually wore; containment, not equality, because the engine
     * builds one Top per core while a persona may layer two (a shirt under a sweater).
     */
    internal suspend fun evaluateDay(
        db: ClothingDatabase,
        persona: Persona,
        fixture: WrittenFixture,
        day: SimDay,
        requestedOccasion: String?,
        yesterdayWornKeys: List<String>?,
        beforeEvaluating: suspend () -> Unit = {},
    ): DayEvaluation {
        val clothing = db.clothingDao()
        for ((key, id) in fixture.itemIds) {
            val status = if (key in day.dirtyAtMorning) WashStatus.Dirty else WashStatus.Clean
            clothing.updateWashStatus(id, status.label, UPDATED_AT)
        }
        beforeEvaluating()

        val occasionId = requestedOccasion?.let { name ->
            db.lookupDao().getOccasions().first().firstOrNull { it.name == name }?.id
                ?: error("Unknown occasion: $name")
        }
        val pipeline = RecommendationPipeline(
            recommendationRepository = RecommendationRepository(db.recommendationDao()),
            engine = OutfitRecommendationEngine(),
            computationDispatcher = Dispatchers.Unconfined,
            tasteWeights = { TasteWeights.DEFAULT },
        )
        val today = day.date.toKotlinLocalDate()
        val weather = WeatherConditions(
            tempLowC = day.weather.lowC,
            tempHighC = day.weather.highC,
            isRaining = day.weather.raining,
            isWindy = day.weather.windy,
        )
        fun request(topN: Int) = PipelineRequest(
            today = today, occasionId = occasionId, weather = weather, topN = topN,
            jitter = Random(today.toEpochDays()),
        )

        val keysById = fixture.itemIds.entries.associate { (key, id) -> id to key }
        fun keys(combo: OutfitCombo) =
            combo.items.map { keysById[it.id] ?: error("Combo item ${it.id} is not in the fixture") }
        val page = pipeline.run(request(PAGE_SIZE)).map(::keys)
        val top10 = pipeline.run(request(HIT_DEPTH)).map(::keys)

        val byKey = persona.items.associateBy { it.key }
        fun core(keys: List<String>) = keys.filter { byKey.getValue(it).category in CORE_CATEGORIES }.toSet()
        val wornCore = core(day.wornItemKeys)
        val hit = top10.any { combo -> core(combo).let { it.isNotEmpty() && wornCore.containsAll(it) } }

        val ctx = CheckContext(persona, day, requestedOccasion, yesterdayWornKeys)
        val outcomes = HashMap<CheckId, MutableList<Outcome>>()
        for (suggestion in page) {
            Checks.perSuggestion(suggestion, ctx).forEach { (id, outcome) -> outcomes.getOrPut(id) { ArrayList() } += outcome }
        }
        outcomes.getOrPut(CheckId.THREE_DIFFER) { ArrayList() } += Checks.threeDiffer(page)

        return DayEvaluation(noResults = page.isEmpty(), hitAt10 = hit, outcomes = outcomes)
    }

    /** Sums evaluation days into a [PersonaResult]; every [CheckId] is present. */
    internal fun tally(persona: Persona, evaluations: List<DayEvaluation>): PersonaResult {
        val checks = CheckId.entries.associateWith { id ->
            val all = evaluations.flatMap { it.outcomes[id].orEmpty() }
            CheckRate(fails = all.count { it == Outcome.FAIL }, applicable = all.count { it != Outcome.NOT_APPLICABLE })
        }
        return PersonaResult(
            persona = persona.name,
            evaluatedDays = evaluations.size,
            hitsAt10 = evaluations.count { it.hitAt10 },
            noResultDays = evaluations.count { it.noResults },
            savedShare = if (persona.outfits.isEmpty()) 0.0
            else persona.outfits.count { it.saved }.toDouble() / persona.outfits.size,
            checks = checks,
        )
    }
}
