package com.closet.features.stats

import app.cash.turbine.test
import com.closet.core.data.dao.BreakdownRow
import com.closet.core.data.dao.ColorBreakdownRow
import com.closet.core.data.dao.StatsOverview
import com.closet.core.data.repository.StatsRepository
import com.closet.core.data.repository.StorageRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun stubStatsRepo(
        overview: StatsOverview = StatsOverview(0, 0, 0, null)
    ): StatsRepository = mockk<StatsRepository>().also { repo ->
        every { repo.getStatsOverview(any()) } returns flowOf(overview)
        every { repo.getMostWornItems(any()) } returns flowOf(emptyList())
        every { repo.getCostPerWear(any()) } returns flowOf(emptyList())
        every { repo.getWearFrequencyByCategory(any()) } returns flowOf(emptyList())
        every { repo.getTotalOutfitsLogged(any()) } returns flowOf(0)
        every { repo.getCategoryBreakdown() } returns flowOf(emptyList())
        every { repo.getSubcategoryBreakdown() } returns flowOf(emptyList())
        every { repo.getColorBreakdown() } returns flowOf(emptyList())
        every { repo.getOccasionBreakdown() } returns flowOf(emptyList())
        every { repo.getWashStatusBreakdown() } returns flowOf(emptyList())
        every { repo.getNeverWornItems() } returns flowOf(emptyList())
    }

    private fun stubStorageRepo(): StorageRepository = mockk<StorageRepository>().also {
        every { it.getFile(any()) } returns java.io.File("")
    }

    private fun buildViewModel(
        statsRepo: StatsRepository = stubStatsRepo(),
        storageRepo: StorageRepository = stubStorageRepo()
    ) = StatsViewModel(statsRepo, storageRepo)

    // ─── Initial state ────────────────────────────────────────────────────────

    @Test
    fun `initial selectedPeriod is ALL_TIME`() = runTest {
        val vm = buildViewModel()
        vm.uiState.test {
            assertEquals(StatPeriod.ALL_TIME, awaitItem().selectedPeriod)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state reflects overview data from repository`() = runTest {
        val expected = StatsOverview(totalItems = 12, wornItems = 8, neverWornItems = 4, totalValue = 350.0)
        val vm = buildViewModel(statsRepo = stubStatsRepo(overview = expected))
        vm.uiState.test {
            assertEquals(expected, awaitItem().overview)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state has empty lists for all collection fields`() = runTest {
        val vm = buildViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList<Any>(), state.mostWorn)
            assertEquals(emptyList<Any>(), state.costPerWear)
            assertEquals(emptyList<Any>(), state.categoryCount)
            assertEquals(emptyList<Any>(), state.neverWorn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Period selection ─────────────────────────────────────────────────────

    @Test
    fun `selectPeriod updates selectedPeriod in uiState`() = runTest {
        val vm = buildViewModel()
        vm.uiState.test {
            awaitItem() // consume initial ALL_TIME state

            vm.selectPeriod(StatPeriod.LAST_30)
            assertEquals(StatPeriod.LAST_30, awaitItem().selectedPeriod)

            vm.selectPeriod(StatPeriod.LAST_90)
            assertEquals(StatPeriod.LAST_90, awaitItem().selectedPeriod)

            vm.selectPeriod(StatPeriod.THIS_YEAR)
            assertEquals(StatPeriod.THIS_YEAR, awaitItem().selectedPeriod)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial ALL_TIME period passes null fromDate to period-sensitive queries`() = runTest {
        val repo = stubStatsRepo()
        val vm = buildViewModel(statsRepo = repo)
        vm.uiState.test {
            awaitItem() // initial state; ALL_TIME queries should already be in flight
            cancelAndIgnoreRemainingEvents()
        }
        verify { repo.getStatsOverview(null) }
        verify { repo.getMostWornItems(null) }
        verify { repo.getTotalOutfitsLogged(null) }
    }

    @Test
    fun `selectPeriod LAST_30 passes non-null fromDate to period-sensitive queries`() = runTest {
        val repo = stubStatsRepo()
        val vm = buildViewModel(statsRepo = repo)
        vm.uiState.test {
            awaitItem()
            vm.selectPeriod(StatPeriod.LAST_30)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        val expectedDate = StatPeriod.LAST_30.toFromDate()!!
        verify { repo.getStatsOverview(expectedDate) }
        verify { repo.getMostWornItems(expectedDate) }
        verify { repo.getTotalOutfitsLogged(expectedDate) }
    }

    @Test
    fun `period-independent queries are never called with a date parameter`() = runTest {
        val repo = stubStatsRepo()
        val vm = buildViewModel(statsRepo = repo)
        vm.uiState.test {
            awaitItem()
            vm.selectPeriod(StatPeriod.LAST_30)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        // These queries have no fromDate parameter — verify they're called exactly once each
        verify(exactly = 1) { repo.getCategoryBreakdown() }
        verify(exactly = 1) { repo.getSubcategoryBreakdown() }
        verify(exactly = 1) { repo.getColorBreakdown() }
        verify(exactly = 1) { repo.getOccasionBreakdown() }
        verify(exactly = 1) { repo.getWashStatusBreakdown() }
        verify(exactly = 1) { repo.getNeverWornItems() }
    }

    // ─── State mapping ────────────────────────────────────────────────────────

    @Test
    fun `composition data flows through to uiState fields`() = runTest {
        val categoryRows = listOf(BreakdownRow("Tops", 5), BreakdownRow("Bottoms", 3))
        val washRows = listOf(BreakdownRow("Clean", 6), BreakdownRow("Dirty", 2))
        val colorRows = listOf(ColorBreakdownRow("Black", "#000000", 4))

        val repo = stubStatsRepo().also {
            every { it.getCategoryBreakdown() } returns flowOf(categoryRows)
            every { it.getWashStatusBreakdown() } returns flowOf(washRows)
            every { it.getColorBreakdown() } returns flowOf(colorRows)
        }

        val vm = buildViewModel(statsRepo = repo)
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(categoryRows, state.categoryCount)
            assertEquals(washRows, state.washStatus)
            assertEquals(colorRows, state.colorBreakdown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── resolveImagePath ─────────────────────────────────────────────────────

    @Test
    fun `resolveImagePath delegates to storageRepository`() {
        val storageRepo = stubStorageRepo()
        val vm = buildViewModel(storageRepo = storageRepo)
        vm.resolveImagePath("some/path.jpg")
        verify { storageRepo.getFile("some/path.jpg") }
    }

    @Test
    fun `resolveImagePath returns null when path is null`() {
        val vm = buildViewModel()
        val result = vm.resolveImagePath(null)
        assertEquals(null, result)
    }
}
