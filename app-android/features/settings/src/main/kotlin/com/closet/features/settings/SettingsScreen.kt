package com.closet.features.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.ui.theme.ClosetAccent
import com.closet.core.ui.theme.ClosetTheme

/**
 * Settings screen entry point.
 *
 * Collects [SettingsViewModel.accent] and [SettingsViewModel.dynamicColor] and
 * delegates all rendering to [SettingsContent].
 *
 * @param onNavigateUp Called when the user taps the back arrow.
 * @param viewModel Hilt-provided [SettingsViewModel]; override in tests.
 */
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val accent by viewModel.accent.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()

    SettingsContent(
        currentAccent = accent,
        dynamicColor = dynamicColor,
        onSetAccent = viewModel::setAccent,
        onSetDynamicColor = viewModel::setDynamicColor,
        onNavigateUp = onNavigateUp,
    )
}

/**
 * Stateless rendering of the Settings screen.
 *
 * Layout: a [Scaffold] with a [CenterAlignedTopAppBar] and a [LazyColumn] body.
 * The Appearance section contains:
 * - A "Use system colors" [Switch] (shown only on Android 12+) that enables
 *   Material You dynamic color.
 * - An accent colour swatch row, disabled when dynamic color is active since
 *   it has no effect while Material You is in control.
 *
 * @param currentAccent The currently active [ClosetAccent].
 * @param dynamicColor Whether Material You dynamic color is enabled.
 * @param onSetAccent Invoked with the newly selected [ClosetAccent].
 * @param onSetDynamicColor Invoked with the new dynamic color enabled state.
 * @param onNavigateUp Called when the user taps the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    currentAccent: ClosetAccent,
    dynamicColor: Boolean,
    onSetAccent: (ClosetAccent) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_up),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    DynamicColorItem(
                        enabled = dynamicColor,
                        onCheckedChange = onSetDynamicColor,
                    )
                }
            }
            item {
                AccentColorItem(
                    currentAccent = currentAccent,
                    onSetAccent = onSetAccent,
                    enabled = !dynamicColor,
                )
            }
        }
    }
}

/**
 * Section heading styled with [MaterialTheme.typography.labelLarge] in
 * [MaterialTheme.colorScheme.primary], matching the Material 3 settings pattern.
 *
 * @param title The label to display above the section's preference items.
 */
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Settings list item for the Material You dynamic color toggle (Android 12+ only).
 *
 * Renders a [ListItem] with a [Switch] as trailing content. When enabled,
 * dynamic color takes over from the user-selected accent.
 *
 * @param enabled Whether dynamic color is currently on.
 * @param onCheckedChange Invoked with the new checked state when the switch is toggled.
 */
@Composable
private fun DynamicColorItem(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
        supportingContent = { Text(stringResource(R.string.settings_dynamic_color_summary)) },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

/**
 * Settings list item for the accent colour preference.
 *
 * Renders a [ListItem] with the preference label as the headline and a horizontal
 * row of [AccentSwatch]s as supporting content. When [enabled] is `false` (i.e.
 * dynamic color is active), the row is visually dimmed and swatches are
 * non-interactive.
 *
 * @param currentAccent The currently active [ClosetAccent].
 * @param onSetAccent Invoked with the newly selected [ClosetAccent].
 * @param enabled Whether the accent preference is interactive.
 */
@Composable
private fun AccentColorItem(
    currentAccent: ClosetAccent,
    onSetAccent: (ClosetAccent) -> Unit,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        modifier = Modifier.alpha(alpha),
        headlineContent = {
            Text(stringResource(R.string.settings_accent_color))
        },
        supportingContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ClosetAccent.entries.forEach { accent ->
                    AccentSwatch(
                        accent = accent,
                        selected = accent == currentAccent,
                        enabled = enabled,
                        onClick = { onSetAccent(accent) },
                    )
                }
            }
        },
    )
}

/**
 * A tappable circular swatch representing a single [ClosetAccent].
 *
 * Renders a 36 dp filled circle inside a 48 dp touch target (meeting M3 minimum).
 * When [selected], a 2 dp ring in [ClosetAccent.muted] is drawn between the touch
 * target boundary and the fill circle to indicate the active choice.
 *
 * Provides a full accessibility [contentDescription] via [semantics]:
 * "[accent name], selected" or "[accent name]".
 *
 * @param accent The accent whose [ClosetAccent.primary] colour fills the circle.
 * @param selected Whether this swatch represents the currently active accent.
 * @param enabled Whether the swatch is interactive; `false` when dynamic color is on.
 * @param onClick Invoked when the user taps the swatch.
 */
@Composable
private fun AccentSwatch(
    accent: ClosetAccent,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentLabel = accentLabel(accent)
    val cd = if (selected) {
        stringResource(R.string.settings_accent_selected, accentLabel)
    } else {
        stringResource(R.string.settings_accent_unselected, accentLabel)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = cd },
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(2.dp, accent.muted, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.primary),
        )
    }
}

/** Returns the localized display name for [accent]. */
@Composable
private fun accentLabel(accent: ClosetAccent): String = when (accent) {
    ClosetAccent.Amber -> stringResource(R.string.settings_accent_amber)
    ClosetAccent.Coral -> stringResource(R.string.settings_accent_coral)
    ClosetAccent.Sage -> stringResource(R.string.settings_accent_sage)
    ClosetAccent.Sky -> stringResource(R.string.settings_accent_sky)
    ClosetAccent.Lavender -> stringResource(R.string.settings_accent_lavender)
    ClosetAccent.Rose -> stringResource(R.string.settings_accent_rose)
}

@Preview(showBackground = true, name = "Accent active")
@Composable
private fun SettingsContentAccentPreview() {
    ClosetTheme {
        SettingsContent(
            currentAccent = ClosetAccent.Amber,
            dynamicColor = false,
            onSetAccent = {},
            onSetDynamicColor = {},
            onNavigateUp = {},
        )
    }
}

@Preview(showBackground = true, name = "Dynamic color active")
@Composable
private fun SettingsContentDynamicPreview() {
    ClosetTheme {
        SettingsContent(
            currentAccent = ClosetAccent.Sky,
            dynamicColor = true,
            onSetAccent = {},
            onSetDynamicColor = {},
            onNavigateUp = {},
        )
    }
}
