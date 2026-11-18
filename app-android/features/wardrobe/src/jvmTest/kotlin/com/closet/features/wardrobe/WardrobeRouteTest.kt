package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WardrobeRouteTest {

    @Test
    fun `closet initial category survives toRoute`() {
        val handle = SavedStateHandle(mapOf("initialCategoryId" to 7L))
        assertEquals(7L, handle.toRoute<ClosetDestination>().initialCategoryId)
    }

    @Test
    fun `closet initial category is null when absent`() {
        assertNull(SavedStateHandle().toRoute<ClosetDestination>().initialCategoryId)
    }

    @Test
    fun `detail item id survives toRoute`() {
        val handle = SavedStateHandle(mapOf("itemId" to 42L))
        assertEquals(42L, handle.toRoute<ClothingDetailDestination>().itemId)
    }

    @Test
    fun `detail item id throws when absent`() {
        assertFailsWith<Exception> { SavedStateHandle().toRoute<ClothingDetailDestination>() }
    }

    @Test
    fun `edit item id survives toRoute`() {
        val handle = SavedStateHandle(mapOf("itemId" to 9L))
        assertEquals(9L, handle.toRoute<EditClothingDestination>().itemId)
    }

    @Test
    fun `edit item id throws when absent so callers fall back to null`() {
        assertFailsWith<Exception> { SavedStateHandle().toRoute<EditClothingDestination>() }
    }

    @Test
    fun `add open camera survives toRoute`() {
        val handle = SavedStateHandle(mapOf("openCamera" to true))
        assertEquals(true, handle.toRoute<AddClothingDestination>().openCamera)
    }

    @Test
    fun `add open camera defaults to false when absent`() {
        assertEquals(false, SavedStateHandle().toRoute<AddClothingDestination>().openCamera)
    }
}
