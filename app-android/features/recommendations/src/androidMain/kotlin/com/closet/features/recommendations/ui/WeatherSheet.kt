package com.closet.features.recommendations.ui

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.closet.core.ui.theme.ClosetTheme
import com.closet.features.recommendations.R
import com.closet.features.recommendations.model.WeatherConditions

/**
 * Conditions available in the condition picker row. Each carries the string
 * resource ID for its label — resolved in the composable so the model stays
 * free of Android dependencies.
 */
// Condition picker options. Only Rainy and Windy map to distinct engine signals
// (WeatherConditions.isRaining / isWindy). Selecting Rainy/Windy also flips the
// matching toggle so the two controls stay consistent.
// Sunny, Cloudy, and Snowy are UX shortcuts that clear both toggles — the engine
// currently has no separate signal for these conditions.
// TODO: add isSnowing (and optionally isSunny) to WeatherConditions + EngineWeather
//  when the engine gains temperature-band logic that distinguishes snow from clear days.
private enum class WeatherConditionOption(val labelRes: Int) {
    Sunny(R.string.recs_weather_condition_sunny),
    Cloudy(R.string.recs_weather_condition_cloudy),
    Rainy(R.string.recs_weather_condition_rainy),
    Snowy(R.string.recs_weather_condition_snowy),
    Windy(R.string.recs_weather_condition_windy),
}

/**
 * Bottom sheet for entering today's weather conditions before the recommendation
 * engine runs. All fields are optional — the sheet is submittable with any
 * combination of values, including none.
 *
 * The sheet manages its own local form state via [rememberSaveable].
 * When [prefill] is non-null the fields are pre-populated from the
 * [WeatherRepository] cache and an "Pulled from location data" chip is shown.
 * The user can freely edit all fields regardless of autofill.
 *
 * @param prefill Pre-populated values from the WeatherRepository cache, or null
 *   if no cached forecast is available.
 * @param onConfirm Called with the current form values when the user taps
 *   "Use these conditions". Temp fields left blank produce null in [WeatherConditions].
 * @param onSkip Called when the user taps the "Skip" text button.
 * @param onDismiss Called when the sheet is dismissed via swipe or back gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSheet(
    prefill: WeatherConditions?,
    onConfirm: (WeatherConditions) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        WeatherSheetContent(
            prefill = prefill,
            onConfirm = onConfirm,
            onSkip = onSkip,
        )
    }
}

/**
 * Inner content for [WeatherSheet], extracted so Android Studio previews work
 * without a [ModalBottomSheet] wrapper.
 */
