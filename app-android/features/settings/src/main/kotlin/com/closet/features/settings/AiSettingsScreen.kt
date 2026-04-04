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
import com.closet.core.data.ai.BatchCaptionProgress
import com.closet.core.data.ai.BatchCaptionResult
import com.closet.core.data.model.AiProvider
import com.closet.core.data.model.StyleVibe
import com.closet.core.data.worker.BatchSegmentationWork
import com.closet.core.ui.theme.ClosetTheme

private val AiProvider.labelRes: Int
    get() = when (this) {
        AiProvider.Nano -> R.string.settings_ai_provider_nano
        AiProvider.OpenAi -> R.string.settings_ai_provider_openai
        AiProvider.Anthropic -> R.string.settings_ai_provider_anthropic
        AiProvider.Gemini -> R.string.settings_ai_provider_gemini
    }

private val StyleVibe.labelRes: Int
    get() = when (this) {
        StyleVibe.SmartCasual -> R.string.settings_style_vibe_smart_casual
        StyleVibe.Minimalist -> R.string.settings_style_vibe_minimalist
        StyleVibe.Streetwear -> R.string.settings_style_vibe_streetwear
        StyleVibe.Business -> R.string.settings_style_vibe_business
        StyleVibe.Casual -> R.string.settings_style_vibe_casual
        StyleVibe.Formal -> R.string.settings_style_vibe_formal
    }

private val StyleVibe.descriptionRes: Int
    get() = when (this) {
        StyleVibe.SmartCasual -> R.string.settings_style_vibe_smart_casual_desc
        StyleVibe.Minimalist -> R.string.settings_style_vibe_minimalist_desc
        StyleVibe.Streetwear -> R.string.settings_style_vibe_streetwear_desc
        StyleVibe.Business -> R.string.settings_style_vibe_business_desc
        StyleVibe.Casual -> R.string.settings_style_vibe_casual_desc
        StyleVibe.Formal -> R.string.settings_style_vibe_formal_desc
    }

