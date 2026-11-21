package com.closet.features.wardrobe

import kotlin.test.Test
import kotlin.test.assertEquals

/** The Live Update progress scale for the bulk workers' notifications (#128). */
class WorkProgressTest {
    @Test fun `progress is a percentage of the ProgressStyle default maximum`() {
        assertEquals(0, liveUpdateProgress(0, 4))
        assertEquals(25, liveUpdateProgress(1, 4))
        assertEquals(66, liveUpdateProgress(2, 3))
        assertEquals(100, liveUpdateProgress(4, 4))
    }

    @Test fun `an empty queue is zero and progress never passes the maximum`() {
        assertEquals(0, liveUpdateProgress(0, 0))
        assertEquals(100, liveUpdateProgress(5, 4))
    }
}
