package com.closet.features.settings

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.closet.core.data.model.AiProvider
import com.closet.core.data.model.StyleVibe
import com.closet.core.data.worker.BatchSegmentationWork
import com.closet.core.data.worker.EmbeddingWork
import com.closet.core.ui.theme.ClosetTheme

// ─── Entry point ──────────────────────────────────────────────────────────────

@Composable
fun AiSettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val aiEnabled by viewModel.aiEnabled.collectAsStateWithLifecycle()
    val selectedAiProvider by viewModel.selectedAiProvider.collectAsStateWithLifecycle()
    val nanoStatus by viewModel.nanoStatus.collectAsStateWithLifecycle()
    val openAiKey by viewModel.openAiKey.collectAsStateWithLifecycle()
    val openAiBaseUrl by viewModel.openAiBaseUrl.collectAsStateWithLifecycle()
    val openAiModel by viewModel.openAiModel.collectAsStateWithLifecycle()
    val styleVibe by viewModel.styleVibe.collectAsStateWithLifecycle()
    val anthropicKey by viewModel.anthropicKey.collectAsStateWithLifecycle()
    val anthropicModel by viewModel.anthropicModel.collectAsStateWithLifecycle()
    val geminiKey by viewModel.geminiKey.collectAsStateWithLifecycle()
    val geminiModel by viewModel.geminiModel.collectAsStateWithLifecycle()
    val openAiModels by viewModel.openAiModels.collectAsStateWithLifecycle()
    val openAiModelsLoading by viewModel.openAiModelsLoading.collectAsStateWithLifecycle()
    val anthropicModels by viewModel.anthropicModels.collectAsStateWithLifecycle()
    val anthropicModelsLoading by viewModel.anthropicModelsLoading.collectAsStateWithLifecycle()

    val embeddingWorkInfo by viewModel.embeddingWorkInfo.collectAsStateWithLifecycle()
    val embeddingIndexSize by viewModel.embeddingIndexSize.collectAsStateWithLifecycle()

    val segmentationSupported = viewModel.segmentationSupported
    val segmentationEligibleCount by viewModel.segmentationEligibleCount.collectAsStateWithLifecycle()
    val batchSegWorkInfo by viewModel.batchSegWorkInfo.collectAsStateWithLifecycle()

    val captionSupported = viewModel.captionSupported
    val captionEligibleCount by viewModel.captionEligibleCount.collectAsStateWithLifecycle()
    val batchCaptionProgress by viewModel.batchCaptionProgress.collectAsStateWithLifecycle()
    val captionResult by viewModel.captionResult.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    var nanoNotSupportedDismissed by remember { mutableStateOf(false) }
    val lastHandledBatchId by viewModel.lastHandledBatchId.collectAsStateWithLifecycle()

    LaunchedEffect(batchSegWorkInfo?.id, batchSegWorkInfo?.state) {
        val info = batchSegWorkInfo ?: return@LaunchedEffect
        if (info.state == WorkInfo.State.SUCCEEDED && info.id != lastHandledBatchId) {
            viewModel.onBatchResultHandled(info.id)
            val done = info.outputData.getInt(BatchSegmentationWork.KEY_DONE, 0)
            val failed = info.outputData.getInt(BatchSegmentationWork.KEY_FAILED, 0)
            val msg = if (failed > 0) {
                context.resources.getQuantityString(
                    R.plurals.settings_wardrobe_batch_result_with_failures, done, done, failed,
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.settings_wardrobe_batch_result, done, done,
                )
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(batchCaptionProgress) {
        view.keepScreenOn = batchCaptionProgress != null
    }
    DisposableEffect(Unit) {
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(captionResult) {
        val result = captionResult ?: return@LaunchedEffect
        viewModel.onCaptionResultConsumed()
        val msg = if (result.failed > 0) {
            context.resources.getQuantityString(
                R.plurals.settings_wardrobe_caption_result_with_failures, result.done, result.done, result.failed,
            )
        } else {
            context.resources.getQuantityString(
                R.plurals.settings_wardrobe_caption_result, result.done, result.done,
            )
        }
        snackbarHostState.showSnackbar(msg)
    }

    if (nanoStatus !is NanoStatus.NotSupported) {
        nanoNotSupportedDismissed = false
    }
    if (nanoStatus is NanoStatus.NotSupported && !nanoNotSupportedDismissed) {
        NanoNotSupportedDialog(
            onSwitchToOpenAi = {
                nanoNotSupportedDismissed = true
                viewModel.onAiProviderSelected(AiProvider.OpenAi)
                viewModel.onAiToggled(true)
            },
            onSwitchToAnthropic = {
                nanoNotSupportedDismissed = true
                viewModel.onAiProviderSelected(AiProvider.Anthropic)
                viewModel.onAiToggled(true)
            },
            onSwitchToGemini = {
                nanoNotSupportedDismissed = true
                viewModel.onAiProviderSelected(AiProvider.Gemini)
                viewModel.onAiToggled(true)
            },
            onDismiss = { nanoNotSupportedDismissed = true },
        )
    }

    AiSettingsContent(
        aiEnabled = aiEnabled,
        onAiToggled = viewModel::onAiToggled,
        styleVibe = styleVibe,
        onStyleVibeSelected = viewModel::onStyleVibeSelected,
        selectedAiProvider = selectedAiProvider,
        onAiProviderSelected = viewModel::onAiProviderSelected,
        nanoStatus = nanoStatus,
        openAiKey = openAiKey,
        openAiBaseUrl = openAiBaseUrl,
        openAiModel = openAiModel,
        onOpenAiKeyChanged = viewModel::onOpenAiKeyChanged,
        onOpenAiBaseUrlChanged = viewModel::onOpenAiBaseUrlChanged,
        onOpenAiModelChanged = viewModel::onOpenAiModelChanged,
        anthropicKey = anthropicKey,
        anthropicModel = anthropicModel,
        onAnthropicKeyChanged = viewModel::onAnthropicKeyChanged,
        onAnthropicModelChanged = viewModel::onAnthropicModelChanged,
        geminiKey = geminiKey,
        geminiModel = geminiModel,
        onGeminiKeyChanged = viewModel::onGeminiKeyChanged,
        onGeminiModelChanged = viewModel::onGeminiModelChanged,
        openAiModels = openAiModels,
        openAiModelsLoading = openAiModelsLoading,
        anthropicModels = anthropicModels,
        anthropicModelsLoading = anthropicModelsLoading,
        embeddingWorkInfo = embeddingWorkInfo,
        embeddingIndexSize = embeddingIndexSize,
        onRebuildIndex = viewModel::triggerEmbeddingRebuild,
        segmentationSupported = segmentationSupported,
        segmentationEligibleCount = segmentationEligibleCount,
        batchSegWorkInfo = batchSegWorkInfo,
        onStartBatchSegmentation = viewModel::startBatchSegmentation,
        captionSupported = captionSupported,
        captionEligibleCount = captionEligibleCount,
        batchCaptionProgress = batchCaptionProgress,
        onStartBatchEnrichment = viewModel::startBatchEnrichment,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
    )
}

// ─── Content (stateless for previews) ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSettingsContent(
    aiEnabled: Boolean,
    onAiToggled: (Boolean) -> Unit,
    styleVibe: StyleVibe,
    onStyleVibeSelected: (StyleVibe) -> Unit,
    selectedAiProvider: AiProvider,
    onAiProviderSelected: (AiProvider) -> Unit,
    nanoStatus: NanoStatus,
    openAiKey: String,
    openAiBaseUrl: String,
    openAiModel: String,
    onOpenAiKeyChanged: (String) -> Unit,
    onOpenAiBaseUrlChanged: (String) -> Unit,
    onOpenAiModelChanged: (String) -> Unit,
    anthropicKey: String,
    anthropicModel: String,
    onAnthropicKeyChanged: (String) -> Unit,
    onAnthropicModelChanged: (String) -> Unit,
    geminiKey: String,
    geminiModel: String,
    onGeminiKeyChanged: (String) -> Unit,
    onGeminiModelChanged: (String) -> Unit,
    openAiModels: List<String>,
    openAiModelsLoading: Boolean,
    anthropicModels: List<String>,
    anthropicModelsLoading: Boolean,
    embeddingWorkInfo: WorkInfo?,
    embeddingIndexSize: Int,
    onRebuildIndex: () -> Unit,
    segmentationSupported: Boolean,
    segmentationEligibleCount: Int,
    batchSegWorkInfo: WorkInfo?,
    onStartBatchSegmentation: () -> Unit,
    captionSupported: Boolean,
    captionEligibleCount: Int,
    batchCaptionProgress: BatchCaptionProgress?,
    onStartBatchEnrichment: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_ai_screen_title)) },
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
            // ── Assistant ─────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(R.string.settings_ai_subsection_assistant))
            }
            item {
                AiToggleItem(
                    enabled = aiEnabled,
                    onCheckedChange = onAiToggled,
                )
            }
            if (aiEnabled) {
                item {
                    StyleVibeItem(
                        selected = styleVibe,
                        onSelect = onStyleVibeSelected,
                    )
                }
            }

            // ── Provider ──────────────────────────────────────────────────────
            if (aiEnabled) {
                item {
                    SettingsSectionHeader(stringResource(R.string.settings_ai_subsection_provider))
                }
                item {
                    AiProviderItem(
                        selected = selectedAiProvider,
                        onSelect = onAiProviderSelected,
                    )
                }
                when (selectedAiProvider) {
                    AiProvider.Nano -> item { NanoStatusItem(status = nanoStatus) }
                    AiProvider.OpenAi -> item {
                        OpenAiFieldsItem(
                            apiKey = openAiKey,
                            baseUrl = openAiBaseUrl,
                            model = openAiModel,
                            models = openAiModels,
                            modelsLoading = openAiModelsLoading,
                            onApiKeyChanged = onOpenAiKeyChanged,
                            onBaseUrlChanged = onOpenAiBaseUrlChanged,
                            onModelChanged = onOpenAiModelChanged,
                        )
                    }
                    AiProvider.Anthropic -> item {
                        AnthropicFieldsItem(
                            apiKey = anthropicKey,
                            model = anthropicModel,
                            models = anthropicModels,
                            modelsLoading = anthropicModelsLoading,
                            onApiKeyChanged = onAnthropicKeyChanged,
                            onModelChanged = onAnthropicModelChanged,
                        )
                    }
                    AiProvider.Gemini -> item {
                        GeminiFieldsItem(
                            apiKey = geminiKey,
                            model = geminiModel,
                            onApiKeyChanged = onGeminiKeyChanged,
                            onModelChanged = onGeminiModelChanged,
                        )
                    }
                }
            }

            // ── Wardrobe Index ────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(R.string.settings_ai_subsection_index))
            }
            item {
                WardrobeIndexItem(
                    workInfo = embeddingWorkInfo,
                    indexSize = embeddingIndexSize,
                    onRebuild = onRebuildIndex,
                )
            }

            // ── Image Enrichment ──────────────────────────────────────────────
            if (segmentationSupported || captionSupported) {
                item {
                    SettingsSectionHeader(stringResource(R.string.settings_ai_subsection_enrichment))
                }
                if (segmentationSupported) {
                    item {
                        BatchSegmentationItem(
                            eligibleCount = segmentationEligibleCount,
                            workInfo = batchSegWorkInfo,
                            onStart = onStartBatchSegmentation,
                        )
                    }
                }
                if (captionSupported) {
                    item {
                        BatchCaptionItem(
                            eligibleCount = captionEligibleCount,
                            progress = batchCaptionProgress,
                            onStart = onStartBatchEnrichment,
                        )
                    }
                }
            }
        }
    }
}

