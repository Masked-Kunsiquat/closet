package com.closet.core.data.util

import com.closet.core.data.model.ClothingItemDetail

/**
 * Converts a fully-loaded [ClothingItemDetail] into a dense English prose paragraph
 * suitable for semantic embedding. Output is deterministic: same inputs → same output.
 *
 * Example output:
 * "Nike Air Max Sneaker. Category: Footwear > Sneakers. Colors: White (Neutral family),
 * Grey (Neutral family). Materials: Mesh, Rubber. Seasons: Spring, Summer.
 * Occasions: Casual, Sport. Pattern: Solid. Size: 10. Worn 12 times.
 * Notes: Great for gym days."
 */
object ItemVectorizer {

    fun describe(detail: ClothingItemDetail): String = buildString {
        val item = detail.item

        // Lead: "[Brand] Name."
        val lead = if (detail.brand != null) "${detail.brand.name} ${item.name}" else item.name
        append(lead).append(".")

        // Category: "Category: Top-level > Subcategory."
        if (detail.category != null) {
            append(" Category: ${detail.category.name}")
            if (detail.subcategory != null) append(" > ${detail.subcategory.name}")
            append(".")
        }

        // Colors: "Colors: White (Neutral family), Grey (Neutral family)."
        if (detail.colors.isNotEmpty()) {
            append(" Colors: ")
            append(detail.colors.joinToString(", ") { "${it.name} (${it.colorFamily} family)" })
            append(".")
        }

        // Materials: "Materials: Cotton, Polyester."
        if (detail.materials.isNotEmpty()) {
            append(" Materials: ")
            append(detail.materials.joinToString(", ") { it.name })
            append(".")
        }

        // Seasons: "Seasons: Spring, Summer."
        if (detail.seasons.isNotEmpty()) {
            append(" Seasons: ")
            append(detail.seasons.joinToString(", ") { it.name })
            append(".")
        }

        // Occasions: "Occasions: Casual, Sport."
        if (detail.occasions.isNotEmpty()) {
            append(" Occasions: ")
            append(detail.occasions.joinToString(", ") { it.name })
            append(".")
        }

        // Pattern: "Pattern: Solid." (singular label matches seeded data)
        if (detail.patterns.isNotEmpty()) {
            append(" Pattern: ")
            append(detail.patterns.joinToString(", ") { it.name })
            append(".")
        }

        // Size: "Size: M." — system name not available in ClothingItemDetail
        if (detail.sizeValue != null) {
            append(" Size: ${detail.sizeValue.value}.")
        }

        // Waist/inseam for bottoms and one-pieces
        val outfitRole = detail.category?.outfitRole
        if (outfitRole == "Bottom" || outfitRole == "OnePiece") {
            val waist = item.waist
            val inseam = item.inseam
            if (waist != null || inseam != null) {
                val parts = buildList {
                    if (waist != null) add("Waist ${waist}\"")
                    if (inseam != null) add("Inseam ${inseam}\"")
                }
                append(" Measurements: ${parts.joinToString(", ")}.")
            }
        }

        // Wear count: always included; grammatically correct singular/plural
        val timeWord = if (detail.wearCount == 1) "time" else "times"
        append(" Worn ${detail.wearCount} $timeWord.")

        // Notes: only when non-blank; avoid double punctuation if note already ends with one
        if (!item.notes.isNullOrBlank()) {
            val trimmed = item.notes.trimEnd()
            val needsPeriod = trimmed.last() !in setOf('.', '!', '?', '。')
            append(" Notes: $trimmed${if (needsPeriod) "." else ""}")
        }
    }.trim()
}
