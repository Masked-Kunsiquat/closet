package com.closet.features.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

// ─── Data model ───────────────────────────────────────────────────────────────

/** A single named segment in a [SegmentedBar]. */
data class BarSegment(
    val label: String,
    val count: Int,
    val color: Color
)

/** Content shown in a tap tooltip over a [SegmentedBar] segment. */
sealed interface TooltipContent {
    /** A named segment — shows "Label · X%". */
    data class SingleSegment(val label: String, val percent: Int) : TooltipContent

    /** The synthetic "Other" segment — shows percent and the top items collapsed into it. */
    data class OtherDetail(
        val totalPercent: Int,
        val topItems: List<BarSegment>,
        val remaining: Int
    ) : TooltipContent
}

// ─── Grouping logic ───────────────────────────────────────────────────────────

/**
 * Caps the list at [maxVisible] segments by grouping the tail into a synthetic "Other" entry.
 *
 * Returns a pair of (visible segments, hidden segments). The hidden list is kept so the
 * tooltip can surface the top items from it on tap.
 */
internal fun List<BarSegment>.withOtherGroup(
    maxVisible: Int = 8,
    otherColor: Color
): Pair<List<BarSegment>, List<BarSegment>> {
    if (size <= maxVisible) return this to emptyList()
    val sorted = sortedByDescending { it.count }
    val visible = sorted.take(maxVisible - 1).toMutableList()
    val hidden = sorted.drop(maxVisible - 1)
    visible += BarSegment(label = "Other", count = hidden.sumOf { it.count }, color = otherColor)
    return visible to hidden
}

// ─── Hit detection ────────────────────────────────────────────────────────────

/**
 * Maps a tap position to a [TooltipContent] by walking the segment list and accumulating
 * fractional widths until the cumulative fraction covers [tapX] / [barWidthPx].
 *
 * Returns [TooltipContent.SingleSegment] for normal segments or [TooltipContent.OtherDetail]
 * for the synthetic "Other" segment (capped at the top 3 [hiddenSegments] with a remainder count).
 * Returns `null` if [barWidthPx] or [totalCount] is zero.
 */
internal fun resolveTooltip(
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

// ─── Composables ─────────────────────────────────────────────────────────────

/**
 * N-segment horizontal bar where each segment's width is proportional to its [BarSegment.count].
 * Uses [Modifier.weight] so Compose handles normalization — no manual math needed.
 *
 * Tap any segment to show a tooltip with the label and percentage. Tapping the synthetic
 * "Other" segment lists the top 3 items collapsed into it.
 *
 * Use [List.withOtherGroup] to cap segments before passing them here.
 */
@Composable
internal fun SegmentedBar(
    segments: List<BarSegment>,
    hiddenSegments: List<BarSegment> = emptyList(),
    modifier: Modifier = Modifier,
    barHeight: Dp = 20.dp,
    cornerRadius: Dp = 4.dp
) {
    if (segments.isEmpty()) return
    val totalCount = segments.sumOf { it.count }
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
                            totalCount = totalCount
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
        // Dismiss layer — tapping anywhere on the bar hides an open tooltip
        if (activeTooltip != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { detectTapGestures { activeTooltip = null } }
            )
        }
    }

    activeTooltip?.let { tooltip ->
        SegmentTooltip(tooltip = tooltip, onDismiss = { activeTooltip = null })
    }
}

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