// ── Wardrobe Index item ────────────────────────────────────────────────────────

@Composable
private fun WardrobeIndexItem(
    workInfo: WorkInfo?,
    indexSize: Int,
    onRebuild: () -> Unit,
) {
    val isRunning = workInfo?.state == WorkInfo.State.RUNNING ||
        workInfo?.state == WorkInfo.State.ENQUEUED

    if (isRunning) {
        val done = workInfo!!.progress.getInt(EmbeddingWork.KEY_DONE, 0)
        val total = workInfo.progress.getInt(EmbeddingWork.KEY_TOTAL, 0)
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.settings_ai_index_building))
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.settings_ai_index_progress, done, total),
                    )
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    } else {
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.settings_ai_index_title))
            },
            supportingContent = {
                Text(
                    if (indexSize > 0) {
                        pluralStringResource(R.plurals.settings_ai_index_size, indexSize, indexSize)
                    } else {
                        stringResource(R.string.settings_ai_index_empty)
                    },
                )
            },
            trailingContent = {
                Button(
                    onClick = onRebuild,
                    enabled = !isRunning,
                ) {
                    Text(stringResource(R.string.settings_ai_index_rebuild))
                }
            },
        )
    }
}

// ── AI toggle ─────────────────────────────────────────────────────────────────

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

