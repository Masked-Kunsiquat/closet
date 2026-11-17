package com.closet.features.wardrobe

import android.content.res.Configuration
import com.closet.core.ui.resources.Res as CoreRes
import com.closet.core.ui.resources.ic_symbol_straighten
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.data.model.*
import com.closet.core.data.model.BrandEntity
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.model.OccasionEntity
import com.closet.core.data.model.SeasonEntity
import com.closet.core.data.model.SizeSystemEntity
import com.closet.core.ui.components.ClothingItemCard
import com.closet.core.ui.components.StringErrorSnackbarEffect
import com.closet.core.ui.theme.ClosetTheme
import com.closet.features.wardrobe.resources.Res
import com.closet.features.wardrobe.resources.brand_management_add_brand
import com.closet.features.wardrobe.resources.brand_management_confirm_delete
import com.closet.features.wardrobe.resources.brand_management_delete_blocked_message
import com.closet.features.wardrobe.resources.brand_management_delete_blocked_title
import com.closet.features.wardrobe.resources.brand_management_delete_confirm_message
import com.closet.features.wardrobe.resources.brand_management_delete_confirm_title
import com.closet.features.wardrobe.resources.brand_management_edit_hint
import com.closet.features.wardrobe.resources.brand_management_empty
import com.closet.features.wardrobe.resources.brand_management_new_brand_hint
import com.closet.features.wardrobe.resources.brand_management_title
import com.closet.features.wardrobe.resources.wardrobe_add_item
import com.closet.features.wardrobe.resources.wardrobe_all_items
import com.closet.features.wardrobe.resources.wardrobe_back
import com.closet.features.wardrobe.resources.wardrobe_cancel
import com.closet.features.wardrobe.resources.wardrobe_cd_open_filters
import com.closet.features.wardrobe.resources.wardrobe_cd_open_settings
import com.closet.features.wardrobe.resources.wardrobe_cd_sort
import com.closet.features.wardrobe.resources.wardrobe_empty_closet
import com.closet.features.wardrobe.resources.wardrobe_field_size_system
import com.closet.features.wardrobe.resources.wardrobe_filter_clear_all
import com.closet.features.wardrobe.resources.wardrobe_filter_clear_filters
import com.closet.features.wardrobe.resources.wardrobe_filter_empty_results
import com.closet.features.wardrobe.resources.wardrobe_filter_favorites
import com.closet.features.wardrobe.resources.wardrobe_filter_section_color
import com.closet.features.wardrobe.resources.wardrobe_filter_section_occasion
import com.closet.features.wardrobe.resources.wardrobe_filter_section_season
import com.closet.features.wardrobe.resources.wardrobe_filter_show_archived
import com.closet.features.wardrobe.resources.wardrobe_filter_title
import com.closet.features.wardrobe.resources.wardrobe_my_closet
import com.closet.features.wardrobe.resources.wardrobe_pin_category
import com.closet.features.wardrobe.resources.wardrobe_save
import com.closet.features.wardrobe.resources.wardrobe_sort_least_worn
import com.closet.features.wardrobe.resources.wardrobe_sort_most_worn
import com.closet.features.wardrobe.resources.wardrobe_sort_name_az
import com.closet.features.wardrobe.resources.wardrobe_sort_name_za
import com.closet.features.wardrobe.resources.wardrobe_sort_newest
import com.closet.features.wardrobe.resources.wardrobe_sort_oldest
import com.closet.features.wardrobe.resources.wardrobe_worn_times
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// --- Previews ---

private val previewBrandsList = listOf(
    BrandEntity(id = 1, name = "Nike", normalizedName = "nike"),
    BrandEntity(id = 2, name = "Adidas", normalizedName = "adidas"),
    BrandEntity(id = 3, name = "Levi's", normalizedName = "levi's"),
    BrandEntity(id = 4, name = "Uniqlo", normalizedName = "uniqlo"),
    BrandEntity(id = 5, name = "Zara", normalizedName = "zara"),
)

