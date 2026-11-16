package com.closet.features.settings

import com.closet.core.data.ai.NanoInitResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NanoReadinessTest {
    @Test fun `success marks Nano ready`() {
        assertEquals(true, nanoReadyFor(NanoInitResult.Success(tokenLimit = 4096)))
    }

    @Test fun `failure and unsupported clear readiness`() {
        assertEquals(false, nanoReadyFor(NanoInitResult.Failed("boom")))
        assertEquals(false, nanoReadyFor(NanoInitResult.NotSupported))
    }

    @Test fun `downloading leaves readiness unchanged`() {
        assertNull(nanoReadyFor(NanoInitResult.Downloading(progressPct = 40)))
    }
}
