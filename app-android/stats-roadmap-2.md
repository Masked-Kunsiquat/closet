# Stats Screen v2 — Implementation Roadmap

## Current State Summary

| What | Status |
|---|---|
| Period selector (All / 30d / 90d / This Year) | ✅ Done |
| Headline cards (items · worn % · value) | ✅ Done |
| Most Worn horizontal scroll | ✅ Done |
| Cost Per Wear ranked list | ✅ Done |
| Total Logs callout | ✅ Done |
| Wear by Category (LinearProgressIndicator bars) | ✅ Done (data + basic UI) |
| Never Worn collapsible list | ✅ Done |
| Category Breakdown (item count) | ⚠️ Data exists in UiState — not rendered |
| Subcategory Breakdown | ❌ No DAO query, no UI |
| Color Breakdown | ❌ No DAO query, no UI |
| Occasion Breakdown | ❌ No DAO query, no UI |
| Wash Status (Clean vs Dirty) | ❌ No DAO query, no UI |
| Vico bar chart for Wear by Category | ❌ Not added |

---

## Chart strategy

- **Vico (`vico-compose-m3`)** — use for **Wear by Category** only. It's the one section
  with frequency/comparison data where animated column bars with axis labels add real value.
- **Custom Compose progress bars** — use for all breakdown sections (category count,
  subcategory, occasion). Already established by the existing `CategoryWearBar` pattern.
- **Color swatches + progress bars** — custom for Color Breakdown. Show the actual color
  dot next to the label; a chart library can't do this.
- **Two-card layout** — for Wash Status (Clean / Dirty). Two numbers side by side,
  no bar needed.

---

## Phase 1 — Data layer

### 1a. New data class: `ColorBreakdownRow`

Add to `StatsDao.kt` alongside the other projection classes:

```kotlin
data class ColorBreakdownRow(
    val label: String,
    val hex: String,
    val count: Int
)
```

`BreakdownRow` can't carry `hex`, so this is a dedicated type.

### 1b. Four new DAO queries in `StatsDao`

```kotlin
/** Item count per subcategory. Only items that have a subcategory are included. */
@Query("""
    SELECT s.name AS label, COUNT(DISTINCT ci.id) AS count
    FROM clothing_items ci
    JOIN subcategories s ON s.id = ci.subcategory_id
    WHERE ci.status = 'Active'
    GROUP BY s.id
    ORDER BY count DESC
""")
fun getBreakdownBySubcategory(): Flow<List<BreakdownRow>>

/** Item count per color (via junction table). Carries the hex value for swatch rendering. */
@Query("""
    SELECT co.name AS label, co.hex, COUNT(DISTINCT ci.id) AS count
    FROM clothing_items ci
    JOIN clothing_item_colors cic ON cic.clothing_item_id = ci.id
    JOIN colors co ON co.id = cic.color_id
    WHERE ci.status = 'Active'
    GROUP BY co.id
    ORDER BY count DESC
""")
fun getBreakdownByColor(): Flow<List<ColorBreakdownRow>>

/** Item count per occasion (via junction table). */
@Query("""
    SELECT o.name AS label, COUNT(DISTINCT ci.id) AS count
    FROM clothing_items ci
    JOIN clothing_item_occasions cio ON cio.clothing_item_id = ci.id
    JOIN occasions o ON o.id = cio.occasion_id
    WHERE ci.status = 'Active'
    GROUP BY o.id
    ORDER BY count DESC
""")
fun getBreakdownByOccasion(): Flow<List<BreakdownRow>>

/** Clean vs Dirty count. Returns at most 2 rows with label = 'Clean' or 'Dirty'. */
@Query("""
    SELECT wash_status AS label, COUNT(*) AS count
    FROM clothing_items
    WHERE status = 'Active'
    GROUP BY wash_status
""")
fun getWashStatusBreakdown(): Flow<List<BreakdownRow>>
```

### 1c. Four new repository methods in `StatsRepository`

```kotlin
fun getSubcategoryBreakdown(): Flow<List<BreakdownRow>> =
    statsDao.getBreakdownBySubcategory()

fun getColorBreakdown(): Flow<List<ColorBreakdownRow>> =
    statsDao.getBreakdownByColor()

fun getOccasionBreakdown(): Flow<List<BreakdownRow>> =
    statsDao.getBreakdownByOccasion()

fun getWashStatusBreakdown(): Flow<List<BreakdownRow>> =
    statsDao.getWashStatusBreakdown()
```

