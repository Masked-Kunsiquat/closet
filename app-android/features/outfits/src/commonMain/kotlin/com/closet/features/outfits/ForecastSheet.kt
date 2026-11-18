package com.closet.features.outfits

import org.jetbrains.compose.resources.painterResource
import com.closet.features.outfits.resources.Res
import com.closet.features.outfits.resources.journal_forecast_empty
import com.closet.features.outfits.resources.journal_forecast_sheet_title
import com.closet.features.outfits.resources.journal_forecast_temp_range
import com.closet.features.outfits.resources.journal_forecast_today
import com.closet.features.outfits.resources.journal_forecast_tomorrow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.closet.core.data.model.DailyForecast
import com.closet.core.ui.format.formatWeekdayDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import com.closet.core.data.model.TemperatureUnit

@Composable
private fun LocalDate.toForecastLabelComposable(today: LocalDate): String = when (this) {
    today -> stringResource(Res.string.journal_forecast_today)
    today.plus(1, DateTimeUnit.DAY) -> stringResource(Res.string.journal_forecast_tomorrow)
    else -> {
        formatWeekdayDay(this)
    }
}

/**
 * Bottom sheet showing a 7-day forecast.
 *
 * Opened by tapping the [TodayForecastChip] on the Journal header.
 * Temperature values are formatted in [temperatureUnit].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForecastSheet(
    forecasts: List<DailyForecast>,
    temperatureUnit: TemperatureUnit,
    today: LocalDate,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(Res.string.journal_forecast_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            if (forecasts.isEmpty()) {
                Text(
                    text = stringResource(Res.string.journal_forecast_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                forecasts.forEachIndexed { index, forecast ->
                    DailyForecastRow(forecast = forecast, temperatureUnit = temperatureUnit, today = today)
                    if (index < forecasts.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyForecastRow(
    forecast: DailyForecast,
    temperatureUnit: TemperatureUnit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = forecast.date.toForecastLabelComposable(today),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(forecast.condition.icon()),
            contentDescription = null, // decorative — condition label follows immediately
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = forecast.condition.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = stringResource(
                Res.string.journal_forecast_temp_range,
                forecast.tempLow.toDisplayTemp(temperatureUnit),
                forecast.tempHigh.toDisplayTemp(temperatureUnit),
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

