package ai.agent.android.data.local.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single execution step of the pipeline trace in the local database.
 */
@Entity(
    tableName = "trace_steps",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("sessionId"),
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class TraceStepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val nodeName: String,
    val outputText: String,
    val timestamp: Long
)