### 1d. Extend `StatsUiState`

```kotlin
data class StatsUiState(
    // existing fields unchanged …
    val subcategoryBreakdown: List<BreakdownRow> = emptyList(),  // NEW
    val colorBreakdown: List<ColorBreakdownRow> = emptyList(),   // NEW
    val occasionBreakdown: List<BreakdownRow> = emptyList(),     // NEW
    val washStatus: List<BreakdownRow> = emptyList(),            // NEW
)
```

### 1e. Wire into `StatsViewModel`

The four new queries are period-independent — fold them into the outer `combine` alongside
`getCategoryBreakdown()` and `getNeverWornItems()`.

`combine` accepts up to 5 flows at once; with the existing 3 + 4 new = 7 period-independent
flows you'll need to nest two `combine` calls or use `combine` on a list. Simplest pattern:

```kotlin
val uiState: StateFlow<StatsUiState> = combine(
    periodData,
    combine(
        statsRepository.getCategoryBreakdown(),
        statsRepository.getSubcategoryBreakdown(),
        statsRepository.getColorBreakdown(),
        statsRepository.getOccasionBreakdown(),
    ) { cat, sub, color, occasion -> Quad(cat, sub, color, occasion) },
    combine(
        statsRepository.getWashStatusBreakdown(),
        statsRepository.getNeverWornItems(),
    ) { wash, never -> wash to never }
) { pd, composition, misc ->
    StatsUiState(
        // period-sensitive
        overview = pd.overview,
        mostWorn = pd.mostWorn,
        costPerWear = pd.costPerWear,
        categoryWear = pd.categoryWear,
        totalLogsCount = pd.totalLogsCount,
        selectedPeriod = pd.period,
        // composition
        categoryCount = composition.first,
        subcategoryBreakdown = composition.second,
        colorBreakdown = composition.third,
        occasionBreakdown = composition.fourth,
        // misc
        washStatus = misc.first,
        neverWorn = misc.second,
    )
}
```

Use a local `data class Quad<A,B,C,D>` or Kotlin `Triple` + one extra field — whatever
keeps it readable.

---

## Phase 2 — Vico integration

### 2a. Add dependency

`gradle/libs.versions.toml`:
```toml
[versions]
vico = "3.0.3"

[libraries]
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }
```

`features/stats/build.gradle.kts`:
```kotlin
implementation(libs.vico.compose.m3)
```

### 2b. Replace `CategoryWearSection` with a Vico column chart

Replace the current `LinearProgressIndicator` loop in `CategoryWearSection` with a
`CartesianChart` from Vico:

```kotlin
@Composable
internal fun CategoryWearSection(rows: List<BreakdownRow>, modifier: Modifier = Modifier) {
    SectionHeader(stringResource(R.string.stats_section_wear_by_category))

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(rows) {
        modelProducer.runTransaction {
            columnSeries { series(rows.map { it.count }) }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { _, x, _ ->
                    rows.getOrNull(x.toInt())?.label ?: ""
                }
            )
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp)
    )
}
```

Vico's M3 module picks up your `MaterialTheme` colors automatically — no manual color wiring.

---

## Phase 3 — New breakdown chart components

All new components follow the existing `CategoryWearBar` pattern (label left, count right,
progress bar below) and live in `StatsComponents.kt`.

### 3a. `CategoryCountSection` — item count per category

Data: `uiState.categoryCount` (already in UiState, just not rendered).
UI: reuse the existing `BreakdownSection` helper below — identical to the current
`CategoryWearBar` pattern.

### 3b. `SubcategoryBreakdownSection`

Data: `uiState.subcategoryBreakdown`.
UI: same `BreakdownSection` helper. Only render if list is non-empty (many items have no
subcategory).

### 3c. `ColorBreakdownSection`

Data: `uiState.colorBreakdown: List<ColorBreakdownRow>`.
UI: custom row — replace the label `Text` with a `Row` containing a 12dp filled circle
(the color swatch) + the color name:

```kotlin
@Composable
private fun ColorBreakdownRow(row: ColorBreakdownRow, maxCount: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(row.hex)))
                )
                Text(text = row.label, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = row.count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { row.count.toFloat() / maxCount },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        )
    }
}
```

Guard: parse `hex` safely with a try/catch around `Color.parseColor` — fall back to
`MaterialTheme.colorScheme.primary` if the hex is malformed.

### 3d. `OccasionBreakdownSection`

