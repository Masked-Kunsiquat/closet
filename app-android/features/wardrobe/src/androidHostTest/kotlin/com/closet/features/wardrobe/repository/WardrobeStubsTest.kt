package com.closet.features.wardrobe.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WardrobeStubsTest {
    @Test fun unsupportedSegmenter_reportsUnsupported() = runTest {
        val s = UnsupportedSubjectSegmenter()
        assertFalse(s.isSupported)
        assertFalse(s.isModelDownloaded())
    }

    @Test fun unsupportedCaptioner_reportsUnsupported_andHasNoBatchState() = runTest {
        val c = UnsupportedImageCaptioner()
        assertFalse(c.isSupported)
        assertFalse(c.isModelDownloaded())
        assertNull(c.progress.value)
        assertNull(c.result.value)
    }
}
