package com.closet.features.recommendations.engine.learning

import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.ItemNeighbor
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import kotlinx.datetime.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Which stage of the two-stage gate (#142, D3c) the result's weights come from. */
enum class TasteTrainingStage {
    /** Nothing was accepted (or training did not run): the weights are the prior. */
    NONE,

    /** Only the habit weight (`historyLift`) was learned; every other weight is the prior's. */
    HABIT,

    /** Every non-frozen weight was learned, and beat the habit-only (or prior) reference. */
    FULL,
}

/**
 * Result of time-ordered cross-validation and final conditional logit fitting.
 *
 * @property weights            Final learned [TasteWeights] (or [TasteWeights.DEFAULT] on cold start / no improvement).
 * @property selectedLambda     $L_2$ regularization strength $\lambda^*$ of the stage that won ([acceptedStage]),
 *                              or `null` when nothing was accepted.
 * @property meanLogLikDelta    Mean per-stratum held-out conditional log-likelihood gain of the winning stage
 *                              over its reference: for [TasteTrainingStage.FULL], over the habit-only model (or
 *                              the prior, when the habit stage was rejected); for [TasteTrainingStage.HABIT],
 *                              over the prior. With nothing accepted, the full stage's gain over its reference.
 * @property strataCount        Total valid choice strata constructed from the wear logs.
 * @property meanLogLikDeltaStandardError Cluster-robust standard error of [meanLogLikDelta], clustered by worn
 *                              outfit ([ChoiceStratum.choiceKey]; #142, D3). 0.0 when cross-validation did not run.
 * @property acceptedStage      Which stage of the two-stage gate won (#142, D3c).
 */
data class TasteTrainingResult(
    val weights: TasteWeights,
    val selectedLambda: Double?,
    val meanLogLikDelta: Double,
    val strataCount: Int,
    val meanLogLikDeltaStandardError: Double = 0.0,
    val acceptedStage: TasteTrainingStage = TasteTrainingStage.NONE,
)

/**
 * End-to-end trainer for personalized [TasteWeights] (Issue #114).
 *
 * 1. Builds chronological 1-case choice strata via [HistoricalStratumBuilder] (or accepts pre-built strata).
 * 2. Applies the cold-start guards: if $N_{\text{strata}} < 20$, or fewer than [MIN_DISTINCT_CHOICES]
 *    distinct worn outfits ([ChoiceStratum.choiceKey]s), returns [TasteWeights.DEFAULT].
 * 3. Cross-validates over expanding time-ordered folds **grouped by worn outfit** (see [groupedFolds]):
 *    a fold never trains on an outfit it validates on. Along the log-spaced path
 *    $\lambda \in \{100.0, 30.0, 10.0, 3.0, 1.0, 0.3, 0.1\}$ (warm starts from $\lambda_{\max}$ down),
 *    the held-out per-stratum gains are summarised by their mean and a cluster-robust standard error
 *    (see [clusterMeanAndStandardError]), and $\lambda^*$ is picked by the **one-standard-error rule**
 *    toward stronger regularization.
 * 4. Gates in two stages (#142, D3c), each accepted only when its gain exceeds [GATE_SE_MULTIPLIER]
 *    standard errors:
 *    - **habit:** only `historyLift` free, against the prior;
 *    - **rules:** every non-frozen weight free, paired against the habit model's held-out
 *      likelihoods if the habit stage was accepted, otherwise the prior's.
 *    The result is the full fit, else the habit-only fit, else the prior, refitted on all
 *    $N_{\text{strata}}$ at the winning stage's $\lambda^*$. A genuine habit therefore can't carry
 *    the rule weights through the gate on its own.
 */
internal object TasteWeightTrainer {

    /**
     * Regularization path walked from largest $\lambda$ (strongest prior shrinkage) down to smallest $\lambda$,
     * enabling warm starts along the path (Reid & Tibshirani 2014, §2.6).
     */
    internal val LAMBDA_PATH = doubleArrayOf(100.0, 30.0, 10.0, 3.0, 1.0, 0.3, 0.1)

    /**
     * The acceptance gate's multiple of the standard error (#135, design D3b): learned weights are
     * kept only when the chosen lambda's mean held-out gain exceeds this many standard errors. At
     * one SE about 15% of pure-noise histories passed, and their fits inverted rules down to the
     * drift floor; at two SE none of the 20 noise seeds in `LearningOutcomeTest` passes.
     */
    internal const val GATE_SE_MULTIPLIER = 2.0

    /**
     * The smallest training fold that is fitted: the initial expanding window, and the floor a
     * grouped fold may be trimmed to before it is skipped (#142, D2).
     */
    internal const val MIN_INITIAL_TRAIN_STRATA = 10

    /**
     * Learning needs at least this many distinct worn outfits ([ChoiceStratum.choiceKey]s) as well
     * as [ConditionalLogitOptimizer.MIN_STRATA_FOR_TRAINING] strata (#142, D4): rule weights can't
     * be identified from a handful of outfits, however often they're worn. A user below it keeps
     * the default weights, whose score already blends in the co-wear habit.
     */
    internal const val MIN_DISTINCT_CHOICES = 20

    /**
     * The gate needs held-out scores from at least this many distinct outfits: one outfit has no
     * spread to estimate a standard error from, so its SE of 0 would pass any positive mean.
     */
    private const val MIN_SCORED_CHOICES = 2
    private const val MAX_EXPANDING_FOLDS = 5

    /**
     * Reconstructs historical strata from [items] and [logs] and fits personalized [TasteWeights].
     */
    fun trainFromHistory(
        items: List<EngineItem>,
        logs: List<HistoricalWearLog>,
        createdDates: Map<Long, LocalDate> = emptyMap(),
        itemNeighbors: Map<Long, List<ItemNeighbor>> = emptyMap(),
        engine: OutfitRecommendationEngine = OutfitRecommendationEngine(),
    ): TasteTrainingResult {
        if (logs.size < ConditionalLogitOptimizer.MIN_STRATA_FOR_TRAINING) {
            return TasteTrainingResult(
                weights = TasteWeights.DEFAULT,
                selectedLambda = null,
                meanLogLikDelta = 0.0,
                strataCount = 0,
            )
        }
        val strata = HistoricalStratumBuilder.buildStrata(
            items = items,
            logs = logs,
            createdDates = createdDates,
            itemNeighbors = itemNeighbors,
            engine = engine,
        )
        return trainOnStrata(strata)
    }

    /**
     * Fits personalized [TasteWeights] on chronologically ordered [strata] with grouped
     * time-ordered cross-validation, the 1-SE rule and the two-stage gate (#142, D3c).
     * Falls back to [prior] when there are too few strata or distinct outfits, when every fold is
     * trimmed below [MIN_INITIAL_TRAIN_STRATA], when fewer than [MIN_SCORED_CHOICES] distinct
     * outfits are scored, or when neither stage passes its gate.
     */
    fun trainOnStrata(
        strata: List<ChoiceStratum>,
        prior: TasteWeights = TasteWeights.DEFAULT,
    ): TasteTrainingResult {
        val valid = strata.filter { it.controlFeatures.isNotEmpty() }
        val n = valid.size
        val fallback = TasteTrainingResult(
            weights = prior,
            selectedLambda = null,
            meanLogLikDelta = 0.0,
            strataCount = n,
        )
        if (n < ConditionalLogitOptimizer.MIN_STRATA_FOR_TRAINING) return fallback
        if (valid.mapTo(HashSet()) { it.choiceKey }.size < MIN_DISTINCT_CHOICES) return fallback

        val scoredFolds = groupedFolds(valid).filterNot { it.isSkipped }
        val scoredIndices: List<Int> = scoredFolds.flatMap { it.validation }
        val scoredKeys: List<Set<Long>> = scoredIndices.map { valid[it].choiceKey }
        if (scoredKeys.toSet().size < MIN_SCORED_CHOICES) return fallback

        val priorLL = DoubleArray(scoredIndices.size) { idx ->
            ConditionalLogitOptimizer.stratumLogLikelihood(valid[scoredIndices[idx]], prior)
        }

        // Stage 1 (habit): only historyLift free, gated against the prior.
        val habit = crossValidateStage(valid, scoredFolds, scoredKeys, prior, HABIT_ONLY, priorLL)
        val habitAccepted = passesImprovementGate(habit.meanDelta, habit.standardError)

        // Stage 2 (rules): every weight free, paired against the habit model's held-out
        // likelihoods when the habit stage was accepted, otherwise the prior's.
        val referenceLL = if (habitAccepted) habit.heldOutLL else priorLL
        val full = crossValidateStage(valid, scoredFolds, scoredKeys, prior, null, referenceLL)

        val (winner, mask, stage) = when {
            passesImprovementGate(full.meanDelta, full.standardError) ->
                Triple(full, null, TasteTrainingStage.FULL)
            habitAccepted -> Triple(habit, HABIT_ONLY, TasteTrainingStage.HABIT)
            else -> return fallback.copy(
                meanLogLikDelta = full.meanDelta,
                meanLogLikDeltaStandardError = full.standardError,
            )
        }

        // Final fit on all N strata, walking down to the winning lambda with warm starts
        var finalWarm = prior
        for (lIdx in 0..winner.selectedIdx) {
            finalWarm = ConditionalLogitOptimizer.fit(
                strata = valid,
                lambda = LAMBDA_PATH[lIdx],
                prior = prior,
                initialGuess = finalWarm,
                enforceMinStrata = true,
                freeFeatures = mask,
            )
        }

        return TasteTrainingResult(
            weights = finalWarm,
            selectedLambda = LAMBDA_PATH[winner.selectedIdx],
            meanLogLikDelta = winner.meanDelta,
            strataCount = n,
            meanLogLikDeltaStandardError = winner.standardError,
            acceptedStage = stage,
        )
    }

    /** Only `historyLift` free: the habit stage's mask (#142, D3c). */
    private val HABIT_ONLY = BooleanArray(TasteWeights.FEATURE_COUNT) { it == TasteWeights.IDX_HISTORY_LIFT }

    /**
     * One stage's cross-validation outcome at its one-SE lambda.
     *
     * @property selectedIdx  Index into [LAMBDA_PATH] chosen by the one-SE rule.
     * @property meanDelta    Mean held-out gain over the reference at that lambda.
     * @property standardError Cluster-robust SE of [meanDelta].
     * @property heldOutLL    Held-out log-likelihood of each scored stratum at that lambda (fold fits).
     */
    private class StageOutcome(
        val selectedIdx: Int,
        val meanDelta: Double,
        val standardError: Double,
        val heldOutLL: DoubleArray,
    )

    /**
     * Fits every scored fold along [LAMBDA_PATH] with [freeFeatures] free (all when `null`), scores
     * each scored validation stratum, takes the per-stratum gains over [referenceLL] (aligned with
     * the scored strata, in fold order), and applies the one-SE rule to the D3 statistics.
     */
    private fun crossValidateStage(
        valid: List<ChoiceStratum>,
        scoredFolds: List<CvFold>,
        scoredKeys: List<Set<Long>>,
        prior: TasteWeights,
        freeFeatures: BooleanArray?,
        referenceLL: DoubleArray,
    ): StageOutcome {
        val scoredCount = referenceLL.size
        val llByLambda = Array(LAMBDA_PATH.size) { DoubleArray(scoredCount) }

        var offset = 0
        for (fold in scoredFolds) {
            val trainFold = fold.trainIndices.map { valid[it] }
            var warmGuess: TasteWeights = prior
            for (lIdx in LAMBDA_PATH.indices) {
                val foldWeights = ConditionalLogitOptimizer.fit(
                    strata = trainFold,
                    lambda = LAMBDA_PATH[lIdx],
                    prior = prior,
                    initialGuess = warmGuess,
                    enforceMinStrata = false,
                    freeFeatures = freeFeatures,
                )
                warmGuess = foldWeights
                for ((k, sIdx) in fold.validation.withIndex()) {
                    llByLambda[lIdx][offset + k] = ConditionalLogitOptimizer.stratumLogLikelihood(valid[sIdx], foldWeights)
                }
            }
            offset += fold.validation.count()
        }

        val meanDeltas = DoubleArray(LAMBDA_PATH.size)
        val seDeltas = DoubleArray(LAMBDA_PATH.size)
        for (lIdx in LAMBDA_PATH.indices) {
            val deltas = DoubleArray(scoredCount) { llByLambda[lIdx][it] - referenceLL[it] }
            val (mean, se) = clusterMeanAndStandardError(deltas, scoredKeys)
            meanDeltas[lIdx] = mean
            seDeltas[lIdx] = se
        }

        // Find lambda_best that maximizes mean delta log-likelihood
        var bestIdx = 0
        for (lIdx in 1 until LAMBDA_PATH.size) {
            if (meanDeltas[lIdx] > meanDeltas[bestIdx]) bestIdx = lIdx
        }
        val oneSeThreshold = meanDeltas[bestIdx] - seDeltas[bestIdx]

        // One-SE rule toward stronger regularization: since LAMBDA_PATH is ordered descending
        // (100.0 down to 0.1), pick the FIRST index <= bestIdx whose meanDelta >= oneSeThreshold
        // AND meanDelta > 0.0 (if bestMean > 0).
        var selectedIdx = bestIdx
        for (lIdx in 0..bestIdx) {
            if (meanDeltas[lIdx] >= oneSeThreshold && meanDeltas[lIdx] > 0.0) {
                selectedIdx = lIdx
                break
            }
        }
        return StageOutcome(
            selectedIdx = selectedIdx,
            meanDelta = meanDeltas[selectedIdx],
            standardError = seDeltas[selectedIdx],
            heldOutLL = llByLambda[selectedIdx],
        )
    }

    /**
     * One expanding time-ordered fold: fit on [trainIndices] (ascending), score [validation].
     */
    internal class CvFold(val trainIndices: List<Int>, val validation: IntRange) {
        /** A fold whose training set is smaller than [MIN_INITIAL_TRAIN_STRATA] is neither fitted nor scored. */
        val isSkipped: Boolean get() = trainIndices.size < MIN_INITIAL_TRAIN_STRATA
    }

    /**
     * The expanding time-ordered folds over [strata], grouped by worn outfit (#142, D2).
     *
     * The initial training window is `max(10, n / 2)` strata, and the rest are split into up to
     * [MAX_EXPANDING_FOLDS] consecutive validation blocks. Fold `k` validates on block `k` and
     * trains on every earlier stratum **except** those whose [ChoiceStratum.choiceKey] appears in
     * block `k`, so a fit is never scored on an outfit it was fitted on. A fold trimmed below
     * [MIN_INITIAL_TRAIN_STRATA] is returned with [CvFold.isSkipped] set. Returns no folds when
     * there is nothing to validate on.
     */
    internal fun groupedFolds(strata: List<ChoiceStratum>): List<CvFold> {
        val n = strata.size
        val initialTrainSize = max(MIN_INITIAL_TRAIN_STRATA, n / 2)
        val valTotal = n - initialTrainSize
        if (valTotal <= 0) return emptyList()
        val numFolds = min(MAX_EXPANDING_FOLDS, valTotal)
        val blockSize = max(1, valTotal / numFolds)
        val folds = ArrayList<CvFold>(numFolds)
        var splitStart = initialTrainSize
        while (splitStart < n) {
            val splitEnd = if (folds.size == numFolds - 1) n else min(n, splitStart + blockSize)
            val validation = splitStart until splitEnd
            val validationKeys = validation.mapTo(HashSet()) { strata[it].choiceKey }
            val trainIndices = (0 until splitStart).filter { strata[it].choiceKey !in validationKeys }
            folds.add(CvFold(trainIndices = trainIndices, validation = validation))
            splitStart = splitEnd
        }
        return folds
    }

    /**
     * The mean and cluster-robust standard error of the held-out [deltas] (#142, D3), clustered by
     * worn outfit: [keys] is aligned with [deltas].
     *
     * The mean is the plain per-stratum mean $\bar d = \sum_i d_i / N$, so a favourite worn often
     * still counts for its wears. The standard error is
     * $\text{SE} = \sqrt{\tfrac{G}{G-1} \sum_g \big(\sum_{i \in g} (d_i - \bar d)\big)^2} \,/\, N$
     * over the $G$ distinct keys, so repeats of one outfit, whose deltas are near-identical, count
     * as one cluster rather than as independent evidence. With no repeats ($G = N$) it is the usual
     * sample standard error. It is 0 with fewer than two keys, which [MIN_SCORED_CHOICES] keeps
     * from reaching the gate.
     */
    internal fun clusterMeanAndStandardError(deltas: DoubleArray, keys: List<Set<Long>>): Pair<Double, Double> {
        require(deltas.size == keys.size) { "deltas (${deltas.size}) and keys (${keys.size}) must align" }
        val count = deltas.size
        if (count == 0) return 0.0 to 0.0
        val mean = deltas.average()
        val residualSums = HashMap<Set<Long>, Double>()
        for ((i, key) in keys.withIndex()) {
            residualSums[key] = (residualSums[key] ?: 0.0) + (deltas[i] - mean)
        }
        val clusters = residualSums.size
        if (clusters < 2) return mean to 0.0
        var sumSq = 0.0
        for (r in residualSums.values) sumSq += r * r
        val se = sqrt(clusters.toDouble() / (clusters - 1).toDouble() * sumSq) / count.toDouble()
        return mean to se
    }

    /**
     * A stage's learned weights are kept only when its chosen $\lambda$'s mean held-out gain beats
     * [GATE_SE_MULTIPLIER] (2) times its own cluster-robust standard error (the one the one-SE rule
     * uses), so a gain at noise level is rejected (#135, D3b; #142, D3 and D3c). A non-positive gain
     * always fails, including when the standard error is 0; a single scored outfit never reaches the
     * gate (see [MIN_SCORED_CHOICES]).
     */
    private fun passesImprovementGate(meanDelta: Double, standardError: Double): Boolean =
        meanDelta > 0.0 && meanDelta > GATE_SE_MULTIPLIER * standardError
}
