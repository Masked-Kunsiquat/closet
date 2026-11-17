package com.closet.features.wardrobe

import com.closet.core.data.model.WashStatus
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.shortcuts.ShortcutUsageReporter
import com.closet.core.data.util.DataResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class BulkWashViewModelTest {

    private val clothingRepository: ClothingRepository = mockk(relaxed = true)
    private val reporter: ShortcutUsageReporter = mockk(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { clothingRepository.getAllItems() } returns flowOf(emptyList())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = BulkWashViewModel(
        clothingRepository = clothingRepository,
        imagePathResolver = ImagePathResolver { "/abs/$it" },
        shortcutUsageReporter = reporter,
    )

    @Test
    fun `laundry_day is reported after a successful apply`() = runTest {
        coEvery { clothingRepository.updateWashStatus(any(), any()) } returns DataResult.Success(Unit)
        val viewModel = buildViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.toggleSelection(1L)
        viewModel.toggleSelection(2L)

        viewModel.applyWashStatus(WashStatus.Clean)
        advanceUntilIdle()

        verify(exactly = 1) { reporter.reportUsed("laundry_day") }
        assertEquals(emptySet(), (viewModel.uiState.value as BulkWashUiState.Success).selectedIds)
    }

    @Test
    fun `laundry_day is not reported when an update fails`() = runTest {
        coEvery { clothingRepository.updateWashStatus(any(), any()) } returns DataResult.Error(RuntimeException("db"))
        val viewModel = buildViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.toggleSelection(1L)

        viewModel.applyWashStatus(WashStatus.Dirty)
        advanceUntilIdle()

        verify(exactly = 0) { reporter.reportUsed(any()) }
        assertTrue((viewModel.uiState.value as BulkWashUiState.Success).applyError)
    }

    @Test
    fun `image paths resolve through the ImagePathResolver`() {
        val viewModel = buildViewModel()
        assertEquals("/abs/a.jpg", viewModel.resolveImagePath("a.jpg"))
        assertNull(viewModel.resolveImagePath(null))
    }
}
