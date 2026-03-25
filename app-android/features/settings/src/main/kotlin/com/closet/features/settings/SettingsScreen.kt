package com.closet.features.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.closet.core.data.model.AiProvider
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherService
import com.closet.core.ui.theme.ClosetAccent
import com.closet.core.ui.theme.ClosetTheme

/**
 * Settings screen entry point.
 *
 * Collects all [SettingsViewModel] state and delegates rendering to [SettingsContent].
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
    val weatherEnabled by viewModel.weatherEnabled.collectAsStateWithLifecycle()
    val weatherService by viewModel.weatherService.collectAsStateWithLifecycle()
    val googleApiKey by viewModel.googleApiKey.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    val aiEnabled by viewModel.aiEnabled.collectAsStateWithLifecycle()
    val selectedAiProvider by viewModel.selectedAiProvider.collectAsStateWithLifecycle()
    val nanoStatus by viewModel.nanoStatus.collectAsStateWithLifecycle()
    val openAiKey by viewModel.openAiKey.collectAsStateWithLifecycle()
    val openAiBaseUrl by viewModel.openAiBaseUrl.collectAsStateWithLifecycle()
    val openAiModel by viewModel.openAiModel.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = LocalActivity.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRationaleDialog by remember { mutableStateOf(false) }

    val deniedMessage = stringResource(R.string.settings_location_snackbar)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setWeatherEnabled(true)
        } else {
            val shouldShow = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            } ?: false
            if (shouldShow) {
                coroutineScope.launch { snackbarHostState.showSnackbar(deniedMessage) }
            } else {
                showRationaleDialog = true
            }
        }
    }

    val onWeatherToggle: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            viewModel.setWeatherEnabled(false)
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.setWeatherEnabled(true)
            } else {
                permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    if (showRationaleDialog) {
        PermissionRationaleDialog(
            onOpenSettings = {
                showRationaleDialog = false
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
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
        aiEnabled = aiEnabled,
        selectedAiProvider = selectedAiProvider,
        nanoStatus = nanoStatus,
        openAiKey = openAiKey,
        openAiBaseUrl = openAiBaseUrl,
        openAiModel = openAiModel,
        onAiToggled = viewModel::onAiToggled,
        onAiProviderSelected = viewModel::onAiProviderSelected,
        onOpenAiKeyChanged = viewModel::onOpenAiKeyChanged,
        onOpenAiBaseUrlChanged = viewModel::onOpenAiBaseUrlChanged,
        onOpenAiModelChanged = viewModel::onOpenAiModelChanged,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
    )
}

/**
 * Stateless rendering of the Settings screen.
 *
 * Sections:
 * - **Appearance**: dynamic color toggle (API 31+) + accent color picker.
 * - **Weather**: forecast toggle; when on — service picker, optional API key
 *   field (Google only), temperature unit picker, and a clear-cache action.
 * - **AI suggestions**: master toggle; when on — provider picker (Nano / OpenAI /
 *   Anthropic), provider-specific config fields.
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
    aiEnabled: Boolean,
    selectedAiProvider: AiProvider,
    nanoStatus: NanoStatus,
    openAiKey: String,
    openAiBaseUrl: String,
    openAiModel: String,
    onAiToggled: (Boolean) -> Unit,
    onAiProviderSelected: (AiProvider) -> Unit,
    onOpenAiKeyChanged: (String) -> Unit,
    onOpenAiBaseUrlChanged: (String) -> Unit,
    onOpenAiModelChanged: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            // ── Appearance ────────────────────────────────────────────────────
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

            // ── Weather ───────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_weather))
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

            // ── AI suggestions ────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_ai))
            }
            item {
                AiToggleItem(
                    enabled = aiEnabled,
                    onCheckedChange = onAiToggled,
                )
            }
            if (aiEnabled) {
                item {
                    AiProviderItem(
                        selected = selectedAiProvider,
                        onSelect = onAiProviderSelected,
                    )
                }
                when (selectedAiProvider) {
                    AiProvider.Nano -> item {
                        NanoStatusItem(status = nanoStatus)
                    }
                    AiProvider.OpenAi -> item {
                        OpenAiFieldsItem(
                            apiKey = openAiKey,
                            baseUrl = openAiBaseUrl,
                            model = openAiModel,
                            onApiKeyChanged = onOpenAiKeyChanged,
                            onBaseUrlChanged = onOpenAiBaseUrlChanged,
                            onModelChanged = onOpenAiModelChanged,
                        )
                    }
                    AiProvider.Anthropic -> {
                        // Anthropic provider not yet implemented — segment is greyed out,
                        // so this branch is unreachable in practice. No fields shown.
                    }
                }
            }
        }
    }
}

// ── Shared ────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
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
    ClosetAccent.Amber -> stringResource(R.string.settings_accent_amber)
    ClosetAccent.Coral -> stringResource(R.string.settings_accent_coral)
    ClosetAccent.Sage -> stringResource(R.string.settings_accent_sage)
    ClosetAccent.Sky -> stringResource(R.string.settings_accent_sky)
    ClosetAccent.Lavender -> stringResource(R.string.settings_accent_lavender)
    ClosetAccent.Rose -> stringResource(R.string.settings_accent_rose)
}

// ── Weather items ─────────────────────────────────────────────────────────────

@Composable
private fun WeatherToggleItem(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_weather_enabled)) },
        supportingContent = { Text(stringResource(R.string.settings_weather_enabled_summary)) },
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
        headlineContent = { Text(stringResource(R.string.settings_weather_service)) },
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
                label = { Text(stringResource(R.string.settings_weather_api_key)) },
                placeholder = { Text(stringResource(R.string.settings_weather_api_key_placeholder)) },
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
        headlineContent = { Text(stringResource(R.string.settings_temperature_unit)) },
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
        headlineContent = { Text(stringResource(R.string.settings_clear_cache)) },
        supportingContent = { Text(stringResource(R.string.settings_clear_cache_summary)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// ── AI items ──────────────────────────────────────────────────────────────────

/**
 * Master AI toggle. Default OFF.
 */