@Preview(showBackground = true, name = "Brand List - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Brand List - Dark")
@Composable
private fun BrandManagementContentPreview() {
    ClosetTheme {
        Surface {
            BrandManagementContent(
                uiState = BrandManagementUiState(brands = previewBrandsList),
                onBack = {},
                onSaveBrand = { _, _ -> },
                onRequestDelete = {},
                onConfirmDelete = {},
                onDismissDialog = {},
                onErrorConsumed = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Empty State")
@Composable
private fun BrandManagementEmptyPreview() {
    ClosetTheme {
        Surface {
            BrandManagementContent(
                uiState = BrandManagementUiState(brands = emptyList()),
                onBack = {},
                onSaveBrand = { _, _ -> },
                onRequestDelete = {},
                onConfirmDelete = {},
                onDismissDialog = {},
                onErrorConsumed = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Confirm Delete Dialog")
@Composable
private fun BrandManagementConfirmDeletePreview() {
    ClosetTheme {
        Surface {
            BrandManagementContent(
                uiState = BrandManagementUiState(
                    brands = previewBrandsList,
                    dialog = BrandManagementDialog.ConfirmDelete(previewBrandsList[0])
                ),
                onBack = {},
                onSaveBrand = { _, _ -> },
                onRequestDelete = {},
                onConfirmDelete = {},
                onDismissDialog = {},
                onErrorConsumed = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Blocked Delete Dialog")
@Composable
private fun BrandManagementBlockedDeletePreview() {
    ClosetTheme {
        Surface {
            BrandManagementContent(
                uiState = BrandManagementUiState(
                    brands = previewBrandsList,
                    dialog = BrandManagementDialog.BlockedDelete(previewBrandsList[1], itemCount = 7)
                ),
                onBack = {},
                onSaveBrand = { _, _ -> },
                onRequestDelete = {},
                onConfirmDelete = {},
                onDismissDialog = {},
                onErrorConsumed = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Items - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Items - Dark")
@Composable
private fun ClosetScreenPreview() {
    ClosetTheme {
        Surface {
            ClosetContent(
                items = listOf(
                    ClothingItemDetail(
                        item = ClothingItemEntity(
                            id = 1,
                            name = "Vintage Denim Jacket",
                            brandId = 1,
                            categoryId = 1,
                            status = ClothingStatus.Active,
                            isFavorite = 1,
                            washStatus = WashStatus.Clean
                        ),
                        wearCount = 12,
                        brand = com.closet.core.data.model.BrandEntity(id = 1, name = "Levi's", normalizedName = "levi's"),
                        category = CategoryEntity(id = 1, name = "Outerwear", sortOrder = 3),
                        subcategory = null,
                        sizeValue = null,
                        colors = emptyList(),
                        materials = emptyList(),
                        seasons = emptyList(),
                        occasions = emptyList(),
                        patterns = emptyList()
                    )
                ),
                categories = listOf(
                    CategoryEntity(id = 1, name = "Tops", sortOrder = 1),
                    CategoryEntity(id = 2, name = "Pants", sortOrder = 2),
                    CategoryEntity(id = 3, name = "Outerwear", sortOrder = 3)
                ),
                selectedCategoryId = null,
                favoritesOnly = false,
                showArchived = false,
                sortOrder = SortOrder.NEWEST,
                colors = emptyList(),
                seasons = emptyList(),
                occasions = emptyList(),
                sizeSystems = emptyList(),
                selectedColorIds = emptySet(),
                selectedSeasonIds = emptySet(),
                selectedOccasionIds = emptySet(),
                selectedSizeSystemIds = emptySet(),
                activeFilterCount = 0,
                onCategorySelect = {},
                onToggleFavorites = {},
                onToggleShowArchived = {},
                onSortOrderSelected = {},
                onToggleColor = {},
                onToggleSeason = {},
                onToggleOccasion = {},
                onToggleSizeSystem = {},
                onClearAdvancedFilters = {},
                onClearAllFilters = {},
                resolveImagePath = { null },
                onAddItemClick = {},
                onItemClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Empty State")
@Composable
private fun ClosetScreenEmptyPreview() {
    ClosetTheme {
        Surface {
            ClosetContent(
                items = emptyList(),
                categories = emptyList(),
                selectedCategoryId = null,
                favoritesOnly = false,
                showArchived = false,
                sortOrder = SortOrder.NEWEST,
                colors = emptyList(),
                seasons = emptyList(),
                occasions = emptyList(),
                sizeSystems = emptyList(),
                selectedColorIds = emptySet(),
                selectedSeasonIds = emptySet(),
                selectedOccasionIds = emptySet(),
                selectedSizeSystemIds = emptySet(),
                activeFilterCount = 0,
                onCategorySelect = {},
                onToggleFavorites = {},
                onToggleShowArchived = {},
                onSortOrderSelected = {},
                onToggleColor = {},
                onToggleSeason = {},
                onToggleOccasion = {},
                onToggleSizeSystem = {},
                onClearAdvancedFilters = {},
                onClearAllFilters = {},
                resolveImagePath = { null },
                onAddItemClick = {},
                onItemClick = {}
            )
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private val filterPreviewColors = listOf(
    ColorEntity(1L, "Black", "#1C1C1C"),
    ColorEntity(2L, "White", "#F5F5F5"),
    ColorEntity(3L, "Navy", "#1B2A4A"),
    ColorEntity(4L, "Olive", "#6B7C3A"),
    ColorEntity(5L, "Cream", "#F5F0E8"),
    ColorEntity(6L, "Burgundy", "#7C1C2A"),
)

private val previewSeasons = listOf(
    SeasonEntity(1L, "Spring", null),
    SeasonEntity(2L, "Summer", null),
    SeasonEntity(3L, "Fall", null),
    SeasonEntity(4L, "Winter", null),
)

private val previewOccasions = listOf(
    OccasionEntity(1L, "Casual", null),
    OccasionEntity(2L, "Work", null),
    OccasionEntity(3L, "Evening", null),
    OccasionEntity(4L, "Sport", null),
    OccasionEntity(5L, "Beach", null),
)

private val previewSizeSystems = listOf(
    SizeSystemEntity(1L, "Letter"),
    SizeSystemEntity(2L, "Women's Numeric"),
    SizeSystemEntity(3L, "Shoes (US Men's)"),
    SizeSystemEntity(4L, "One Size"),
)

/**
 * Renders the filter panel content directly (without [ModalBottomSheet]) so
 * Android Studio can display a pixel-accurate preview.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanelContentPreview(
    selectedColorIds: Set<Long> = emptySet(),
    selectedSeasonIds: Set<Long> = emptySet(),
    selectedOccasionIds: Set<Long> = emptySet(),
    selectedSizeSystemIds: Set<Long> = emptySet(),
) {
    val anyActive = selectedColorIds.isNotEmpty() ||
            selectedSeasonIds.isNotEmpty() ||
            selectedOccasionIds.isNotEmpty() ||
            selectedSizeSystemIds.isNotEmpty()

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.wardrobe_filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {}, enabled = anyActive) {
                    Text(stringResource(Res.string.wardrobe_filter_clear_all))
                }
            }
            HorizontalDivider()
            FilterSection(title = stringResource(Res.string.wardrobe_filter_section_color)) {
                filterPreviewColors.forEach { color ->
                    val fallback = MaterialTheme.colorScheme.outlineVariant
                    val swatchColor = remember(color.hex) {
                        color.hex?.let {
                            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
                        } ?: fallback
                    }
                    FilterChip(
                        selected = color.id in selectedColorIds,
                        onClick = {},
                        label = { Text(color.name) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                            )
                        }
                    )
                }
            }
            FilterSection(title = stringResource(Res.string.wardrobe_field_size_system)) {
                previewSizeSystems.forEach { system ->
                    FilterChip(
                        selected = system.id in selectedSizeSystemIds,
                        onClick = {},
                        label = { Text(system.name) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(CoreRes.drawable.ic_symbol_straighten),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
            FilterSection(title = stringResource(Res.string.wardrobe_filter_section_season)) {
                previewSeasons.forEach { season ->
                    FilterChip(
                        selected = season.id in selectedSeasonIds,
                        onClick = {},
                        label = { Text(season.name) }
                    )
                }
            }
            FilterSection(title = stringResource(Res.string.wardrobe_filter_section_occasion)) {
                previewOccasions.forEach { occasion ->
                    FilterChip(
                        selected = occasion.id in selectedOccasionIds,
                        onClick = {},
                        label = { Text(occasion.name) }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Filter Panel - No Selection - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Filter Panel - No Selection - Dark")
@Composable
private fun FilterPanelEmptyPreview() {
    ClosetTheme { FilterPanelContentPreview() }
}

@Preview(showBackground = true, name = "Filter Panel - Active Selections")
@Composable
private fun FilterPanelActivePreview() {
    ClosetTheme {
        FilterPanelContentPreview(
            selectedColorIds = setOf(1L, 3L),
            selectedSeasonIds = setOf(2L),
            selectedSizeSystemIds = setOf(1L),
        )
    }
}

// region Previews

private val previewColorItems = listOf(
    MultiSelectItem(1L, "Black", Unit, colorHex = "#000000"),
    MultiSelectItem(2L, "White", Unit, colorHex = "#FFFFFF"),
    MultiSelectItem(3L, "Navy", Unit, colorHex = "#001F5B"),
    MultiSelectItem(4L, "Red", Unit, colorHex = "#CC0000"),
    MultiSelectItem(5L, "Olive", Unit, colorHex = "#708238"),
)

private val previewLabelItems = listOf(
    MultiSelectItem(1L, "Spring", Unit),
    MultiSelectItem(2L, "Summer", Unit),
    MultiSelectItem(3L, "Fall", Unit),
    MultiSelectItem(4L, "Winter", Unit),
)

@Composable
private fun MultiSelectSheetContentPreview(
    title: String,
    items: List<MultiSelectItem<Unit>>,
    selectedIds: Set<Long>
) {
    // Render the inner sheet content directly (ModalBottomSheet not renderable in preview)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = {}) { Text("Save") }
            }
            HorizontalDivider()
            items.forEach { item ->
                MultiSelectRow(
                    label = item.label,
                    colorHex = item.colorHex,
                    icon = item.icon,
                    isSelected = item.id in selectedIds,
                    onToggle = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Color Picker - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Color Picker - Dark")
@Composable
private fun MultiSelectSheetColorsPreview() {
    ClosetTheme {
        MultiSelectSheetContentPreview(
            title = "Colors",
            items = previewColorItems,
            selectedIds = setOf(1L, 3L)
        )
    }
}

@Preview(showBackground = true, name = "Label Picker - Partial Selection")
@Composable
private fun MultiSelectSheetLabelsPreview() {
    ClosetTheme {
        MultiSelectSheetContentPreview(
            title = "Seasons",
            items = previewLabelItems,
            selectedIds = setOf(2L)
        )
    }
}

@Preview(showBackground = true, name = "Empty State")
@Composable
private fun MultiSelectSheetEmptyPreview() {
    ClosetTheme {
        MultiSelectSheetContentPreview(
            title = "Materials",
            items = emptyList(),
            selectedIds = emptySet()
        )
    }
}

// endregion
