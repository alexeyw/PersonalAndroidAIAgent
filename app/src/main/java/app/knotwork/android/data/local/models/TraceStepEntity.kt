package app.knotwork.android.data.local.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single record of the persistent pipeline-run trace.
 *
 * The table stores two kinds of rows, discriminated by [recordKind]:
 *
 * - **`NODE_IO`** — the input/output of one executed pipeline node ([nodeId], [nodeName],
 *   [inputText], [outputText], [durationMs], [tokenCount]). These rows back the Vars and
 *   Traces tabs of the console for completed runs and the checkpoint/resume mechanism.
 * - **`CONSOLE_EVENT`** — one console log line mirroring
 *   [app.knotwork.android.domain.models.ConsoleEvent]: [consoleEventType] holds the event
 *   type name, [outputText] holds the pre-formatted message, [timestamp] the emission time.
 *
 * [outputText] is deliberately the single payload column for both kinds (node output for
 * `NODE_IO`, console message for `CONSOLE_EVENT`) — the two are mutually exclusive per row,
 * so a dedicated `message` column would always be redundant.
 *
 * Rows written before run-trace persistence have `runId = NULL` and keep legacy semantics
 * (per-session node outputs without run attribution).
 *
 * @property id Auto-generated primary key.
 * @property sessionId The chat session the record belongs to.
 * @property nodeName The type name of the executed pipeline node; empty for `CONSOLE_EVENT` rows.
 * @property outputText The payload: node output for `NODE_IO`, console message for `CONSOLE_EVENT`.
 * @property timestamp Wall-clock time (in millis) when the step completed / the event was emitted.
 * @property durationMs How long the node took to execute, in milliseconds; `0` for console events.
 * @property tokenCount The approximate number of LLM tokens produced by the node, or `null`
 *   for non-LLM nodes (routers, IF-conditions, tool nodes, queue processors, etc.) and
 *   console events.
 * @property runId The pipeline run the record belongs to, or `null` for legacy rows written
 *   before run-trace persistence. Cascade-deletes with the parent `pipeline_runs` row so
 *   retention cleanup of a run removes its trace atomically.
 * @property seq Zero-based monotonic position of the record within its run. The replay/live
 *   seam in the console deduplicates by this number, so it must be unique per run.
 * @property recordKind Discriminator between `NODE_IO` and `CONSOLE_EVENT` rows
 *   (see [KIND_NODE_IO] / [KIND_CONSOLE_EVENT]).
 * @property consoleEventType The [app.knotwork.android.domain.models.ConsoleEventType] name
 *   for `CONSOLE_EVENT` rows; `null` for `NODE_IO` rows.
 * @property nodeId The graph node id for `NODE_IO` rows; `null` for console events and
 *   legacy rows.
 * @property inputText The text the node received as input for `NODE_IO` rows; `null` for
 *   console events and legacy rows.
 */
@Entity(
    tableName = "trace_steps",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("sessionId"),
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PipelineRunEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("runId"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId"]), Index(value = ["runId"])],
)
data class TraceStepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val nodeName: String,
    val outputText: String,
    val timestamp: Long,
    val durationMs: Long = 0,
    val tokenCount: Int? = null,
    val runId: String? = null,
    val seq: Long = 0,
    val recordKind: String = KIND_NODE_IO,
    val consoleEventType: String? = null,
    val nodeId: String? = null,
    val inputText: String? = null,
) {
    companion object {
        /** [recordKind] value of a per-node input/output record. */
        const val KIND_NODE_IO: String = "NODE_IO"

        /** [recordKind] value of a persisted console log event. */
        const val KIND_CONSOLE_EVENT: String = "CONSOLE_EVENT"
    }
}
