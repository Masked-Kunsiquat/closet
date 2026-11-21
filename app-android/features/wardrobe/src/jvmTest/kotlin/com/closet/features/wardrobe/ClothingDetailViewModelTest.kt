package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import com.closet.core.data.BuildFlags
import com.closet.core.data.color.ExtractedColor
import com.closet.core.data.color.ReferenceShade
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.repository.LogRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import com.closet.core.ui.util.UserMessage
import com.closet.core.ui.util.asUserMessage
import com.closet.features.wardrobe.resources.Res
import com.closet.features.wardrobe.resources.wardrobe_redetect_none
import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
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
    private val lookupRepository: LookupRepository = mockk(relaxed = true)
    private val logRepository: LogRepository = mockk(relaxed = true)
    private val photoProcessor = FakeItemPhotoProcessor()

    private fun buildViewModel(isDebug: Boolean = false) = ClothingDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("itemId" to 42L)),
        clothingRepository = clothingRepository,
        lookupRepository = lookupRepository,
        logRepository = logRepository,
        photoProcessor = photoProcessor,
        imagePathResolver = ImagePathResolver { "/abs/$it" },
        colorRedetector = ColorRedetector(photoProcessor, lookupRepository, clothingRepository),
        buildFlags = BuildFlags(isDebug = isDebug),
    )

    // --- Re-detect colors fixtures ---

    private val red = ColorEntity(id = 1, name = "Red", hex = "#FF0000")
    private val blue = ColorEntity(id = 2, name = "Blue", hex = "#0000FF")
    private val green = ColorEntity(id = 3, name = "Green", hex = "#00FF00")
    private val shades = listOf(ReferenceShade(1, "#FF0000"), ReferenceShade(2, "#0000FF"), ReferenceShade(3, "#00FF00"))
    private val measured = listOf(
        ExtractedColor(colorId = 2, hex = "#1020E0", share = 0.62),
        ExtractedColor(colorId = 1, hex = "#E01010", share = 0.38),
    )

    /** Stubs the item (image [imagePath], colours [current]) and the lookups the detail screen reads. */
    private fun givenItem(imagePath: String? = "item.png", current: List<ColorEntity> = listOf(green)) {
        val detail = detailOf(imagePath, current)
        every { clothingRepository.getItemDetail(42) } returns flowOf(detail)
        every { lookupRepository.getColors() } returns flowOf(listOf(red, blue, green))
        every { lookupRepository.getMaterials() } returns flowOf(emptyList())
        every { lookupRepository.getSeasons() } returns flowOf(emptyList())
        every { lookupRepository.getOccasions() } returns flowOf(emptyList())
        every { lookupRepository.getPatterns() } returns flowOf(emptyList())
        every { lookupRepository.getSizeSystems() } returns flowOf(emptyList())
        every { logRepository.getLogsForItem(42) } returns flowOf(emptyList())
        coEvery { lookupRepository.getReferenceShades() } returns DataResult.Success(shades)
    }

    private fun detailOf(imagePath: String?, current: List<ColorEntity> = listOf(green)) =
        ClothingItemDetail(
            item = ClothingItemEntity(id = 42, name = "Shirt", imagePath = imagePath),
            wearCount = 0,
            category = CategoryEntity(id = 1, name = "Tops", sortOrder = 1),
            subcategory = null,
            brand = null,
            sizeValue = null,
            colors = current,
            materials = emptyList(),
            seasons = emptyList(),
            occasions = emptyList(),
            patterns = emptyList(),
        )

    /** A ViewModel whose uiState is collected, as the screen does, so it reaches Success. */
    private fun TestScope.subscribedViewModel(): ClothingDetailViewModel {
        val vm = buildViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

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

    // --- Re-detect colors ---

    @Test
    fun `re-detect measures the image and previews the proposal with its shares`() = runTest {
        givenItem(current = listOf(green))
        photoProcessor.colorsByPath["item.png"] = measured
        val gate = CompletableDeferred<Unit>()
        photoProcessor.extractGate = { gate.await() }
        val vm = subscribedViewModel()

        vm.onRedetectColors()
        assertEquals(RedetectState.Measuring, vm.redetectState.value)
        gate.complete(Unit)

        assertEquals(listOf("item.png"), photoProcessor.extractCalls)
        assertEquals(
            RedetectState.Preview(
                current = listOf(green),
                // The lookup colour carrying the measured hex (for its swatch), and its share.
                proposed = listOf(blue.copy(hex = "#1020E0") to 0.62, red.copy(hex = "#E01010") to 0.38),
            ),
            vm.redetectState.value,
        )
        coVerify(exactly = 0) { clothingRepository.applyRedetectedColors(any(), any(), any()) }
    }

    @Test
    fun `apply writes the proposed colours and returns to Idle`() = runTest {
        givenItem()
        photoProcessor.colorsByPath["item.png"] = measured
        coEvery { clothingRepository.applyRedetectedColors(42, any(), any()) } returns DataResult.Success(true)
        val vm = subscribedViewModel()

        vm.onRedetectColors()
        vm.onApplyRedetect()

        // Apply carries the photo that was measured, so the write can refuse a replaced photo.
        coVerify(exactly = 1) { clothingRepository.applyRedetectedColors(42, measured, "item.png") }
        coVerify(exactly = 1) { clothingRepository.revectorizeItem(42) }
        assertEquals(RedetectState.Idle, vm.redetectState.value)
    }

    @Test
    fun `apply that wrote nothing does not revectorize`() = runTest {
        givenItem()
        photoProcessor.colorsByPath["item.png"] = measured
        coEvery { clothingRepository.applyRedetectedColors(42, any(), any()) } returns DataResult.Success(false)
        val vm = subscribedViewModel()

        vm.onRedetectColors()
        vm.onApplyRedetect()

        coVerify(exactly = 0) { clothingRepository.revectorizeItem(any()) }
    }

    @Test
    fun `a failed apply is reported`() = runTest {
        givenItem()
        photoProcessor.colorsByPath["item.png"] = measured
        coEvery { clothingRepository.applyRedetectedColors(42, any(), any()) } returns DataResult.Error(RuntimeException("db"))
        val vm = subscribedViewModel()
        val errors = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.actionError.toList(errors) }

        vm.onRedetectColors()
        vm.onApplyRedetect()

        assertEquals(1, errors.size)
        assertEquals(RedetectState.Idle, vm.redetectState.value)
        coVerify(exactly = 0) { clothingRepository.revectorizeItem(any()) }
    }

    @Test
    fun `cancel writes nothing and returns to Idle`() = runTest {
        givenItem()
        photoProcessor.colorsByPath["item.png"] = measured
        val vm = subscribedViewModel()

        vm.onRedetectColors()
        assertTrue(vm.redetectState.value is RedetectState.Preview)
        vm.onCancelRedetect()

        assertEquals(RedetectState.Idle, vm.redetectState.value)
        // After a cancel there is no preview left to apply.
        vm.onApplyRedetect()
        coVerify(exactly = 0) { clothingRepository.applyRedetectedColors(any(), any(), any()) }
    }

    @Test
    fun `an empty measurement fails with a snackbar, returns to Idle and writes nothing`() = runTest {
        givenItem()
        // No colours registered for item.png: the extraction is empty.
        val vm = subscribedViewModel()
        val errors = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.actionError.toList(errors) }

        vm.onRedetectColors()

        assertEquals(listOf("item.png"), photoProcessor.extractCalls)
        assertEquals(listOf(UserMessage(Res.string.wardrobe_redetect_none)), errors)
        assertEquals(RedetectState.Idle, vm.redetectState.value)
        coVerify(exactly = 0) { clothingRepository.applyRedetectedColors(any(), any(), any()) }
    }

    @Test
    fun `a failed shades load shows the error snackbar, not nothing-detected`() = runTest {
        givenItem()
        photoProcessor.colorsByPath["item.png"] = measured
        val cause = AppError.DatabaseError.QueryError(RuntimeException("db"))
        coEvery { lookupRepository.getReferenceShades() } returns DataResult.Error(cause)
        val vm = subscribedViewModel()
        val errors = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.actionError.toList(errors) }

        vm.onRedetectColors()

        assertEquals(listOf(cause.asUserMessage()), errors)
        assertEquals(RedetectState.Idle, vm.redetectState.value)
        assertTrue(photoProcessor.extractCalls.isEmpty())
    }

    @Test
    fun `a throwing extraction shows the generic error snackbar and writes nothing`() = runTest {
        givenItem()
        val cause = IllegalStateException("decode")
        photoProcessor.extractGate = { throw cause }
        val vm = subscribedViewModel()
        val errors = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.actionError.toList(errors) }

        vm.onRedetectColors()

        assertEquals(listOf(AppError.Unexpected(cause).asUserMessage()), errors)
        assertEquals(RedetectState.Idle, vm.redetectState.value)
        coVerify(exactly = 0) { clothingRepository.applyRedetectedColors(any(), any(), any()) }
    }

    @Test
    fun `shades not loaded shows the generic error snackbar`() = runTest {
        givenItem()
        coEvery { lookupRepository.getReferenceShades() } returns DataResult.Loading
        val vm = subscribedViewModel()
        val errors = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.actionError.toList(errors) }

        vm.onRedetectColors()

        assertEquals(listOf(AppError.Unexpected(null).asUserMessage()), errors)
        assertEquals(RedetectState.Idle, vm.redetectState.value)
    }

    @Test
    fun `an item without an image is not measured`() = runTest {
        givenItem(imagePath = null)
        val vm = subscribedViewModel()

        vm.onRedetectColors()

        assertEquals(RedetectState.Idle, vm.redetectState.value)
        assertTrue(photoProcessor.extractCalls.isEmpty())
    }

    @Test
    fun `a photo replaced while measuring shows no preview`() = runTest {
        givenItem()
        val detail = MutableStateFlow(detailOf("item.png"))
        every { clothingRepository.getItemDetail(42) } returns detail
        photoProcessor.colorsByPath["item.png"] = measured
        val gate = CompletableDeferred<Unit>()
        photoProcessor.extractGate = { gate.await() }
        val vm = subscribedViewModel()

        vm.onRedetectColors()
        // Background removal swaps the photo while item.png is being measured.
        detail.value = detailOf("item.webp")
        gate.complete(Unit)

        assertEquals(RedetectState.Idle, vm.redetectState.value)
        vm.onApplyRedetect()
        coVerify(exactly = 0) { clothingRepository.applyRedetectedColors(any(), any(), any()) }
    }

    @Test
    fun `a second tap while measuring does not measure twice`() = runTest {
        givenItem()
        photoProcessor.colorsByPath["item.png"] = measured
        val gate = CompletableDeferred<Unit>()
        photoProcessor.extractGate = { gate.await() }
        val vm = subscribedViewModel()

        vm.onRedetectColors()
        vm.onRedetectColors()
        gate.complete(Unit)

        assertEquals(1, photoProcessor.extractCalls.size)
        assertTrue(vm.redetectState.value is RedetectState.Preview)
    }
}
