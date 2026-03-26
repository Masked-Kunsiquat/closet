package com.closet.features.recommendations

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.ui.theme.ClosetTheme
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.OutfitCombo
import com.closet.features.recommendations.ui.OccasionSheet
import com.closet.features.recommendations.ui.OutfitComboCard
import com.closet.features.recommendations.ui.WeatherSheet
import java.io.File

/**
 * Root composable for the outfit recommendations flow.
 *
 * Collects [RecommendationUiState] from [RecommendationViewModel] and delegates
 * rendering to state-specific content. Bottom sheets ([OccasionSheet],
 * [WeatherSheet]) are overlaid on top of the underlying content — they do not
 * replace it.
 *
 * ### One-shot events
 * - [logItEvent][RecommendationViewModel.logItEvent] — navigates to outfit logging
 *   with the combo's item IDs pre-loaded by calling [onNavigateToLog].
 * - [saveResult][RecommendationViewModel.saveResult] — shows a [SnackbarHost]
 *   confirmation ("Outfit saved") or error.
 *
 * @param onNavigateUp     Back-navigation lambda — passed in, not handled internally.
 * @param onNavigateToLog  Called with item IDs when the user taps "Log it". Pass null to
 *                         disable the "Log it" button until the log flow is wired up.
 * @param viewModel        Injected by Hilt; override only in tests.
 */
@Composable
fun RecommendationScreen(
    onNavigateUp: () -> Unit = {},
    onNavigateToLog: ((List<Long>) -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    viewModel: RecommendationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val occasions by viewModel.occasions.collectAsStateWithLifecycle()
    val aiEnabled by viewModel.aiEnabled.collectAsStateWithLifecycle()
    val styleVibeLabel by viewModel.styleVibeLabel.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── One-shot: "Log it" — navigate to log screen ──────────────────────────
    LaunchedEffect(viewModel) {
        viewModel.logItEvent.collect { itemIds ->
            onNavigateToLog?.invoke(itemIds)
        }
    }

    // ── One-shot: "Save for later" result — show snackbar ────────────────────
    val savedMessage = stringResource(R.string.recs_saved_confirmation)
    LaunchedEffect(viewModel) {
        viewModel.saveResult.collect { result ->
            val message = when (result) {
                is SaveResult.Saved -> savedMessage
                is SaveResult.Failed -> result.message
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Underlying content (always rendered) ─────────────────────────
            val contentState = when (uiState) {
                // When a sheet is open, show the content that was visible before
                is RecommendationUiState.OccasionSheet -> RecommendationUiState.Idle
                is RecommendationUiState.WeatherSheet -> RecommendationUiState.Idle
                else -> uiState
            }

            when (contentState) {
                RecommendationUiState.Idle -> IdleContent(
                    onGetSuggestions = viewModel::onGetSuggestionsClicked,
                    aiEnabled = aiEnabled,
                    styleVibeLabel = styleVibeLabel,
                    onNavigateToSettings = onNavigateToSettings,
                    modifier = Modifier.fillMaxSize(),
                )

                RecommendationUiState.Loading -> LoadingContent(
                    modifier = Modifier.fillMaxSize(),
                )

                is RecommendationUiState.Results -> ResultsContent(
                    combos = contentState.combos,
                    resolveImage = { path -> path?.let { viewModel.resolveImage(it) } },
                    logItEnabled = onNavigateToLog != null,
                    onLogIt = viewModel::onLogIt,
                    onSaveForLater = viewModel::onSaveForLater,
                    onRegenerate = viewModel::onRegenerate,
                    aiEnabled = aiEnabled,
                    styleVibeLabel = styleVibeLabel,
                    onNavigateToSettings = onNavigateToSettings,
                    modifier = Modifier.fillMaxSize(),
                )

                is RecommendationUiState.NoResults -> NoResultsContent(
                    onRegenerate = viewModel::onRegenerate,
                    modifier = Modifier.fillMaxSize(),
                )

                is RecommendationUiState.Error -> ErrorContent(
                    message = contentState.message,
                    onRetry = viewModel::onGetSuggestionsClicked,
                    modifier = Modifier.fillMaxSize(),
                )

                // Sheets are handled below — these cases are unreachable here
                is RecommendationUiState.OccasionSheet,
                is RecommendationUiState.WeatherSheet,
                -> Unit
            }

            // ── Sheets — overlaid, not full-screen replacements ───────────────
            when (val state = uiState) {
                is RecommendationUiState.OccasionSheet -> {
                    OccasionSheet(
                        occasions = occasions,
                        onSelected = viewModel::onOccasionSelected,
                        onSkip = viewModel::onOccasionSkipped,
                        onDismiss = viewModel::onDismiss,
                    )
                }

                is RecommendationUiState.WeatherSheet -> {
                    WeatherSheet(
                        prefill = state.prefill,
                        isAutofilled = state.prefill != null,
                        onConfirm = viewModel::onWeatherConfirmed,
                        onSkip = viewModel::onWeatherSkipped,
                        onDismiss = viewModel::onDismiss,
                    )
                }

                else -> Unit
            }
        }
    }
}

// ─── State content composables ─────────────────────────────────────────────────

/**
 * Shown in [RecommendationUiState.Idle]. Centered "Get Suggestions" button,
 * plus a style-vibe shortcut row when AI is enabled.
 */
@Composable
private fun IdleContent(
    onGetSuggestions: () -> Unit,
    aiEnabled: Boolean,
    styleVibeLabel: String,
    onNavigateToSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Button(onClick = onGetSuggestions) {
            Text(stringResource(R.string.recs_get_suggestions))
        }
        if (aiEnabled && onNavigateToSettings != null) {
            Spacer(modifier = Modifier.height(12.dp))
            StyleVibeShortcutRow(
                styleVibeLabel = styleVibeLabel,
                onNavigateToSettings = onNavigateToSettings,
            )
        }
    }
}

/**
 * Shown in [RecommendationUiState.Loading]. Centered progress indicator.
 */
@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
        )
    }
}

