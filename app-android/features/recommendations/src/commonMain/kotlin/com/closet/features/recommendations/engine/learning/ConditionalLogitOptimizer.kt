package com.closet.features.recommendations.engine.learning

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One logged choice stratum $d$ for conditional logistic regression (McFadden choice model).
 *
 * - [chosenFeatures]: feature vector $x_{\text{worn}, d} \in \mathbb{R}^8$ of the case ($y = 1$).
 * - [controlFeatures]: feature vectors $x_{d, k} \in \mathbb{R}^8$ of competing control candidates ($y = 0$).
 *
 * The full candidate set $C_d$ is $\{x_{\text{worn}, d}\} \cup \text{controlFeatures}$.
 */
internal class ChoiceStratum(
    val chosenFeatures: DoubleArray,
    val controlFeatures: List<DoubleArray>,
) {
    /** All candidates in stratum $d$, with index 0 always being the worn case ($y = 1$). */
    val allCandidates: List<DoubleArray> = buildList(1 + controlFeatures.size) {
        add(chosenFeatures)
        addAll(controlFeatures)
    }
}

/**
 * Pure-Kotlin $L_2$-penalized Newton-Raphson optimizer for the 1-case-per-stratum
 * Conditional Logit model (McFadden 1974; Reid & Tibshirani 2014 `clogitL1` §2.1–2.6).
 *
 * Maximizes the regularized conditional log-likelihood centered on the prior $\beta_0$:
 * $$\ell(\tilde{\beta}) = \sum_d \left( \tilde{\beta}^\top \tilde{x}_{\text{worn}, d} - \ln \sum_{k \in C_d} \exp(\tilde{\beta}^\top \tilde{x}_{d, k}) \right) - \frac{\lambda}{2} \|\tilde{\beta} - \tilde{\beta}_0\|_2^2$$
 */
internal object ConditionalLogitOptimizer {

    /** Cold-start safeguard: require at least 20 valid strata before departing from [TasteWeights.DEFAULT]. */
    internal const val MIN_STRATA_FOR_TRAINING = 20

    /** Thin-data regime threshold: clamp maximum per-weight drift when $N_{\text{strata}} < 30$. */
    internal const val THIN_DATA_STRATA_THRESHOLD = 30

    /** Maximum $|\hat{\beta}_j - \beta_{0, j}|$ allowed when $N_{\text{strata}} < [THIN_DATA_STRATA_THRESHOLD]$. */
    internal const val THIN_DATA_MAX_DRIFT = 0.5

    /** Floor on regularization strength $\lambda$ to guarantee strict concavity under complete separation. */
    internal const val MIN_LAMBDA = 0.1

    private const val ZERO_VARIANCE_EPS = 1e-8
    private const val GRAD_TOL = 1e-6
    private const val STEP_TOL = 1e-6
    private const val MAX_ITERATIONS = 30
    private const val ARMIJO_C1 = 1e-4
    private const val MIN_STEP_ALPHA = 1e-8

