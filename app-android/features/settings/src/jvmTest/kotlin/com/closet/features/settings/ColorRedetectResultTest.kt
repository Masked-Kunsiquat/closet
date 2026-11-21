package com.closet.features.settings

import com.closet.core.data.worker.WorkState
import com.closet.core.data.worker.WorkStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test fun `the result is acknowledged once its snackbar has shown`() = runTest {
        val events = mutableListOf<String>()

        showThenAcknowledge(show = { events += "shown" }, acknowledge = { events += "acknowledged" })

        assertEquals(listOf("shown", "acknowledged"), events)
    }

    @Test fun `a snackbar cancelled while showing leaves the result unacknowledged`() = runTest {
        var acknowledged = false
        val showing = CompletableDeferred<Unit>()

        val effect = launch {
            showThenAcknowledge(
                show = { showing.complete(Unit); awaitCancellation() },
                acknowledge = { acknowledged = true },
            )
        }
        showing.await()
        effect.cancel() // the screen leaves composition while the snackbar is up

        effect.join()
        assertFalse(acknowledged)
    }
}
