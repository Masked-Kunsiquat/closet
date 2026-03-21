package com.closet.features.wardrobe

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.closet.core.data.model.BrandEntity
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ClothingStatus
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.model.MaterialEntity
import com.closet.core.data.model.OccasionEntity
import com.closet.core.data.model.SeasonEntity
import com.closet.core.data.model.SubcategoryEntity
import com.closet.core.data.model.WashStatus
import com.closet.core.ui.theme.ClosetTheme

// ─── Shared preview data ──────────────────────────────────────────────────────

internal val previewDetailItem = ClothingItemDetail(
    item = ClothingItemEntity(
        id = 1L,
        name = "Vintage Denim Jacket",
        purchasePrice = 85.00,
        status = ClothingStatus.Active,
        washStatus = WashStatus.Clean,
        isFavorite = 1,
        notes = "Great for layering. Found at a thrift store in Brooklyn."
    ),
    wearCount = 12,
    category = CategoryEntity(id = 1L, name = "Tops", sortOrder = 1),
    subcategory = SubcategoryEntity(id = 2L, categoryId = 1L, name = "Jackets", sortOrder = 2),
    brand = BrandEntity(id = 1L, name = "Levi's"),
    sizeValue = null,
    colors = listOf(
        ColorEntity(id = 1L, name = "Blue", hex = "#3A6BAE"),
        ColorEntity(id = 2L, name = "White", hex = "#FFFFFF"),
    ),
    materials = listOf(
        MaterialEntity(id = 1L, name = "Denim"),
        MaterialEntity(id = 2L, name = "Cotton")
    ),
    seasons = listOf(
        SeasonEntity(id = 1L, name = "Spring", icon = "flower"),
        SeasonEntity(id = 3L, name = "Fall", icon = "leaf")
    ),
    occasions = listOf(
        OccasionEntity(id = 1L, name = "Casual", icon = "couch"),
        OccasionEntity(id = 2L, name = "Weekend", icon = "coffee")
    ),
    patterns = emptyList()
)

internal val previewDetailItemMinimal = ClothingItemDetail(
    item = ClothingItemEntity(
        id = 2L,
        name = "Plain White Tee",
        status = ClothingStatus.Active,
        washStatus = WashStatus.Dirty,
        isFavorite = 0
    ),
    wearCount = 0,
    category = CategoryEntity(id = 1L, name = "Tops", sortOrder = 1),
    subcategory = null,
    brand = null,
    sizeValue = null,
    colors = emptyList(),
    materials = emptyList(),
    seasons = emptyList(),
    occasions = emptyList(),
    patterns = emptyList()
)

// ─── ClothingAttributes previews ─────────────────────────────────────────────

@Preview(showBackground = true, name = "Attributes - Filled - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Attributes - Filled - Dark")
@Composable
private fun ClothingAttributesFilledPreview() {
    ClosetTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                ClothingAttributes(
                    item = previewDetailItem,
                    onEditSeasons = {},
                    onEditOccasions = {},
                    onEditColors = {},
                    onEditMaterials = {},
                    onEditPatterns = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Attributes - Empty State")
@Composable
private fun ClothingAttributesEmptyPreview() {
    ClosetTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                ClothingAttributes(
                    item = previewDetailItemMinimal,
                    onEditSeasons = {},
                    onEditOccasions = {},
                    onEditColors = {},
                    onEditMaterials = {},
                    onEditPatterns = {}
                )
            }
        }
    }
}

// ─── AttributeChip / AttributeSection previews ───────────────────────────────

@Preview(showBackground = true, name = "AttributeChip - Text Only")
@Preview(showBackground = true, name = "AttributeChip - With Color")
@Composable
private fun AttributeChipPreview() {
    ClosetTheme {
        Surface {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttributeChip(label = "Casual", onClick = {})
                AttributeChip(label = "Blue", color = Color(0xFF3A6BAE), onClick = {})
                AttributeChip(label = "Fall", onClick = {})
            }
        }
    }
}

@Preview(showBackground = true, name = "AttributeSection - With Items")
@Composable
private fun AttributeSectionPreview() {
    ClosetTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                AttributeSection(title = "Colors", onEditClick = {}) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AttributeChip(label = "Blue", color = Color(0xFF3A6BAE), onClick = {})
                        AttributeChip(label = "White", color = Color(0xFFFFFFFF), onClick = {})
                    }
                }
            }
        }
    }
}
