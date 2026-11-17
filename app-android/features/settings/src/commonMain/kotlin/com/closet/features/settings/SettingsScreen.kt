package com.closet.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherService
import com.closet.core.ui.resources.ic_symbol_arrow_back
import com.closet.core.ui.resources.ic_symbol_chevron_right
import com.closet.core.ui.theme.ClosetAccent
import com.closet.core.ui.theme.supportsDynamicColor
import com.closet.features.settings.resources.settings_accent_amber
import com.closet.features.settings.resources.settings_accent_color
import com.closet.features.settings.resources.settings_accent_coral
import com.closet.features.settings.resources.settings_accent_lavender
import com.closet.features.settings.resources.settings_accent_rose
import com.closet.features.settings.resources.settings_accent_sage
import com.closet.features.settings.resources.settings_accent_selected
import com.closet.features.settings.resources.settings_accent_sky
import com.closet.features.settings.resources.settings_accent_unselected
import com.closet.features.settings.resources.settings_ai_tools_navigate
import com.closet.features.settings.resources.settings_ai_tools_summary
import com.closet.features.settings.resources.settings_backup_navigate
import com.closet.features.settings.resources.settings_backup_summary
import com.closet.features.settings.resources.settings_clear_cache
import com.closet.features.settings.resources.settings_clear_cache_summary
import com.closet.features.settings.resources.settings_dynamic_color
import com.closet.features.settings.resources.settings_dynamic_color_summary
import com.closet.features.settings.resources.settings_location_dialog_cancel
import com.closet.features.settings.resources.settings_location_dialog_message
import com.closet.features.settings.resources.settings_location_dialog_open_settings
import com.closet.features.settings.resources.settings_location_dialog_title
import com.closet.features.settings.resources.settings_location_snackbar
import com.closet.features.settings.resources.settings_navigate_up
import com.closet.features.settings.resources.settings_section_ai_tools
import com.closet.features.settings.resources.settings_section_appearance
import com.closet.features.settings.resources.settings_section_backup
import com.closet.features.settings.resources.settings_section_wardrobe
import com.closet.features.settings.resources.settings_section_weather
import com.closet.features.settings.resources.settings_temperature_unit
import com.closet.features.settings.resources.settings_title
import com.closet.features.settings.resources.settings_wardrobe_laundry_day
import com.closet.features.settings.resources.settings_wardrobe_laundry_day_summary
import com.closet.features.settings.resources.settings_weather_api_key
import com.closet.features.settings.resources.settings_weather_api_key_placeholder
import com.closet.features.settings.resources.settings_weather_enabled
import com.closet.features.settings.resources.settings_weather_enabled_summary
import com.closet.features.settings.resources.settings_weather_service
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.closet.core.ui.resources.Res as CoreRes
import com.closet.features.settings.resources.Res

/**
 * Settings screen entry point.
 *
 * Collects all [SettingsViewModel] state and delegates rendering to [SettingsContent].
 *
 * @param onNavigateUp Called when the user taps the back arrow.
 * @param viewModel Koin-provided [SettingsViewModel]; override in tests.
 */
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onNavigateToBulkWash: () -> Unit = {},
    onNavigateToAiSettings: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = uiState.accent
    val dynamicColor = uiState.dynamicColor
    val weatherEnabled = uiState.weatherEnabled
    val weatherService = uiState.weatherService
    val googleApiKey = uiState.googleApiKey
    val temperatureUnit = uiState.temperatureUnit

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRationaleDialog by remember { mutableStateOf(false) }

    val deniedMessage = stringResource(Res.string.settings_location_snackbar)
    val openAppSettings = rememberOpenAppSettings()

    val enableWeather = rememberWeatherEnabler(
        onEnabled = { viewModel.setWeatherEnabled(true) },
        onDeniedCanAskAgain = {
            coroutineScope.launch { snackbarHostState.showSnackbar(deniedMessage) }
        },
        onDeniedPermanently = { showRationaleDialog = true },
    )

    val onWeatherToggle: (Boolean) -> Unit = { enabled ->
        if (!enabled) viewModel.setWeatherEnabled(false) else enableWeather()
    }

    if (showRationaleDialog) {
        PermissionRationaleDialog(
            onOpenSettings = {
                showRationaleDialog = false
                openAppSettings()
            },
            onDismiss = { showRationaleDialog = false },
        )
    }

    SettingsContent(
        currentAccent = accent,
        dynamicColor = dynamicColor,
        onSetAccent = viewModel::setAccent,
        onSetDynamicColor = viewModel::setDynamicColor,
        weatherEnabled = weatherEnabled,
        weatherService = weatherService,
        googleApiKey = googleApiKey,
        temperatureUnit = temperatureUnit,
        onWeatherEnabledChange = onWeatherToggle,
        onWeatherServiceChange = viewModel::setWeatherService,
        onGoogleApiKeyChange = viewModel::setGoogleApiKey,
        onTemperatureUnitChange = viewModel::setTemperatureUnit,
        onClearCache = viewModel::clearForecastCache,
        onNavigateToAiSettings = onNavigateToAiSettings,
        onNavigateToBackup = onNavigateToBackup,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onNavigateToBulkWash = onNavigateToBulkWash,
    )
}

