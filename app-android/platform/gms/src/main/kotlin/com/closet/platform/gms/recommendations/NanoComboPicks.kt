package com.closet.platform.gms.recommendations

import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.addJsonObject

/**
 * Structured-output schema for Nano outfit recommendations. The ML Kit schema compiler (KSP)
 * generates the `GenerableProvider`s. Public because the generated providers expose them publicly
 * (`internal` fails to compile).
 */
@Generable(description = "The three best outfit combos from the provided list")
data class NanoComboPicks(
    @Guide(description = "Exactly three distinct picks", minItems = 3, maxItems = 3)
    val picks: List<NanoComboPick>,
)

@Generable(description = "One picked outfit combo")
data class NanoComboPick(
    @Guide(description = "The combo_id of a combo from the provided list")
    val comboId: Int,
    @Guide(description = "One sentence explaining why this combo works")
    val reason: String,
)

/**
 * Serializes the picks to the JSON array text the plain-text path returns
 * (`[{"combo_id":0,"reason":"…"}, …]`), so [NanoProvider]'s parsing and validation apply unchanged.
 */
internal fun NanoComboPicks.toResponseJson(): String = buildJsonArray {
    picks.forEach { pick ->
        addJsonObject {
            put("combo_id", pick.comboId)
            put("reason", pick.reason)
        }
    }
}.toString()
