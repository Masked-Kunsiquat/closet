package com.closet.features.outfits

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class OutfitBuilderRouteTest {

    @Test
    fun `preselected ids survive toRoute`() {
        val handle = SavedStateHandle(mapOf("preselectedItemIds" to longArrayOf(3L, 5L)))
        assertEquals(listOf(3L, 5L), handle.toRoute<OutfitBuilderDestination>().preselectedItemIds)
    }

    @Test
    fun `absent preselected ids fall back to empty`() {
        assertEquals(emptyList(), SavedStateHandle().toRoute<OutfitBuilderDestination>().preselectedItemIds)
    }

    @Test
    fun `outfit id survives toRoute`() {
        val handle = SavedStateHandle(mapOf("outfitId" to 7L))
        assertEquals(7L, handle.toRoute<OutfitBuilderDestination>().outfitId)
    }
}
