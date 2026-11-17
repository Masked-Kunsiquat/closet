package com.closet.features.recommendations

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.closet.core.data.model.OccasionEntity
import com.closet.core.ui.theme.ClosetTheme
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.OutfitCombo
import com.closet.features.recommendations.model.WeatherConditions
import com.closet.features.recommendations.ui.OccasionSheetContent
import com.closet.features.recommendations.ui.OutfitComboCard
import com.closet.features.recommendations.ui.WeatherSheetContent

// ─── Sample data ──────────────────────────────────────────────────────────────

private val previewItems = listOf(
    EngineItem(
        id = 1L,
        name = "White Oxford Shirt",
        imagePath = null,
        categoryId = 1L,
        subcategoryId = null,
        outfitRole = "Top",
        warmthLayer = "Base",
        colorFamilies = setOf("Neutral"),
        isPatternSolid = true,
    ),
    EngineItem(
        id = 2L,
        name = "Slim Chinos",
        imagePath = null,
        categoryId = 2L,
        subcategoryId = null,
        outfitRole = "Bottom",
        warmthLayer = "None",
        colorFamilies = setOf("Earth"),
        isPatternSolid = true,
    ),
    EngineItem(
        id = 3L,
        name = "White Sneakers",
        imagePath = null,
        categoryId = 6L,
        subcategoryId = null,
        outfitRole = "Footwear",
        warmthLayer = "None",
        colorFamilies = setOf("Neutral"),
        isPatternSolid = true,
    ),
)

private val previewCombos = listOf(
    OutfitCombo(items = previewItems, score = 0.87),
    OutfitCombo(items = previewItems.take(2), score = 0.75),
    OutfitCombo(items = previewItems, score = 0.65),
)

private val previewCombo = OutfitCombo(
    items = previewItems,
    score = 0.87,
)

private val previewAiCombo = OutfitCombo(
    items = previewItems,
    score = 0.91,
    isAiSelected = true,
    reason = "Neutral tones and a single earth accent create a clean, cohesive look suited to today's mild conditions.",
)

private val previewOccasions = listOf(
    OccasionEntity(1L, "Casual"),
    OccasionEntity(2L, "Work"),
    OccasionEntity(3L, "Evening"),
    OccasionEntity(4L, "Sport"),
    OccasionEntity(5L, "Beach"),
    OccasionEntity(6L, "Formal"),
)

// ─── Screen content ───────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Results - Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Results - Dark",
)
@Composable
private fun ResultsContentPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ResultsContent(
                combos = previewCombos,
                resolveImage = { null },
                logItEnabled = false,
                onLogIt = {},
                onSaveForLater = {},
                onRegenerate = {},
                aiEnabled = false,
                styleVibeLabel = "Smart Casual",
                onNavigateToSettings = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(showBackground = true, name = "Idle - Light")
@Composable
private fun IdleContentPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            IdleContent(
                onGetSuggestions = {},
                aiEnabled = false,
                styleVibeLabel = "Smart Casual",
                onNavigateToSettings = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(showBackground = true, name = "Error - Light")
@Composable
private fun ErrorContentPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ErrorContent(
                message = "Unable to reach the database. Check your device storage.",
                onRetry = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ─── Combo card ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "OutfitComboCard - Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "OutfitComboCard - Dark",
)
@Composable
private fun OutfitComboCardPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            OutfitComboCard(
                combo = previewCombo,
                resolveImage = { null },
                onLogIt = {},
                onSaveForLater = {},
                onRegenerate = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "OutfitComboCard - AI pick (Light)")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "OutfitComboCard - AI pick (Dark)",
)
@Composable
private fun OutfitComboCardAiPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            OutfitComboCard(
                combo = previewAiCombo,
                resolveImage = { null },
                onLogIt = {},
                onSaveForLater = {},
                onRegenerate = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

// ─── Occasion sheet ───────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Occasion Sheet - Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Occasion Sheet - Dark",
)
@Composable
private fun OccasionSheetPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            OccasionSheetContent(
                occasions = previewOccasions,
                onSelected = {},
                onSkip = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Occasion Sheet - Empty")
@Composable
private fun OccasionSheetEmptyPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            OccasionSheetContent(
                occasions = emptyList(),
                onSelected = {},
                onSkip = {},
            )
        }
    }
}

// ─── Weather sheet ────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Weather Sheet - Empty - Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Weather Sheet - Empty - Dark",
)
@Composable
private fun WeatherSheetEmptyPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            WeatherSheetContent(
                prefill = null,
                onConfirm = {},
                onSkip = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Weather Sheet - Autofilled - Light")
@Composable
private fun WeatherSheetAutofilledPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            WeatherSheetContent(
                prefill = WeatherConditions(
                    tempLowC = 12.0,
                    tempHighC = 19.0,
                    isRaining = false,
                    isWindy = true,
                ),
                onConfirm = {},
                onSkip = {},
            )
        }
    }
}
