package com.closet.features.outfits

import androidx.lifecycle.SavedStateHandle
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.repository.OutfitRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OutfitBuilderViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val clothingRepository = mockk<ClothingRepository>()
    private val outfitRepository = mockk<OutfitRepository>()
    private val imagePathResolver = ImagePathResolver { "/root/$it" }

    private fun detail(id: Long): ClothingItemDetail {
        val entity = mockk<ClothingItemEntity>()
        every { entity.id } returns id
        val d = mockk<ClothingItemDetail>()
        every { d.item } returns entity
        return d
    }

    @BeforeTest fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { clothingRepository.getAllItemDetails() } returns flowOf(listOf(detail(3L), detail(5L), detail(9L)))
        every { outfitRepository.getOutfitById(any()) } returns flowOf(null)
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(handle: SavedStateHandle) =
        OutfitBuilderViewModel(handle, clothingRepository, outfitRepository, imagePathResolver)

    @Test fun `preselected item ids end up selected`() = runTest {
        val vm = vm(SavedStateHandle(mapOf("preselectedItemIds" to listOf(3L, 5L))))
        backgroundScope.launch(testDispatcher) { vm.members.collect {} }
        assertEquals(listOf(3L, 5L), vm.members.value.map { it.item.item.id })
    }

    @Test fun `outfitId loads that outfit and enters edit mode`() = runTest {
        val vm = vm(SavedStateHandle(mapOf("outfitId" to 7L)))
        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        verify { outfitRepository.getOutfitById(7L) }
        assertTrue(vm.uiState.value.isEditing)
    }

    @Test fun `resolveImagePath uses the resolver`() {
        val vm = vm(SavedStateHandle())
        assertEquals("/root/a.jpg", vm.resolveImagePath("a.jpg"))
        assertNull(vm.resolveImagePath(null))
    }
}
