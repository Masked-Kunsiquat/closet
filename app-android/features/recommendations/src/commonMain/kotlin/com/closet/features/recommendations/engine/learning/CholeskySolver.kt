package com.closet.features.recommendations.engine.learning

import kotlin.math.sqrt

/**
 * Pure-Kotlin Cholesky solver ($A = L L^\top$) for small symmetric positive-definite (SPD)
 * systems $A x = b$.
 *
 * Used by [ConditionalLogitOptimizer] to solve the $8 \times 8$ Newton-Raphson system
 * $(\sum_d \text{Cov}_d(\tilde{x}) + \lambda I) \Delta\tilde{\beta} = g(\tilde{\beta})$
 * in microseconds without external linear algebra dependencies.
 */
internal object CholeskySolver {

    /**
     * Solves $A x = b$ for symmetric positive-definite matrix [a] ($n \times n$) and
     * right-hand side [b] ($n$).
     *
     * @return Solution vector $x$, or `null` if [a] is not numerically positive-definite.
     */
    fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        require(a.size == n && a.all { it.size == n }) {
            "Dimension mismatch: matrix is ${a.size}x${a.firstOrNull()?.size}, rhs is $n"
        }

        // 1. Factorization: A = L * L^T
        val l = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in 0..i) {
                var sum = a[i][j]
                for (k in 0 until j) {
                    sum -= l[i][k] * l[j][k]
                }
                if (i == j) {
                    if (sum <= 0.0 || !sum.isFinite()) return null
                    l[i][j] = sqrt(sum)
                } else {
                    l[i][j] = sum / l[j][j]
                }
            }
        }

        // 2. Forward substitution: L * y = b
        val y = DoubleArray(n)
        for (i in 0 until n) {
            var sum = b[i]
            for (k in 0 until i) {
                sum -= l[i][k] * y[k]
            }
            y[i] = sum / l[i][i]
        }

        // 3. Back substitution: L^T * x = y
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            var sum = y[i]
            for (k in i + 1 until n) {
                sum -= l[k][i] * x[k]
            }
            x[i] = sum / l[i][i]
        }

        return x
    }
}
