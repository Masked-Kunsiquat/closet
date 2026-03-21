package com.closet.features.outfits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.closet.core.ui.components.UserMessageSnackbarEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.data.dao.CalendarDay
import com.closet.core.data.dao.OutfitLogWithMeta
import com.closet.core.data.model.OutfitWithItems
import com.closet.core.data.model.WeatherCondition
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ─── Public entry point ───────────────────────────────────────────────────────

/**
 * Journal screen: a monthly calendar showing days with wear logs.
 *
 * Tapping a logged day opens [DayDetailSheet] for that date.
 */
@Composable
fun JournalScreen(
    initialDate: String? = null,
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialDate) {
        if (initialDate != null) viewModel.navigateToDate(initialDate)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val outfits by viewModel.outfits.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageSnackbarEffect(viewModel.actionError, snackbarHostState)
    JournalContent(
        uiState = uiState,
        outfits = outfits,
        snackbarHostState = snackbarHostState,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onDayClick = viewModel::selectDate,
        onDismissSheet = { viewModel.selectDate(null) },
        onOotdToggle = viewModel::toggleOotd,
        onDeleteLog = viewModel::deleteLog,
        onAddLog = viewModel::openOutfitPicker,
        onDismissPicker = viewModel::dismissOutfitPicker,
        onOutfitSelected = viewModel::logOutfitOnDate,
        onEditLog = viewModel::openLogEdit,
        onDismissEdit = viewModel::dismissLogEdit,
        onSaveEdit = viewModel::saveLogEdit,
        resolveImage = viewModel::resolveImagePath,
        modifier = modifier,
    )
}

// ─── Content (pure / testable) ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JournalContent(
    uiState: JournalUiState,
    outfits: List<OutfitWithItems>,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (String) -> Unit,
    onDismissSheet: () -> Unit,
    onOotdToggle: (logId: Long, currentIsOotd: Boolean) -> Unit,
    onDeleteLog: (logId: Long) -> Unit,
    onAddLog: () -> Unit,
    onDismissPicker: () -> Unit,
    onOutfitSelected: (outfitId: Long) -> Unit,
    onEditLog: (OutfitLogWithMeta) -> Unit,
    onDismissEdit: () -> Unit,
    onSaveEdit: (notes: String?, weatherCondition: WeatherCondition?) -> Unit,
    resolveImage: (String?) -> File?,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val logsByDate = remember(uiState.calendarDays) {
        uiState.calendarDays.associateBy { it.date }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.journal_title)) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            MonthNavHeader(
                yearMonth = uiState.currentYearMonth,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
            )

            Spacer(Modifier.height(8.dp))
            WeekDayRow()
            Spacer(Modifier.height(4.dp))

            CalendarGrid(
                yearMonth = uiState.currentYearMonth,
                logsByDate = logsByDate,
                selectedDate = uiState.selectedDate,
                today = today,
                onDayClick = onDayClick,
            )

            if (uiState.calendarDays.isEmpty()) {
                JournalEmptyState()
            }
        }
    }

    val selectedDate = uiState.selectedDate
    if (selectedDate != null) {
        when {
            uiState.showOutfitPicker -> OutfitPickerForDate(
                date = selectedDate,
                outfits = outfits,
                onDismiss = onDismissPicker,
                onOutfitSelected = onOutfitSelected,
                resolveImage = resolveImage,
            )
            uiState.editingLog != null -> LogEditSheet(
                log = uiState.editingLog,
                onDismiss = onDismissEdit,
                onSave = onSaveEdit,
            )
            else -> DayDetailSheet(
                date = selectedDate,
                logs = uiState.logsForSelectedDate,
                onDismiss = onDismissSheet,
                onAddLog = onAddLog,
                onEditLog = onEditLog,
                onOotdToggle = onOotdToggle,
                onDeleteLog = onDeleteLog,
                resolveImage = resolveImage,
            )
        }
    }
}

// ─── Month navigation header ──────────────────────────────────────────────────

@Composable
private fun MonthNavHeader(
    yearMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthLabel = remember(yearMonth) {
        yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    }
    val isCurrentMonth = yearMonth >= YearMonth.now()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.journal_prev_month),
            )
        }

        Text(
            text = monthLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        IconButton(onClick = onNext, enabled = !isCurrentMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.journal_next_month),
                tint = if (isCurrentMonth) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                       else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─── Week day headers ─────────────────────────────────────────────────────────

@Composable
private fun WeekDayRow(modifier: Modifier = Modifier) {
    val dayNames = remember {
        // Monday-start week (DayOfWeek.values() starts at MONDAY = index 0)
        DayOfWeek.values().map { it.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
    }
    Row(modifier = modifier.fillMaxWidth()) {
        dayNames.forEach { name ->
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Calendar grid ────────────────────────────────────────────────────────────

@Composable
private fun CalendarGrid(
    yearMonth: YearMonth,
    logsByDate: Map<String, CalendarDay>,
    selectedDate: String?,
    today: LocalDate,
    onDayClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Monday = 0 offset, Sunday = 6 offset
    val firstDayOffset = yearMonth.atDay(1).dayOfWeek.value - 1
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = firstDayOffset + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(modifier = modifier.fillMaxWidth()) {
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val day = cellIndex - firstDayOffset + 1
                    Box(Modifier.weight(1f)) {
                        if (day in 1..daysInMonth) {
                            val dateStr = yearMonth.atDay(day).toString()
                            val calDay = logsByDate[dateStr]
                            DayCell(
                                day = day,
                                isToday = yearMonth.atDay(day) == today,
                                isSelected = dateStr == selectedDate,
                                hasLog = calDay != null,
                                isOotd = calDay?.hasOotd == 1,
                                onClick = { onDayClick(dateStr) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Day cell ─────────────────────────────────────────────────────────────────

@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    hasLog: Boolean,
    isOotd: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> primary.copy(alpha = 0.2f)
                    isToday -> primary.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isOotd || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isOotd || isSelected -> primary
                    isToday -> primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            // Log dot — always present in layout to keep cell height stable
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(
                        color = when {
                            isOotd -> primary
                            hasLog -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> Color.Transparent
                        },
                        shape = CircleShape,
                    )
            )
        }
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun JournalEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.journal_empty_month),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
