package com.closet.features.recommendations

import com.closet.core.data.dao.CandidateItem
import com.closet.core.data.model.AiProvider
import com.closet.core.data.model.StyleVibe
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.repository.RecommendationRepository
import com.closet.core.data.util.DataResult
import com.closet.features.recommendations.ai.OutfitCoherenceScorer
import com.closet.features.recommendations.engine.OutfitCombo
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.engine.key
import com.closet.features.recommendations.model.WeatherConditions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
    private fun buildViewModel(aiReady: Boolean): RecommendationViewModel {
        val repo = mockk<RecommendationRepository>()
        coEvery { repo.getCandidates(any(), any(), any(), any()) } returns DataResult.Success(
            listOf(candidate(1, "Top"), candidate(2, "Top"), candidate(3, "Bottom"), candidate(4, "Bottom"))
        )
        coEvery { repo.getTempPercentiles(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getRainSuitability(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getWindSuitability(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getLastWornDates(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getItemColorFamilies(any()) } returns DataResult.Success(emptyList())
        coEvery { repo.getItemPatternNames(any()) } returns DataResult.Success(emptyList())
        return RecommendationViewModel(
            recommendationRepository = repo,
            engine = OutfitRecommendationEngine(),
            scorer = scorer,
            weatherRepository = mockk(relaxed = true),
            outfitRepository = mockk(relaxed = true),
            lookupRepository = mockk(relaxed = true),
            imagePathResolver = ImagePathResolver { it },
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
