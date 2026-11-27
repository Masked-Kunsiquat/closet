package com.closet.features.recommendations

import com.closet.core.data.dao.CandidateItem
import com.closet.core.data.repository.RecommendationRepository
import com.closet.core.data.util.DataResult
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

    @Test fun `no candidates gives an empty list without further queries`() = runTest {
        val repo = repo(emptyList())

        val combos = pipeline(repo).run(request(seed = 42))

        assertEquals(emptyList(), combos)
        coVerify(exactly = 0) { repo.getTempPercentiles(any()) }
    }
}