/**
 * Stateless rendering of the Settings screen.
 *
 * Sections:
 * - **Appearance**: dynamic color toggle (API 31+) + accent color picker.
 * - **Weather**: forecast toggle; when on — service picker, optional API key
 *   field (Google only), temperature unit picker, and a clear-cache action.
 * - **Wardrobe**: laundry day shortcut.
 * - **AI & Image Tools**: single navigable row — opens [AiSettingsScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    currentAccent: ClosetAccent,
    dynamicColor: Boolean,
    onSetAccent: (ClosetAccent) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    weatherEnabled: Boolean,
    weatherService: WeatherService,
    googleApiKey: String,
    temperatureUnit: TemperatureUnit,
    onWeatherEnabledChange: (Boolean) -> Unit,
    onWeatherServiceChange: (WeatherService) -> Unit,
    onGoogleApiKeyChange: (String) -> Unit,
    onTemperatureUnitChange: (TemperatureUnit) -> Unit,
    onClearCache: () -> Unit,
    onNavigateToAiSettings: () -> Unit,
    onNavigateToBackup: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onNavigateToBulkWash: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            painter = painterResource(CoreRes.drawable.ic_symbol_arrow_back),
                            contentDescription = stringResource(Res.string.settings_navigate_up),
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
            // ── Appearance ────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(Res.string.settings_section_appearance))
            }
            if (supportsDynamicColor) {
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

            // ── Weather ───────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(Res.string.settings_section_weather))
            }
            item {
                WeatherToggleItem(
                    enabled = weatherEnabled,
                    onCheckedChange = onWeatherEnabledChange,
                )
            }
            if (weatherEnabled) {
                item {
                    WeatherServiceItem(
                        selected = weatherService,
                        onSelect = onWeatherServiceChange,
                    )
                }
                if (weatherService == WeatherService.Google) {
                    item {
                        ApiKeyItem(
                            apiKey = googleApiKey,
                            onApiKeyChange = onGoogleApiKeyChange,
                        )
                    }
                }
                item {
                    TemperatureUnitItem(
                        selected = temperatureUnit,
                        onSelect = onTemperatureUnitChange,
                    )
                }
            }
            item {
                ClearCacheItem(onClick = onClearCache)
            }

            // ── Backup & Restore ──────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(Res.string.settings_section_backup))
            }
            item {
                BackupNavItem(onClick = onNavigateToBackup)
            }

            // ── Wardrobe ──────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(Res.string.settings_section_wardrobe))
            }
            item {
                LaundryDayItem(onClick = onNavigateToBulkWash)
            }

            // ── AI & Image Tools ──────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(Res.string.settings_section_ai_tools))
            }
            item {
                AiToolsNavItem(onClick = onNavigateToAiSettings)
            }
        }
    }
}

// ── Shared ────────────────────────────────────────────────────────────────────

@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// ── Appearance items ──────────────────────────────────────────────────────────

@Composable
private fun DynamicColorItem(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_dynamic_color)) },
        supportingContent = { Text(stringResource(Res.string.settings_dynamic_color_summary)) },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

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
            Text(stringResource(Res.string.settings_accent_color))
        },
        supportingContent = {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
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
        stringResource(Res.string.settings_accent_selected, accentLabel)
    } else {
        stringResource(Res.string.settings_accent_unselected, accentLabel)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = cd; role = Role.Button },
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

@Composable
private fun accentLabel(accent: ClosetAccent): String = when (accent) {
    ClosetAccent.Amber -> stringResource(Res.string.settings_accent_amber)
    ClosetAccent.Coral -> stringResource(Res.string.settings_accent_coral)
    ClosetAccent.Sage -> stringResource(Res.string.settings_accent_sage)
    ClosetAccent.Sky -> stringResource(Res.string.settings_accent_sky)
    ClosetAccent.Lavender -> stringResource(Res.string.settings_accent_lavender)
    ClosetAccent.Rose -> stringResource(Res.string.settings_accent_rose)
}

// ── Weather items ─────────────────────────────────────────────────────────────

@Composable
private fun WeatherToggleItem(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_weather_enabled)) },
        supportingContent = { Text(stringResource(Res.string.settings_weather_enabled_summary)) },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

/**
 * Service picker using a [SingleChoiceSegmentedButtonRow].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherServiceItem(
    selected: WeatherService,
    onSelect: (WeatherService) -> Unit,
) {
    val services = WeatherService.entries
    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_weather_service)) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                services.forEachIndexed { index, service ->
                    SegmentedButton(
                        selected = service == selected,
                        onClick = { onSelect(service) },
                        shape = SegmentedButtonDefaults.itemShape(index, services.size),
                        label = { Text(service.label) },
                    )
                }
            }
        },
    )
}

/**
 * Google API key input. Uses [KeyboardType.Password] to suppress autofill
 * suggestions, but text is kept visible so the user can verify the key.
 */