@Composable
private fun AiToggleItem(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_ai_enabled)) },
        supportingContent = { Text(stringResource(R.string.settings_ai_enabled_summary)) },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

/**
 * Provider picker: On-device (Nano) / OpenAI / Anthropic.
 *
 * The Anthropic segment is always [enabled = false] — [AnthropicProvider] is not yet
 * implemented. A "(coming soon)" note is embedded in its label string.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiProviderItem(
    selected: AiProvider,
    onSelect: (AiProvider) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_ai_provider)) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                AiProvider.entries.forEachIndexed { index, provider ->
                    val isAnthropic = provider == AiProvider.Anthropic
                    SegmentedButton(
                        selected = provider == selected,
                        onClick = { if (!isAnthropic) onSelect(provider) },
                        enabled = !isAnthropic,
                        shape = SegmentedButtonDefaults.itemShape(index, AiProvider.entries.size),
                        label = {
                            Text(
                                text = when (provider) {
                                    AiProvider.Nano -> stringResource(R.string.settings_ai_provider_nano)
                                    AiProvider.OpenAi -> stringResource(R.string.settings_ai_provider_openai)
                                    AiProvider.Anthropic -> stringResource(R.string.settings_ai_provider_anthropic)
                                },
                            )
                        },
                    )
                }
            }
        },
    )
}

/**
 * Status indicator shown below the provider picker when Nano is selected.
 *
 * - [NanoStatus.Idle] — nothing shown (init not yet started)
 * - [NanoStatus.Checking] — indeterminate [LinearProgressIndicator]
 * - [NanoStatus.Downloading] — determinate [LinearProgressIndicator] + percentage text
 * - [NanoStatus.Ready] — green checkmark + "Model ready"
 * - [NanoStatus.Failed] — error text
 * - [NanoStatus.NotSupported] — muted "Not supported on this device"
 */
