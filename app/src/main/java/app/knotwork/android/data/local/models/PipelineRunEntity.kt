package app.knotwork.android.data.local.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity persisting one pipeline run in the `pipeline_runs` table.
 *
 * Mirrors the domain model `PipelineRun`; enum-typed fields (`origin`,
 * `status`) are stored as their `name` strings for forward-compatible,
 * human-debuggable rows. There is deliberately **no** foreign key on
 * [sessionId]: a run record is created at enqueue time, which for
 * scheduler-originated tasks precedes the creation of the chat-session row
 * itself (the session is auto-created when the first message is saved).
 * This follows the `chat_messages` precedent; cleanup on session deletion is
 * explicit via `PipelineRunDao.deleteRunsForSession`.
 *
 * @property id Unique run id (UUID), equal to the id of the originating agent task.
 * @property sessionId Id of the owning chat session (indexed, no FK — see above).
 * @property pipelineId Id of the resolved pipeline; `null` while the run is queued.
 * @property origin `RunOrigin` name: what triggered the run (`CHAT` / `SCHEDULER`).
 * @property status `PipelineRunStatus` name (indexed — the orphan sweep and the
 *   reattach protocol both query by status).
 * @property currentNodeId Id of the last graph node that started executing.
 * @property startedAt Epoch millis when the run was enqueued.
 * @property finishedAt Epoch millis of the terminal transition; `null` while active.
 * @property errorMessage Failure / interruption reason for FAILED and INTERRUPTED runs.
 * @property graphContentHash Content hash of the executing graph, captured at the
 *   RUNNING transition; `null` while queued.
 */
@Entity(
    tableName = "pipeline_runs",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["status"]),
    ],
)
data class PipelineRunEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val pipelineId: String?,
    val origin: String,
    val status: String,
    val currentNodeId: String?,
    val startedAt: Long,
    val finishedAt: Long?,
    val errorMessage: String?,
    val graphContentHash: String?,
)