// ── Style vibe ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleVibeItem(
    selected: StyleVibe,
    onSelect: (StyleVibe) -> Unit,
) {
    val vibes = StyleVibe.entries
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_ai_style_vibe)) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
            ) {
                vibes.forEachIndexed { index, vibe ->
                    SegmentedButton(
                        selected = vibe == selected,
                        onClick = { onSelect(vibe) },
                        shape = SegmentedButtonDefaults.itemShape(index, vibes.size),
                        label = { Text(vibe.label) },
                    )
                }
            }
        },
    )
}

// ── Provider picker ────────────────────────────────────────────────────────────

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
                    SegmentedButton(
                        selected = provider == selected,
                        onClick = { onSelect(provider) },
                        shape = SegmentedButtonDefaults.itemShape(index, AiProvider.entries.size),
                        label = {
                            Text(
                                text = when (provider) {
                                    AiProvider.Nano -> stringResource(R.string.settings_ai_provider_nano)
                                    AiProvider.OpenAi -> stringResource(R.string.settings_ai_provider_openai)
                                    AiProvider.Anthropic -> stringResource(R.string.settings_ai_provider_anthropic)
                                    AiProvider.Gemini -> stringResource(R.string.settings_ai_provider_gemini)
                                },
                            )
                        },
                    )
                }
            }
        },
    )
}