Data: `uiState.occasionBreakdown`.
UI: same `BreakdownSection` helper.

### 3e. `WashStatusSection` — Clean vs Dirty

Data: `uiState.washStatus` (2-row list, labels "Clean" / "Dirty").
UI: two `StatHeadlineCard`s side by side (reuse the existing private composable). Extract
the clean and dirty counts from the list by matching the label string.

```kotlin
@Composable
internal fun WashStatusSection(rows: List<BreakdownRow>, modifier: Modifier = Modifier) {
    val clean = rows.firstOrNull { it.label == "Clean" }?.count ?: 0
    val dirty = rows.firstOrNull { it.label == "Dirty" }?.count ?: 0
    SectionHeader(stringResource(R.string.stats_section_wash_status))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatHeadlineCard(
            value = clean.toString(),
            label = stringResource(R.string.stats_wash_clean),
            accessibilityLabel = "$clean items clean",
            modifier = Modifier.weight(1f)
        )
        StatHeadlineCard(
            value = dirty.toString(),
            label = stringResource(R.string.stats_wash_dirty),
            accessibilityLabel = "$dirty items need washing",
            modifier = Modifier.weight(1f)
        )
    }
}
```

Note: `StatHeadlineCard` is currently `private` in `StatsComponents.kt` — make it
`internal` so `WashStatusSection` can use it.

### 3f. `MultipleLinearProgressIndicator` — dual-layer progress bar

For sections where two related metrics share the same scale, use a stacked two-layer bar:

```kotlin
@Composable
fun MultipleLinearProgressIndicator(
    primaryProgress: Float,    // foreground — accent color
    secondaryProgress: Float,  // background fill — muted color
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.primaryContainer,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    clipShape: Shape = RoundedCornerShape(3.dp)
) {
    Box(
        modifier = modifier
            .clip(clipShape)
            .background(backgroundColor)
            .height(6.dp)
    ) {
        Box(
            modifier = Modifier
                .background(secondaryColor)
                .fillMaxHeight()
                .fillMaxWidth(secondaryProgress)
        )
        Box(
            modifier = Modifier
                .background(primaryColor)
                .fillMaxHeight()
                .fillMaxWidth(primaryProgress)
        )
    }
}
```

**Where to use it:** The Category section is the prime candidate. Each category bar shows:
- **Secondary (muted)**: item count proportion — how much of your wardrobe is this category
- **Primary (accent)**: wear count proportion — how often you actually wear it

This makes "I own a lot of Bottoms but barely wear them" immediately visible without
reading numbers. Normalize both against the same max (e.g. `max(maxItemCount, maxWearCount)`).

For all other breakdown sections (subcategory, occasion, color), a single-layer bar is
sufficient — they show one metric only.

### 3g. `SegmentedBar` — N-segment horizontal bar (pie chart alternative)

Vico has no pie chart. A horizontal segmented bar built from `Row` + `Modifier.weight()` gives
the same at-a-glance proportional read without a third-party dependency.

`Modifier.weight(value)` distributes space proportionally to each segment's raw count — no
manual normalization to `[0, 1]` required. The layout engine does it automatically.

#### Data model

```kotlin
/** A single named segment in the bar. */
data class BarSegment(
    val label: String,
    val count: Int,
    val color: Color
)
```

#### "Other" grouping logic

Cap the bar at **8 visible segments** (beyond that, slivers are unreadable). Sort descending
by count, take the top 7, group the remainder into a synthetic "Other" segment:

```kotlin
fun List<BarSegment>.withOtherGroup(
    maxVisible: Int = 8,
    otherColor: Color
): Pair<List<BarSegment>, List<BarSegment>> {   // visible, hidden
    if (size <= maxVisible) return this to emptyList()
    val sorted = sortedByDescending { it.count }
    val visible = sorted.take(maxVisible - 1).toMutableList()
    val hidden = sorted.drop(maxVisible - 1)
    val otherCount = hidden.sumOf { it.count }
    visible += BarSegment(label = "Other", count = otherCount, color = otherColor)
    return visible to hidden
}
```

`hidden` is kept in state so the tooltip can surface the top items from it.

#### Composable

