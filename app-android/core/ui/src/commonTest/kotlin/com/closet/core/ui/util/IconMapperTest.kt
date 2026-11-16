package com.closet.core.ui.util

import com.closet.core.ui.resources.Res
import com.closet.core.ui.resources.ic_icon_sun
import com.closet.core.ui.resources.ic_icon_t_shirt
import com.closet.core.ui.resources.ic_pattern_plaid_tartan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IconMapperTest {

    @Test
    fun normalizesHyphenToUnderscore() {
        assertEquals(Res.drawable.ic_icon_t_shirt, IconMapper.getIconResource("t-shirt"))
    }

    @Test
    fun mapsPatternAliasCaseInsensitively() {
        assertEquals(Res.drawable.ic_pattern_plaid_tartan, IconMapper.getIconResource("Plaid/Tartan"))
    }

    @Test
    fun mapsFullResourceName() {
        assertEquals(Res.drawable.ic_icon_sun, IconMapper.getIconResource("ic_icon_sun"))
    }

    @Test
    fun unknownNameIsNull() {
        assertNull(IconMapper.getIconResource("unknown"))
    }

    @Test
    fun nullNameIsNull() {
        assertNull(IconMapper.getIconResource(null))
    }
}
