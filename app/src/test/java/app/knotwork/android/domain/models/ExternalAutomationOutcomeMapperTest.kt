package app.knotwork.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Verifies [externalAutomationStatusForTerminal] — the deliberately coarse mapping
 * from the app's four terminal statuses onto the two settled statuses the
 * third-party contract publishes.
 */
class ExternalAutomationOutcomeMapperTest {

    @Test
    fun `given a completed run when mapped then the caller is told Completed`() {
        assertEquals(
            ExternalAutomationStatus.Completed,
            externalAutomationStatusForTerminal(PipelineRunStatus.COMPLETED),
        )
    }

    @Test
    fun `given any unsuccessful terminal status when mapped then the caller is told Failed`() {
        // Deliberately collapsed. The distinction between a defect, a user
        // cancellation and a platform kill is kept inside the app (the trigger
        // journal exists to keep them apart) and is unactionable in another process.
        listOf(PipelineRunStatus.FAILED, PipelineRunStatus.CANCELLED, PipelineRunStatus.INTERRUPTED)
            .forEach { status ->
                assertEquals(
                    "Expected Failed for $status",
                    ExternalAutomationStatus.Failed,
                    externalAutomationStatusForTerminal(status),
                )
            }
    }

    @Test
    fun `given a non-terminal status when mapped then it throws rather than inventing an outcome`() {
        listOf(
            PipelineRunStatus.QUEUED,
            PipelineRunStatus.RUNNING,
            PipelineRunStatus.WAITING_APPROVAL,
            PipelineRunStatus.WAITING_CLARIFICATION,
        ).forEach { status ->
            assertThrows(IllegalArgumentException::class.java) {
                externalAutomationStatusForTerminal(status)
            }
        }
    }

    @Test
    fun `given every terminal status when mapped then all are covered`() {
        // Guards the exhaustive `when`: a future terminal status must be classified
        // deliberately rather than fall into a default.
        PipelineRunStatus.entries.filter { it.isTerminal }.forEach { status ->
            externalAutomationStatusForTerminal(status)
        }
    }
}
