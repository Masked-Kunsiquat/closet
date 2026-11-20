package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.closet.core.data.color.ExtractedColor
import com.closet.core.data.color.ReferenceShade
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemColorEntity
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.model.ColorSource
import com.closet.core.data.model.TaggedColor
import com.closet.core.data.model.SizeSystemEntity
import com.closet.core.data.model.SubcategoryEntity
import com.closet.core.data.repository.BrandRepository
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.time.FixedAppClock
import com.closet.core.data.shortcuts.ShortcutUsageReporter
import com.closet.core.data.util.DataResult
import com.closet.core.ui.resources.error_database_query
import com.closet.core.ui.resources.Res as CoreRes
import com.closet.features.wardrobe.resources.Res
import com.closet.features.wardrobe.resources.wardrobe_model_download_error
import com.closet.features.wardrobe.resources.wardrobe_segmentation_error
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ClothingFormViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val lookupRepository: LookupRepository = mockk(relaxed = true)
    private val brandRepository: BrandRepository = mockk(relaxed = true)
    private val clothingRepository: ClothingRepository = mockk(relaxed = true)
    private val imagePathResolver = ImagePathResolver { "/abs/$it" }
    private val shortcutUsageReporter: ShortcutUsageReporter = mockk(relaxed = true)
    private val photos = FakeItemPhotoProcessor()

    private val red = ColorEntity(id = 1, name = "Red", hex = "#FF0000")
    private val blue = ColorEntity(id = 2, name = "Blue", hex = "#0000FF")
    private val shades = listOf(ReferenceShade(1, "#FF0000"), ReferenceShade(2, "#0000FF"))

    /** An extraction result for [color]; the measured hex differs from the seeded one on purpose. */
    private fun measured(color: ColorEntity, hex: String = "#C81E1E", share: Double = 1.0) =
        ExtractedColor(color.id, hex, share)

    private val sizeSystems = listOf(
        SizeSystemEntity(1, "Letter"),
        SizeSystemEntity(2, "Shoes (US Men's)"),
        SizeSystemEntity(3, "One Size")
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { lookupRepository.getSizeSystems() } returns flowOf(sizeSystems)
        every { lookupRepository.getCategories() } returns flowOf(emptyList())
        every { lookupRepository.getColors() } returns flowOf(listOf(red, blue))
        every { lookupRepository.getSubcategories(any()) } returns flowOf(emptyList())
        every { lookupRepository.getSizeValues(any()) } returns flowOf(emptyList())
        every { lookupRepository.getSeasons() } returns flowOf(emptyList())
        every { lookupRepository.getOccasions() } returns flowOf(emptyList())
        every { lookupRepository.getMaterials() } returns flowOf(emptyList())
        every { lookupRepository.getPatterns() } returns flowOf(emptyList())
        every { brandRepository.getAllBrands() } returns flowOf(DataResult.Success(emptyList()))
        coEvery { lookupRepository.getReferenceShades() } returns DataResult.Success(shades)
        coEvery {
            clothingRepository.insertItemWithAttributes(any(), any(), any(), any(), any(), any())
        } returns DataResult.Success(1L)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = ClothingFormViewModel(
        savedStateHandle = savedStateHandle,
        lookupRepository = lookupRepository,
        brandRepository = brandRepository,
        clothingRepository = clothingRepository,
        photoProcessor = photos,
        imagePathResolver = imagePathResolver,
        shortcutUsageReporter = shortcutUsageReporter,
        clock = FixedAppClock(kotlin.time.Instant.parse("2031-07-10T12:00:00Z")),
    )

    /** Keeps [ClothingFormViewModel.uiState] (and the lookup flows it combines) subscribed. */
    private fun TestScope.subscribed(viewModel: ClothingFormViewModel): ClothingFormViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    // ─── Size-system suggestions (ported) ─────────────────────────────────────

    @Test
    fun `selecting footwear subcategory auto-suggests Shoes size system`() = runTest {
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial state

            val sneakers = SubcategoryEntity(id = 10, categoryId = 1, name = "Sneakers", sortOrder = 1)
            viewModel.onSubcategorySelected(sneakers)

            assertEquals(2L, expectMostRecentItem().selectedSizeSystemId) // Shoes (US Men's)
        }
    }

    @Test
    fun `selecting accessory subcategory auto-suggests One Size system`() = runTest {
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            awaitItem()

            val backpack = SubcategoryEntity(id = 20, categoryId = 2, name = "Backpack", sortOrder = 1)
            viewModel.onSubcategorySelected(backpack)

            assertEquals(3L, expectMostRecentItem().selectedSizeSystemId) // One Size
        }
    }

    @Test
    fun `user manual size system selection prevents auto-suggestion override`() = runTest {
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            awaitItem()

            // User manually selects "Letter"
            viewModel.onSizeSystemSelected(sizeSystems[0].id)
            assertEquals(1L, expectMostRecentItem().selectedSizeSystemId)

            // Now select "Sneakers" - should NOT change to Shoes because of manual override
            val sneakers = SubcategoryEntity(id = 10, categoryId = 1, name = "Sneakers", sortOrder = 1)
            viewModel.onSubcategorySelected(sneakers)

            // State should still be Letter (1L)
            assertEquals(1L, expectMostRecentItem().selectedSizeSystemId)
        }
    }

    @Test
    fun `changing category resets the manual override and allows new auto-suggestions`() = runTest {
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            awaitItem()

            // 1. Manually override
            viewModel.onSizeSystemSelected(sizeSystems[0].id)
            expectMostRecentItem()

            // 2. Change category - should reset override flag
            viewModel.onCategorySelected(CategoryEntity(id = 5, name = "Footwear", sortOrder = 1))
            assertEquals(5L, expectMostRecentItem().category?.id)

            // 3. Select subcategory - auto-suggestion should work again
            val sneakers = SubcategoryEntity(id = 10, categoryId = 5, name = "Sneakers", sortOrder = 1)
            viewModel.onSubcategorySelected(sneakers)

            assertEquals(2L, expectMostRecentItem().selectedSizeSystemId)
        }
    }

    // ─── Photo pipeline ───────────────────────────────────────────────────────

    @Test
    fun `selecting an image imports it and sets path, colors and caption`() = runTest {
        photos.colorsByPath["picked.jpg"] = listOf(measured(red))
        val viewModel = subscribed(buildViewModel())

        viewModel.onImageSelected("content://media/picked/1")

        val state = viewModel.uiState.value
        assertEquals(listOf("content://media/picked/1"), photos.imported)
        assertEquals("picked.jpg", state.imagePath)
        assertEquals("/abs/picked.jpg", state.resolvedImagePath)
        assertEquals(listOf(red), state.selectedColors)
        assertEquals(listOf("picked.jpg"), photos.extractCalls)
        assertEquals("caption:picked.jpg", state.imageCaption)
        assertFalse(state.isCaptioning)
        assertFalse(state.isLoading)
    }

    @Test
    fun `a null image source is ignored`() = runTest {
        val viewModel = subscribed(buildViewModel())

        viewModel.onImageSelected(null)

        assertTrue(photos.imported.isEmpty())
        assertNull(viewModel.uiState.value.imagePath)
    }

    @Test
    fun `no caption is requested when captioning is unsupported`() = runTest {
        photos.isCaptionSupported = false
        val viewModel = subscribed(buildViewModel())

        viewModel.onImageSelected("content://media/picked/1")

        assertTrue(photos.describeCalls.isEmpty())
        assertNull(viewModel.uiState.value.imageCaption)
    }

    @Test
    fun `segmentation success swaps the path, re-extracts colors and re-captions`() = runTest {
        photos.colorsByPath["picked.jpg"] = listOf(measured(red))
        photos.colorsByPath["segmented.png"] = listOf(measured(blue, "#1A2B8C"))
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")

        viewModel.removeBackground()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, photos.ensureModelCalls)
        assertEquals(listOf("picked.jpg"), photos.removeBackgroundCalls)
        assertEquals("segmented.png", state.imagePath)
        assertTrue(state.hasSegmentedImage)
        assertFalse(state.isSegmenting)
        assertFalse(state.isDownloadingModel)
        assertEquals("/abs/picked.jpg", state.resolvedOriginalImagePath)
        assertEquals(listOf("picked.jpg", "segmented.png"), photos.extractCalls)
        assertEquals(listOf(blue), state.selectedColors)
        assertEquals("caption:segmented.png", state.imageCaption)
    }

    @Test
    fun `segmentation failure restores the original path and caption`() = runTest {
        photos.removeBackgroundFailure = IllegalStateException("boom")
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")

        viewModel.removeBackground()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("picked.jpg", state.imagePath)
        assertEquals("caption:picked.jpg", state.imageCaption)
        assertNull(state.resolvedOriginalImagePath)
        assertFalse(state.hasSegmentedImage)
        assertFalse(state.isSegmenting)
        assertEquals(Res.string.wardrobe_segmentation_error, state.errorMessage)
    }

    @Test
    fun `model download failure reports an error and does not segment`() = runTest {
        photos.ensureModelFailure = IllegalStateException("no model")
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")

        viewModel.removeBackground()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(photos.removeBackgroundCalls.isEmpty())
        assertEquals("picked.jpg", state.imagePath)
        assertFalse(state.isDownloadingModel)
        assertEquals(Res.string.wardrobe_model_download_error, state.errorMessage)
    }

    @Test
    fun `removeBackground is a no-op when segmentation is unsupported`() = runTest {
        photos.isSegmentationSupported = false
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")

        viewModel.removeBackground()
        advanceUntilIdle()

        assertEquals(0, photos.ensureModelCalls)
        assertFalse(viewModel.uiState.value.isSegmentationSupported)
    }

    @Test
    fun `revert deletes the segmented image and cancels its caption job`() = runTest {
        var segmentedCaptionCancelled = false
        photos.describeHandler = { path ->
            if (path == "segmented.png") {
                try {
                    awaitCancellation()
                } finally {
                    segmentedCaptionCancelled = true
                }
            } else {
                "caption:$path"
            }
        }
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")
        viewModel.removeBackground()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCaptioning)

        viewModel.revertSegmentation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(segmentedCaptionCancelled)
        assertEquals(listOf("segmented.png"), photos.deleted)
        assertEquals("picked.jpg", state.imagePath)
        assertFalse(state.hasSegmentedImage)
        assertFalse(state.isCaptioning)
        assertNull(state.resolvedOriginalImagePath)
    }

    @Test
    fun `replacing an image deletes the previous one`() = runTest {
        photos.importResults.clear()
        photos.importResults.addAll(listOf("first.jpg", "second.jpg"))
        val viewModel = subscribed(buildViewModel())

        viewModel.onImageSelected("content://media/picked/1")
        viewModel.onImageSelected("content://media/picked/2")

        assertEquals("second.jpg", viewModel.uiState.value.imagePath)
        assertEquals(listOf("first.jpg"), photos.deleted)
    }

    @Test
    fun `a stale caption never overwrites a newer image`() = runTest {
        photos.importResults.clear()
        photos.importResults.addAll(listOf("first.jpg", "second.jpg"))
        // The first caption ignores cancellation and returns late, after the second image.
        var lateFirstCaption: Continuation<String?>? = null
        photos.describeHandler = { path ->
            if (path == "first.jpg") suspendCoroutine { lateFirstCaption = it } else "caption:$path"
        }
        val viewModel = subscribed(buildViewModel())

        viewModel.onImageSelected("content://media/picked/1")
        viewModel.onImageSelected("content://media/picked/2")
        assertEquals("caption:second.jpg", viewModel.uiState.value.imageCaption)

        lateFirstCaption!!.resume("caption:first.jpg")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("second.jpg", state.imagePath)
        assertEquals("caption:second.jpg", state.imageCaption)
        assertFalse(state.isCaptioning)
    }

    @Test
    fun `a late color extraction for an earlier image neither overwrites colors nor cancels the current caption`() = runTest {
        photos.importResults.clear()
        photos.importResults.addAll(listOf("first.jpg", "second.jpg"))
        photos.colorsByPath["first.jpg"] = listOf(measured(red))
        photos.colorsByPath["second.jpg"] = listOf(measured(blue, "#1A2B8C"))
        // The first image's extraction finishes only after the second image is picked.
        var lateFirstExtraction: Continuation<Unit>? = null
        photos.extractGate = { path -> if (path == "first.jpg") suspendCoroutine { lateFirstExtraction = it } }
        // The second image's caption is still in flight when the late extraction lands.
        var pendingSecondCaption: Continuation<String?>? = null
        photos.describeHandler = { path ->
            if (path == "second.jpg") suspendCoroutine { pendingSecondCaption = it } else "caption:$path"
        }
        val viewModel = subscribed(buildViewModel())

        viewModel.onImageSelected("content://media/picked/1")
        viewModel.onImageSelected("content://media/picked/2")
        assertEquals(listOf(blue), viewModel.uiState.value.selectedColors)

        lateFirstExtraction!!.resume(Unit)
        advanceUntilIdle()
        pendingSecondCaption!!.resume("caption:second.jpg")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("second.jpg", state.imagePath)
        assertEquals(listOf(blue), state.selectedColors)
        assertEquals("caption:second.jpg", state.imageCaption)
        assertEquals(listOf("second.jpg"), photos.describeCalls)
    }

    @Test
    fun `cancel deletes the staged image`() = runTest {
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")

        viewModel.cancel()
        advanceUntilIdle()

        assertEquals(listOf("picked.jpg"), photos.deleted)
    }

    @Test
    fun `save deletes the pre-segmentation original`() = runTest {
        val viewModel = subscribed(buildViewModel())
        viewModel.onNameChange("Shirt")
        viewModel.onImageSelected("content://media/picked/1")
        viewModel.removeBackground()
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertEquals(listOf("picked.jpg"), photos.deleted)
    }

    // ─── Colour rows on save ──────────────────────────────────────────────────

    /** Saves a new item and returns the colour rows handed to the repository. */
    private fun TestScope.savedInsertColors(viewModel: ClothingFormViewModel): List<TaggedColor> {
        val colors = slot<List<TaggedColor>>()
        coEvery {
            clothingRepository.insertItemWithAttributes(any(), capture(colors), any(), any(), any(), any())
        } returns DataResult.Success(1L)
        viewModel.onNameChange("Shirt")
        viewModel.save()
        advanceUntilIdle()
        return colors.captured
    }

    @Test
    fun `save after extraction stores measured auto rows`() = runTest {
        photos.colorsByPath["picked.jpg"] = listOf(measured(red, "#C81E1E", 0.7), measured(blue, "#1A2B8C", 0.3))
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")

        val saved = savedInsertColors(viewModel)

        assertEquals(listOf(shades), photos.extractShades)
        assertEquals(
            listOf(
                TaggedColor(red.id, hex = "#C81E1E", share = 0.7, source = ColorSource.AUTO),
                TaggedColor(blue.id, hex = "#1A2B8C", share = 0.3, source = ColorSource.AUTO),
            ),
            saved,
        )
    }

    @Test
    fun `toggling after extraction marks rows user but keeps measurements`() = runTest {
        photos.colorsByPath["picked.jpg"] = listOf(measured(red, "#C81E1E", 0.8))
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")

        viewModel.onColorToggle(blue)
        val saved = savedInsertColors(viewModel)

        assertEquals(
            listOf(
                TaggedColor(red.id, hex = "#C81E1E", share = 0.8, source = ColorSource.USER),
                TaggedColor(blue.id, hex = null, share = null, source = ColorSource.USER),
            ),
            saved,
        )
    }

    @Test
    fun `editing without touching colours preserves rows`() = runTest {
        val entity = ClothingItemEntity(id = 7, name = "Shirt")
        coEvery { clothingRepository.getItemEntityById(7) } returns DataResult.Success(entity)
        coEvery { clothingRepository.getItemDetailOnce(7) } returns DataResult.Success(
            ClothingItemDetail(
                item = entity, wearCount = 0, category = null, subcategory = null, sizeValue = null,
                colors = listOf(red, blue), materials = emptyList(), seasons = emptyList(),
                occasions = emptyList(), patterns = emptyList(), brand = null,
            )
        )
        coEvery { clothingRepository.getItemColorRows(7) } returns DataResult.Success(
            listOf(
                ClothingItemColorEntity(7, red.id, hex = "#C81E1E", share = 0.6, source = ColorSource.AUTO),
                ClothingItemColorEntity(7, blue.id, hex = "#1A2B8C", share = 0.4, source = ColorSource.AUTO),
            )
        )
        val colors = slot<List<TaggedColor>>()
        coEvery {
            clothingRepository.updateItemWithAttributes(any(), capture(colors), any(), any(), any(), any())
        } returns DataResult.Success(1)
        val viewModel = subscribed(buildViewModel(SavedStateHandle(mapOf("itemId" to 7L))))
        advanceUntilIdle()

        viewModel.onNameChange("Renamed shirt")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            listOf(
                TaggedColor(red.id, hex = "#C81E1E", share = 0.6, source = ColorSource.AUTO),
                TaggedColor(blue.id, hex = "#1A2B8C", share = 0.4, source = ColorSource.AUTO),
            ),
            colors.captured,
        )
    }

    @Test
    fun `new extraction resets the user edited flag`() = runTest {
        photos.importResults.clear()
        photos.importResults.addAll(listOf("first.jpg", "second.jpg"))
        photos.colorsByPath["first.jpg"] = listOf(measured(red, "#C81E1E", 1.0))
        photos.colorsByPath["second.jpg"] = listOf(measured(blue, "#1A2B8C", 0.9))
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")
        viewModel.onColorToggle(red) // a user edit before the new photo

        viewModel.onImageSelected("content://media/picked/2")
        val saved = savedInsertColors(viewModel)

        assertEquals(listOf(TaggedColor(blue.id, hex = "#1A2B8C", share = 0.9, source = ColorSource.AUTO)), saved)
    }

    /** Stubs edit-mode item 7 with [rows] (and their colours as the detail's colours). */
    private fun stubEditItem(rows: List<ClothingItemColorEntity>, imagePath: String? = null) {
        val entity = ClothingItemEntity(id = 7, name = "Shirt", imagePath = imagePath)
        val byId = listOf(red, blue).associateBy { it.id }
        coEvery { clothingRepository.getItemEntityById(7) } returns DataResult.Success(entity)
        coEvery { clothingRepository.getItemDetailOnce(7) } returns DataResult.Success(
            ClothingItemDetail(
                item = entity, wearCount = 0, category = null, subcategory = null, sizeValue = null,
                colors = rows.map { byId.getValue(it.colorId) }, materials = emptyList(), seasons = emptyList(),
                occasions = emptyList(), patterns = emptyList(), brand = null,
            )
        )
        coEvery { clothingRepository.getItemColorRows(7) } returns DataResult.Success(rows)
    }

    /** Saves the edit-mode item and returns the colour rows handed to the repository. */
    private fun TestScope.savedUpdateColors(viewModel: ClothingFormViewModel): List<TaggedColor> {
        val colors = slot<List<TaggedColor>>()
        coEvery {
            clothingRepository.updateItemWithAttributes(any(), capture(colors), any(), any(), any(), any())
        } returns DataResult.Success(1)
        viewModel.save()
        advanceUntilIdle()
        return colors.captured
    }

    @Test
    fun `segmentation keeps a curated selection`() = runTest {
        // A migrated v1 row (user, no measurement) and a user-kept measured row.
        stubEditItem(
            rows = listOf(
                ClothingItemColorEntity(7, red.id, hex = null, share = null, source = ColorSource.USER),
                ClothingItemColorEntity(7, blue.id, hex = "#1A2B8C", share = 0.4, source = ColorSource.USER),
            ),
            imagePath = "shirt.jpg",
        )
        photos.colorsByPath["segmented.png"] = listOf(measured(red, "#AA0000", 1.0))
        val viewModel = subscribed(buildViewModel(SavedStateHandle(mapOf("itemId" to 7L))))
        advanceUntilIdle()

        viewModel.removeBackground()
        advanceUntilIdle()
        assertEquals("segmented.png", viewModel.uiState.value.imagePath)
        val saved = savedUpdateColors(viewModel)

        assertEquals(
            listOf(
                TaggedColor(red.id, hex = null, share = null, source = ColorSource.USER),
                TaggedColor(blue.id, hex = "#1A2B8C", share = 0.4, source = ColorSource.USER),
            ),
            saved,
        )
    }

    @Test
    fun `segmentation refreshes an untouched auto selection`() = runTest {
        photos.colorsByPath["picked.jpg"] = listOf(measured(red, "#C81E1E", 1.0))
        photos.colorsByPath["segmented.png"] = listOf(measured(blue, "#1A2B8C", 0.9))
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")

        viewModel.removeBackground()
        advanceUntilIdle()
        val saved = savedInsertColors(viewModel)

        assertEquals(listOf(TaggedColor(blue.id, hex = "#1A2B8C", share = 0.9, source = ColorSource.AUTO)), saved)
    }

    @Test
    fun `undoing background removal restores the pre-segmentation colours`() = runTest {
        photos.colorsByPath["picked.jpg"] = listOf(measured(red, "#C81E1E", 1.0))
        photos.colorsByPath["segmented.png"] = listOf(measured(blue, "#1A2B8C", 0.9))
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")
        viewModel.removeBackground()
        advanceUntilIdle()
        assertEquals(listOf(blue), viewModel.uiState.value.selectedColors)

        viewModel.revertSegmentation()
        advanceUntilIdle()

        assertEquals(listOf(red), viewModel.uiState.value.selectedColors)
        assertEquals(
            listOf(TaggedColor(red.id, hex = "#C81E1E", share = 1.0, source = ColorSource.AUTO)),
            savedInsertColors(viewModel),
        )
    }

    @Test
    fun `undoing background removal keeps a colour edit made after it`() = runTest {
        photos.colorsByPath["picked.jpg"] = listOf(measured(red, "#C81E1E", 1.0))
        photos.colorsByPath["segmented.png"] = listOf(measured(blue, "#1A2B8C", 0.9))
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")
        viewModel.removeBackground()
        advanceUntilIdle()
        viewModel.onColorToggle(red) // the user's choice on the segmented photo

        viewModel.revertSegmentation()
        advanceUntilIdle()

        assertEquals(
            listOf(
                TaggedColor(blue.id, hex = "#1A2B8C", share = 0.9, source = ColorSource.USER),
                TaggedColor(red.id, hex = null, share = null, source = ColorSource.USER),
            ),
            savedInsertColors(viewModel),
        )
    }

    @Test
    fun `a toggle made while extraction runs is not overwritten`() = runTest {
        photos.colorsByPath["picked.jpg"] = listOf(measured(red))
        var pending: Continuation<Unit>? = null
        photos.extractGate = { path -> if (path == "picked.jpg") suspendCoroutine { pending = it } }
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")

        viewModel.onColorToggle(blue) // while picked.jpg is still being measured
        pending!!.resume(Unit)
        advanceUntilIdle()

        assertEquals(listOf(blue), viewModel.uiState.value.selectedColors)
        assertEquals(listOf(TaggedColor(blue.id, source = ColorSource.USER)), savedInsertColors(viewModel))
    }

    @Test
    fun `a toggle made while the previous image is being deleted is not overwritten`() = runTest {
        photos.importResults.clear()
        photos.importResults.addAll(listOf("first.jpg", "second.jpg"))
        photos.colorsByPath["second.jpg"] = listOf(measured(red))
        var pendingDelete: Continuation<Unit>? = null
        photos.deleteGate = { path -> if (path == "first.jpg") suspendCoroutine { pendingDelete = it } }
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")

        viewModel.onImageSelected("content://media/picked/2")
        assertEquals("second.jpg", viewModel.uiState.value.imagePath)
        viewModel.onColorToggle(blue) // second.jpg is published; first.jpg's cleanup is still running
        pendingDelete!!.resume(Unit)
        advanceUntilIdle()

        assertEquals(listOf("second.jpg"), photos.extractCalls.filter { it == "second.jpg" })
        assertEquals(listOf(blue), viewModel.uiState.value.selectedColors)
    }

    @Test
    fun `a new photo with no extracted colours keeps the selection without the old measurements`() = runTest {
        stubEditItem(
            rows = listOf(
                ClothingItemColorEntity(7, red.id, hex = "#C81E1E", share = 0.6, source = ColorSource.AUTO),
                ClothingItemColorEntity(7, blue.id, hex = "#1A2B8C", share = 0.4, source = ColorSource.AUTO),
            ),
            imagePath = "shirt.jpg",
        )
        val viewModel = subscribed(buildViewModel(SavedStateHandle(mapOf("itemId" to 7L))))
        advanceUntilIdle()

        viewModel.onImageSelected("content://media/picked/1") // picked.jpg extracts nothing
        val saved = savedUpdateColors(viewModel)

        assertEquals(
            listOf(
                TaggedColor(red.id, hex = null, share = null, source = ColorSource.AUTO),
                TaggedColor(blue.id, hex = null, share = null, source = ColorSource.AUTO),
            ),
            saved,
        )
    }

    @Test
    fun `saving while a new photo is measured carries no measurement from the previous photo`() = runTest {
        photos.importResults.clear()
        photos.importResults.addAll(listOf("first.jpg", "second.jpg"))
        photos.colorsByPath["first.jpg"] = listOf(measured(red, "#C81E1E", 1.0))
        photos.colorsByPath["second.jpg"] = listOf(measured(blue, "#1A2B8C", 1.0))
        photos.extractGate = { path -> if (path == "second.jpg") awaitCancellation() }
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")
        assertEquals(listOf(red), viewModel.uiState.value.selectedColors)

        viewModel.onImageSelected("content://media/picked/2") // second.jpg's extraction never finishes
        val saved = savedInsertColors(viewModel)

        assertEquals(listOf(TaggedColor(red.id, hex = null, share = null, source = ColorSource.AUTO)), saved)
    }

    @Test
    fun `the edit load fails when the colour rows cannot be read`() = runTest {
        stubEditItem(rows = emptyList())
        coEvery { clothingRepository.getItemColorRows(7) } returns DataResult.Error(RuntimeException("db"))
        val viewModel = subscribed(buildViewModel(SavedStateHandle(mapOf("itemId" to 7L))))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CoreRes.string.error_database_query, state.errorMessage)
        assertFalse(state.isLoading)
        assertFalse(state.canSave)
        viewModel.save()
        advanceUntilIdle()
        coVerify(exactly = 0) { clothingRepository.updateItemWithAttributes(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `editing a colour on an item with auto rows saves every row as user with its measurement`() = runTest {
        stubEditItem(
            rows = listOf(
                ClothingItemColorEntity(7, red.id, hex = "#C81E1E", share = 0.6, source = ColorSource.AUTO),
                ClothingItemColorEntity(7, blue.id, hex = "#1A2B8C", share = 0.4, source = ColorSource.AUTO),
            ),
        )
        val viewModel = subscribed(buildViewModel(SavedStateHandle(mapOf("itemId" to 7L))))
        advanceUntilIdle()

        viewModel.onColorToggle(blue)
        viewModel.onColorToggle(blue) // off and on again: same set, but a manual edit
        val saved = savedUpdateColors(viewModel)

        assertEquals(
            listOf(
                TaggedColor(red.id, hex = "#C81E1E", share = 0.6, source = ColorSource.USER),
                TaggedColor(blue.id, hex = "#1A2B8C", share = 0.4, source = ColorSource.USER),
            ),
            saved,
        )
    }

    @Test
    fun `a reference shade load failure is not cached`() = runTest {
        coEvery { lookupRepository.getReferenceShades() } returnsMany listOf(
            DataResult.Error(RuntimeException("db")),
            DataResult.Success(shades),
        )
        photos.importResults.clear()
        photos.importResults.addAll(listOf("first.jpg", "second.jpg"))
        photos.colorsByPath["second.jpg"] = listOf(measured(red))
        val viewModel = subscribed(buildViewModel())

        viewModel.onImageSelected("content://media/picked/1")
        viewModel.onImageSelected("content://media/picked/2")

        coVerify(exactly = 2) { lookupRepository.getReferenceShades() }
        assertEquals(listOf("second.jpg"), photos.extractCalls)
        assertEquals(listOf(shades), photos.extractShades)
        assertEquals(listOf(red), viewModel.uiState.value.selectedColors)
    }

    @Test
    fun `reference shades are loaded once and reused`() = runTest {
        val viewModel = subscribed(buildViewModel())
        viewModel.onImageSelected("content://media/picked/1")
        viewModel.removeBackground()
        advanceUntilIdle()

        assertEquals(listOf(shades, shades), photos.extractShades)
        coVerify(exactly = 1) { lookupRepository.getReferenceShades() }
    }

    @Test
    fun `a reference shade load failure skips extraction and leaves the selection`() = runTest {
        coEvery { lookupRepository.getReferenceShades() } returns DataResult.Error(RuntimeException("db"))
        photos.colorsByPath["picked.jpg"] = listOf(measured(red))
        val viewModel = subscribed(buildViewModel())
        viewModel.onColorToggle(blue)

        viewModel.onImageSelected("content://media/picked/1")

        assertTrue(photos.extractCalls.isEmpty())
        assertEquals(listOf(blue), viewModel.uiState.value.selectedColors)
    }

    // ─── Shortcuts ────────────────────────────────────────────────────────────

    @Test
    fun `quick_add is reported after a successful add from the shortcut`() = runTest {
        val viewModel = subscribed(buildViewModel(SavedStateHandle(mapOf("openCamera" to true))))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect {} }
        viewModel.onNameChange("Shirt")

        viewModel.save()
        advanceUntilIdle()

        verify(exactly = 1) { shortcutUsageReporter.reportUsed("quick_add") }
    }

    @Test
    fun `quick_add is not reported for a regular add`() = runTest {
        val viewModel = subscribed(buildViewModel())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect {} }
        viewModel.onNameChange("Shirt")

        viewModel.save()
        advanceUntilIdle()

        verify(exactly = 0) { shortcutUsageReporter.reportUsed(any()) }
    }

    @Test
    fun `quick_add is not reported when the add fails`() = runTest {
        coEvery {
            clothingRepository.insertItemWithAttributes(any(), any(), any(), any(), any(), any())
        } returns DataResult.Error(RuntimeException("db"))
        val viewModel = subscribed(buildViewModel(SavedStateHandle(mapOf("openCamera" to true))))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect {} }
        viewModel.onNameChange("Shirt")

        viewModel.save()
        advanceUntilIdle()

        verify(exactly = 0) { shortcutUsageReporter.reportUsed(any()) }
    }
}
