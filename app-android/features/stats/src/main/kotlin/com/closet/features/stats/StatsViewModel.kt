package com.closet.features.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.dao.BreakdownRow
import com.closet.core.data.dao.CategorySubcategoryRow
import com.closet.core.data.dao.ColorBreakdownRow
import com.closet.core.data.dao.CostPerWearItem
import com.closet.core.data.dao.StatItem
import com.closet.core.data.dao.StatsOverview
import com.closet.core.data.repository.StatsRepository
import com.closet.core.data.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/** Time window used to filter wear-based statistics. */
enum class StatPeriod {
    ALL_TIME,
    LAST_30,
    LAST_90,
    THIS_YEAR;

    /**
     * Returns the ISO-8601 start date (YYYY-MM-DD) for this period,
     * or null for [ALL_TIME] (no date filter).
     */
    fun toFromDate(): String? = when (this) {
        ALL_TIME -> null
        LAST_30 -> LocalDate.now().minusDays(30).toString()
        LAST_90 -> LocalDate.now().minusDays(90).toString()
        THIS_YEAR -> LocalDate.of(LocalDate.now().year, 1, 1).toString()
    }
}

/**
 * Snapshot of all data needed to render the Stats screen.
 */
data class StatsUiState(
    val overview: StatsOverview = StatsOverview(0, 0, 0, null),
    val mostWorn: List<StatItem> = emptyList(),
    val costPerWear: List<CostPerWearItem> = emptyList(),
    val categorySubcategoryBreakdown: List<CategorySubcategoryRow> = emptyList(),
    val categoryWear: List<BreakdownRow> = emptyList(),
    val totalLogsCount: Int = 0,
    val neverWorn: List<StatItem> = emptyList(),
    val selectedPeriod: StatPeriod = StatPeriod.ALL_TIME,
    val colorBreakdown: List<ColorBreakdownRow> = emptyList(),
    val occasionBreakdown: List<BreakdownRow> = emptyList(),
    val washStatus: List<BreakdownRow> = emptyList(),
)

/**
 * Intermediate holder for the five period-sensitive queries, carrying the active
 * [StatPeriod] so it surfaces in [StatsUiState] without an extra flow in the outer combine.
 */
private data class PeriodData(
    val period: StatPeriod,
    val overview: StatsOverview,
    val mostWorn: List<StatItem>,
    val costPerWear: List<CostPerWearItem>,
    val categoryWear: List<BreakdownRow>,
    val totalLogsCount: Int
)

/**
 * ViewModel for the Stats screen.
 *
 * Wear-based queries are subscribed via [flatMapLatest] on [_selectedPeriod]: changing the
 * period cancels the previous subscriptions and re-subscribes all five filterable flows at once.
 * Period-independent flows (category, subcategory, color, occasion, wash status, never worn)
 * are folded in by the outer [combine] using nested combines to stay within the 5-flow limit.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(StatPeriod.ALL_TIME)

    private val periodData = _selectedPeriod.flatMapLatest { period ->
        val fromDate = period.toFromDate()
        combine(
            statsRepository.getStatsOverview(fromDate),
            statsRepository.getMostWornItems(fromDate),
            statsRepository.getCostPerWear(fromDate),
            statsRepository.getWearFrequencyByCategory(fromDate),
            statsRepository.getTotalOutfitsLogged(fromDate)
        ) { overview, mostWorn, costPerWear, categoryWear, totalLogs ->
            PeriodData(period, overview, mostWorn, costPerWear, categoryWear, totalLogs)
        }
    }

    /** Aggregated UI state for the Stats screen. */
    val uiState: StateFlow<StatsUiState> = combine(
        periodData,
        combine(
            statsRepository.getCategorySubcategoryBreakdown(),
            statsRepository.getColorBreakdown(),
            statsRepository.getOccasionBreakdown(),
        ) { catSub, color, occasion -> Triple(catSub, color, occasion) },
        combine(
            statsRepository.getWashStatusBreakdown(),
            statsRepository.getNeverWornItems(),
        ) { wash, never -> wash to never }
    ) { pd, composition, misc ->
        StatsUiState(
            overview = pd.overview,
            mostWorn = pd.mostWorn,
            costPerWear = pd.costPerWear,
            categorySubcategoryBreakdown = composition.first,
            categoryWear = pd.categoryWear,
            totalLogsCount = pd.totalLogsCount,
            selectedPeriod = pd.period,
            colorBreakdown = composition.second,
            occasionBreakdown = composition.third,
            washStatus = misc.first,
            neverWorn = misc.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState()
    )

    /** Resolves a stored relative image [path] to a [File], or null if [path] is null. */
    fun resolveImagePath(path: String?): File? = path?.let { storageRepository.getFile(it) }

    /**
     * Updates the active time period. All five period-sensitive flows re-subscribe immediately
     * via [flatMapLatest]; the two period-independent flows are unaffected.
     */
    fun selectPeriod(period: StatPeriod) {
        _selectedPeriod.value = period
    }
}
