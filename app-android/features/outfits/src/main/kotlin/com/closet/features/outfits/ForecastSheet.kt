package com.closet.features.outfits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.closet.core.data.model.DailyForecast
import com.closet.core.data.model.TemperatureUnit
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
private fun LocalDate.toForecastLabelComposable(today: LocalDate): String = when (this) {
    today -> stringResource(R.string.journal_forecast_today)
    today.plusDays(1) -> stringResource(R.string.journal_forecast_tomorrow)
    else -> "${dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $dayOfMonth"
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.journal_forecast_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            val today = LocalDate.now()
            if (forecasts.isEmpty()) {
                Text(
                    text = stringResource(R.string.journal_forecast_empty),
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
            imageVector = forecast.condition.icon(),
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
            text = "${forecast.tempLow.toDisplayTemp(temperatureUnit)} / ${forecast.tempHigh.toDisplayTemp(temperatureUnit)}",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

