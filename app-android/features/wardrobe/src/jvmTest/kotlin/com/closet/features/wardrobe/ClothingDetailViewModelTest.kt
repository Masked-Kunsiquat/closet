package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import com.closet.core.data.BuildFlags
import com.closet.core.data.repository.ImagePathResolver
import io.mockk.mockk
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

    private fun buildViewModel(isDebug: Boolean) = ClothingDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("itemId" to 42L)),
        clothingRepository = mockk(relaxed = true),
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
}
