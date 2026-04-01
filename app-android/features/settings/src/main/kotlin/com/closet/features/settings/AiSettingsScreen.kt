package com.closet.features.settings

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.closet.core.data.ai.AiProvider
import com.closet.core.data.ai.BatchCaptionProgress
import com.closet.core.data.ai.BatchCaptionResult
import com.closet.core.data.ai.NanoStatus
import com.closet.core.data.ai.StyleVibe
import com.closet.core.data.work.BatchSegmentationWork
import com.closet.core.ui.theme.ClosetTheme

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

    LaunchedEffect(nanoStatus) {
        if (nanoStatus !is NanoStatus.NotSupported) {
            nanoNotSupportedDismissed = false
        }
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
            onDismiss = {
                nanoNotSupportedDismissed = true
            },
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
        embeddingIndexSize = embeddingIndexSize,
        embeddingWorkInfo = embeddingWorkInfo,
        onRebuildEmbeddingIndex = viewModel::onRebuildEmbeddingIndex,
        segmentationSupported = segmentationSupported,
        segmentationEligibleCount = segmentationEligibleCount,
        batchSegWorkInfo = batchSegWorkInfo,
        onStartBatchSegmentation = viewModel::onStartBatchSegmentation,
        captionSupported = captionSupported,
        captionEligibleCount = captionEligibleCount,
        batchCaptionProgress = batchCaptionProgress,
        onStartBatchCaption = viewModel::onStartBatchCaption,
        onNavigateUp = onNavigateUp,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSettingsContent(
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
    embeddingIndexSize: Int,
    embeddingWorkInfo: WorkInfo?,
    onRebuildEmbeddingIndex: () -> Unit,
    segmentationSupported: Boolean,
    segmentationEligibleCount: Int,
    batchSegWorkInfo: WorkInfo?,
    onStartBatchSegmentation: () -> Unit,
    captionSupported: Boolean,
    captionEligibleCount: Int,
    batchCaptionProgress: BatchCaptionProgress?,
    onStartBatchCaption: () -> Unit,
    onNavigateUp: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_ai_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_enable)) },
                supportingContent = { Text(stringResource(R.string.settings_ai_enable_summary)) },
                trailingContent = {
                    Switch(checked = aiEnabled, onCheckedChange = onAiToggled)
                },
            )

            if (aiEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                StyleVibeSection(
                    selectedVibe = styleVibe,
                    onVibeSelected = onStyleVibeSelected,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                AiProviderSection(
                    selectedProvider = selectedAiProvider,
                    onProviderSelected = onAiProviderSelected,
                    nanoStatus = nanoStatus,
                    openAiKey = openAiKey,
                    openAiBaseUrl = openAiBaseUrl,
                    openAiModel = openAiModel,
                    onOpenAiKeyChanged = onOpenAiKeyChanged,
                    onOpenAiBaseUrlChanged = onOpenAiBaseUrlChanged,
                    onOpenAiModelChanged = onOpenAiModelChanged,
                    anthropicKey = anthropicKey,
                    anthropicModel = anthropicModel,
                    onAnthropicKeyChanged = onAnthropicKeyChanged,
                    onAnthropicModelChanged = onAnthropicModelChanged,
                    geminiKey = geminiKey,
                    geminiModel = geminiModel,
                    onGeminiKeyChanged = onGeminiKeyChanged,
                    onGeminiModelChanged = onGeminiModelChanged,
                    openAiModels = openAiModels,
                    openAiModelsLoading = openAiModelsLoading,
                    anthropicModels = anthropicModels,
                    anthropicModelsLoading = anthropicModelsLoading,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(R.string.settings_wardrobe_management),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                EmbeddingIndexItem(
                    indexSize = embeddingIndexSize,
                    workInfo = embeddingWorkInfo,
                    onRebuild = onRebuildEmbeddingIndex,
                )

                if (segmentationSupported) {
                    BatchSegmentationItem(
                        eligibleCount = segmentationEligibleCount,
                        workInfo = batchSegWorkInfo,
                        onStart = onStartBatchSegmentation,
                    )
                }

                if (captionSupported) {
                    BatchCaptionItem(
                        eligibleCount = captionEligibleCount,
                        progress = batchCaptionProgress,
                        onStart = onStartBatchCaption,
                    )
                }
            }
        }
    }
}

