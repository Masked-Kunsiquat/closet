package com.closet.features.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.closet.core.data.model.BrandEntity
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.model.SizeSystemEntity
import com.closet.core.data.model.SizeValueEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ─── DropdownSelector ────────────────────────────────────────────────────────

/**
 * Generic nullable-selection dropdown backed by an arbitrary list.
 *
 * Renders an [OutlinedTextField] (read-only) with a Material trailing chevron and a
 * [DropdownMenu] that always includes a "None / clear" row at the top.
 *
 * @param selectedItem  The currently selected item, or null.
 * @param items         The full list of options to display.
 * @param onItemSelect  Called with the selected item, or null when "None" is chosen.
 * @param label         The floating label shown on the text field.
 * @param itemLabel     Converts an item to its display string.
 * @param enabled       When false the field is visually disabled and the menu cannot open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> DropdownSelector(
    selectedItem: T?,
    items: List<T>,
    onItemSelect: (T?) -> Unit,
    label: String,
    itemLabel: (T) -> String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedItem?.let(itemLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )

        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expanded = true }
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.wardrobe_field_none)) },
                onClick = {
                    onItemSelect(null)
                    expanded = false
                }
            )
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        onItemSelect(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ─── ColorSelectionGrid ───────────────────────────────────────────────────────

@Composable
internal fun ColorSelectionGrid(
    selectedColors: List<ColorEntity>,
    allColors: List<ColorEntity>,
    onColorToggle: (ColorEntity) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
    ) {
        items(allColors) { color ->
            val isSelected = selectedColors.any { it.id == color.id }
            val hexColor = try {
                Color(android.graphics.Color.parseColor(color.hex))
            } catch (_: Exception) {
                MaterialTheme.colorScheme.outlineVariant
            }
            val label = color.name.ifBlank { color.hex ?: "" }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = label
                        selected = isSelected
                        role = Role.Checkbox
                    }
                    .clickable { onColorToggle(color) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(hexColor)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isColorDark(hexColor)) Color.White else Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

internal fun isColorDark(color: Color): Boolean {
    val luminance = 0.2126 * color.red + 0.7152 * color.green + 0.0722 * color.blue
    return luminance < 0.5
}

// ─── SizeSection ──────────────────────────────────────────────────────────────

@Composable
internal fun SizeSection(
    sizeSystems: List<SizeSystemEntity>,
    sizeValues: List<SizeValueEntity>,
    selectedSizeSystemId: Long?,
    selectedSizeValueId: Long?,
    onSizeSystemSelected: (Long?) -> Unit,
    onSizeValueSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DropdownSelector(
            selectedItem = sizeSystems.find { it.id == selectedSizeSystemId },
            items = sizeSystems,
            onItemSelect = { onSizeSystemSelected(it?.id) },
            label = stringResource(R.string.wardrobe_field_size_system),
            itemLabel = { it.name }
        )
        if (selectedSizeSystemId != null && sizeValues.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sizeValues) { sizeValue ->
                    FilterChip(
                        selected = sizeValue.id == selectedSizeValueId,
                        onClick = {
                            val newId = if (sizeValue.id == selectedSizeValueId) null else sizeValue.id
                            onSizeValueSelected(newId)
                        },
                        label = { Text(sizeValue.value) }
                    )
                }
            }
        }
    }
}

// ─── DatePickerField ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerField(
    selectedDate: LocalDate?,
    onDateChange: (LocalDate?) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = selectedDate?.toString() ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.wardrobe_field_purchase_date)) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
                Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null)
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                ?.atStartOfDay(ZoneId.systemDefault())
                ?.toInstant()
                ?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val date = Instant.ofEpochMilli(it)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        onDateChange(date)
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.wardrobe_date_confirm))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onDateChange(null)
                        showDatePicker = false
                    }) {
                        Text(stringResource(R.string.wardrobe_date_clear))
                    }
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(R.string.wardrobe_date_cancel))
                    }
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ─── BrandAutocompleteField ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrandAutocompleteField(
    query: String,
    allBrands: List<BrandEntity>,
    onQueryChange: (String) -> Unit,
    onBrandSelect: (BrandEntity) -> Unit,
    onAddNewBrand: ((String) -> Unit)? = null
) {
    val filteredBrands = allBrands.filter { it.name.contains(query, ignoreCase = true) }
    val showAddOption = onAddNewBrand != null && query.isNotBlank() &&
            filteredBrands.none { it.name.equals(query, ignoreCase = true) }

    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it); expanded = true },
            label = { Text(stringResource(R.string.wardrobe_field_brand)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded && (filteredBrands.isNotEmpty() || showAddOption),
            onDismissRequest = { expanded = false }
        ) {
            filteredBrands.forEach { brand ->
                DropdownMenuItem(
                    text = { Text(brand.name) },
                    onClick = { onBrandSelect(brand); expanded = false }
                )
            }
            if (showAddOption) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.wardrobe_brand_add, query)) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = { onAddNewBrand(query); expanded = false }
                )
            }
        }
    }
}

