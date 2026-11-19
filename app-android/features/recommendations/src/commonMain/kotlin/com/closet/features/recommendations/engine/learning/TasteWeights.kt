package com.closet.features.recommendations.engine.learning

import kotlin.math.abs

/**
 * Learned per-user taste weights for the 8 candidate outfit log-features in
 * [com.closet.features.recommendations.engine.OutfitRecommendationEngine].
 *
 * Fitted on-device from the user's wear logs via a regularized conditional logit
 * (McFadden discrete choice model) centered at [DEFAULT] ($\beta_0 = [1, 1, \dots, 1]^\top$).
 *
 * Feature index mapping:
 * - 0: [meanItem] — log mean per-item score (weather suitability and wear recency).
 * - 1: [hueHarmony] — $(1 - W) \ln(\text{hueMultiplier})$ across chromatic hex pairs.
 * - 2: [familyHarmony] — $(1 - W) \ln(\text{familyMultiplier})$ across coarse color-family pairs.
 * - 3: [patternMix] — $(1 - W) \ln(\text{patternMultiplier})$ (single patterned bonus / multi-pattern clash).
 * - 4: [formalityGap] — $(1 - W) \ln(\text{formalityMultiplier})$ across item pairs.
 * - 5: [historyLift] — $W \ln(H)$ co-wear habit lift weighted by evidence confidence $W$.
 * - 6: [layering] — $\ln(\text{layeringMultiplier})$ weather layering fit.
 * - 7: [footwear] — $\ln(\text{footwearMultiplier})$ missing-footwear penalty.
 */
data class TasteWeights(
    val meanItem: Double = 1.0,
    val hueHarmony: Double = 1.0,
    val familyHarmony: Double = 1.0,
    val patternMix: Double = 1.0,
    val formalityGap: Double = 1.0,
    val historyLift: Double = 1.0,
    val layering: Double = 1.0,
    val footwear: Double = 1.0,
) {
    /**
     * True when every weight is at its hand-tuned default (1.0 within floating-point tolerance).
     * When true, the engine runs its exact legacy arithmetic blend for cold-start parity.
     */
    val isDefault: Boolean
        get() = abs(meanItem - 1.0) <= EPSILON &&
            abs(hueHarmony - 1.0) <= EPSILON &&
            abs(familyHarmony - 1.0) <= EPSILON &&
            abs(patternMix - 1.0) <= EPSILON &&
            abs(formalityGap - 1.0) <= EPSILON &&
            abs(historyLift - 1.0) <= EPSILON &&
            abs(layering - 1.0) <= EPSILON &&
            abs(footwear - 1.0) <= EPSILON

    /** Converts this weight vector to an 8-element [DoubleArray] in feature-index order. */
    fun toDoubleArray(): DoubleArray = doubleArrayOf(
        meanItem,
        hueHarmony,
        familyHarmony,
        patternMix,
        formalityGap,
        historyLift,
        layering,
        footwear,
    )

    /** Computes the linear utility $u = \beta^\top x$ for an 8-element feature vector [features]. */
    fun dot(features: DoubleArray): Double {
        require(features.size == FEATURE_COUNT) {
            "Expected $FEATURE_COUNT features, got ${features.size}"
        }
        return meanItem * features[IDX_MEAN_ITEM] +
            hueHarmony * features[IDX_HUE_HARMONY] +
            familyHarmony * features[IDX_FAMILY_HARMONY] +
            patternMix * features[IDX_PATTERN_MIX] +
            formalityGap * features[IDX_FORMALITY_GAP] +
            historyLift * features[IDX_HISTORY_LIFT] +
            layering * features[IDX_LAYERING] +
            footwear * features[IDX_FOOTWEAR]
    }

    /** Serializes this weight vector as a compact comma-separated string for DataStore persistence. */
    fun serialize(): String = toDoubleArray().joinToString(separator = ",")

    companion object {
        const val FEATURE_COUNT = 8

        const val IDX_MEAN_ITEM = 0
        const val IDX_HUE_HARMONY = 1
        const val IDX_FAMILY_HARMONY = 2
        const val IDX_PATTERN_MIX = 3
        const val IDX_FORMALITY_GAP = 4
        const val IDX_HISTORY_LIFT = 5
        const val IDX_LAYERING = 6
        const val IDX_FOOTWEAR = 7

        private const val EPSILON = 1e-9

        /** Default prior center $\beta_0 = [1.0, \dots, 1.0]^\top$. */
        val DEFAULT = TasteWeights()

        /** Constructs [TasteWeights] from an 8-element [DoubleArray]. */
        fun fromDoubleArray(values: DoubleArray): TasteWeights {
            require(values.size == FEATURE_COUNT) {
                "Expected $FEATURE_COUNT weights, got ${values.size}"
            }
            return TasteWeights(
                meanItem = values[IDX_MEAN_ITEM],
                hueHarmony = values[IDX_HUE_HARMONY],
                familyHarmony = values[IDX_FAMILY_HARMONY],
                patternMix = values[IDX_PATTERN_MIX],
                formalityGap = values[IDX_FORMALITY_GAP],
                historyLift = values[IDX_HISTORY_LIFT],
                layering = values[IDX_LAYERING],
                footwear = values[IDX_FOOTWEAR],
            )
        }

        /**
         * Parses a serialized comma-separated weight string back into [TasteWeights].
         * Returns [DEFAULT] if [raw] is null, blank, malformed, or contains non-finite numbers.
         */
        fun deserialize(raw: String?): TasteWeights {
            if (raw.isNullOrBlank()) return DEFAULT
            val parts = raw.split(",")
            if (parts.size != FEATURE_COUNT) return DEFAULT
            val values = DoubleArray(FEATURE_COUNT)
            for (i in 0 until FEATURE_COUNT) {
                val parsed = parts[i].trim().toDoubleOrNull() ?: return DEFAULT
                if (!parsed.isFinite()) return DEFAULT
                values[i] = parsed
            }
            return fromDoubleArray(values)
        }
    }
}
