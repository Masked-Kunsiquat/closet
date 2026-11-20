package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import com.closet.core.data.BuildFlags
import com.closet.core.data.model.ClothingItemColorEntity
import com.closet.core.data.model.ColorSource
import com.closet.core.data.model.TaggedColor
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.util.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
    fun `updateColors saves user rows and keeps existing measurements`() {
        coEvery { clothingRepository.getItemColorRows(42) } returns DataResult.Success(
            listOf(
                ClothingItemColorEntity(42, 1, hex = "#C81E1E", share = 0.6, source = ColorSource.AUTO),
                ClothingItemColorEntity(42, 3, hex = "#222222", share = 0.4, source = ColorSource.AUTO),
            )
        )
        val saved = slot<List<TaggedColor>>()
        coEvery { clothingRepository.updateItemColors(42, capture(saved)) } returns DataResult.Success(Unit)

        buildViewModel().updateColors(listOf(1L, 2L))

        assertEquals(
            listOf(
                TaggedColor(1, hex = "#C81E1E", share = 0.6, source = ColorSource.USER),
                TaggedColor(2, hex = null, share = null, source = ColorSource.USER),
            ),
            saved.captured,
        )
    }

    @Test
    fun `updateColors writes nothing when the picked set is unchanged`() {
        coEvery { clothingRepository.getItemColorRows(42) } returns DataResult.Success(
            listOf(
                ClothingItemColorEntity(42, 1, hex = "#C81E1E", share = 0.6, source = ColorSource.AUTO),
                ClothingItemColorEntity(42, 3, hex = "#222222", share = 0.4, source = ColorSource.AUTO),
            )
        )

        buildViewModel().updateColors(listOf(3L, 1L))

        coVerify(exactly = 0) { clothingRepository.updateItemColors(any(), any()) }
    }

    @Test
    fun `updateColors writes nothing when the existing rows cannot be read`() {
        coEvery { clothingRepository.getItemColorRows(42) } returns DataResult.Error(RuntimeException("db"))

        buildViewModel().updateColors(listOf(1L))

        coVerify(exactly = 0) { clothingRepository.updateItemColors(any(), any()) }
    }
}
