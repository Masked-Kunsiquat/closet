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
            },
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
        every { engine.recommend(capture(captured), any(), any(), any(), any()) } returns emptyList()
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
        coEvery { repo.getItemColorHexes(any()) } returns DataResult.Success(listOf(ItemColorHex(3, "#112233")))

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
        assertEquals(listOf("#112233"), items.getValue(3).colorHexes)
        assertEquals(emptyList(), items.getValue(1).colorHexes)
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
}