// ── Nano status ────────────────────────────────────────────────────────────────

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
                                text = stringResource(
                                    R.string.settings_ai_nano_downloading,
                                    status.progressPct,
                                ),
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

// ── OpenAI-compatible fields ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenAiFieldsItem(
    apiKey: String,
    baseUrl: String,
    model: String,
    models: List<String>,
    modelsLoading: Boolean,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
) {
    var keyVisible by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChanged,
                    label = { Text(stringResource(R.string.settings_ai_openai_key)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_openai_key_placeholder)) },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                                imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = cd,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
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
                UrlPresetChips(
                    presets = OPENAI_URL_PRESETS,
                    currentUrl = baseUrl,
                    onSelect = onBaseUrlChanged,
                )
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded && models.isNotEmpty(),
                    onExpandedChange = { if (models.isNotEmpty()) modelDropdownExpanded = it },
                ) {
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
                        trailingIcon = {
                            if (modelsLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else if (models.isNotEmpty()) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded && models.isNotEmpty(),
                        onDismissRequest = { modelDropdownExpanded = false },
                        modifier = Modifier.heightIn(max = 280.dp),
                    ) {
                        models.forEach { modelId ->
                            DropdownMenuItem(
                                text = { Text(modelId, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    onModelChanged(modelId)
                                    modelDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

// ── Anthropic fields ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnthropicFieldsItem(
    apiKey: String,
    model: String,
    models: List<String>,
    modelsLoading: Boolean,
    onApiKeyChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
) {
    var keyVisible by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChanged,
                    label = { Text(stringResource(R.string.settings_ai_anthropic_key)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_anthropic_key_placeholder)) },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    trailingIcon = {
                        val cd = stringResource(
                            if (keyVisible) R.string.settings_ai_anthropic_hide_key
                            else R.string.settings_ai_anthropic_show_key,
                        )
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = cd,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded && models.isNotEmpty(),
                    onExpandedChange = { if (models.isNotEmpty()) modelDropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = onModelChanged,
                        label = { Text(stringResource(R.string.settings_ai_anthropic_model)) },
                        placeholder = { Text(stringResource(R.string.settings_ai_anthropic_model_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                        trailingIcon = {
                            if (modelsLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else if (models.isNotEmpty()) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded && models.isNotEmpty(),
                        onDismissRequest = { modelDropdownExpanded = false },
                        modifier = Modifier.heightIn(max = 280.dp),
                    ) {
                        models.forEach { modelId ->
                            DropdownMenuItem(
                                text = { Text(modelId, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    onModelChanged(modelId)
                                    modelDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

// ── Gemini fields ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiFieldsItem(
    apiKey: String,
    model: String,
    onApiKeyChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
) {
    var keyVisible by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChanged,
                    label = { Text(stringResource(R.string.settings_ai_gemini_key)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_gemini_key_placeholder)) },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    trailingIcon = {
                        val cd = stringResource(
                            if (keyVisible) R.string.settings_ai_gemini_hide_key
                            else R.string.settings_ai_gemini_show_key,
                        )
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = cd,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = onModelChanged,
                    label = { Text(stringResource(R.string.settings_ai_gemini_model)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_gemini_model_placeholder)) },
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

// ── URL preset chips ───────────────────────────────────────────────────────────

@Composable
private fun UrlPresetChips(
    presets: List<Pair<String, String>>,
    currentUrl: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { (label, url) ->
            SuggestionChip(
                onClick = { onSelect(url) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = if (currentUrl == url) SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) else SuggestionChipDefaults.suggestionChipColors(),
            )
        }
    }
}

private val OPENAI_URL_PRESETS = listOf(
    "OpenAI" to "https://api.openai.com",
    "Groq" to "https://api.groq.com/openai",
    "Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai",
    "Mistral" to "https://api.mistral.ai",
    "Ollama (Emulator)" to "http://10.0.2.2:11434",
)

// ── Batch segmentation item ────────────────────────────────────────────────────

@Composable
private fun BatchSegmentationItem(
    eligibleCount: Int,
    workInfo: WorkInfo?,
    onStart: () -> Unit,
) {
    val isRunning = workInfo?.state == WorkInfo.State.RUNNING ||
        workInfo?.state == WorkInfo.State.ENQUEUED
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.settings_wardrobe_batch_confirm_title)) },
            text = { Text(stringResource(R.string.settings_wardrobe_batch_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onStart()
                }) {
                    Text(stringResource(R.string.settings_wardrobe_batch_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.settings_wardrobe_batch_confirm_cancel))
                }
            },
        )
    }

    if (isRunning) {
        val done = workInfo!!.progress.getInt(BatchSegmentationWork.KEY_DONE, 0)
        val total = workInfo.progress.getInt(BatchSegmentationWork.KEY_TOTAL, 0)
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.settings_wardrobe_removing_backgrounds))
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(
                            R.string.settings_wardrobe_removing_backgrounds_progress,
                            done,
                            total,
                        ),
                    )
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    } else if (eligibleCount > 0) {
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.settings_wardrobe_remove_backgrounds))
            },
            supportingContent = {
                Text(
                    pluralStringResource(
                        R.plurals.settings_wardrobe_remove_backgrounds_summary,
                        eligibleCount,
                        eligibleCount,
                    ),
                )
            },
            modifier = Modifier.clickable { showConfirmDialog = true },
        )
    } else {
        ListItem(
            headlineContent = {
                Text(
                    stringResource(R.string.settings_wardrobe_all_done),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

// ── Batch caption item ─────────────────────────────────────────────────────────

@Composable
private fun BatchCaptionItem(
    eligibleCount: Int,
    progress: BatchCaptionProgress?,
    onStart: () -> Unit,
) {
    if (progress != null) {
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.settings_wardrobe_enriching_descriptions))
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(
                            R.string.settings_wardrobe_enriching_descriptions_progress,
                            progress.done,
                            progress.total,
                        ),
                    )
                    LinearProgressIndicator(
                        progress = { if (progress.total > 0) progress.done.toFloat() / progress.total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    } else if (eligibleCount > 0) {
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.settings_wardrobe_enrich_descriptions))
            },
            supportingContent = {
                Text(
                    pluralStringResource(
                        R.plurals.settings_wardrobe_enrich_descriptions_summary,
                        eligibleCount,
                        eligibleCount,
                    ),
                )
            },
            modifier = Modifier.clickable { onStart() },
        )
    } else {
        ListItem(
            headlineContent = {
                Text(
                    stringResource(R.string.settings_wardrobe_descriptions_up_to_date),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

// ── Nano Not Supported dialog ──────────────────────────────────────────────────

@Composable
private fun NanoNotSupportedDialog(
    onSwitchToOpenAi: () -> Unit,
    onSwitchToAnthropic: () -> Unit,
    onSwitchToGemini: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_ai_nano_not_supported_dialog_title)) },
        text = { Text(stringResource(R.string.settings_ai_nano_not_supported_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onSwitchToOpenAi) {
                Text(stringResource(R.string.settings_ai_nano_not_supported_use_openai))
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onSwitchToAnthropic) {
                    Text(stringResource(R.string.settings_ai_nano_not_supported_use_anthropic))
                }
                TextButton(onClick = onSwitchToGemini) {
                    Text(stringResource(R.string.settings_ai_nano_not_supported_use_gemini))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_ai_nano_not_supported_dismiss))
                }
            }
        },
    )
}

// ── Previews ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "AI off — Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AiSettingsOffPreview() {
    ClosetTheme {
        AiSettingsContent(
            aiEnabled = false,
            onAiToggled = {},
            styleVibe = StyleVibe.SmartCasual,
            onStyleVibeSelected = {},
            selectedAiProvider = AiProvider.Gemini,
            onAiProviderSelected = {},
            nanoStatus = NanoStatus.Idle,
            openAiKey = "", openAiBaseUrl = "", openAiModel = "",
            onOpenAiKeyChanged = {}, onOpenAiBaseUrlChanged = {}, onOpenAiModelChanged = {},
            anthropicKey = "", anthropicModel = "",
            onAnthropicKeyChanged = {}, onAnthropicModelChanged = {},
            geminiKey = "", geminiModel = "",
            onGeminiKeyChanged = {}, onGeminiModelChanged = {},
            openAiModels = emptyList(), openAiModelsLoading = false,
            anthropicModels = emptyList(), anthropicModelsLoading = false,
            embeddingWorkInfo = null,
            embeddingIndexSize = 0,
            onRebuildIndex = {},
            segmentationSupported = true,
            segmentationEligibleCount = 3,
            batchSegWorkInfo = null,
            onStartBatchSegmentation = {},
            captionSupported = true,
            captionEligibleCount = 2,
            batchCaptionProgress = null,
            onStartBatchEnrichment = {},
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
        )
    }
}

@Preview(showBackground = true, name = "AI on / Gemini — Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AiSettingsGeminiPreview() {
    ClosetTheme {
        AiSettingsContent(
            aiEnabled = true,
            onAiToggled = {},
            styleVibe = StyleVibe.SmartCasual,
            onStyleVibeSelected = {},
            selectedAiProvider = AiProvider.Gemini,
            onAiProviderSelected = {},
            nanoStatus = NanoStatus.Idle,
            openAiKey = "", openAiBaseUrl = "", openAiModel = "",
            onOpenAiKeyChanged = {}, onOpenAiBaseUrlChanged = {}, onOpenAiModelChanged = {},
            anthropicKey = "", anthropicModel = "",
            onAnthropicKeyChanged = {}, onAnthropicModelChanged = {},
            geminiKey = "AIza••••••••••••••••••••", geminiModel = "models/gemini-2.5-flash",
            onGeminiKeyChanged = {}, onGeminiModelChanged = {},
            openAiModels = emptyList(), openAiModelsLoading = false,
            anthropicModels = emptyList(), anthropicModelsLoading = false,
            embeddingWorkInfo = null,
            embeddingIndexSize = 42,
            onRebuildIndex = {},
            segmentationSupported = true,
            segmentationEligibleCount = 0,
            batchSegWorkInfo = null,
            onStartBatchSegmentation = {},
            captionSupported = true,
            captionEligibleCount = 5,
            batchCaptionProgress = null,
            onStartBatchEnrichment = {},
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
        )
    }
}

@Preview(showBackground = true, name = "Index building — Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AiSettingsIndexBuildingPreview() {
    ClosetTheme {
        AiSettingsContent(
            aiEnabled = false,
            onAiToggled = {},
            styleVibe = StyleVibe.SmartCasual,
            onStyleVibeSelected = {},
            selectedAiProvider = AiProvider.Gemini,
            onAiProviderSelected = {},
            nanoStatus = NanoStatus.Idle,
            openAiKey = "", openAiBaseUrl = "", openAiModel = "",
            onOpenAiKeyChanged = {}, onOpenAiBaseUrlChanged = {}, onOpenAiModelChanged = {},
            anthropicKey = "", anthropicModel = "",
            onAnthropicKeyChanged = {}, onAnthropicModelChanged = {},
            geminiKey = "", geminiModel = "",
            onGeminiKeyChanged = {}, onGeminiModelChanged = {},
            openAiModels = emptyList(), openAiModelsLoading = false,
            anthropicModels = emptyList(), anthropicModelsLoading = false,
            embeddingWorkInfo = null, // preview: idle state shown
            embeddingIndexSize = 38,
            onRebuildIndex = {},
            segmentationSupported = false,
            segmentationEligibleCount = 0,
            batchSegWorkInfo = null,
            onStartBatchSegmentation = {},
            captionSupported = false,
            captionEligibleCount = 0,
            batchCaptionProgress = null,
            onStartBatchEnrichment = {},
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
        )
    }
}
