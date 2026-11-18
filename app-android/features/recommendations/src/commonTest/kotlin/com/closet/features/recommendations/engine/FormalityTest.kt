package com.closet.features.recommendations.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormalityTest {
    @Test fun loungewearAndAthleticAreOne() {
        assertEquals(1.0, formalityLevel(listOf("Loungewear")))
        assertEquals(1.0, formalityLevel(listOf("Athletic")))
    }

    @Test fun casualOutdoorAndVacationAreTwo() {
        assertEquals(2.0, formalityLevel(listOf("Casual")))
        assertEquals(2.0, formalityLevel(listOf("Outdoor/Hiking")))
        assertEquals(2.0, formalityLevel(listOf("Vacation")))
    }

    @Test fun dateNightIsThree() = assertEquals(3.0, formalityLevel(listOf("Date Night")))

    @Test fun workIsFour() = assertEquals(4.0, formalityLevel(listOf("Work/Business")))

    @Test fun formalAndSpecialOccasionAreFive() {
        assertEquals(5.0, formalityLevel(listOf("Formal")))
        assertEquals(5.0, formalityLevel(listOf("Special Occasion")))
    }

    @Test fun oddCountMedianIsTheMiddleLevel() =
        assertEquals(2.0, formalityLevel(listOf("Formal", "Casual", "Loungewear")))

    @Test fun evenCountMedianAveragesTheMiddlePair() =
        assertEquals(3.0, formalityLevel(listOf("Casual", "Work/Business")))

    @Test fun unknownNamesAreIgnored() =
        assertEquals(5.0, formalityLevel(listOf("My Gala", "Formal")))

    @Test fun noKnownLevelIsNull() {
        assertNull(formalityLevel(emptyList()))
        assertNull(formalityLevel(listOf("My Gala")))
    }
}