@Composable
private fun NanoStatusItem(status: NanoStatus) {
    AnimatedVisibility(visible = status !is NanoStatus.Idle) {
        ListItem(
            headlineContent = {
                when (status) {
                    NanoStatus.Idle -> Unit
                    NanoStatus.Checking -> {
                        Column {
                            Text(
                                text = stringResource(R.string.settings_ai_nano_checking),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is NanoStatus.Downloading -> {
                        Column {
                            Text(
                                text = stringResource(R.string.settings_ai_nano_downloading, status.progressPct),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { status.progressPct / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    NanoStatus.Ready -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.settings_ai_nano_ready),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    is NanoStatus.Failed -> {
                        Text(
                            text = stringResource(R.string.settings_ai_nano_failed, status.message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    NanoStatus.NotSupported -> {
                        Text(
                            text = stringResource(R.string.settings_ai_nano_not_supported),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }
}

/**
 * OpenAI-compatible provider config fields:
 * - API key (password field with toggleable visibility)
 * - Base URL override (optional; hint for default)
 * - Model override (optional; hint for default)
 *
 * All changes persist on every keystroke via the provided callbacks.
 *
 * TODO: Keys are stored as plain strings in DataStore for now. Before shipping to
 *   production, migrate to EncryptedSharedPreferences or Android Keystore to avoid
 *   storing credentials in cleartext on-device.
 */
@Composable
private fun OpenAiFieldsItem(
    apiKey: String,
    baseUrl: String,
    model: String,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
) {
    var keyVisible by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // API key field with toggleable visibility
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChanged,
                    label = { Text(stringResource(R.string.settings_ai_openai_key)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_openai_key_placeholder)) },
                    singleLine = true,
                    visualTransformation = if (keyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    trailingIcon = {
                        val cd = stringResource(
                            if (keyVisible) R.string.settings_ai_openai_hide_key
                            else R.string.settings_ai_openai_show_key,
                        )
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = cd,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Base URL override
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChanged,
                    label = { Text(stringResource(R.string.settings_ai_openai_base_url)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_openai_base_url_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Model override
                OutlinedTextField(
                    value = model,
                    onValueChange = onModelChanged,
                    label = { Text(stringResource(R.string.settings_ai_openai_model)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_openai_model_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
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
        title = { Text(stringResource(R.string.settings_location_dialog_title)) },
        text = { Text(stringResource(R.string.settings_location_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.settings_location_dialog_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_location_dialog_cancel))
            }
        },
    )
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Appearance / weather off")
@Composable
private fun SettingsContentDefaultPreview() {
    ClosetTheme {
        SettingsContent(
            currentAccent = ClosetAccent.Amber,
            dynamicColor = false,
            onSetAccent = {},
            onSetDynamicColor = {},
            weatherEnabled = false,
            weatherService = WeatherService.OpenMeteo,
            googleApiKey = "",
            temperatureUnit = TemperatureUnit.Celsius,
            onWeatherEnabledChange = {},
            onWeatherServiceChange = {},
            onGoogleApiKeyChange = {},
            onTemperatureUnitChange = {},
            onClearCache = {},
            aiEnabled = false,
            selectedAiProvider = AiProvider.Nano,
            nanoStatus = NanoStatus.Idle,
            openAiKey = "",
            openAiBaseUrl = "",
            openAiModel = "",
            onAiToggled = {},
            onAiProviderSelected = {},
            onOpenAiKeyChanged = {},
            onOpenAiBaseUrlChanged = {},
            onOpenAiModelChanged = {},
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
        )
    }
}

@Preview(showBackground = true, name = "Weather on / Open-Meteo")
@Composable
private fun SettingsContentWeatherOpenMeteoPreview() {
    ClosetTheme {
        SettingsContent(
            currentAccent = ClosetAccent.Sky,
            dynamicColor = false,
            onSetAccent = {},
            onSetDynamicColor = {},
            weatherEnabled = true,
            weatherService = WeatherService.OpenMeteo,
            googleApiKey = "",
            temperatureUnit = TemperatureUnit.Celsius,
            onWeatherEnabledChange = {},
            onWeatherServiceChange = {},
            onGoogleApiKeyChange = {},
            onTemperatureUnitChange = {},
            onClearCache = {},
            aiEnabled = false,
            selectedAiProvider = AiProvider.Nano,
            nanoStatus = NanoStatus.Idle,
            openAiKey = "",
            openAiBaseUrl = "",
            openAiModel = "",
            onAiToggled = {},
            onAiProviderSelected = {},
            onOpenAiKeyChanged = {},
            onOpenAiBaseUrlChanged = {},
            onOpenAiModelChanged = {},
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
        )
    }
}

@Preview(showBackground = true, name = "Weather on / Google + API key")
@Composable
private fun SettingsContentWeatherGooglePreview() {
    ClosetTheme {
        SettingsContent(
            currentAccent = ClosetAccent.Coral,
            dynamicColor = false,
            onSetAccent = {},
            onSetDynamicColor = {},
            weatherEnabled = true,
            weatherService = WeatherService.Google,
            googleApiKey = "AIzaSy\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022",
            temperatureUnit = TemperatureUnit.Fahrenheit,
            onWeatherEnabledChange = {},
            onWeatherServiceChange = {},
            onGoogleApiKeyChange = {},
            onTemperatureUnitChange = {},
            onClearCache = {},
            aiEnabled = false,
            selectedAiProvider = AiProvider.Nano,
            nanoStatus = NanoStatus.Idle,
            openAiKey = "",
            openAiBaseUrl = "",
            openAiModel = "",
            onAiToggled = {},
            onAiProviderSelected = {},
            onOpenAiKeyChanged = {},
            onOpenAiBaseUrlChanged = {},
            onOpenAiModelChanged = {},
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
        )
    }
}

@Preview(showBackground = true, name = "AI on / Nano checking")
@Composable
private fun SettingsContentAiNanoCheckingPreview() {
    ClosetTheme {
        SettingsContent(
            currentAccent = ClosetAccent.Sage,
            dynamicColor = false,
            onSetAccent = {},
            onSetDynamicColor = {},
            weatherEnabled = false,
            weatherService = WeatherService.OpenMeteo,
            googleApiKey = "",
            temperatureUnit = TemperatureUnit.Celsius,
            onWeatherEnabledChange = {},
            onWeatherServiceChange = {},
            onGoogleApiKeyChange = {},
            onTemperatureUnitChange = {},
            onClearCache = {},
            aiEnabled = true,
            selectedAiProvider = AiProvider.Nano,
            nanoStatus = NanoStatus.Checking,
            openAiKey = "",
            openAiBaseUrl = "",
            openAiModel = "",
            onAiToggled = {},
            onAiProviderSelected = {},
            onOpenAiKeyChanged = {},
            onOpenAiBaseUrlChanged = {},
            onOpenAiModelChanged = {},
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
        )
    }
}

@Preview(showBackground = true, name = "AI on / Nano downloading 42%")
@Composable
private fun SettingsContentAiNanoDownloadingPreview() {
    ClosetTheme {
        SettingsContent(
            currentAccent = ClosetAccent.Sage,
            dynamicColor = false,
            onSetAccent = {},
            onSetDynamicColor = {},
            weatherEnabled = false,
            weatherService = WeatherService.OpenMeteo,
            googleApiKey = "",
            temperatureUnit = TemperatureUnit.Celsius,
            onWeatherEnabledChange = {},
            onWeatherServiceChange = {},
            onGoogleApiKeyChange = {},
            onTemperatureUnitChange = {},
            onClearCache = {},
            aiEnabled = true,
            selectedAiProvider = AiProvider.Nano,
            nanoStatus = NanoStatus.Downloading(42),
            openAiKey = "",
            openAiBaseUrl = "",
            openAiModel = "",
            onAiToggled = {},
            onAiProviderSelected = {},
            onOpenAiKeyChanged = {},
            onOpenAiBaseUrlChanged = {},
            onOpenAiModelChanged = {},
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
        )
    }
}

@Preview(showBackground = true, name = "AI on / Nano not supported")
@Composable
private fun SettingsContentAiNanoNotSupportedPreview() {
    ClosetTheme {
        SettingsContent(
            currentAccent = ClosetAccent.Sage,
            dynamicColor = false,
            onSetAccent = {},
            onSetDynamicColor = {},
            weatherEnabled = false,
            weatherService = WeatherService.OpenMeteo,
            googleApiKey = "",
            temperatureUnit = TemperatureUnit.Celsius,
            onWeatherEnabledChange = {},
            onWeatherServiceChange = {},
            onGoogleApiKeyChange = {},
            onTemperatureUnitChange = {},
            onClearCache = {},
            aiEnabled = true,
            selectedAiProvider = AiProvider.Nano,
            nanoStatus = NanoStatus.NotSupported,
            openAiKey = "",
            openAiBaseUrl = "",
            openAiModel = "",
            onAiToggled = {},
            onAiProviderSelected = {},
            onOpenAiKeyChanged = {},
            onOpenAiBaseUrlChanged = {},
            onOpenAiModelChanged = {},
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
        )
    }
}

@Preview(showBackground = true, name = "AI on / OpenAI fields")
@Composable
private fun SettingsContentAiOpenAiPreview() {
    ClosetTheme {
        SettingsContent(
            currentAccent = ClosetAccent.Lavender,
            dynamicColor = false,
            onSetAccent = {},
            onSetDynamicColor = {},
            weatherEnabled = false,
            weatherService = WeatherService.OpenMeteo,
            googleApiKey = "",
            temperatureUnit = TemperatureUnit.Celsius,
            onWeatherEnabledChange = {},
            onWeatherServiceChange = {},
            onGoogleApiKeyChange = {},
            onTemperatureUnitChange = {},
            onClearCache = {},
            aiEnabled = true,
            selectedAiProvider = AiProvider.OpenAi,
            nanoStatus = NanoStatus.Idle,
            openAiKey = "sk-\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022",
            openAiBaseUrl = "",
            openAiModel = "gpt-4o",
            onAiToggled = {},
            onAiProviderSelected = {},
            onOpenAiKeyChanged = {},
            onOpenAiBaseUrlChanged = {},
            onOpenAiModelChanged = {},
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
        )
    }
}
