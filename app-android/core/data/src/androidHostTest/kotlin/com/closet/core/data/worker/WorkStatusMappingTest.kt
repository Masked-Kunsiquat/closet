package com.closet.core.data.worker

import androidx.work.WorkInfo
import androidx.work.workDataOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class WorkStatusMappingTest {
    private val id = UUID.randomUUID()

    @Test fun runningWorkReadsProgress() {
        val info = WorkInfo(
            id = id,
            state = WorkInfo.State.RUNNING,
            tags = emptySet(),
            progress = workDataOf(
                WorkStatus.KEY_DONE to 3,
                WorkStatus.KEY_TOTAL to 10,
                WorkStatus.KEY_FAILED to 1,
            ),
        )

        assertEquals(WorkStatus(id.toString(), WorkState.RUNNING, 3, 10, 1, 0), info.toWorkStatus())
    }

    @Test fun finishedWorkReadsOutput() {
        val info = WorkInfo(
            id = id,
            state = WorkInfo.State.SUCCEEDED,
            tags = emptySet(),
            outputData = workDataOf(
                WorkStatus.KEY_DONE to 10,
                WorkStatus.KEY_TOTAL to 10,
                WorkStatus.KEY_SKIPPED to 2,
            ),
        )

        val status = info.toWorkStatus()
        assertEquals(WorkState.SUCCEEDED, status.state)
        assertEquals(10, status.done)
        assertEquals(2, status.skipped)
    }

    @Test fun missingKeysMapToZero() {
        val info = WorkInfo(id = id, state = WorkInfo.State.ENQUEUED, tags = emptySet())

        assertEquals(WorkStatus(id.toString(), WorkState.ENQUEUED, 0, 0, 0, 0), info.toWorkStatus())
    }

    @Test fun everyStateMapsToTheSameName() {
        for (state in WorkInfo.State.entries) {
            val status = WorkInfo(id = id, state = state, tags = emptySet()).toWorkStatus()
            assertEquals(state.name, status.state.name)
        }
    }
}
