package com.closet.features.settings

import com.closet.core.data.worker.WorkState
import com.closet.core.data.worker.WorkStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When the bulk colour re-detect result snackbar shows (#128). */
class ColorRedetectResultTest {
    private fun status(state: WorkState = WorkState.SUCCEEDED, total: Int = 3, id: String = "run-1") =
        WorkStatus(id, state, done = 2, total = total, failed = 0, skipped = 1)

    @Test fun `an unhandled finished run with items shows`() {
        assertTrue(status().showsColorRedetectResult(lastHandledId = null))
        assertTrue(status().showsColorRedetectResult(lastHandledId = "older"))
    }

    @Test fun `a run with nothing queued shows nothing`() {
        assertFalse(status(total = 0).showsColorRedetectResult(lastHandledId = null))
    }

    @Test fun `a handled run shows nothing`() {
        assertFalse(status().showsColorRedetectResult(lastHandledId = "run-1"))
    }

    @Test fun `an unfinished or failed run shows nothing`() {
        assertFalse(status(state = WorkState.RUNNING).showsColorRedetectResult(lastHandledId = null))
        assertFalse(status(state = WorkState.FAILED).showsColorRedetectResult(lastHandledId = null))
    }
}