@Composable
fun AiSettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    var nanoNotSupportedDismissed by remember { mutableStateOf(false) }
    var lastHandledCompressionId by remember { mutableStateOf<java.util.UUID?>(null) }

    LaunchedEffect(uiState.batchSegWorkInfo?.id, uiState.batchSegWorkInfo?.state) {
        val info = uiState.batchSegWorkInfo ?: return@LaunchedEffect
        if (info.state == WorkInfo.State.SUCCEEDED && info.id != uiState.lastHandledBatchId) {
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

    LaunchedEffect(uiState.batchCaptionProgress) {
        view.keepScreenOn = uiState.batchCaptionProgress != null
    }
    DisposableEffect(Unit) {
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(uiState.captionResult) {
        val result = uiState.captionResult ?: return@LaunchedEffect
        if (result.resultId == uiState.lastHandledCaptionId) return@LaunchedEffect
        viewModel.onCaptionResultHandled(result.resultId)
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

    LaunchedEffect(uiState.compressionWorkInfo?.id, uiState.compressionWorkInfo?.state) {
        val info = uiState.compressionWorkInfo ?: return@LaunchedEffect
        if (info.state == WorkInfo.State.SUCCEEDED && info.id != lastHandledCompressionId) {
            lastHandledCompressionId = info.id
            val done = info.outputData.getInt(com.closet.core.data.worker.ImageCompressionWork.KEY_DONE, 0)
            val skipped = info.outputData.getInt(com.closet.core.data.worker.ImageCompressionWork.KEY_SKIPPED, 0)
            val failed = info.outputData.getInt(com.closet.core.data.worker.ImageCompressionWork.KEY_FAILED, 0)
            val msg = if (failed > 0) {
                context.resources.getQuantityString(
                    R.plurals.settings_image_compress_result_with_failures, done, done, skipped, failed,
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.settings_image_compress_result, done, done, skipped,
                )
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(uiState.nanoStatus) {
        if (uiState.nanoStatus !is NanoStatus.NotSupported) {
            nanoNotSupportedDismissed = false
        }
    }

    if (uiState.selectedAiProvider == AiProvider.Nano &&
        uiState.nanoStatus is NanoStatus.NotSupported &&
        !nanoNotSupportedDismissed) {
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
        aiEnabled = uiState.aiEnabled,
        onAiToggled = viewModel::onAiToggled,
        styleVibe = uiState.styleVibe,
        onStyleVibeSelected = viewModel::onStyleVibeSelected,
        selectedAiProvider = uiState.selectedAiProvider,
        onAiProviderSelected = viewModel::onAiProviderSelected,
        nanoStatus = uiState.nanoStatus,
        openAiKey = uiState.openAiKey,
        openAiBaseUrl = uiState.openAiBaseUrl,
        openAiModel = uiState.openAiModel,
        onOpenAiKeyChanged = viewModel::onOpenAiKeyChanged,
        onOpenAiBaseUrlChanged = viewModel::onOpenAiBaseUrlChanged,
        onOpenAiModelChanged = viewModel::onOpenAiModelChanged,
        anthropicKey = uiState.anthropicKey,
        anthropicModel = uiState.anthropicModel,
        onAnthropicKeyChanged = viewModel::onAnthropicKeyChanged,
        onAnthropicModelChanged = viewModel::onAnthropicModelChanged,
        geminiKey = uiState.geminiKey,
        geminiModel = uiState.geminiModel,
        onGeminiKeyChanged = viewModel::onGeminiKeyChanged,
        onGeminiModelChanged = viewModel::onGeminiModelChanged,
        openAiModels = uiState.openAiModels,
        openAiModelsLoading = uiState.openAiModelsLoading,
        anthropicModels = uiState.anthropicModels,
        anthropicModelsLoading = uiState.anthropicModelsLoading,
        geminiModels = uiState.geminiModels,
        geminiModelsLoading = uiState.geminiModelsLoading,
        storageUsedBytes = uiState.storageUsedBytes,
        compressionWorkInfo = uiState.compressionWorkInfo,
        onCompressImages = viewModel::onCompressImages,
        embeddingIndexSize = uiState.embeddingIndexSize,
        embeddingWorkInfo = uiState.embeddingWorkInfo,
        onRebuildEmbeddingIndex = viewModel::onRebuildEmbeddingIndex,
        segmentationSupported = uiState.segmentationSupported,
        segmentationEligibleCount = uiState.segmentationEligibleCount,
        batchSegWorkInfo = uiState.batchSegWorkInfo,
        onStartBatchSegmentation = viewModel::onStartBatchSegmentation,
        captionSupported = uiState.captionSupported,
        captionEligibleCount = uiState.captionEligibleCount,
        batchCaptionProgress = uiState.batchCaptionProgress,
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
    geminiModels: List<String>,
    geminiModelsLoading: Boolean,
    storageUsedBytes: Long,
    compressionWorkInfo: WorkInfo?,
    onCompressImages: () -> Unit,
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
                    geminiModels = geminiModels,
                    geminiModelsLoading = geminiModelsLoading,
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.settings_image_storage),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            StorageUsedItem(storageUsedBytes = storageUsedBytes)

            CompressImagesItem(
                workInfo = compressionWorkInfo,
                onStart = onCompressImages,
            )
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
    geminiModels: List<String>,
    geminiModelsLoading: Boolean,
) {
    Column {
        Text(
            text = stringResource(R.string.settings_ai_provider),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        AiProvider.entries.forEach { provider ->
            val isNano = provider == AiProvider.Nano
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
                                is NanoStatus.Checking, is NanoStatus.Downloading -> stringResource(R.string.settings_ai_nano_status_loading)
                                is NanoStatus.Ready -> stringResource(R.string.settings_ai_nano_status_ready)
                                is NanoStatus.Idle -> stringResource(R.string.settings_ai_nano_status_idle)
                                is NanoStatus.Failed -> nanoStatus.message
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
                            availableModels = geminiModels,
                            isLoadingModels = geminiModelsLoading,
                            keyLabel = stringResource(R.string.settings_ai_gemini_key),
                            modelLabel = stringResource(R.string.settings_ai_gemini_model),
                        )
                    }
                    AiProvider.Nano -> { /* No settings for Nano */ }
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

// ── Image storage ─────────────────────────────────────────────────────────────

@Composable
private fun StorageUsedItem(storageUsedBytes: Long) {
    val summary = if (storageUsedBytes < 0) {
        stringResource(R.string.settings_image_storage_used_computing)
    } else {
        stringResource(R.string.settings_image_storage_used_summary, formatBytes(storageUsedBytes))
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_image_storage_used)) },
        supportingContent = { Text(summary) },
    )
}

@Composable
private fun CompressImagesItem(
    workInfo: WorkInfo?,
    onStart: () -> Unit,
) {
    val isRunning = workInfo?.state == WorkInfo.State.RUNNING ||
        workInfo?.state == WorkInfo.State.ENQUEUED

    if (isRunning) {
        val done = workInfo!!.progress.getInt(com.closet.core.data.worker.ImageCompressionWork.KEY_DONE, 0)
        val total = workInfo.progress.getInt(com.closet.core.data.worker.ImageCompressionWork.KEY_TOTAL, 0)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_image_compress_running)) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_image_compress_progress, done, total))
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    } else {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_image_compress)) },
            supportingContent = { Text(stringResource(R.string.settings_image_compress_summary)) },
            trailingContent = {
                androidx.compose.material3.TextButton(onClick = onStart) {
                    Text(stringResource(R.string.settings_image_compress_run))
                }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(java.util.Locale.ROOT, "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024L         -> String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1_024.0)
    else                    -> "$bytes B"
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
            geminiModels = emptyList(), geminiModelsLoading = false,
            storageUsedBytes = 24_800_000L, compressionWorkInfo = null,
            onCompressImages = {},
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
