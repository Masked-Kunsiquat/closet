package com.closet.platform.gms.recommendations

import org.junit.Assert.assertEquals
import org.junit.Test

class NanoComboPicksTest {

    @Test
    fun `picks serialize to the combo_id and reason array the caller parses`() {
        val picks = NanoComboPicks(
            picks = listOf(
                NanoComboPick(comboId = 0, reason = "Neutral base"),
                NanoComboPick(comboId = 3, reason = "Bold \"pop\""),
                NanoComboPick(comboId = 7, reason = "Layered"),
            ),
        )
        assertEquals(
            """[{"combo_id":0,"reason":"Neutral base"},{"combo_id":3,"reason":"Bold \"pop\""},""" +
                """{"combo_id":7,"reason":"Layered"}]""",
            picks.toResponseJson(),
        )
    }

    @Test
    fun `empty picks serialize to an empty array`() {
        assertEquals("[]", NanoComboPicks(picks = emptyList()).toResponseJson())
    }
}