/**
 * Shown in [RecommendationUiState.Results]. Horizontal pager carousel with
 * dot indicators below, plus a style-vibe shortcut row when AI is enabled.
 */
@Composable
private fun ResultsContent(
    combos: List<OutfitCombo>,
    resolveImage: (String?) -> File?,
    logItEnabled: Boolean,
    onLogIt: (OutfitCombo) -> Unit,
    onSaveForLater: (OutfitCombo) -> Unit,
    onRegenerate: () -> Unit,
    aiEnabled: Boolean,
    styleVibeLabel: String,
    onNavigateToSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { combos.size })

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val combo = combos[page]
            OutfitComboCard(
                combo = combo,
                resolveImage = resolveImage,
                logItEnabled = logItEnabled,
                onLogIt = { onLogIt(combo) },
                onSaveForLater = { onSaveForLater(combo) },
                onRegenerate = onRegenerate,
            )
        }

        // ── Page indicator dots ───────────────────────────────────────────────
        PagerIndicator(
            pageCount = combos.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )

        // ── Style vibe shortcut row ───────────────────────────────────────────
        if (aiEnabled && onNavigateToSettings != null) {
            StyleVibeShortcutRow(
                styleVibeLabel = styleVibeLabel,
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

/**
 * Shown in [RecommendationUiState.NoResults]. The engine ran successfully but
 * couldn't form any valid combos — e.g. wardrobe too small, all items dirty/inactive.
 * Offers a Regenerate button in case the user wants to retry with different filters.
 */
@Composable
private fun NoResultsContent(
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.recs_no_results_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recs_no_results_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRegenerate) {
            Text(stringResource(R.string.recs_retry))
        }
    }
}

/**
 * Shown in [RecommendationUiState.Error]. Centered error message and retry button.
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.recs_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onRetry) {
            Text(stringResource(R.string.recs_retry))
        }
    }
}

/**
 * Small shortcut row shown when AI is enabled. Displays the active style vibe and
 * a "Change" text button that navigates to Settings so the user can update it.
 *
 * Appears in both [IdleContent] and [ResultsContent] — visibility is controlled by
 * the caller via [aiEnabled] and a non-null [onNavigateToSettings].
 */
@Composable
private fun StyleVibeShortcutRow(
    styleVibeLabel: String,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.recs_style_vibe_label, styleVibeLabel),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onNavigateToSettings) {
            Text(
                text = stringResource(R.string.recs_style_vibe_change),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * Row of dot indicators for a [HorizontalPager]. The active dot uses
 * [MaterialTheme.colorScheme.primary]; inactive dots use a muted variant.
 *
 * @param pageCount   Total number of pages.
 * @param currentPage Zero-based index of the currently visible page.
 */
@Composable
private fun PagerIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.recs_page_indicator_description, currentPage + 1, pageCount)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isActive) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    ),
            )
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

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
