package com.closet.features.settings

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.closet.core.data.model.AiProvider
import com.closet.core.data.model.StyleVibe
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherService
import com.closet.core.ui.theme.ClosetAccent
import com.closet.core.ui.theme.ClosetTheme

@Preview(showBackground = true, name = "Default — Dark")
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
            onNavigateToAiSettings = {},
            onNavigateToBackup = {},
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
        )
    }
}

@Preview(showBackground = true, name = "Weather on / Open-Meteo — Dark")
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
            onNavigateToAiSettings = {},
            onNavigateToBackup = {},
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
        )
    }
}

@Preview(showBackground = true, name = "Weather on / Google + API key — Dark")
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
            onNavigateToAiSettings = {},
            onNavigateToBackup = {},
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
        )
    }
}

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
            storageUsedBytes = 24_800_000L, compressionWorkStatus = null,
            onCompressImages = {},
            embeddingIndexSize = 0, embeddingWorkStatus = null,
            onRebuildEmbeddingIndex = {},
            segmentationSupported = true,
            segmentationEligibleCount = 0,
            batchSegWorkStatus = null,
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