@Composable
internal fun WeatherSheetContent(
    prefill: WeatherConditions?,
    onConfirm: (WeatherConditions) -> Unit,
    onSkip: () -> Unit,
) {
    // ── Local form state ──────────────────────────────────────────────────────
    // Keys intentionally omit `prefill` so late-arriving autofill doesn't reset
    // in-progress edits. Instead, prefill is applied once below when the form
    // is still pristine (all fields at their default empty/false state).
    var tempLowText by rememberSaveable { mutableStateOf("") }
    var tempHighText by rememberSaveable { mutableStateOf("") }
    var selectedCondition by rememberSaveable { mutableStateOf<WeatherConditionOption?>(null) }
    var isRaining by rememberSaveable { mutableStateOf(false) }
    var isWindy by rememberSaveable { mutableStateOf(false) }
    var didApplyPrefill by rememberSaveable { mutableStateOf(false) }

    // Apply autofill in an effect (not during composition) to avoid mutating
    // rememberSaveable state mid-recomposition. The pristine check ensures a
    // late-arriving prefill never overwrites values the user has already typed.
    LaunchedEffect(prefill) {
        if (prefill != null &&
            tempLowText.isEmpty() && tempHighText.isEmpty() &&
            selectedCondition == null && !isRaining && !isWindy
        ) {
            tempLowText = prefill.tempLowC?.let { formatTemp(it) } ?: ""
            tempHighText = prefill.tempHighC?.let { formatTemp(it) } ?: ""
            isRaining = prefill.isRaining
            isWindy = prefill.isWindy
            selectedCondition = when {
                prefill.isRaining && prefill.isWindy -> null
                prefill.isRaining -> WeatherConditionOption.Rainy
                prefill.isWindy -> WeatherConditionOption.Windy
                else -> null
            }
            didApplyPrefill = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recs_weather_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        // ── Autofill chip ─────────────────────────────────────────────────────
        if (didApplyPrefill) {
            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.recs_weather_autofill_chip)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = if (didApplyPrefill) 4.dp else 0.dp))

        // ── Temp range inputs ─────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.recs_weather_section_temperature),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = tempLowText,
                onValueChange = { v -> if (v.matches(Regex("""^-?\d*(\.\d*)?$"""))) tempLowText = v },
                label = { Text(stringResource(R.string.recs_weather_temp_low)) },
                suffix = { Text(stringResource(R.string.recs_weather_temp_unit)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )

            OutlinedTextField(
                value = tempHighText,
                onValueChange = { v -> if (v.matches(Regex("""^-?\d*(\.\d*)?$"""))) tempHighText = v },
                label = { Text(stringResource(R.string.recs_weather_temp_high)) },
                suffix = { Text(stringResource(R.string.recs_weather_temp_unit)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Condition picker ──────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.recs_weather_section_condition),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WeatherConditionOption.entries.forEach { option ->
                FilterChip(
                    selected = selectedCondition == option,
                    onClick = {
                        val newCondition = if (selectedCondition == option) null else option
                        selectedCondition = newCondition
                        // Syncing toggles from chip: Rainy/Windy set that toggle; others clear both
                        isRaining = newCondition == WeatherConditionOption.Rainy
                        isWindy = newCondition == WeatherConditionOption.Windy
                    },
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }

        // ── Precipitation and wind toggles ────────────────────────────────────
        Text(
            text = stringResource(R.string.recs_weather_section_conditions),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recs_weather_toggle_raining),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = isRaining,
                onCheckedChange = { checked ->
                    isRaining = checked
                    selectedCondition = when {
                        checked && isWindy -> null
                        checked -> WeatherConditionOption.Rainy
                        isWindy -> WeatherConditionOption.Windy
                        else -> null
                    }
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recs_weather_toggle_windy),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = isWindy,
                onCheckedChange = { checked ->
                    isWindy = checked
                    selectedCondition = when {
                        isRaining && checked -> null
                        checked -> WeatherConditionOption.Windy
                        isRaining -> WeatherConditionOption.Rainy
                        else -> null
                    }
                },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // ── Actions ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.recs_sheet_skip))
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = {
                    onConfirm(
                        WeatherConditions(
                            tempLowC = tempLowText.toDoubleOrNull(),
                            tempHighC = tempHighText.toDoubleOrNull(),
                            isRaining = isRaining,
                            isWindy = isWindy,
                        )
                    )
                },
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Text(stringResource(R.string.recs_weather_confirm))
            }
        }

        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/**
 * Formats a temperature double for display in the text field.
 * Drops the trailing ".0" for whole numbers to keep the field clean.
 */
private fun formatTemp(value: Double): String =
    if (value == kotlin.math.floor(value)) value.toInt().toString() else value.toString()

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Weather Sheet - Empty - Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Weather Sheet - Empty - Dark",
)
@Composable
private fun WeatherSheetEmptyPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            WeatherSheetContent(
                prefill = null,
                onConfirm = {},
                onSkip = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Weather Sheet - Autofilled - Light")
@Composable
private fun WeatherSheetAutofilledPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            WeatherSheetContent(
                prefill = WeatherConditions(
                    tempLowC = 12.0,
                    tempHighC = 19.0,
                    isRaining = false,
                    isWindy = true,
                ),
                onConfirm = {},
                onSkip = {},
            )
        }
    }
}
