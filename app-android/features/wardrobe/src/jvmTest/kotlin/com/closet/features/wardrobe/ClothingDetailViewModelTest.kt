package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import com.closet.core.data.BuildFlags
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.util.DataResult
import com.closet.core.ui.util.UserMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ClothingDetailViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val clothingRepository: ClothingRepository = mockk(relaxed = true)

    private fun buildViewModel(isDebug: Boolean = false) = ClothingDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("itemId" to 42L)),
        clothingRepository = clothingRepository,
        lookupRepository = mockk(relaxed = true),
        logRepository = mockk(relaxed = true),
        photoProcessor = FakeItemPhotoProcessor(),
        imagePathResolver = ImagePathResolver { "/abs/$it" },
        buildFlags = BuildFlags(isDebug = isDebug),
    )

    @Test
    fun `the item id comes from the route`() {
        assertEquals(42L, buildViewModel(isDebug = false).itemId)
    }

    @Test
    fun `the debug card follows BuildFlags`() {
        assertTrue(buildViewModel(isDebug = true).showDebugCard)
        assertFalse(buildViewModel(isDebug = false).showDebugCard)
    }

    @Test
    fun `image paths resolve through the ImagePathResolver`() {
        assertEquals("/abs/a.jpg", buildViewModel(isDebug = false).resolveImagePath("a.jpg"))
    }

    @Test
    fun `updateColors writes through updateColorsAsUser in one call`() {
        coEvery { clothingRepository.updateColorsAsUser(42, any()) } returns DataResult.Success(true)

        buildViewModel().updateColors(listOf(1L, 2L))

        // The read, the unchanged check and the write are one repository transaction; the
        // ViewModel no longer reads the rows itself.
        coVerify(exactly = 1) { clothingRepository.updateColorsAsUser(42, listOf(1L, 2L)) }
        coVerify(exactly = 0) { clothingRepository.getItemColorRows(any()) }
        coVerify(exactly = 0) { clothingRepository.updateItemColors(any(), any()) }
    }

    @Test
    fun `updateColors reports a failed write`() = runTest {
        coEvery { clothingRepository.updateColorsAsUser(42, any()) } returns DataResult.Error(RuntimeException("db"))
        val vm = buildViewModel()
        val errors = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.actionError.toList(errors) }

        vm.updateColors(listOf(1L))

        coVerify(exactly = 1) { clothingRepository.updateColorsAsUser(42, listOf(1L)) }
        assertEquals(1, errors.size)
    }

    @Test
    fun `updateColors with an unchanged set reports nothing`() = runTest {
        coEvery { clothingRepository.updateColorsAsUser(42, any()) } returns DataResult.Success(false)
        val vm = buildViewModel()
        val errors = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.actionError.toList(errors) }

        vm.updateColors(listOf(3L, 1L))

        assertEquals(emptyList(), errors)
        coVerify(exactly = 0) { clothingRepository.updateItemColors(any(), any()) }
    }
}
