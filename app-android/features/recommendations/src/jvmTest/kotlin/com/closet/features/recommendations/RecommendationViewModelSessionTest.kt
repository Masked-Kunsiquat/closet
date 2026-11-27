package com.closet.features.recommendations

import com.closet.core.data.dao.CandidateItem
import com.closet.core.data.dao.ItemColorHex
import com.closet.core.data.dao.ItemCount
import com.closet.core.data.dao.ItemOccasionName
import com.closet.core.data.dao.ItemPair
import com.closet.core.data.model.AiProvider
import com.closet.core.data.model.StyleVibe
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.repository.RecommendationRepository
import com.closet.core.data.time.AppClock
import com.closet.core.data.time.FixedAppClock
import com.closet.core.data.util.DataResult
import com.closet.features.recommendations.ai.OutfitCoherenceScorer
import com.closet.features.recommendations.engine.CoWearStats
import com.closet.features.recommendations.engine.ColorHex
import com.closet.features.recommendations.engine.core
import com.closet.features.recommendations.engine.EngineInput
import com.closet.features.recommendations.engine.OutfitCombo
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.engine.key
import com.closet.features.recommendations.model.WeatherConditions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationViewModelSessionTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    // uiState is WhileSubscribed, so a collector must be active for .value to update.
    private val collectorScope = CoroutineScope(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() {
        collectorScope.cancel()
        Dispatchers.resetMain()
    }

    private fun candidate(id: Long, role: String) = CandidateItem(
        id = id, name = "Item $id", imagePath = null, categoryId = null,
        subcategoryId = id, outfitRole = role, warmthLayer = null,
    )

    // Always returns null, so the AI path takes the programmatic fallback.
    private val scorer = mockk<OutfitCoherenceScorer> {
        coEvery { score(any(), any(), any(), any()) } returns null
    }

    // 2 tops x 2 bottoms = exactly 4 valid combos; the engine returns 3 per run.
    private val viewModel by lazy { buildViewModel(aiReady = false) }

    /** AI ready = enabled with an OpenAI key; the scorer always returns null (fallback path). */
    private fun buildViewModel(
        aiReady: Boolean,
        clock: AppClock = FixedAppClock(Instant.parse("2031-07-10T12:00:00Z")),
        repo: RecommendationRepository = mockk<RecommendationRepository>(),
        engine: OutfitRecommendationEngine = OutfitRecommendationEngine(),
    ): RecommendationViewModel {
        coEvery { repo.getCandidates(any(), any(), any(), any()) } returns DataResult.Success(
            listOf(candidate(1, "Top"), candidate(2, "Top"), candidate(3, "Bottom"), candidate(4, "Bottom"))
        )
        coEvery { repo.getTempPercentiles(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getRainSuitability(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getWindSuitability(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getLastWornDates(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getItemColorFamilies(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getItemPatternNames(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getCoWearPairs(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getItemLogCounts(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getTotalLogCount() } returns DataResult.Success(0)
        coEvery { repo.getSavedOutfitPairs(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getSavedItemCounts(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getSavedOutfitCount() } returns DataResult.Success(0)
        coEvery { repo.getItemOccasionNames(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getItemColorHexes(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getItemEmbeddings(any(), any()) } returns DataResult.Success(emptyMap())
        coEvery { repo.getItemEmbeddings(any()) } returns DataResult.Success(emptyMap())
        coEvery { repo.getSubcategoryCoWearPairs() } returns DataResult.Success(emptyList())
        coEvery { repo.getSubcategorySavedOutfitPairs() } returns DataResult.Success(emptyList())
        coEvery { repo.getSubcategoryLogCounts() } returns DataResult.Success(emptyList())
        coEvery { repo.getSubcategorySavedItemCounts() } returns DataResult.Success(emptyList())
        coEvery { repo.getItemSubcategoryNames(any()) } returns DataResult.Success(emptyMap())
        coEvery { repo.getItemMaterialNames(any()) } returns DataResult.Success(emptyMap())
        coEvery { repo.getMaxLogId() } returns DataResult.Success(0L)
        coEvery { repo.getTrainingItems() } returns DataResult.Success(emptyList())
        coEvery { repo.getTrainingWearLogs() } returns DataResult.Success(emptyList())
        coEvery { repo.getTrainingWearLogItems() } returns DataResult.Success(emptyList())
        return RecommendationViewModel(
            recommendationRepository = repo,
            engine = engine,
            scorer = scorer,
            weatherRepository = mockk(relaxed = true),
            outfitRepository = mockk(relaxed = true),
            lookupRepository = mockk(relaxed = true),
            imagePathResolver = ImagePathResolver { it },
            clock = clock,
            aiPrefsRepo = mockk(relaxed = true) {
                every { getAiEnabled() } returns flowOf(aiReady)
                every { getSelectedProvider() } returns flowOf(AiProvider.OpenAi)
                every { getOpenAiApiKey() } returns flowOf("test-key")
                every { getStyleVibe() } returns flowOf(StyleVibe.SmartCasual)
                every { getTasteWeightsRaw() } returns flowOf(null)
                every { getTasteWeightsWatermark() } returns flowOf(null)
            },
            computationDispatcher = UnconfinedTestDispatcher(),
        ).also { vm -> collectorScope.launch { vm.uiState.collect { } } }
    }

    private val weather = WeatherConditions(
        tempLowC = 10.0, tempHighC = 20.0, isRaining = false, isWindy = false,
    )

    private fun results(vm: RecommendationViewModel = viewModel): List<OutfitCombo> {
        val state = vm.uiState.value
        assertIs<RecommendationUiState.Results>(state, "state was $state")
        return state.combos
    }

    private fun keys(combos: List<OutfitCombo>) = combos.map { it.key() }

    @Test fun compatibilitySignalsReachEngineInput() {
        val repo = mockk<RecommendationRepository>()
        val engine = mockk<OutfitRecommendationEngine>()
        val captured = slot<EngineInput>()
        every { engine.recommend(capture(captured), any(), any(), any()) } returns emptyList()
        val vm = buildViewModel(aiReady = false, repo = repo, engine = engine)
        coEvery { repo.getCoWearPairs(any()) } returns DataResult.Success(listOf(ItemPair(1, 3, 4)))
        coEvery { repo.getItemLogCounts(any()) } returns
            DataResult.Success(listOf(ItemCount(1, 4), ItemCount(3, 5)))
        coEvery { repo.getTotalLogCount() } returns DataResult.Success(10)
        coEvery { repo.getSavedOutfitPairs(any()) } returns DataResult.Success(listOf(ItemPair(1, 3, 2)))
        coEvery { repo.getSavedItemCounts(any()) } returns DataResult.Success(listOf(ItemCount(1, 2)))
        coEvery { repo.getSavedOutfitCount() } returns DataResult.Success(4)
        coEvery { repo.getItemOccasionNames(any()) } returns DataResult.Success(
            listOf(
                ItemOccasionName(1, "Formal"), ItemOccasionName(1, "Casual"),
                ItemOccasionName(1, "My Gala"), ItemOccasionName(2, "My Gala"),
            )
        )
        coEvery { repo.getItemColorHexes(any()) } returns DataResult.Success(
            listOf(
                ItemColorHex(3, "#112233", "#445566", 0.8, "Cool", true),
                ItemColorHex(3, "#778899", null, null, "Warm", false),
            )
        )
        val v1 = floatArrayOf(1.0f, 0.0f, 0.0f)
        val v2 = floatArrayOf(0.9f, 0.43588989f, 0.0f)
        coEvery { repo.getItemEmbeddings(any()) } returns DataResult.Success(mapOf(1L to v1, 2L to v2))
        coEvery { repo.getSubcategoryCoWearPairs() } returns DataResult.Success(listOf(ItemPair(10, 20, 3)))
        coEvery { repo.getSubcategorySavedOutfitPairs() } returns DataResult.Success(listOf(ItemPair(10, 20, 2)))
        coEvery { repo.getSubcategoryLogCounts() } returns DataResult.Success(listOf(ItemCount(10, 3)))
        coEvery { repo.getSubcategorySavedItemCounts() } returns DataResult.Success(listOf(ItemCount(10, 2)))
        coEvery { repo.getItemSubcategoryNames(any()) } returns DataResult.Success(
            mapOf(1L to "Sweater", 3L to "Jeans")
        )
        coEvery { repo.getItemMaterialNames(any()) } returns DataResult.Success(
            mapOf(1L to setOf("Wool", "Cashmere"), 3L to setOf("Denim"))
        )

        vm.onWeatherConfirmed(weather)

        val input = captured.captured
        assertEquals(
            CoWearStats(
                pairCounts = mapOf((1L to 3L) to 5.0),
                itemCounts = mapOf(1L to 5.0, 3L to 5.0),
                total = 12.0,
            ),
            input.coWear,
        )
        val items = input.candidates.associateBy { it.id }
        assertEquals(3.5, items.getValue(1).formalityLevel)
        assertEquals(null, items.getValue(2).formalityLevel)
        // The measured hex leads, with the name hex kept as its fallback.
        assertEquals(
            listOf(
                ColorHex("#445566", "Cool", share = 0.8, wearsAsNeutral = true, fallbackHex = "#112233"),
                ColorHex("#778899", "Warm", share = null, wearsAsNeutral = false, fallbackHex = "#778899"),
            ),
            items.getValue(3).colorHexes,
        )
        assertEquals(emptyList(), items.getValue(1).colorHexes)
        assertEquals("Sweater", items.getValue(1).subcategoryName)
        assertEquals(null, items.getValue(2).subcategoryName)
        assertEquals("Jeans", items.getValue(3).subcategoryName)
        assertEquals(setOf("Wool", "Cashmere"), items.getValue(1).materials)
        assertEquals(emptySet(), items.getValue(2).materials)
        assertEquals(setOf("Denim"), items.getValue(3).materials)
        assertEquals(1, input.itemNeighbors[1L]?.size)
        assertEquals(2L, input.itemNeighbors[1L]?.first()?.itemId)
        assertEquals(
            CoWearStats(
                pairCounts = mapOf((10L to 20L) to 4.0),
                itemCounts = mapOf(10L to 4.0),
                total = 12.0,
            ),
            input.subcategoryCoWear,
        )
    }

    @Test fun calendarSeasonComesFromTheClockMonth() {
        val januaryRepo = mockk<RecommendationRepository>()
        buildViewModel(
            aiReady = false,
            clock = FixedAppClock(Instant.parse("2031-01-10T12:00:00Z")),
            repo = januaryRepo,
        ).onWeatherConfirmed(weather)
        coVerify(exactly = 1) { januaryRepo.getCandidates(any(), any(), any(), any()) }
        coVerify(exactly = 1) { januaryRepo.getCandidates("Winter", any(), any(), any()) }

        val julyRepo = mockk<RecommendationRepository>()
        buildViewModel(
            aiReady = false,
            clock = FixedAppClock(Instant.parse("2031-07-10T12:00:00Z")),
            repo = julyRepo,
        ).onWeatherConfirmed(weather)
        coVerify(exactly = 1) { julyRepo.getCandidates(any(), any(), any(), any()) }
        coVerify(exactly = 1) { julyRepo.getCandidates("Summer", any(), any(), any()) }
    }

    @Test fun regenerateShowsUnseenComboThenCycles() {
        viewModel.onWeatherConfirmed(weather)
        val first = results()
        assertEquals(3, first.size)

        viewModel.onRegenerate()
        val second = results()
        // Regenerate is unseeded: assert the property, not an order. Only the 4th combo is unseen.
        assertEquals(1, second.size)
        assertNotEquals(keys(first).toSet(), keys(second).toSet())
        assertTrue(keys(second).none { it in keys(first) })

        // Every combo has now been shown; the cycle restarts instead of NoResults.
        viewModel.onRegenerate()
        assertTrue(results().isNotEmpty())
    }

    /**
     * Echo-chamber guard (Review Focus 3) on 3 tops (1-3) x 3 bottoms (4-6). Of 40 logs, Top 1 +
     * Bottom 4 were worn together 20 times; Top 2 and Bottom 5 were each worn 6 times, never with
     * each other or with 1 or 4. (The date seed alone leads with 2 + 5.)
     */
    private fun echoChamberViewModel(withHistory: Boolean): RecommendationViewModel {
        val repo = mockk<RecommendationRepository>()
        val vm = buildViewModel(aiReady = false, repo = repo)
        coEvery { repo.getCandidates(any(), any(), any(), any()) } returns DataResult.Success(
            listOf(1L, 2L, 3L).map { candidate(it, "Top") } + listOf(4L, 5L, 6L).map { candidate(it, "Bottom") }
        )
        if (withHistory) {
            coEvery { repo.getCoWearPairs(any()) } returns DataResult.Success(listOf(ItemPair(1, 4, 20)))
            coEvery { repo.getItemLogCounts(any()) } returns DataResult.Success(
                listOf(ItemCount(1, 20), ItemCount(2, 6), ItemCount(4, 20), ItemCount(5, 6))
            )
            coEvery { repo.getTotalLogCount() } returns DataResult.Success(40)
        }
        return vm
    }

    @Test fun dominantCoWornPairDoesNotCrowdOutOtherCores() {
        val dominant = setOf(1L, 4L)
        fun usesNeither(combo: OutfitCombo) = combo.core().none { it in dominant }

        // Habit: the same date seed without history leads with a different core.
        val cold = echoChamberViewModel(withHistory = false)
        cold.onWeatherConfirmed(weather)
        assertNotEquals(dominant, results(cold).first().core(), "without history the seed leads elsewhere")

        val vm = echoChamberViewModel(withHistory = true)
        vm.onWeatherConfirmed(weather)
        val first = results(vm)
        assertEquals(dominant, first.first().core(), "habit ranks the dominant pair first")
        assertEquals(3, first.map { it.core() }.toSet().size, "the page has three distinct cores")
        assertTrue(first.any(::usesNeither), "the first page has a core with neither dominant item")

        vm.onRegenerate()
        val second = results(vm)
        assertTrue(second.isNotEmpty())
        assertTrue(second.none { it.core() == dominant }, "Regenerate does not repeat the dominant pair")
        assertTrue(second.any(::usesNeither), "Regenerate surfaces a core with neither dominant item")
        assertTrue(keys(second).none { it in keys(first) }, "Regenerate shows only unseen combos")
    }

    @Test fun freshSessionRepeatsTheFirstRun() {
        viewModel.onWeatherConfirmed(weather)
        val first = keys(results())
        viewModel.onRegenerate()
        viewModel.onWeatherConfirmed(weather)
        assertEquals(first, keys(results()))
    }

    @Test fun aiFallbackShowsAndRecordsOnlyThePage() {
        val vm = buildViewModel(aiReady = true)
        vm.onWeatherConfirmed(weather)
        val first = results(vm)
        // The AI pool holds all 4 combos, but the null-scorer fallback shows only the page.
        assertEquals(3, first.size)
        coVerify(atLeast = 1) { scorer.score(any(), any(), any(), any()) }

        vm.onRegenerate()
        val second = results(vm)
        // Only the 3 shown keys were recorded, so exactly the 4th combo remains.
        // (Recording the whole pool would exclude all 4 and restart the cycle with 3.)
        assertEquals(1, second.size)
        assertTrue(keys(second).none { it in keys(first) })
    }

    @Test fun cachedTasteWeightsArePassedToEngineWhenWatermarkMatches() {
        val repo = mockk<RecommendationRepository>()
        val customWeights = com.closet.features.recommendations.engine.learning.TasteWeights(historyLift = 1.8)
        var capturedInput: EngineInput? = null
        val customScorer = mockk<OutfitCoherenceScorer> {
            coEvery { score(any(), any(), any(), any()) } answers {
                capturedInput = secondArg()
                null
            }
        }
        val vm = buildViewModel(aiReady = true, repo = repo).let {
            coEvery { repo.getTotalLogCount() } returns DataResult.Success(25)
            coEvery { repo.getMaxLogId() } returns DataResult.Success(42L)
            RecommendationViewModel(
                recommendationRepository = repo,
                engine = OutfitRecommendationEngine(),
                scorer = customScorer,
                weatherRepository = mockk(relaxed = true),
                outfitRepository = mockk(relaxed = true),
                lookupRepository = mockk(relaxed = true),
                imagePathResolver = ImagePathResolver { it },
                clock = FixedAppClock(Instant.parse("2031-07-10T12:00:00Z")),
                aiPrefsRepo = mockk(relaxed = true) {
                    every { getAiEnabled() } returns flowOf(true)
                    every { getSelectedProvider() } returns flowOf(AiProvider.OpenAi)
                    every { getOpenAiApiKey() } returns flowOf("test-key")
                    every { getStyleVibe() } returns flowOf(StyleVibe.SmartCasual)
                    every { getTasteWeightsWatermark() } returns flowOf("v5:25:42")
                    every { getTasteWeightsRaw() } returns flowOf(customWeights.serialize())
                },
                computationDispatcher = UnconfinedTestDispatcher(),
            ).also { v -> collectorScope.launch { v.uiState.collect { } } }
        }
        vm.onWeatherConfirmed(weather)
        assertEquals(customWeights, capturedInput?.tasteWeights)
        coVerify(exactly = 0) { repo.getTrainingWearLogs() }
    }
}
