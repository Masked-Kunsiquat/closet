package com.closet.features.settings

import com.closet.core.data.ai.NanoAvailability
import com.closet.core.data.ai.NanoInitResult
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

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

    @Test fun `each init result maps to its availability`() {
        assertEquals(NanoAvailability.READY, nanoAvailabilityFor(NanoInitResult.Success(tokenLimit = 4096)))
        assertEquals(NanoAvailability.DOWNLOADING, nanoAvailabilityFor(NanoInitResult.Downloading(progressPct = 40)))
        assertEquals(NanoAvailability.NOT_SUPPORTED, nanoAvailabilityFor(NanoInitResult.NotSupported))
        assertEquals(NanoAvailability.FAILED, nanoAvailabilityFor(NanoInitResult.Failed("boom")))
    }
}
