package app.knotwork.android.domain.models

/**
 * Persistent record of a single pipeline run.
 *
 * A run is created when an [AgentTask] is enqueued and survives process death:
 * unlike the in-memory orchestrator state flows, this record lets the app
 * reconstruct what happened to a run after Doze, an OOM kill, or a swipe from
 * recents. The record id equals the id of the [AgentTask] that started the
 * run — task and run relate strictly one-to-one, so no second identifier is
 * minted.
 *
 * @property id Unique identifier of the run (UUID). Equal to [AgentTask.id].
 * @property sessionId Id of the chat session the run belongs to.
 * @property pipelineId Id of the pipeline executing the run. `null` while the
 *   run is still [PipelineRunStatus.QUEUED]: the pipeline is resolved (session
 *   binding → application default) only when processing starts, so a queued
 *   run may not have a concrete pipeline yet.
 * @property origin What triggered the run — an interactive chat message or the
 *   background scheduler. See [RunOrigin].
 * @property status Current lifecycle status. See [PipelineRunStatus].
 * @property currentNodeId Id of the graph node currently executing (or the
 *   last node that started). `null` before the first node starts.
 * @property startedAt Epoch millis when the run was enqueued.
 * @property finishedAt Epoch millis when the run reached a terminal status;
 *   `null` while the run is still active.
 * @property errorMessage Human-readable failure or interruption reason.
 *   `null` unless the run finished with [PipelineRunStatus.FAILED] or
 *   [PipelineRunStatus.INTERRUPTED].
 * @property graphContentHash Content hash of the executing pipeline graph
 *   captured at the moment the run transitioned to
 *   [PipelineRunStatus.RUNNING] (see `PipelineGraph.contentHash`). Used to
 *   invalidate checkpoint resume when the graph was edited between
 *   interruption and resume. `null` while the run is still queued.
 * @property userPrompt The user message that started the run, captured at
 *   enqueue time. Checkpoint resume feeds it back into the engine as the
 *   immutable `originalUserMessage` (context blocks, lazy memory retrieval,
 *   INPUT-node passthrough) — recovering it from chat history would be
 *   ambiguous. `null` only for records written before this field existed;
 *   such runs cannot be resumed.
 * @property parentRunId Id of the parent run when this run is a sub-pipeline
 *   spawned by a `PIPELINE` node; `null` for a top-level run. The parent/child
 *   links form the run tree the nested console, the shared step budget and the
 *   resume-across-boundary mechanism rely on. The root of a tree is reached by
 *   walking [parentRunId] up until it is `null`.
 */
data class PipelineRun(
    val id: String,
    val sessionId: String,
    val pipelineId: String?,
    val origin: RunOrigin,
    val status: PipelineRunStatus,
    val currentNodeId: String?,
    val startedAt: Long,
    val finishedAt: Long?,
    val errorMessage: String?,
    val graphContentHash: String?,
    val userPrompt: String? = null,
    val parentRunId: String? = null,
)

/**
 * Source that triggered a pipeline run.
 */
enum class RunOrigin {
    /** The run was started by an interactive chat message. */
    CHAT,

    /** The run was started by the background task scheduler. */
    SCHEDULER,
}

/**
 * Lifecycle status of a persistent pipeline run.
 *
 * Transitions: [QUEUED] → [RUNNING] → ([WAITING_APPROVAL] | [WAITING_CLARIFICATION] → [RUNNING])*
 * → terminal ([COMPLETED] | [FAILED] | [CANCELLED]). [INTERRUPTED] is the terminal status applied
 * to QUEUED/RUNNING records whose owning process died before the run could finish (orphan sweep
 * at application start). User-initiated cancellation maps to [CANCELLED], never to [FAILED] —
 * stopping a run is not a failure.
 */
enum class PipelineRunStatus {
    /** Enqueued, waiting for the worker to pick the task up. */
    QUEUED,

    /** The execution engine is actively walking the graph. */
    RUNNING,

    /** Suspended on a human-in-the-loop tool approval. */
    WAITING_APPROVAL,

    /** Suspended on a clarification question to the user. */
    WAITING_CLARIFICATION,

    /** Terminal: the run reached the OUTPUT node and produced a final answer. */
    COMPLETED,

    /** Terminal: the run failed with an error. */
    FAILED,

    /** Terminal: the user cancelled the run. Not a failure. */
    CANCELLED,

    /** Terminal: the owning process died while the run was active. */
    INTERRUPTED,

    ;

    /**
     * Whether this status is terminal — once written, the run record must
     * never transition again (enforced by the repository's guarded updates).
     */
    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED || this == INTERRUPTED
}
