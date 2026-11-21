package com.closet.features.settings

import com.closet.core.data.ai.BatchCaptionProgress
import com.closet.core.data.ai.BatchCaptionResult
import com.closet.core.data.model.AiProvider
import com.closet.core.data.model.StyleVibe
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherService
import com.closet.core.data.worker.WorkStatus
import com.closet.core.ui.theme.ClosetAccent
import kotlin.uuid.Uuid

/**
 * Single state object for [SettingsViewModel].
 *
 * Consolidates all previously-individual [kotlinx.coroutines.flow.StateFlow]s so that
 * both [SettingsScreen] and [AiSettingsScreen] can subscribe to a single flow.
 */
data class SettingsUiState(
    // ── App ──────────────────────────────────────────────────────────────────
    val accent: ClosetAccent = ClosetAccent.Amber,
    val dynamicColor: Boolean = false,
    val weatherEnabled: Boolean = false,
    val weatherService: WeatherService = WeatherService.OpenMeteo,
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
    val geminiModels: List<String> = emptyList(),
    val geminiModelsLoading: Boolean = false,

    // ── Image storage ─────────────────────────────────────────────────────────
    /** Total bytes used by `closet_images/`. `-1` while the value is being computed. */
    val storageUsedBytes: Long = -1L,
    /** Live [WorkStatus] of the user-triggered immediate compression run. */
    val compressionWorkStatus: WorkStatus? = null,

    // ── Embedding index ───────────────────────────────────────────────────────
    val embeddingWorkStatus: WorkStatus? = null,
    val embeddingIndexSize: Int = 0,

    // ── Batch captioning ──────────────────────────────────────────────────────
    val captionSupported: Boolean = false,
    val captionEligibleCount: Int = 0,
    val batchCaptionProgress: BatchCaptionProgress? = null,
    val captionResult: BatchCaptionResult? = null,
    /** ID of the last caption result the UI acknowledged; mirrors [lastHandledBatchId] for segmentation. */
    val lastHandledCaptionId: Uuid? = null,

    // ── Batch segmentation ────────────────────────────────────────────────────
    val segmentationSupported: Boolean = false,
    val segmentationEligibleCount: Int = 0,
    val batchSegWorkStatus: WorkStatus? = null,
    val lastHandledBatchId: String? = null,

    // ── Bulk colour re-detect (#128) ──────────────────────────────────────────
    val colorRedetectSupported: Boolean = false,
    /** Items with a photo whose colours the user hasn't edited (the dialog's default mode). */
    val colorRedetectUncuratedCount: Int = 0,
    /** Every item with a photo, curated colours included. */
    val colorRedetectAllCount: Int = 0,
    /** Live status of the job; `skipped` holds the unchanged count. */
    val colorRedetectWorkStatus: WorkStatus? = null,
    val lastHandledColorRedetectId: String? = null,
)
