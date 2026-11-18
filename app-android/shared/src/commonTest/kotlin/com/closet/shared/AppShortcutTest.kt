package com.closet.shared

import com.closet.features.outfits.OutfitBuilderDestination
import com.closet.features.wardrobe.AddClothingDestination
import com.closet.features.wardrobe.BulkWashDestination
import com.closet.features.wardrobe.ClosetDestination
import kotlin.test.Test
import kotlin.test.assertEquals

class AppShortcutTest {
    @Test
    fun quickAddOpensCameraForm() {
        assertEquals(AddClothingDestination(openCamera = true), AppShortcut.QuickAdd.destination())
    }

    @Test
    fun logFitOpensOutfitBuilder() {
        assertEquals(OutfitBuilderDestination(), AppShortcut.LogFit.destination())
    }

    @Test
    fun laundryDayOpensBulkWash() {
        assertEquals(BulkWashDestination, AppShortcut.LaundryDay.destination())
    }

    @Test
    fun categoryCarriesId() {
        assertEquals(ClosetDestination(initialCategoryId = 7L), AppShortcut.Category(7L).destination())
    }

    @Test
    fun categoryWithNullIdIsPlainCloset() {
        assertEquals(ClosetDestination(initialCategoryId = null), AppShortcut.Category(null).destination())
    }
}