```kotlin
@Composable
internal fun SegmentedBar(
    segments: List<BarSegment>,
    hiddenSegments: List<BarSegment> = emptyList(),   // "Other" detail
    modifier: Modifier = Modifier,
    barHeight: Dp = 20.dp,
    cornerRadius: Dp = 4.dp
) {
    if (segments.isEmpty()) return

    var barWidthPx by remember { mutableStateOf(0) }
    var activeTooltip by remember { mutableStateOf<TooltipContent?>(null) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(cornerRadius))
                .onSizeChanged { barWidthPx = it.width }
                .pointerInput(segments) {
                    detectTapGestures { offset ->
                        activeTooltip = resolveTooltip(
                            tapX = offset.x,
                            barWidthPx = barWidthPx,
                            segments = segments,
                            hiddenSegments = hiddenSegments,
                            totalCount = segments.sumOf { it.count }
                        )
                    }
                }
        ) {
            segments.forEach { seg ->
                Box(
                    modifier = Modifier
                        .weight(seg.count.toFloat())
                        .fillMaxHeight()
                        .background(seg.color)
                )
            }
        }

        activeTooltip?.let { tooltip ->
            // Dismiss on tap anywhere outside
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { activeTooltip = null } }
            )
            SegmentTooltip(
                tooltip = tooltip,
                onDismiss = { activeTooltip = null }
            )
        }
    }
}
```

#### Tap hit detection

Map the tap X coordinate to a segment using cumulative fraction math:

```kotlin
private fun resolveTooltip(
    tapX: Float,
    barWidthPx: Int,
    segments: List<BarSegment>,
    hiddenSegments: List<BarSegment>,
    totalCount: Int
): TooltipContent? {
    if (barWidthPx == 0 || totalCount == 0) return null
    val fraction = tapX / barWidthPx
    var cumulative = 0f
    for (seg in segments) {
        cumulative += seg.count.toFloat() / totalCount
        if (fraction <= cumulative) {
            return if (seg.label == "Other") {
                TooltipContent.OtherDetail(
                    totalPercent = seg.count * 100 / totalCount,
                    topItems = hiddenSegments.take(3),
                    remaining = (hiddenSegments.size - 3).coerceAtLeast(0)
                )
            } else {
                TooltipContent.SingleSegment(
                    label = seg.label,
                    percent = seg.count * 100 / totalCount
                )
            }
        }
    }
    return null
}
```

#### Tooltip content model

```kotlin
sealed interface TooltipContent {
    data class SingleSegment(val label: String, val percent: Int) : TooltipContent
    data class OtherDetail(
        val totalPercent: Int,
        val topItems: List<BarSegment>,
        val remaining: Int
    ) : TooltipContent
}
```

#### Tooltip display

Use a simple `Surface` + `Column` card positioned near the tap. A `Popup` is the cleanest
approach — it floats above all content without disrupting layout:

```kotlin
@Composable
private fun SegmentTooltip(tooltip: TooltipContent, onDismiss: () -> Unit) {
    Popup(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (tooltip) {
                    is TooltipContent.SingleSegment -> {
                        Text(
                            text = "${tooltip.label} · ${tooltip.percent}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is TooltipContent.OtherDetail -> {
                        Text(
                            text = "Other · ${tooltip.totalPercent}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        tooltip.topItems.forEach { item ->
                            Text(
                                text = "• ${item.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (tooltip.remaining > 0) {
                            Text(
                                text = "+ ${tooltip.remaining} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
```

#### Where each pattern applies

| Section | Component | Why |
|---------|-----------|-----|
| Wear by Category | Vico `CartesianChart` | Frequency comparison — axis labels and animation add real value |
| Category item count vs wear (dual metric) | `MultipleLinearProgressIndicator` | Two metrics on one scale; wear vs owned |
| Color Breakdown | `SegmentedBar` | Many colors; proportional share at a glance; color swatches as segment fills |
| Occasion Breakdown | `SegmentedBar` | 5–10 occasions; tap tooltip replaces clutter |
| Subcategory Breakdown | `BreakdownSection` (progress bars) | Often only 2–4 entries; bar list is clearer than segment |
| Wash Status | `WashStatusSection` (two cards) | Binary; a bar is unnecessary |

#### Color assignment for `SegmentedBar`

Don't hardcode colors. Map each segment to a color from a fixed palette derived from
`MaterialTheme.colorScheme` extended tokens, cycling if needed:

```kotlin
private val segmentPalette: List<Color>
    @Composable get() = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.inversePrimary,
        MaterialTheme.colorScheme.outline,
    )

// Usage when building segments from BreakdownRow:
val palette = segmentPalette
val otherColor = MaterialTheme.colorScheme.surfaceVariant
val allSegments = rows.mapIndexed { i, row ->
    BarSegment(label = row.label, count = row.count, color = palette[i % palette.size])
}
val (visible, hidden) = allSegments.withOtherGroup(otherColor = otherColor)
```