// ── Style vibe ──────────────────────────────────────────────────────────────

@Composable
private fun StyleVibeSection(
    selectedVibe: StyleVibe,
    onVibeSelected: (StyleVibe) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.settings_ai_style_vibe),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        StyleVibe.entries.forEach { vibe ->
            ListItem(
                headlineContent = { Text(stringResource(vibe.labelRes)) },
                supportingContent = { Text(stringResource(vibe.descriptionRes)) },
                trailingContent = {
                    val isSelected = vibe == selectedVibe
                    androidx.compose.material3.RadioButton(
                        selected = isSelected,
                        onClick = { onVibeSelected(vibe) },
                    )
                },
                modifier = Modifier.clickable { onVibeSelected(vibe) },
            )
        }
    }
}

// ── AI provider ─────────────────────────────────────────────────────────────

@Composable
private fun AiProviderSection(
    selectedProvider: AiProvider,
    onProviderSelected: (AiProvider) -> Unit,
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
) {
    Column {
        Text(
            text = stringResource(R.string.settings_ai_provider),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        AiProvider.entries.forEach { provider ->
            val isNano = provider == AiProvider.GeminiNano
            val isEnabled = !isNano || nanoStatus !is NanoStatus.NotSupported

            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(provider.labelRes),
                        color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    )
                },
                supportingContent = if (isNano) {
                    {
                        val color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        Text(
                            text = when (nanoStatus) {
                                is NanoStatus.NotSupported -> stringResource(R.string.settings_ai_nano_status_not_supported)
                                is NanoStatus.Loading -> stringResource(R.string.settings_ai_nano_status_loading)
                                is NanoStatus.Ready -> stringResource(R.string.settings_ai_nano_status_ready)
                                is NanoStatus.Idle -> stringResource(R.string.settings_ai_nano_status_idle)
                            },
                            color = color,
                        )
                    }
                } else null,
                trailingContent = {
                    androidx.compose.material3.RadioButton(
                        selected = provider == selectedProvider,
                        onClick = { onProviderSelected(provider) },
                        enabled = isEnabled,
                    )
                },
                modifier = Modifier.clickable(enabled = isEnabled) { onProviderSelected(provider) },
            )

            if (provider == selectedProvider) {
                when (provider) {
                    AiProvider.OpenAi -> {
                        ProviderSettings(
                            apiKey = openAiKey,
                            onApiKeyChanged = onOpenAiKeyChanged,
                            baseUrl = openAiBaseUrl,
                            onBaseUrlChanged = onOpenAiBaseUrlChanged,
                            model = openAiModel,
                            onModelChanged = onOpenAiModelChanged,
                            availableModels = openAiModels,
                            isLoadingModels = openAiModelsLoading,
                            keyLabel = stringResource(R.string.settings_ai_openai_key),
                            baseUrlLabel = stringResource(R.string.settings_ai_openai_base_url),
                            modelLabel = stringResource(R.string.settings_ai_openai_model),
                        )
                    }
                    AiProvider.Anthropic -> {
                        ProviderSettings(
                            apiKey = anthropicKey,
                            onApiKeyChanged = onAnthropicKeyChanged,
                            model = anthropicModel,
                            onModelChanged = onAnthropicModelChanged,
                            availableModels = anthropicModels,
                            isLoadingModels = anthropicModelsLoading,
                            keyLabel = stringResource(R.string.settings_ai_anthropic_key),
                            modelLabel = stringResource(R.string.settings_ai_anthropic_model),
                        )
                    }
                    AiProvider.Gemini -> {
                        ProviderSettings(
                            apiKey = geminiKey,
                            onApiKeyChanged = onGeminiKeyChanged,
                            model = geminiModel,
                            onModelChanged = onGeminiModelChanged,
                            availableModels = listOf("gemini-1.5-flash", "gemini-1.5-pro"),
                            isLoadingModels = false,
                            keyLabel = stringResource(R.string.settings_ai_gemini_key),
                            modelLabel = stringResource(R.string.settings_ai_gemini_model),
                        )
                    }
                    AiProvider.GeminiNano -> { /* No settings for Nano */ }
                }
            }
        }
    }
}

