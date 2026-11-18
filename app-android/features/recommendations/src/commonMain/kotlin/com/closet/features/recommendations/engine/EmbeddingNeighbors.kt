package com.closet.features.recommendations.engine

import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.MIN_NEIGHBOR_SIMILARITY
import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.TOP_K_NEIGHBORS

/**
 * Computes nearest candidate neighbors from pre-computed embeddings.
 *
 * Embeddings are L2-normalized 384-dimensional vectors from the ONNX encoder,
 * so their dot product equals their cosine similarity.
 *
 * For each item, finds up to [topK] other items with cosine similarity >= [minSimilarity],
 * sorted descending by similarity.
 */
fun buildItemNeighbors(
    embeddings: Map<Long, FloatArray>,
    topK: Int = TOP_K_NEIGHBORS,
    minSimilarity: Double = MIN_NEIGHBOR_SIMILARITY,
): Map<Long, List<ItemNeighbor>> {
    if (embeddings.size < 2) return emptyMap()
    val result = HashMap<Long, MutableList<ItemNeighbor>>()
    val entries = embeddings.entries.toList()
    for (i in entries.indices) {
        val (idA, vecA) = entries[i]
        for (j in i + 1 until entries.size) {
            val (idB, vecB) = entries[j]
            val sim = dotProduct(vecA, vecB).toDouble()
            if (sim >= minSimilarity) {
                result.getOrPut(idA) { mutableListOf() }.add(ItemNeighbor(idB, sim))
                result.getOrPut(idB) { mutableListOf() }.add(ItemNeighbor(idA, sim))
            }
        }
    }
    return result.mapValues { (_, neighbors) ->
        neighbors.sortedByDescending { it.similarity }.take(topK)
    }
}

internal fun dotProduct(a: FloatArray, b: FloatArray): Float {
    val len = minOf(a.size, b.size)
    var sum = 0f
    for (i in 0 until len) {
        sum += a[i] * b[i]
    }
    return sum
}
