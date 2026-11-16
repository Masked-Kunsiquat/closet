package com.closet.features.stats

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SegmentedBarTest {

    private val red = Color.Red
    private val blue = Color.Blue
    private val gray = Color.Gray

    // ─── withOtherGroup ───────────────────────────────────────────────────────

    @Test
    fun withOtherGroupListWithinLimitReturnsSelfWithEmptyHiddenList() {
        val segs = listOf(BarSegment("A", 5, red), BarSegment("B", 3, blue))
        val (visible, hidden) = segs.withOtherGroup(maxVisible = 8, otherColor = gray)
        assertEquals(segs, visible)
        assertTrue(hidden.isEmpty())
    }

    @Test
    fun withOtherGroupListExactlyAtLimitReturnsSelfWithEmptyHiddenList() {
        val segs = (1..8).map { BarSegment("Item$it", it, red) }
        val (visible, hidden) = segs.withOtherGroup(maxVisible = 8, otherColor = gray)
        assertEquals(segs, visible)
        assertTrue(hidden.isEmpty())
    }

    @Test
    fun withOtherGroupListExceedingLimitCollapsesTailIntoOtherSegment() {
        val segs = (1..10).map { BarSegment("Item$it", it, red) }
        val (visible, hidden) = segs.withOtherGroup(maxVisible = 8, otherColor = gray)
        assertEquals(8, visible.size)          // 7 named + 1 Other
        assertEquals("Other", visible.last().label)
        assertEquals(gray, visible.last().color)
        assertEquals(3, hidden.size)           // 10 items − 7 kept = 3 hidden
    }

    @Test
    fun withOtherGroupOtherSegmentCountEqualsSumOfHiddenSegmentCounts() {
        val segs = (1..10).map { BarSegment("Item$it", it, red) }
        val (visible, hidden) = segs.withOtherGroup(maxVisible = 8, otherColor = gray)
        assertEquals(hidden.sumOf { it.count }, visible.last().count)
    }

    @Test
    fun withOtherGroupVisibleSegmentsAreSortedDescendingByCountBeforeOther() {
        // Need more items than maxVisible to trigger the sort-then-cap code path
        val segs = (1..10).map { BarSegment("Item$it", it, red) }
        val (visible, _) = segs.withOtherGroup(maxVisible = 8, otherColor = gray)
        val counts = visible.filter { it.label != "Other" }.map { it.count }
        assertEquals(counts.sortedDescending(), counts)
    }

    @Test
    fun withOtherGroupEmptyListReturnsEmptyVisibleAndHidden() {
        val (visible, hidden) = emptyList<BarSegment>().withOtherGroup(maxVisible = 8, otherColor = gray)
        assertTrue(visible.isEmpty())
        assertTrue(hidden.isEmpty())
    }

    // ─── resolveTooltip ───────────────────────────────────────────────────────

    @Test
    fun resolveTooltipReturnsNullWhenBarWidthPxIsZero() {
        val segs = listOf(BarSegment("A", 5, red))
        val result = resolveTooltip(tapX = 0f, barWidthPx = 0, segments = segs, hiddenSegments = emptyList(), totalCount = 5)
        assertNull(result)
    }

    @Test
    fun resolveTooltipReturnsNullWhenTotalCountIsZero() {
        val segs = listOf(BarSegment("A", 0, red))
        val result = resolveTooltip(tapX = 50f, barWidthPx = 100, segments = segs, hiddenSegments = emptyList(), totalCount = 0)
        assertNull(result)
    }

    @Test
    fun resolveTooltipTapInFirstHalfReturnsSingleSegmentForFirstSegment() {
        val segs = listOf(BarSegment("Alpha", 50, red), BarSegment("Beta", 50, blue))
        val result = resolveTooltip(tapX = 10f, barWidthPx = 100, segments = segs, hiddenSegments = emptyList(), totalCount = 100)
        assertTrue(result is TooltipContent.SingleSegment)
        val single = result as TooltipContent.SingleSegment
        assertEquals("Alpha", single.label)
        assertEquals(50, single.percent)
    }

    @Test
    fun resolveTooltipTapInSecondSegmentReturnsCorrectLabel() {
        val segs = listOf(BarSegment("Alpha", 30, red), BarSegment("Beta", 70, blue))
        val result = resolveTooltip(tapX = 80f, barWidthPx = 100, segments = segs, hiddenSegments = emptyList(), totalCount = 100)
        assertTrue(result is TooltipContent.SingleSegment)
        assertEquals("Beta", (result as TooltipContent.SingleSegment).label)
    }

    @Test
    fun resolveTooltipPercentIsComputedCorrectly() {
        val segs = listOf(BarSegment("Alpha", 1, red), BarSegment("Beta", 3, blue))
        val result = resolveTooltip(tapX = 1f, barWidthPx = 100, segments = segs, hiddenSegments = emptyList(), totalCount = 4)
        assertEquals(25, (result as TooltipContent.SingleSegment).percent)
    }

    @Test
    fun resolveTooltipTappingOtherSegmentReturnsOtherDetail() {
        val hidden = listOf(BarSegment("X", 2, red), BarSegment("Y", 1, blue))
        val segs = listOf(BarSegment("Alpha", 97, red), BarSegment("Other", 3, gray, isSyntheticOther = true))
        val result = resolveTooltip(tapX = 99f, barWidthPx = 100, segments = segs, hiddenSegments = hidden, totalCount = 100)
        assertTrue(result is TooltipContent.OtherDetail)
        val detail = result as TooltipContent.OtherDetail
        assertEquals(3, detail.totalPercent)
        assertEquals(hidden, detail.topItems)
        assertEquals(0, detail.remaining)
    }

    @Test
    fun resolveTooltipARealSegmentNamedOtherIsASingleSegment() {
        // Only the synthetic segment from withOtherGroup is "Other"; a user's own "Other" label isn't.
        val segs = listOf(BarSegment("Alpha", 97, red), BarSegment("Other", 3, blue))
        val result = resolveTooltip(tapX = 99f, barWidthPx = 100, segments = segs, hiddenSegments = emptyList(), totalCount = 100)
        assertEquals(TooltipContent.SingleSegment(label = "Other", percent = 3), result)
    }

    @Test
    fun resolveTooltipOtherDetailCapsTopItemsAt3AndReportsRemainingCorrectly() {
        val hidden = (1..6).map { BarSegment("H$it", it, red) }
        val segs = listOf(BarSegment("Alpha", 79, red), BarSegment("Other", 21, gray, isSyntheticOther = true))
        val result = resolveTooltip(tapX = 99f, barWidthPx = 100, segments = segs, hiddenSegments = hidden, totalCount = 100)
        val detail = result as TooltipContent.OtherDetail
        assertEquals(3, detail.topItems.size)
        assertEquals(3, detail.remaining) // 6 hidden − 3 shown
    }
}