For `ColorBreakdownSection` specifically, use the actual hex value from `ColorBreakdownRow`
as the segment fill — the swatch IS the bar:

```kotlin
val allSegments = colorRows.map { row ->
    val color = runCatching {
        Color(android.graphics.Color.parseColor(row.hex))
    }.getOrElse { MaterialTheme.colorScheme.primary }
    BarSegment(label = row.label, count = row.count, color = color)
}
```

---

### 3g. Shared `BreakdownSection` helper

Extract the repeated "label + count + progress bar" pattern into one reusable composable
rather than duplicating it for category count, subcategory, and occasion:

```kotlin
@Composable
internal fun BreakdownSection(
    title: String,
    rows: List<BreakdownRow>,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) return
    val maxCount = rows.maxOf { it.count }.takeIf { it > 0 } ?: 1
    SectionHeader(title)
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            BreakdownBar(row = row, maxCount = maxCount)
        }
        Spacer(Modifier.height(4.dp))
    }
}
```

---

## Phase 4 — Screen layout update

Update `StatsContent` in `StatsScreen.kt` to include all new sections. New layout order:

```
Period selector
Headline cards (items · worn % · value)
─── Activity (shown only if totalLogsCount > 0) ──────────────
  Most Worn
  Cost Per Wear
  Total Logs Callout
  Wear by Category [Vico chart]
─── Wardrobe Composition (always shown if totalItems > 0) ────
  Wash Status (Clean / Dirty)
  Category Breakdown (item count)
  Subcategory Breakdown
  Color Breakdown
  Occasion Breakdown
─── Never Worn (collapsible, always shown) ───────────────────
  Never Worn
```

Add string resources for all new section headers and accessibility labels.

---

## Phase 5 — String resources

New strings needed in `features/stats/src/main/res/values/strings.xml`:

```xml
<!-- Section headers -->
<string name="stats_section_wash_status">Clean vs Dirty</string>
<string name="stats_section_category_count">Wardrobe by Category</string>
<string name="stats_section_subcategory">Wardrobe by Subcategory</string>
<string name="stats_section_color">Wardrobe by Color</string>
<string name="stats_section_occasion">Wardrobe by Occasion</string>

<!-- Wash status labels -->
<string name="stats_wash_clean">Clean</string>
<string name="stats_wash_dirty">Dirty</string>
```

---

## Execution Order

```
Phase 1  →  Phase 2  →  Phase 3  →  Phase 4  →  Phase 5
```

Phases 2 and 3 can be developed in parallel once Phase 1 is complete (they depend on
data but not on each other). Phase 5 can be done alongside Phase 3.

---

## Risk Notes

- **`combine` arity limit**: Kotlin's `combine` tops out at 5 flows. With 7 period-independent
  flows, nest two `combine` calls as shown in Phase 1e — do not chain `.zip()` (it doesn't
  resubscribe on upstream changes correctly).
- **Hex parsing**: `android.graphics.Color.parseColor` throws on malformed input. Wrap in
  `runCatching` and fall back to a neutral color.
- **Empty subcategory list**: Many items may have no subcategory assigned. The query uses
  `JOIN` (not `LEFT JOIN`) so items without a subcategory are excluded. `BreakdownSection`
  guards on `rows.isEmpty()` so no empty header is shown.
- **Vico version**: Use `3.0.3`. v3 broke the Compose module naming (it's now `compose-m3`,
  not `compose`). Do not use v2.
- **`StatHeadlineCard` visibility**: Currently `private`. Make `internal` before reusing it
  in `WashStatusSection`.
- **`SegmentedBar` zero count**: A segment with `count = 0` gets `weight(0f)` — Compose
  renders it as zero width, which is safe. Guard `withOtherGroup` against an all-zero list
  (return early) to avoid division by zero in the tooltip percent calculation.
- **Tooltip `Popup` positioning**: `Popup` renders at the top of the screen by default.
  Pass `alignment = Alignment.Center` or use `offset` to anchor it near the tap point.
  Keep it simple — centered is fine for MVP.
- **Tap area accuracy**: `onSizeChanged` gives the bar's pixel width at layout time. If the
  bar width changes (e.g. orientation change), the state updates automatically on next
  recomposition. No special handling needed.