    /**
     * Fits [TasteWeights] on [strata] with $L_2$ regularization strength [lambda] toward [prior].
     *
     * @param strata       Training choice strata. Stratum with no controls are ignored.
     * @param lambda       $L_2$ penalty strength (clamped to $\ge [MIN_LAMBDA]$).
     * @param prior        Prior center $\beta_0$ (defaults to [TasteWeights.DEFAULT]).
     * @param initialGuess Optional unstandardized warm-start weight vector $\beta^{(0)}$.
     * @param enforceMinStrata When true (default), returns [prior] immediately if valid strata $< 20$.
     *                         Cross-validation folds with smaller training windows can pass `false`
     *                         when the overall dataset satisfies $N \ge 20$.
     */
    fun fit(
        strata: List<ChoiceStratum>,
        lambda: Double = 1.0,
        prior: TasteWeights = TasteWeights.DEFAULT,
        initialGuess: TasteWeights? = null,
        enforceMinStrata: Boolean = true,
    ): TasteWeights {
        val validStrata = strata.filter { it.controlFeatures.isNotEmpty() }
        if (validStrata.isEmpty()) return prior
        if (enforceMinStrata && validStrata.size < MIN_STRATA_FOR_TRAINING) return prior

        val effectiveLambda = max(lambda, MIN_LAMBDA)
        val p = TasteWeights.FEATURE_COUNT
        val beta0 = prior.toDoubleArray()

        // 1. Compute pooled within-stratum standard deviation sigma_j for each feature.
        //    Because per-stratum constants cancel out in conditional logit, within-stratum
        //    spread accurately captures whether a feature varies across a day's candidates.
        val sigma = computeWithinStratumSpread(validStrata, p)
        val active = BooleanArray(p) { j -> sigma[j] >= ZERO_VARIANCE_EPS }

        // If no features vary at all, return prior
        if (active.none { it }) return prior

        // 2. Standardize candidate features: \tilde{x}_j = x_j / sigma_j (or 0 if inactive)
        val stdStrata = Array(validStrata.size) { d ->
            val candidates = validStrata[d].allCandidates
            Array(candidates.size) { k ->
                val raw = candidates[k]
                DoubleArray(p) { j -> if (active[j]) raw[j] / sigma[j] else 0.0 }
            }
        }

        // Standardized prior center: \tilde{\beta}_{0, j} = \beta_{0, j} * sigma_j
        val tildeBeta0 = DoubleArray(p) { j -> if (active[j]) beta0[j] * sigma[j] else beta0[j] }

        // Initialize \tilde{\beta} from warm start or prior center
        val startUnstd = initialGuess?.toDoubleArray() ?: beta0
        var tildeBeta = DoubleArray(p) { j ->
            if (active[j]) startUnstd[j] * sigma[j] else tildeBeta0[j]
        }

        // 3. Newton-Raphson loop with Cholesky solver and Armijo backtracking line search
        val grad = DoubleArray(p)
        val hess = Array(p) { DoubleArray(p) }

        for (iter in 0 until MAX_ITERATIONS) {
            val currentObj = evaluateObjectiveGradHessian(
                stdStrata = stdStrata,
                tildeBeta = tildeBeta,
                tildeBeta0 = tildeBeta0,
                active = active,
                lambda = effectiveLambda,
                outGrad = grad,
                outNegHess = hess,
            )

            val maxGrad = grad.maxOf { abs(it) }
            if (maxGrad < GRAD_TOL) break

            val step = CholeskySolver.solve(hess, grad) ?: break

            // Ensure ascent direction: grad^T * step > 0
            var directionalDeriv = 0.0
            for (j in 0 until p) {
                directionalDeriv += grad[j] * step[j]
            }
            if (directionalDeriv <= 0.0 || !directionalDeriv.isFinite()) break

            // Armijo backtracking line search
            var alpha = 1.0
            var accepted = false
            var candidateBeta = tildeBeta
            while (alpha >= MIN_STEP_ALPHA) {
                val trial = DoubleArray(p) { j ->
                    if (active[j]) tildeBeta[j] + alpha * step[j] else tildeBeta0[j]
                }
                val trialObj = evaluateObjectiveOnly(
                    stdStrata = stdStrata,
                    tildeBeta = trial,
                    tildeBeta0 = tildeBeta0,
                    active = active,
                    lambda = effectiveLambda,
                )
                if (trialObj.isFinite() && trialObj >= currentObj + ARMIJO_C1 * alpha * directionalDeriv) {
                    candidateBeta = trial
                    accepted = true
                    break
                }
                alpha *= 0.5
            }

            if (!accepted) break
            val maxStep = (0 until p).maxOf { j -> abs(candidateBeta[j] - tildeBeta[j]) }
            tildeBeta = candidateBeta
            if (maxStep < STEP_TOL) break
        }

        // 4. Unstandardize and apply thin-data drift clamping if N_strata < 30
        val clampDrift = validStrata.size < THIN_DATA_STRATA_THRESHOLD
        val unstdBeta = DoubleArray(p) { j ->
            val rawWeight = if (active[j]) tildeBeta[j] / sigma[j] else beta0[j]
            val safeWeight = if (rawWeight.isFinite()) rawWeight else beta0[j]
            if (clampDrift) {
                safeWeight.coerceIn(beta0[j] - THIN_DATA_MAX_DRIFT, beta0[j] + THIN_DATA_MAX_DRIFT)
            } else {
                safeWeight
            }
        }

        return TasteWeights.fromDoubleArray(unstdBeta)
    }

    /**
     * Computes the unpenalized conditional log-likelihood of [weights] on a single [stratum]:
     * $$\ell_d(\beta) = \beta^\top x_{\text{worn}, d} - \ln \sum_{k \in C_d} \exp(\beta^\top x_{d, k})$$
     */
    fun stratumLogLikelihood(stratum: ChoiceStratum, weights: TasteWeights): Double {
        val candidates = stratum.allCandidates
        if (candidates.size <= 1) return 0.0
        val utilities = DoubleArray(candidates.size) { k -> weights.dot(candidates[k]) }
        val lse = logSumExp(utilities)
        return utilities[0] - lse
    }

    /**
     * Computes the total unpenalized conditional log-likelihood of [weights] across [strata].
     */
    fun totalLogLikelihood(strata: List<ChoiceStratum>, weights: TasteWeights): Double =
        strata.sumOf { stratumLogLikelihood(it, weights) }

