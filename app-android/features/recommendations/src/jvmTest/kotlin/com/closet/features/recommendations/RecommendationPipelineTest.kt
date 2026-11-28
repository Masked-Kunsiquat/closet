package com.closet.features.recommendations

import com.closet.core.data.dao.CandidateItem
import com.closet.core.data.dao.ItemTempPercentiles
import com.closet.core.data.repository.RecommendationRepository
import com.closet.core.data.util.DataResult
import com.closet.core.data.weather.WarmthLevel
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.engine.learning.TasteWeights
import com.closet.features.recommendations.model.WeatherConditions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationPipelineTest {

    private fun candidate(id: Long, role: String) = CandidateItem(
        id = id, name = "Item $id", imagePath = null, categoryId = null,
        subcategoryId = id, outfitRole = role, warmthLayer = null,
    )

    private fun repo(candidates: List<CandidateItem>): RecommendationRepository {
        val repo = mockk<RecommendationRepository>()
        coEvery { repo.getCandidates(any(), any(), any(), any()) } returns DataResult.Success(candidates)
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
        return repo
    }

    private fun pipeline(repo: RecommendationRepository) = RecommendationPipeline(
        recommendationRepository = repo,
        engine = OutfitRecommendationEngine(),
        computationDispatcher = UnconfinedTestDispatcher(),
        tasteWeights = { TasteWeights.DEFAULT },
    )

    private fun request(seed: Long) = PipelineRequest(
        today = LocalDate(2031, 7, 10),
        occasionId = null,
        weather = WeatherConditions(tempLowC = 10.0, tempHighC = 20.0, isRaining = false, isWindy = false),
        topN = OutfitRecommendationEngine.TOP_N,
        jitter = Random(seed),
    )

    @Test fun `same request and seed give the same combos`() = runTest {
        val wardrobe = listOf(1L, 2L, 3L).map { candidate(it, "Top") } +
            listOf(4L, 5L, 6L).map { candidate(it, "Bottom") }
        val pipeline = pipeline(repo(wardrobe))

        val first = pipeline.run(request(seed = 42))
        val second = pipeline.run(request(seed = 42))

        assertTrue(first.isNotEmpty())
        assertEquals(first, second)
    }

    @Test fun `engine items carry effective warmth and rain readiness`() = runTest {
        val wardrobe = listOf(
            // Nylon Jacket: derived MEDIUM and rain ready; the override says HEAVY.
            candidate(1, "Outerwear").copy(warmthOverride = WarmthLevel.HEAVY.ordinal),
            // Sweater: derived MEDIUM, worn on cold days, so history raises it to WARM.
            candidate(2, "Top"),
            // T-Shirt: derived NONE and not rain ready; the override marks it rain ready.
            candidate(3, "Top").copy(rainReadyOverride = true),
            // Jeans with no history: derived NONE and not rain ready.
            candidate(4, "Bottom"),
        )
        val repo = repo(wardrobe)
        coEvery { repo.getItemSubcategoryNames(any()) } returns DataResult.Success(
            mapOf(1L to "Jacket", 2L to "Sweater", 3L to "T-Shirt", 4L to "Jeans"),
        )
        coEvery { repo.getItemMaterialNames(any()) } returns DataResult.Success(
            mapOf(1L to setOf("Nylon"), 4L to setOf("Denim")),
        )
        coEvery { repo.getTempPercentiles(any()) } returns DataResult.Success(
            listOf(ItemTempPercentiles(clothingItemId = 2, p10TempLow = -10.0, p90TempHigh = 5.0, logCount = 6)),
        )

        val items = pipeline(repo).engineInput(request(seed = 42))!!.candidates.associateBy { it.id }

        assertEquals(WarmthLevel.HEAVY, items.getValue(1).warmthLevel)
        assertTrue(items.getValue(1).rainReady)
        assertEquals(WarmthLevel.WARM, items.getValue(2).warmthLevel)
        assertFalse(items.getValue(2).rainReady)
        assertEquals(WarmthLevel.NONE, items.getValue(3).warmthLevel)
        assertTrue(items.getValue(3).rainReady)
        assertEquals(WarmthLevel.NONE, items.getValue(4).warmthLevel)
        assertFalse(items.getValue(4).rainReady)
    }

    @Test fun `no candidates gives an empty list without further queries`() = runTest {
        val repo = repo(emptyList())

        val combos = pipeline(repo).run(request(seed = 42))

        assertEquals(emptyList(), combos)
        coVerify(exactly = 0) { repo.getTempPercentiles(any()) }
    }
}
