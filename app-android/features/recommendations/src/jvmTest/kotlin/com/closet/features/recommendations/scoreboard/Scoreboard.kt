package com.closet.features.recommendations.scoreboard

import com.closet.core.data.ClothingDatabase
import com.closet.core.data.model.WashStatus
import com.closet.core.data.repository.RecommendationRepository
import com.closet.features.recommendations.PipelineRequest
import com.closet.features.recommendations.RecommendationPipeline
import com.closet.features.recommendations.RecommendationViewModel
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
 * A day's *core* is its items whose fixture category is in [Scoreboard.CORE_ROLES] (Tops, Bottoms,
 * Dresses & Jumpsuits).
 *
 * @property hitsAt10 evaluation days on which some top-10 combo's core is a subset of the worn core.
 *   Subset, not equality: the app's core is exactly one Top + one Bottom, or one OnePiece, so a
 *   worn core of a shirt under a sweater plus chinos can never be an engine core, and the rule
 *   counts the engine's shirt + chinos (or sweater + chinos) as a hit. [layeredCoreDays] shows how
 *   often that generosity can apply.
 * @property layeredCoreDays evaluation days whose worn core cannot be an engine core: more than one
 *   Top, more than one Bottom, or a OnePiece together with a Top or a Bottom.
 * @property unreachableCoreDays evaluation days whose worn core can never be a hit: it has no Top +
 *   Bottom pair and no OnePiece (for example a tee with Activewear shorts, or an empty core).
 * @property savedShare the share of the persona's catalogue outfits that are saved in the app.
 */
data class PersonaResult(
    val persona: String,
    val evaluatedDays: Int,
    val hitsAt10: Int,
    val noResultDays: Int,
    val layeredCoreDays: Int,
    val unreachableCoreDays: Int,
    val savedShare: Double,
    val checks: Map<CheckId, CheckRate>,
)

