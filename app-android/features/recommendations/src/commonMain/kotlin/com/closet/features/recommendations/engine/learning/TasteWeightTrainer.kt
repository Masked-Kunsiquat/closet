package com.closet.features.recommendations.engine.learning

import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.ItemNeighbor
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import kotlinx.datetime.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Result of time-ordered cross-validation and final conditional logit fitting.
 *
 * @property weights            Final learned [TasteWeights] (or [TasteWeights.DEFAULT] on cold start / no improvement).
 * @property selectedLambda     Chosen $L_2$ regularization strength $\lambda^*$, or `null` if cold start / fallback.
 * @property meanLogLikDelta    Mean per-stratum held-out conditional log-likelihood improvement over [TasteWeights.DEFAULT].
 * @property strataCount        Total valid choice strata constructed from the wear logs.
 */
data class TasteTrainingResult(
    val weights: TasteWeights,
    val selectedLambda: Double?,
    val meanLogLikDelta: Double,
    val strataCount: Int,
)

/**
 * End-to-end trainer for personalized [TasteWeights] (Issue #114).
 *
 * 1. Builds chronological 1-case choice strata via [HistoricalStratumBuilder] (or accepts pre-built strata).
 * 2. Applies the cold-start guard: if $N_{\text{strata}} < 20$, returns [TasteWeights.DEFAULT].
 * 3. Evaluates the log-spaced regularization path $\lambda \in \{100.0, 30.0, 10.0, 3.0, 1.0, 0.3, 0.1\}$
 *    from $\lambda_{\max}$ down to $\lambda_{\min}$ using warm starts across expanding time-ordered folds.
 * 4. Selects $\lambda^*$ using the **one-standard-error rule** toward stronger regularization (larger $\lambda$).
 * 5. If held-out log-likelihood does not improve over [TasteWeights.DEFAULT] ($\overline{\Delta\ell}(\lambda^*) \le 0$),
 *    falls back to [TasteWeights.DEFAULT]; otherwise refits on all $N_{\text{strata}}$ at $\lambda^*$.
 */
internal object TasteWeightTrainer {

    /**
     * Regularization path walked from largest $\lambda$ (strongest prior shrinkage) down to smallest $\lambda$,
     * enabling warm starts along the path (Reid & Tibshirani 2014, §2.6).
     */
    internal val LAMBDA_PATH = doubleArrayOf(100.0, 30.0, 10.0, 3.0, 1.0, 0.3, 0.1)