@Composable
private fun ProviderSettings(
    apiKey: String,
    onApiKeyChanged: (String) -> Unit,
    model: String,
    onModelChanged: (String) -> Unit,
    availableModels: List<String>,
    isLoadingModels: Boolean,
    keyLabel: String,
    modelLabel: String,
    baseUrl: String? = null,
    onBaseUrlChanged: ((String) -> Unit)? = null,
    baseUrlLabel: String? = null,
) {
    Column(
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChanged,
            label = { Text(keyLabel) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        )

        if (baseUrl != null && onBaseUrlChanged != null && baseUrlLabel != null) {
            androidx.compose.material3.OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChanged,
                label = { Text(baseUrlLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://api.openai.com/v1") },
            )
        }

        ModelSelector(
            selectedModel = model,
            onModelSelected = onModelChanged,
            availableModels = availableModels,
            isLoading = isLoadingModels,
            label = modelLabel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelector(
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    availableModels: List<String>,
    isLoading: Boolean,
    label: String,
) {
    var expanded by remember { mutableStateOf(false) }

    androidx.compose.material3.ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = if (isLoading) stringResource(R.string.settings_ai_models_loading) else selectedModel,
            onValueChange = onModelSelected,
            label = { Text(label) },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            readOnly = false, // Allow manual input if discovery fails
        )

        if (availableModels.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                availableModels.forEach { model ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            onModelSelected(model)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

// ── Embedding index ──────────────────────────────────────────────────────────

@Composable
private fun EmbeddingIndexItem(
    indexSize: Int,
    workInfo: WorkInfo?,
    onRebuild: () -> Unit,
) {
    val isRunning = workInfo?.state == WorkInfo.State.RUNNING ||
        workInfo?.state == WorkInfo.State.ENQUEUED

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_wardrobe_search_index)) },
        supportingContent = {
            if (isRunning) {
                Text(stringResource(R.string.settings_wardrobe_search_index_rebuilding))
            } else {
                Text(
                    pluralStringResource(
                        R.plurals.settings_wardrobe_search_index_summary,
                        indexSize,
                        indexSize,
                    ),
                )
            }
        },
        trailingContent = {
            if (isRunning) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = androidx.compose.ui.Modifier.padding(8.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(onClick = onRebuild) {
                    Text(stringResource(R.string.settings_wardrobe_search_index_rebuild))
                }
            }
        },
    )
}

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
                    stringResource(R.string.settings_wardrobe_all_backgrounds_removed),
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
            modifier = Modifier.clickable { showConfirmDialog = true },
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
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_ai_nano_not_supported_dialog_message))
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = stringResource(R.string.settings_ai_nano_not_supported_alternatives),
                    style = MaterialTheme.typography.labelLarge,
                )
                
                TextButton(
                    onClick = onSwitchToOpenAi,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Text(stringResource(R.string.settings_ai_nano_not_supported_use_openai))
                    }
                }
                TextButton(
                    onClick = onSwitchToAnthropic,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Text(stringResource(R.string.settings_ai_nano_not_supported_use_anthropic))
                    }
                }
                TextButton(
                    onClick = onSwitchToGemini,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Text(stringResource(R.string.settings_ai_nano_not_supported_use_gemini))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_ai_nano_not_supported_dismiss))
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
            embeddingIndexSize = 0, embeddingWorkInfo = null,
            onRebuildEmbeddingIndex = {},
            segmentationSupported = true,
            segmentationEligibleCount = 0,
            batchSegWorkInfo = null,
            onStartBatchSegmentation = {},
            captionSupported = true,
            captionEligibleCount = 0,
            batchCaptionProgress = null,
            onStartBatchCaption = {},
            onNavigateUp = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
