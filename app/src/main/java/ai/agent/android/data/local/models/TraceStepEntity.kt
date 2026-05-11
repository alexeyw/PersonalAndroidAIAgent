package ai.agent.android.data.local.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single execution step of the pipeline trace in the local database.
 *
 * @property id Auto-generated primary key.
 * @property sessionId The chat session the step belongs to.
 * @property nodeName The type name of the executed pipeline node.
 * @property outputText The text the node produced.
 * @property timestamp Wall-clock time (in millis) when the step completed.
 * @property durationMs How long the node took to execute, in milliseconds.
 * @property tokenCount The approximate number of LLM tokens produced by the node, or `null`
 *   for non-LLM nodes (routers, IF-conditions, tool nodes, queue processors, etc.).
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
    ],
    indices = [Index(value = ["sessionId"])],
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
)