    // -------------------------------------------------------------------------
    // Internal math helpers
    // -------------------------------------------------------------------------

    private fun computeWithinStratumSpread(strata: List<ChoiceStratum>, p: Int): DoubleArray {
        val sumSq = DoubleArray(p)
        var totalDof = 0
        for (stratum in strata) {
            val candidates = stratum.allCandidates
            val m = candidates.size
            if (m < 2) continue
            val mean = DoubleArray(p)
            for (c in candidates) {
                for (j in 0 until p) mean[j] += c[j]
            }
            for (j in 0 until p) mean[j] /= m.toDouble()
            for (c in candidates) {
                for (j in 0 until p) {
                    val diff = c[j] - mean[j]
                    sumSq[j] += diff * diff
                }
            }
            totalDof += (m - 1)
        }
        if (totalDof <= 0) return DoubleArray(p) { 0.0 }
        return DoubleArray(p) { j -> sqrt(sumSq[j] / totalDof.toDouble()) }
    }

    private fun evaluateObjectiveOnly(
        stdStrata: Array<Array<DoubleArray>>,
        tildeBeta: DoubleArray,
        tildeBeta0: DoubleArray,
        active: BooleanArray,
        lambda: Double,
    ): Double {
        val p = tildeBeta.size
        var logLik = 0.0
        for (candidates in stdStrata) {
            val m = candidates.size
            val utilities = DoubleArray(m) { k ->
                var u = 0.0
                val x = candidates[k]
                for (j in 0 until p) u += tildeBeta[j] * x[j]
                u
            }
            val lse = logSumExp(utilities)
            logLik += utilities[0] - lse
        }
        var penalty = 0.0
        for (j in 0 until p) {
            if (active[j]) {
                val diff = tildeBeta[j] - tildeBeta0[j]
                penalty += diff * diff
            }
        }
        return logLik - 0.5 * lambda * penalty
    }

    private fun evaluateObjectiveGradHessian(
        stdStrata: Array<Array<DoubleArray>>,
        tildeBeta: DoubleArray,
        tildeBeta0: DoubleArray,
        active: BooleanArray,
        lambda: Double,
        outGrad: DoubleArray,
        outNegHess: Array<DoubleArray>,
    ): Double {
        val p = tildeBeta.size
        outGrad.fill(0.0)
        for (i in 0 until p) outNegHess[i].fill(0.0)

        var logLik = 0.0
        val mu = DoubleArray(p)

        for (candidates in stdStrata) {
            val m = candidates.size
            val utilities = DoubleArray(m) { k ->
                var u = 0.0
                val x = candidates[k]
                for (j in 0 until p) u += tildeBeta[j] * x[j]
                u
            }
            val lse = logSumExp(utilities)
            logLik += utilities[0] - lse

            val probs = DoubleArray(m) { k -> exp(utilities[k] - lse) }

            mu.fill(0.0)
            for (k in 0 until m) {
                val pk = probs[k]
                val xk = candidates[k]
                for (j in 0 until p) {
                    mu[j] += pk * xk[j]
                }
            }

            // Score increment: x_{worn} - mu
            val xWorn = candidates[0]
            for (j in 0 until p) {
                if (active[j]) {
                    outGrad[j] += xWorn[j] - mu[j]
                }
            }

            // Covariance increment: sum_k p_k (x_k - mu)(x_k - mu)^T
            for (k in 0 until m) {
                val pk = probs[k]
                val xk = candidates[k]
                for (i in 0 until p) {
                    if (!active[i]) continue
                    val di = xk[i] - mu[i]
                    for (j in 0..i) {
                        if (!active[j]) continue
                        outNegHess[i][j] += pk * di * (xk[j] - mu[j])
                    }
                }
            }
        }

        // Symmetrize upper triangle of negative Hessian and add L2 penalty
        var penalty = 0.0
        for (i in 0 until p) {
            for (j in 0 until i) {
                outNegHess[j][i] = outNegHess[i][j]
            }
            if (active[i]) {
                val diff = tildeBeta[i] - tildeBeta0[i]
                penalty += diff * diff
                outGrad[i] -= lambda * diff
                outNegHess[i][i] += lambda
            } else {
                outGrad[i] = 0.0
                outNegHess[i][i] = 1.0
            }
        }

        return logLik - 0.5 * lambda * penalty
    }

    private fun logSumExp(values: DoubleArray): Double {
        var maxVal = Double.NEGATIVE_INFINITY
        for (v in values) {
            if (v > maxVal) maxVal = v
        }
        if (!maxVal.isFinite()) return maxVal
        var sumExp = 0.0
        for (v in values) {
            sumExp += exp(v - maxVal)
        }
        return maxVal + ln(sumExp)
    }
}