    private const val MIN_INITIAL_TRAIN_STRATA = 10
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
     * Fits personalized [TasteWeights] on chronologically ordered [strata] using
     * expanding-window time-ordered cross-validation and the 1-SE rule.
     */
    fun trainOnStrata(
        strata: List<ChoiceStratum>,
        prior: TasteWeights = TasteWeights.DEFAULT,
    ): TasteTrainingResult {
        val valid = strata.filter { it.controlFeatures.isNotEmpty() }
        val n = valid.size
        if (n < ConditionalLogitOptimizer.MIN_STRATA_FOR_TRAINING) {
            return TasteTrainingResult(
                weights = prior,
                selectedLambda = null,
                meanLogLikDelta = 0.0,
                strataCount = n,
            )
        }

        // Build expanding-window split boundaries: initial training window is max(10, n / 2),
        // and the remaining validation strata are partitioned into up to MAX_EXPANDING_FOLDS blocks.
        val initialTrainSize = max(MIN_INITIAL_TRAIN_STRATA, n / 2)
        val valTotal = n - initialTrainSize
        if (valTotal <= 0) {
            return TasteTrainingResult(
                weights = prior,
                selectedLambda = null,
                meanLogLikDelta = 0.0,
                strataCount = n,
            )
        }

        val numFolds = min(MAX_EXPANDING_FOLDS, valTotal)
        val blockSize = max(1, valTotal / numFolds)
        val splitRanges = ArrayList<Pair<Int, Int>>(numFolds)
        var splitStart = initialTrainSize
        while (splitStart < n) {
            val splitEnd = if (splitRanges.size == numFolds - 1) n else min(n, splitStart + blockSize)
            splitRanges.add(splitStart to splitEnd)
            splitStart = splitEnd
        }

        // Baseline held-out log-likelihood under prior for each validation stratum
        val baselineLL = DoubleArray(valTotal) { idx ->
            ConditionalLogitOptimizer.stratumLogLikelihood(valid[initialTrainSize + idx], prior)
        }

        // For each fold, walk LAMBDA_PATH from largest to smallest with warm starts,
        // recording per-stratum log-likelihood delta over prior.
        val deltasByLambda = Array(LAMBDA_PATH.size) { DoubleArray(valTotal) }

        for ((trainEnd, valEnd) in splitRanges) {
            val trainFold = valid.subList(0, trainEnd)
            var warmGuess: TasteWeights = prior

            for (lIdx in LAMBDA_PATH.indices) {
                val lambda = LAMBDA_PATH[lIdx]
                val foldWeights = ConditionalLogitOptimizer.fit(
                    strata = trainFold,
                    lambda = lambda,
                    prior = prior,
                    initialGuess = warmGuess,
                    enforceMinStrata = false,
                )
                warmGuess = foldWeights

                for (sIdx in trainEnd until valEnd) {
                    val vOffset = sIdx - initialTrainSize
                    val ll = ConditionalLogitOptimizer.stratumLogLikelihood(valid[sIdx], foldWeights)
                    deltasByLambda[lIdx][vOffset] = ll - baselineLL[vOffset]
                }
            }
        }

        // Compute mean and standard error of per-stratum delta log-likelihood for each lambda
        val meanDeltas = DoubleArray(LAMBDA_PATH.size)
        val seDeltas = DoubleArray(LAMBDA_PATH.size)
        for (lIdx in LAMBDA_PATH.indices) {
            val deltas = deltasByLambda[lIdx]
            val mean = deltas.average()
            meanDeltas[lIdx] = mean
            if (valTotal > 1) {
                var sumSq = 0.0
                for (d in deltas) {
                    val diff = d - mean
                    sumSq += diff * diff
                }
                val sd = sqrt(sumSq / (valTotal - 1).toDouble())
                seDeltas[lIdx] = sd / sqrt(valTotal.toDouble())
            } else {
                seDeltas[lIdx] = 0.0
            }
        }

        // Find lambda_best that maximizes mean delta log-likelihood
        var bestIdx = 0
        for (lIdx in 1 until LAMBDA_PATH.size) {
            if (meanDeltas[lIdx] > meanDeltas[bestIdx]) {
                bestIdx = lIdx
            }
        }

        val bestMean = meanDeltas[bestIdx]
        val oneSeThreshold = bestMean - seDeltas[bestIdx]

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

        val selectedMeanDelta = meanDeltas[selectedIdx]
        if (selectedMeanDelta <= 0.0) {
            // Learned weights did not improve out-of-sample log-likelihood over the prior
            return TasteTrainingResult(
                weights = prior,
                selectedLambda = null,
                meanLogLikDelta = selectedMeanDelta,
                strataCount = n,
            )
        }

        val chosenLambda = LAMBDA_PATH[selectedIdx]

        // Final fit on all N strata walking down to chosenLambda with warm starts
        var finalWarm = prior
        for (lIdx in 0..selectedIdx) {
            finalWarm = ConditionalLogitOptimizer.fit(
                strata = valid,
                lambda = LAMBDA_PATH[lIdx],
                prior = prior,
                initialGuess = finalWarm,
                enforceMinStrata = true,
            )
        }

        return TasteTrainingResult(
            weights = finalWarm,
            selectedLambda = chosenLambda,
            meanLogLikDelta = selectedMeanDelta,
            strataCount = n,
        )
    }
}
