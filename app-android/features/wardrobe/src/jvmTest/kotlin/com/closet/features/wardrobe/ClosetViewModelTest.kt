package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.repository.LookupRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class ClosetViewModelTest {

    private val clothingRepository: ClothingRepository = mockk(relaxed = true)
    private val lookupRepository: LookupRepository = mockk(relaxed = true)
    private val pinner: CategoryShortcutPinner = mockk(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { clothingRepository.getAllItemDetails() } returns flowOf(emptyList())
        every { lookupRepository.getCategories() } returns flowOf(emptyList())
        every { lookupRepository.getColors() } returns flowOf(emptyList())
        every { lookupRepository.getSeasons() } returns flowOf(emptyList())
        every { lookupRepository.getOccasions() } returns flowOf(emptyList())
        every { lookupRepository.getSizeSystems() } returns flowOf(emptyList())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = ClosetViewModel(
        savedStateHandle = savedStateHandle,
        clothingRepository = clothingRepository,
        lookupRepository = lookupRepository,
        imagePathResolver = ImagePathResolver { "/abs/$it" },
        categoryShortcutPinner = pinner,
    )

    @Test
    fun `pinning a category delegates the id and label to the pinner`() {
        buildViewModel().pinCategoryShortcut(4L, "Outerwear")

        verify(exactly = 1) { pinner.pin(4L, "Outerwear") }
    }

    @Test
    fun `the initial category comes from the route`() = runTest {
        val viewModel = buildViewModel(SavedStateHandle(mapOf("initialCategoryId" to 7L)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(7L, viewModel.uiState.value.selectedCategoryId)
    }

    @Test
    fun `no initial category without a route argument`() = runTest {
        val viewModel = buildViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertNull(viewModel.uiState.value.selectedCategoryId)
    }

    @Test
    fun `updating initialCategoryId on SavedStateHandle or consumeInitialCategoryRequest updates selectedCategoryId`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val viewModel = buildViewModel(savedStateHandle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertNull(viewModel.uiState.value.selectedCategoryId)

        // Simulates singleTop re-delivery updating the argument on the existing entry
        savedStateHandle["initialCategoryId"] = 2L
        assertEquals(2L, viewModel.uiState.value.selectedCategoryId)

        // First shortcut request for category 7 is consumed
        val request1 = Any()
        viewModel.consumeInitialCategoryRequest(initialCategoryId = 7L, requestIdentity = request1)
        assertEquals(7L, viewModel.uiState.value.selectedCategoryId)

        // User changes filter to category 2, opens detail, and returns (same NavBackStackEntry request1):
        // consumeInitialCategoryRequest must not overwrite category 2 back to 7
        viewModel.selectCategory(2L)
        assertEquals(2L, viewModel.uiState.value.selectedCategoryId)
        viewModel.consumeInitialCategoryRequest(initialCategoryId = 7L, requestIdentity = request1)
        assertEquals(2L, viewModel.uiState.value.selectedCategoryId)

        // Consecutive shortcut for the SAME category 7 creates a new NavBackStackEntry (request2):
        // consumeInitialCategoryRequest must reapply category 7
        val request2 = Any()
        viewModel.consumeInitialCategoryRequest(initialCategoryId = 7L, requestIdentity = request2)
        assertEquals(7L, viewModel.uiState.value.selectedCategoryId)
    }

    @Test
    fun `image paths resolve through the ImagePathResolver`() {
        val viewModel = buildViewModel()
        assertEquals("/abs/a.jpg", viewModel.resolveImagePath("a.jpg"))
        assertNull(viewModel.resolveImagePath(null))
    }
}