@Composable
private fun ApiKeyItem(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
) {
    ListItem(
        headlineContent = {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text(stringResource(Res.string.settings_weather_api_key)) },
                placeholder = { Text(stringResource(Res.string.settings_weather_api_key_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/**
 * Temperature unit picker (°C / °F).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemperatureUnitItem(
    selected: TemperatureUnit,
    onSelect: (TemperatureUnit) -> Unit,
) {
    val units = TemperatureUnit.entries
    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_temperature_unit)) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 8.dp)) {
                units.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = unit == selected,
                        onClick = { onSelect(unit) },
                        shape = SegmentedButtonDefaults.itemShape(index, units.size),
                        label = { Text(unit.label) },
                    )
                }
            }
        },
    )
}

/** Tappable list item that clears the cached forecast from DataStore. */
@Composable
private fun ClearCacheItem(onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_clear_cache)) },
        supportingContent = { Text(stringResource(Res.string.settings_clear_cache_summary)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// ── Wardrobe items + AI nav ───────────────────────────────────────────────────

@Composable
private fun LaundryDayItem(onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_wardrobe_laundry_day)) },
        supportingContent = { Text(stringResource(Res.string.settings_wardrobe_laundry_day_summary)) },
        trailingContent = {
            Icon(
                painter = painterResource(CoreRes.drawable.ic_symbol_chevron_right),
                contentDescription = null,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun BackupNavItem(onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_section_backup)) },
        supportingContent = { Text(stringResource(Res.string.settings_backup_summary)) },
        trailingContent = {
            Icon(
                painter = painterResource(CoreRes.drawable.ic_symbol_chevron_right),
                contentDescription = stringResource(Res.string.settings_backup_navigate),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun AiToolsNavItem(onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_section_ai_tools)) },
        supportingContent = { Text(stringResource(Res.string.settings_ai_tools_summary)) },
        trailingContent = {
            Icon(
                painter = painterResource(CoreRes.drawable.ic_symbol_chevron_right),
                contentDescription = stringResource(Res.string.settings_ai_tools_navigate),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

/**
 * Shown when the user has permanently denied [ACCESS_COARSE_LOCATION].
 * Explains why the permission is needed and offers a direct link to system
 * app settings so the user can grant it without navigating manually.
 */
@Composable
private fun PermissionRationaleDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_location_dialog_title)) },
        text = { Text(stringResource(Res.string.settings_location_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(Res.string.settings_location_dialog_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_location_dialog_cancel))
            }
        },
    )
}
