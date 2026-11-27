package com.closet.features.recommendations.scoreboard

import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Spec §7's sensitivity check (#133): with colour harmony forced to × 1.0 the scoreboard must move,
 * which proves it can see colour. The measured differences are printed for the record.
 */
class SensitivityTest {

    @Test
    fun `turning harmony off moves the scoreboard`() = runTest(timeout = 10.minutes) {
        val moves = ArrayList<String>()
        for (persona in AllPersonas.list) {
            val on = Scoreboard.run(persona, engine = OutfitRecommendationEngine.withColorHarmony(true))
            val off = Scoreboard.run(persona, engine = OutfitRecommendationEngine.withColorHarmony(false))
            if (Baseline.rateDiffers(on.hitsAt10, on.evaluatedDays, off.hitsAt10, off.evaluatedDays)) {
                moves += "${persona.name} hit@10 on ${on.hitsAt10}/${on.evaluatedDays} off ${off.hitsAt10}/${off.evaluatedDays}"
            }
            for (id in CheckId.entries) {
                val a = on.checks.getValue(id)
                val b = off.checks.getValue(id)
                if (Baseline.rateDiffers(a.fails, a.applicable, b.fails, b.applicable)) {
                    moves += "${persona.name} ${id.name} on ${a.fails}/${a.applicable} off ${b.fails}/${b.applicable}"
                }
            }
        }
        println("Harmony sensitivity:\n" + moves.joinToString("\n").ifEmpty { "nothing moved" })
        assertTrue(
            moves.isNotEmpty(),
            "the scoreboard can't see colour: turning harmony off moved neither hit@10 nor any check's fail rate",
        )
    }
}