/** One evaluation day's outcome: per check, one [Outcome] per shown suggestion (one for THREE_DIFFER). */
internal data class DayEvaluation(
    val noResults: Boolean,
    val hitAt10: Boolean,
    /** The worn core cannot be an engine core (see [PersonaResult.layeredCoreDays]). */
    val layeredCore: Boolean,
    /** The worn core can never be a hit (see [PersonaResult.unreachableCoreDays]). */
    val unreachableCore: Boolean,
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
 * [RecommendationViewModel.LEARNED_TASTE_ENABLED] is false. [evaluateDay] fails with a
 * `check` once that flag is true, so the scoreboard (which gates #145) cannot silently keep scoring
 * DEFAULT weights: wire production's learned-taste resolution in here first. The pipeline reads the
 * date only from the request, so no clock is needed. The JVM has no item embeddings, so the
 * neighbour backoff is off (see [Report]).
 *
 * Whether a day requests its occasion is drawn from `Random(persona.seed xor` [OCCASION_SEED_SALT]`)`,
 * so the draws are independent of the simulator's own `Random(persona.seed)` stream.
 */
object Scoreboard {
    val DEFAULT_START: LocalDate = LocalDate.of(2025, 1, 6)
    const val DEFAULT_DAYS = 365

    private const val WARM_UP_DAYS = 60L
    private const val EVAL_EVERY = 3
    private const val OCCASION_REQUEST_CHANCE = 0.70

    /** A fixed salt for the occasion-request seed; any constant would do, it only has to differ from 0. */
    private const val OCCASION_SEED_SALT = 0x0CCA5_10AL

    /** The app's page size, not a scoring rule. */
    private const val PAGE_SIZE = OutfitRecommendationEngine.TOP_N
    private const val HIT_DEPTH = 10
    private const val UPDATED_AT = "2026-01-01T00:00:00Z"
    /** The fixture categories whose items form a core; they map to the app's Top, Bottom and OnePiece roles. */
    internal val CORE_ROLES = setOf(Checks.TOPS, Checks.BOTTOMS, Checks.ONE_PIECE)

    /** [engine] defaults to production's; the sensitivity check passes one with colour harmony off. */
    suspend fun run(
        persona: Persona,
        start: LocalDate = DEFAULT_START,
        days: Int = DEFAULT_DAYS,
        engine: OutfitRecommendationEngine = OutfitRecommendationEngine(),
    ): PersonaResult = runObserved(persona, start, days, engine) { _, _ -> }

    /** [run], calling [beforeEvaluating] on each evaluation day after its wash states are set. */
    internal suspend fun runObserved(
        persona: Persona,
        start: LocalDate,
        days: Int,
        engine: OutfitRecommendationEngine = OutfitRecommendationEngine(),
        beforeEvaluating: suspend (ClothingDatabase, SimDay) -> Unit,
    ): PersonaResult = ScoreboardDatabase().use { env ->
        val db = env.db
        val fixture = FixtureWriter.write(db, persona)
        val simulator = Simulator(persona)
        val plan = simulator.plan(start, days)
        val occasionDraws = Random(persona.seed xor OCCASION_SEED_SALT)
        val warmUpEnd = start.plusDays(WARM_UP_DAYS)
        val evaluations = ArrayList<DayEvaluation>()
        var loggedAfterWarmUp = 0

        plan.forEachIndexed { index, day ->
            if (day.logged && !day.date.isBefore(warmUpEnd)) {
                loggedAfterWarmUp++
                if (loggedAfterWarmUp % EVAL_EVERY == 0) {
                    val requested = if (occasionDraws.nextDouble() < OCCASION_REQUEST_CHANCE) day.occasion else null
                    val yesterday = plan.getOrNull(index - 1)?.takeIf { it.logged }?.wornItemKeys
                    evaluations += evaluateDay(db, persona, fixture, day, requested, yesterday, engine) {
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
     * A hit means some top-10 combo's core (its [CORE_ROLES] items) is a non-empty subset of the core
     * the persona actually wore. Subset, not equality, because the app's core is one Top + one
     * Bottom, or one OnePiece, while a persona may layer two Tops (a shirt under a sweater). Such a
     * day is also flagged [DayEvaluation.layeredCore], so the report shows how often this applies.
     */
    internal suspend fun evaluateDay(
        db: ClothingDatabase,
        persona: Persona,
        fixture: WrittenFixture,
        day: SimDay,
        requestedOccasion: String?,
        yesterdayWornKeys: List<String>?,
        engine: OutfitRecommendationEngine = OutfitRecommendationEngine(),
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
        check(!RecommendationViewModel.LEARNED_TASTE_ENABLED) {
            "wire learned taste resolution into the scoreboard before enabling it (#145)"
        }
        val pipeline = RecommendationPipeline(
            recommendationRepository = RecommendationRepository(db.recommendationDao()),
            engine = engine,
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
        fun core(keys: List<String>) = keys.filter { byKey.getValue(it).category in CORE_ROLES }.toSet()
        val wornCore = core(day.wornItemKeys)
        val wornCategories = wornCore.map { byKey.getValue(it).category }
        val tops = wornCategories.count { it == Checks.TOPS }
        val bottoms = wornCategories.count { it == Checks.BOTTOMS }
        val onePieces = wornCategories.count { it == Checks.ONE_PIECE }
        val layered = tops > 1 || bottoms > 1 || (onePieces > 0 && tops + bottoms > 0)
        val unreachable = !(tops > 0 && bottoms > 0) && onePieces == 0
        val hit = top10.any { combo -> core(combo).let { it.isNotEmpty() && wornCore.containsAll(it) } }

        val ctx = CheckContext(persona, day, requestedOccasion, yesterdayWornKeys)
        val outcomes = HashMap<CheckId, MutableList<Outcome>>()
        for (suggestion in page) {
            Checks.perSuggestion(suggestion, ctx).forEach { (id, outcome) -> outcomes.getOrPut(id) { ArrayList() } += outcome }
        }
        outcomes.getOrPut(CheckId.THREE_DIFFER) { ArrayList() } += Checks.threeDiffer(page)

        return DayEvaluation(noResults = page.isEmpty(), hitAt10 = hit, layeredCore = layered,
            unreachableCore = unreachable, outcomes = outcomes,
        )
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
            layeredCoreDays = evaluations.count { it.layeredCore },
            unreachableCoreDays = evaluations.count { it.unreachableCore },
            savedShare = if (persona.outfits.isEmpty()) 0.0
            else persona.outfits.count { it.saved }.toDouble() / persona.outfits.size,
            checks = checks,
        )
    }
}
