package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.SizeSystemEntity
import com.closet.core.data.model.SubcategoryEntity
import com.closet.core.data.repository.BrandRepository
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.features.wardrobe.repository.ImageCaptionRepository
import com.closet.features.wardrobe.repository.SegmentationRepository
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClothingFormViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    
    private val lookupRepository: LookupRepository = mockk(relaxed = true)
    private val brandRepository: BrandRepository = mockk(relaxed = true)
    private val storageRepository: StorageRepository = mockk(relaxed = true)
    private val clothingRepository: ClothingRepository = mockk(relaxed = true)
    private val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
    private val appContext: Context = mockk(relaxed = true)
    private val segmentationRepository: SegmentationRepository = mockk(relaxed = true)
    private val imageCaptionRepository: ImageCaptionRepository = mockk(relaxed = true)

    private val sizeSystems = listOf(
        SizeSystemEntity(1, "Letter"),
        SizeSystemEntity(2, "Shoes (US Men's)"),
        SizeSystemEntity(3, "One Size")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { lookupRepository.getSizeSystems() } returns flowOf(sizeSystems)
        every { lookupRepository.getCategories() } returns flowOf(emptyList())
        every { lookupRepository.getColors() } returns flowOf(emptyList())
        every { brandRepository.getAllBrands() } returns flowOf(com.closet.core.data.util.DataResult.Success(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = ClothingFormViewModel(
        appContext = appContext,
        savedStateHandle = savedStateHandle,
        lookupRepository = lookupRepository,
        brandRepository = brandRepository,
        storageRepository = storageRepository,
        clothingRepository = clothingRepository,
        segmentationRepository = segmentationRepository,
        imageCaptionRepository = imageCaptionRepository,
    )

    @Test
    fun `selecting footwear subcategory auto-suggests Shoes size system`() = runTest {
        val viewModel = buildViewModel()
        
        viewModel.uiState.test {
            awaitItem() // Initial state
            
            val sneakers = SubcategoryEntity(id = 10, categoryId = 1, name = "Sneakers", sortOrder = 1)
            viewModel.onSubcategorySelected(sneakers)
            
            val state = awaitItem()
            assertEquals(2L, state.selectedSizeSystemId) // Shoes (US Men's)
        }
    }

    @Test
    fun `selecting accessory subcategory auto-suggests One Size system`() = runTest {
        val viewModel = buildViewModel()
        
        viewModel.uiState.test {
            awaitItem()
            
            val backpack = SubcategoryEntity(id = 20, categoryId = 2, name = "Backpack", sortOrder = 1)
            viewModel.onSubcategorySelected(backpack)
            
            val state = awaitItem()
            assertEquals(3L, state.selectedSizeSystemId) // One Size
        }
    }

    @Test
    fun `user manual size system selection prevents auto-suggestion override`() = runTest {
        val viewModel = buildViewModel()
        
        viewModel.uiState.test {
            awaitItem()
            
            // User manually selects "Letter"
            viewModel.onSizeSystemSelected(sizeSystems[0].id) 
            assertEquals(1L, awaitItem().selectedSizeSystemId)
            
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
            awaitItem()
            
            // 2. Change category - should reset override flag
            viewModel.onCategorySelected(CategoryEntity(id = 5, name = "Footwear", sortOrder = 1))
            val stateAfterCat = awaitItem()
            assertEquals(5L, stateAfterCat.category?.id)
            
            // 3. Select subcategory - auto-suggestion should work again
            val sneakers = SubcategoryEntity(id = 10, categoryId = 5, name = "Sneakers", sortOrder = 1)
            viewModel.onSubcategorySelected(sneakers)
            
            assertEquals(2L, awaitItem().selectedSizeSystemId)
        }
    }
}
