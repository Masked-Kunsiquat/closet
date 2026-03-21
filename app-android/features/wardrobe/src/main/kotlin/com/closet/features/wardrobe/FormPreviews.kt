package com.closet.features.wardrobe

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.closet.core.data.model.BrandEntity
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.model.SubcategoryEntity
import com.closet.core.ui.theme.ClosetTheme

// ─── Shared preview data ──────────────────────────────────────────────────────

internal val previewCategories = listOf(
    CategoryEntity(id = 1L, name = "Tops", sortOrder = 1),
    CategoryEntity(id = 2L, name = "Bottoms", sortOrder = 2),
    CategoryEntity(id = 3L, name = "Shoes", sortOrder = 3),
)

internal val previewSubcategories = listOf(
    SubcategoryEntity(id = 1L, categoryId = 1L, name = "T-Shirts", sortOrder = 1),
    SubcategoryEntity(id = 2L, categoryId = 1L, name = "Jackets", sortOrder = 2),
)

internal val previewColors = listOf(
    ColorEntity(id = 1L, name = "Black", hex = "#000000"),
    ColorEntity(id = 2L, name = "White", hex = "#FFFFFF"),
    ColorEntity(id = 3L, name = "Navy", hex = "#001F5B"),
    ColorEntity(id = 4L, name = "Red", hex = "#CC0000"),
    ColorEntity(id = 5L, name = "Olive", hex = "#708238"),
    ColorEntity(id = 6L, name = "Tan", hex = "#D2B48C"),
)

internal val previewBrands = listOf(
    BrandEntity(id = 1L, name = "Nike"),
    BrandEntity(id = 2L, name = "Levi's"),
    BrandEntity(id = 3L, name = "Uniqlo"),
)

// ─── ClothingFormTopBar previews ──────────────────────────────────────────────

@Preview(showBackground = true, name = "Top Bar - Add Mode")
@Composable
private fun ClothingFormTopBarAddPreview() {
    ClosetTheme {
        Surface {
            ClothingFormTopBar(
                isEditMode = false,
                canSave = false,
                onBackClick = {},
                onSaveClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Top Bar - Edit Mode - Can Save")
@Composable
private fun ClothingFormTopBarEditPreview() {
    ClosetTheme {
        Surface {
            ClothingFormTopBar(
                isEditMode = true,
                canSave = true,
                onBackClick = {},
                onSaveClick = {}
            )
        }
    }
}

// ─── ClothingFormContent previews ─────────────────────────────────────────────

@Preview(showBackground = true, name = "Form - Empty/Add Mode - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Form - Empty/Add Mode - Dark")
@Composable
private fun ClothingFormContentEmptyPreview() {
    ClosetTheme {
        Surface {
            ClothingFormContent(
                uiState = ClothingFormUiState(
                    isEditMode = false,
                    categories = previewCategories,
                    allColors = previewColors,
                    allBrands = previewBrands
                ),
                onNameChange = {},
                onBrandQueryChange = {},
                onBrandSelect = {},
                onAddNewBrand = {},
                onManageBrands = {},
                onCategorySelect = {},
                onSubcategorySelect = {},
                onPriceChange = {},
                onDateChange = {},
                onLocationChange = {},
                onNotesChange = {},
                onImageClick = {},
                onColorToggle = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Form - Partially Filled")
@Composable
private fun ClothingFormContentFilledPreview() {
    ClosetTheme {
        Surface {
            ClothingFormContent(
                uiState = ClothingFormUiState(
                    isEditMode = true,
                    name = "Vintage Denim Jacket",
                    brandQuery = "Levi's",
                    category = previewCategories[0],
                    subcategory = previewSubcategories[1],
                    price = "85.00",
                    purchaseLocation = "Brooklyn Thrift Store",
                    categories = previewCategories,
                    subcategories = previewSubcategories,
                    allColors = previewColors,
                    selectedColors = listOf(previewColors[2]),
                    allBrands = previewBrands,
                    canSave = true,
                    isDirty = true
                ),
                onNameChange = {},
                onBrandQueryChange = {},
                onBrandSelect = {},
                onAddNewBrand = {},
                onManageBrands = {},
                onCategorySelect = {},
                onSubcategorySelect = {},
                onPriceChange = {},
                onDateChange = {},
                onLocationChange = {},
                onNotesChange = {},
                onImageClick = {},
                onColorToggle = {}
            )
        }
    }
}

// ─── FormComponents previews ──────────────────────────────────────────────────

@Preview(showBackground = true, name = "DropdownSelector - Category Selected")
@Composable
private fun DropdownSelectorCategoryPreview() {
    ClosetTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                DropdownSelector(
                    selectedItem = previewCategories[0],
                    items = previewCategories,
                    onItemSelect = {},
                    label = "Category",
                    itemLabel = { it.name }
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "DropdownSelector - Subcategory Enabled")
@Preview(showBackground = true, name = "DropdownSelector - Subcategory Disabled")
@Composable
private fun DropdownSelectorSubcategoryPreview() {
    ClosetTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DropdownSelector(
                    selectedItem = previewSubcategories[0],
                    items = previewSubcategories,
                    onItemSelect = {},
                    label = "Subcategory",
                    itemLabel = { it.name },
                    enabled = true
                )
                DropdownSelector(
                    selectedItem = null,
                    items = emptyList<SubcategoryEntity>(),
                    onItemSelect = {},
                    label = "Subcategory",
                    itemLabel = { it.name },
                    enabled = false
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Color Selection Grid")
@Composable
private fun ColorSelectionGridPreview() {
    ClosetTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                ColorSelectionGrid(
                    allColors = previewColors,
                    selectedColors = listOf(previewColors[0], previewColors[2]),
                    onColorToggle = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Date Picker Field - No Date")
@Composable
private fun DatePickerFieldPreview() {
    ClosetTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                DatePickerField(selectedDate = null, onDateChange = {})
            }
        }
    }
}

@Preview(showBackground = true, name = "Brand Autocomplete - With Results")
@Composable
private fun BrandAutocompleteFieldPreview() {
    ClosetTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                BrandAutocompleteField(
                    query = "Ni",
                    allBrands = previewBrands,
                    onQueryChange = {},
                    onBrandSelect = {},
                    onAddNewBrand = {}
                )
            }
        }
    }
}
