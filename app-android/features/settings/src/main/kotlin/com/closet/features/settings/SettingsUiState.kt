package com.closet.features.settings

import androidx.work.WorkInfo
import com.closet.core.data.ai.BatchCaptionProgress
import com.closet.core.data.ai.BatchCaptionResult
import com.closet.core.data.model.AiProvider
import com.closet.core.data.model.StyleVibe
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherService
import com.closet.core.ui.theme.ClosetAccent
import java.util.UUID

/**
 * Single state object for [SettingsViewModel].
 *
 * Consolidates all previously-individual [kotlinx.coroutines.flow.StateFlow]s so that
 * both [SettingsScreen] and [AiSettingsScreen] can subscribe to a single flow.
 */
data class SettingsUiState(
    // ── App ──────────────────────────────────────────────────────────────────
    val accent: ClosetAccent = ClosetAccent.Blue,
    val dynamicColor: Boolean = false,
    val weatherEnabled: Boolean = false,
    val weatherService: WeatherService = WeatherService.OpenWeatherMap,
    val googleApiKey: String = "",
    val temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,

    // ── AI ───────────────────────────────────────────────────────────────────
    val aiEnabled: Boolean = false,
    val styleVibe: StyleVibe = StyleVibe.SmartCasual,
    val selectedAiProvider: AiProvider = AiProvider.Gemini,
    val nanoStatus: NanoStatus = NanoStatus.Idle,

    // ── Provider keys & models ────────────────────────────────────────────────
    val openAiKey: String = "",
    val openAiBaseUrl: String = "",
    val openAiModel: String = "",
    val openAiModels: List<String> = emptyList(),
    val openAiModelsLoading: Boolean = false,
    val anthropicKey: String = "",
    val anthropicModel: String = "",
    val anthropicModels: List<String> = emptyList(),
    val anthropicModelsLoading: Boolean = false,
    val geminiKey: String = "",
    val geminiModel: String = "",

    // ── Embedding index ───────────────────────────────────────────────────────
    val embeddingWorkInfo: WorkInfo? = null,
    val embeddingIndexSize: Int = 0,

    // ── Batch captioning ──────────────────────────────────────────────────────
    val captionSupported: Boolean = false,
    val captionEligibleCount: Int = 0,
    val batchCaptionProgress: BatchCaptionProgress? = null,
    val captionResult: BatchCaptionResult? = null,
    /** ID of the last caption result the UI acknowledged; mirrors [lastHandledBatchId] for segmentation. */
    val lastHandledCaptionId: UUID? = null,

    // ── Batch segmentation ────────────────────────────────────────────────────
    val segmentationSupported: Boolean = false,
    val segmentationEligibleCount: Int = 0,
    val batchSegWorkInfo: WorkInfo? = null,
    val lastHandledBatchId: UUID? = null,
)
