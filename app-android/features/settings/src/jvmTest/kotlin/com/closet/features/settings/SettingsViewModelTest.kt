package com.closet.features.settings

import com.closet.core.data.ai.BatchCaptionProgress
import com.closet.core.data.ai.BatchCaptionResult
import com.closet.core.data.ai.NanoAvailability
import com.closet.core.data.ai.NanoInitResult
import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.model.AiProvider
import com.closet.core.data.model.StyleVibe
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherService
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.repository.BatchCaptioner
import com.closet.core.data.repository.ImageStorageStats
import com.closet.core.data.repository.ModelDiscoveryRepository
import com.closet.core.data.repository.WeatherPreferencesRepository
import com.closet.core.data.util.EmbeddingIndex
import com.closet.core.data.worker.BatchSegmentationScheduler
import com.closet.core.data.worker.ColorRedetectScheduler
import com.closet.core.data.worker.EmbeddingScheduler
import com.closet.core.data.worker.ImageCompressionScheduler
import com.closet.core.data.worker.WorkState
import com.closet.core.data.worker.WorkStatus
import com.closet.core.ui.preferences.PreferencesRepository
import com.closet.core.ui.theme.ClosetAccent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val compressionStatus = MutableStateFlow<WorkStatus?>(null)
    private val captionProgress = MutableStateFlow<BatchCaptionProgress?>(null)
    private val captionResult = MutableStateFlow<BatchCaptionResult?>(null)

    private val aiPrefs = mockk<AiPreferencesRepository>(relaxed = true)
    private val imageStats = mockk<ImageStorageStats>()
    private val captioner = mockk<BatchCaptioner>(relaxed = true)
    private val nanoInitializer = mockk<NanoInitializer>()
    private val prefs = mockk<PreferencesRepository>()
    private val colorRedetect = mockk<ColorRedetectScheduler>(relaxed = true)
    private val colorRedetectStatus = MutableStateFlow<WorkStatus?>(null)
    private val lastHandledColorRedetectId = MutableStateFlow<String?>(null)

    @BeforeTest fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { aiPrefs.getAiEnabled() } returns flowOf(false)
        every { aiPrefs.getStyleVibe() } returns flowOf(StyleVibe.SmartCasual)
        every { aiPrefs.getSelectedProvider() } returns flowOf(AiProvider.Gemini)
        every { aiPrefs.getOpenAiApiKey() } returns flowOf("")
        every { aiPrefs.getOpenAiBaseUrl() } returns flowOf("")
        every { aiPrefs.getOpenAiModel() } returns flowOf("")
        every { aiPrefs.getAnthropicApiKey() } returns flowOf("")
        every { aiPrefs.getAnthropicModel() } returns flowOf("")
        every { aiPrefs.getGeminiApiKey() } returns flowOf("")
        every { aiPrefs.getGeminiModel() } returns flowOf("")
        every { captioner.isSupported } returns true
        every { captioner.progress } returns captionProgress
        every { captioner.result } returns captionResult
        every { nanoInitializer.initNanoFlow() } returns flowOf()
        coEvery { imageStats.usedBytes() } returns 2048L
        every { colorRedetect.isSupported } returns true
        every { colorRedetect.workStatus } returns colorRedetectStatus
        every { prefs.lastHandledColorRedetectId } returns lastHandledColorRedetectId
        coEvery { prefs.setLastHandledColorRedetectId(any()) } answers {
            lastHandledColorRedetectId.value = firstArg()
            Result.success(Unit)
        }
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): SettingsViewModel {
        every { prefs.getAccent() } returns flowOf(ClosetAccent.Amber)
        every { prefs.getDynamicColor() } returns flowOf(false)
        every { prefs.lastHandledBatchId } returns flowOf(null)

        val weather = mockk<WeatherPreferencesRepository>()
        every { weather.getWeatherEnabled() } returns flowOf(false)
        every { weather.getWeatherService() } returns flowOf(WeatherService.OpenMeteo)
        every { weather.getGoogleApiKey() } returns flowOf("")
        every { weather.getTemperatureUnit() } returns flowOf(TemperatureUnit.Celsius)

        val dao = mockk<ClothingDao>()
        every { dao.getCaptionEligibleCount() } returns flowOf(0)
        every { dao.getSegmentationEligibleCount() } returns flowOf(0)
        every { dao.getColorRedetectEligibleCount(true) } returns flowOf(3)
        every { dao.getColorRedetectEligibleCount(false) } returns flowOf(7)

        val embeddingScheduler = mockk<EmbeddingScheduler>(relaxed = true)
        every { embeddingScheduler.workStatus } returns flowOf(null)
        val embeddingIndex = mockk<EmbeddingIndex>(relaxed = true)
        every { embeddingIndex.size } returns 0

        val segmentation = mockk<BatchSegmentationScheduler>(relaxed = true)
        every { segmentation.isSupported } returns false
        every { segmentation.workStatus } returns flowOf(null)

        val compression = mockk<ImageCompressionScheduler>(relaxed = true)
        every { compression.workStatus } returns compressionStatus

        return SettingsViewModel(
            preferencesRepository = prefs,
            aiPreferencesRepository = aiPrefs,
            weatherPreferencesRepository = weather,
            clothingDao = dao,
            modelDiscovery = mockk<ModelDiscoveryRepository>(relaxed = true),
            nanoInitializer = nanoInitializer,
            embeddingScheduler = embeddingScheduler,
            embeddingIndex = embeddingIndex,
            batchSegmentationScheduler = segmentation,
            batchCaptioner = captioner,
            imageCompressionScheduler = compression,
            imageStorageStats = imageStats,
            colorRedetectScheduler = colorRedetect,
        )
    }

    /** Subscribes to uiState (it is WhileSubscribed) and drains the scheduler. */
    private fun TestScope.started(vm: SettingsViewModel): SettingsViewModel {
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
        return vm
    }

    private fun status(state: WorkState) = WorkStatus("id", state, 0, 0, 0, 0)

    @Test fun `storage is computed on start and after compression finishes`() = runTest(dispatcher) {
        val vm = started(buildViewModel())
        assertEquals(2048L, vm.uiState.value.storageUsedBytes)

        coEvery { imageStats.usedBytes() } returns 4096L
        compressionStatus.value = status(WorkState.SUCCEEDED)
        advanceUntilIdle()
        assertEquals(4096L, vm.uiState.value.storageUsedBytes)
    }

    @Test fun `a running compression does not recompute storage`() = runTest(dispatcher) {
        val vm = started(buildViewModel())
        compressionStatus.value = status(WorkState.RUNNING)
        advanceUntilIdle()
        assertEquals(2048L, vm.uiState.value.storageUsedBytes)
        coVerify(exactly = 1) { imageStats.usedBytes() }

        compressionStatus.value = status(WorkState.SUCCEEDED)
        advanceUntilIdle()
        coVerify(exactly = 2) { imageStats.usedBytes() }
    }

    @Test fun `nano success persists readiness and shows Ready`() = runTest(dispatcher) {
        every { nanoInitializer.initNanoFlow() } returns flowOf(NanoInitResult.Success(tokenLimit = 4096))
        val vm = started(buildViewModel())
        coVerify { aiPrefs.setAiReady(true) }
        coVerify { aiPrefs.setNanoAvailability(NanoAvailability.READY) }
        coVerify { aiPrefs.setTokenLimit(4096) }
        assertEquals(NanoStatus.Ready, vm.uiState.value.nanoStatus)
    }

    @Test fun `nano downloading does not change readiness`() = runTest(dispatcher) {
        every { nanoInitializer.initNanoFlow() } returns flowOf(NanoInitResult.Downloading(progressPct = 40))
        val vm = started(buildViewModel())
        coVerify(exactly = 0) { aiPrefs.setAiReady(any()) }
        assertEquals(NanoStatus.Downloading(40), vm.uiState.value.nanoStatus)
    }

    @Test fun `a persist failure does not crash and still shows Ready`() = runTest(dispatcher) {
        every { nanoInitializer.initNanoFlow() } returns flowOf(NanoInitResult.Success(tokenLimit = 4096))
        coEvery { aiPrefs.setNanoAvailability(any()) } throws IllegalStateException("disk")
        val vm = started(buildViewModel())
        coVerify { aiPrefs.setNanoAvailability(any()) }
        coVerify(exactly = 0) { aiPrefs.setTokenLimit(any()) }
        assertEquals(NanoStatus.Ready, vm.uiState.value.nanoStatus)
    }

    @Test fun `caption result hand-off`() = runTest(dispatcher) {
        val vm = started(buildViewModel())
        val id = Uuid.random()
        vm.onCaptionResultHandled(id)
        advanceUntilIdle()
        assertEquals(id, vm.uiState.value.lastHandledCaptionId)

        vm.onCaptionResultConsumed()
        verify { captioner.consumeResult() }
    }

    @Test fun `captionSupported mirrors the captioner`() = runTest(dispatcher) {
        val vm = started(buildViewModel())
        assertEquals(true, vm.uiState.value.captionSupported)
    }

    @Test fun `colour re-detect counts both modes and mirrors support`() = runTest(dispatcher) {
        val vm = started(buildViewModel())
        val state = vm.uiState.value
        assertEquals(true, state.colorRedetectSupported)
        assertEquals(3, state.colorRedetectUncuratedCount)
        assertEquals(7, state.colorRedetectAllCount)
    }

    @Test fun `colour re-detect is hidden when the scheduler is unsupported`() = runTest(dispatcher) {
        every { colorRedetect.isSupported } returns false
        val vm = started(buildViewModel())
        assertEquals(false, vm.uiState.value.colorRedetectSupported)
    }

    @Test fun `starting colour re-detect passes the dialog's choice`() = runTest(dispatcher) {
        val vm = started(buildViewModel())
        vm.onStartColorRedetect(onlyUncurated = true)
        verify(exactly = 1) { colorRedetect.schedule(true) }
        vm.onStartColorRedetect(onlyUncurated = false)
        verify(exactly = 1) { colorRedetect.schedule(false) }
    }

    @Test fun `colour re-detect status and handled result reach the ui state`() = runTest(dispatcher) {
        val vm = started(buildViewModel())
        val finished = WorkStatus("run-1", WorkState.SUCCEEDED, done = 4, total = 6, failed = 1, skipped = 1)
        colorRedetectStatus.value = finished
        advanceUntilIdle()
        assertEquals(finished, vm.uiState.value.colorRedetectWorkStatus)
        assertEquals(null, vm.uiState.value.lastHandledColorRedetectId)

        vm.onColorRedetectResultHandled("run-1")
        advanceUntilIdle()
        coVerify { prefs.setLastHandledColorRedetectId("run-1") }
        assertEquals("run-1", vm.uiState.value.lastHandledColorRedetectId)
    }
}
