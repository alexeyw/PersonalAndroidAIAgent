package ai.agent.android.data.local.models

import ai.agent.android.domain.models.NodeContextConfig
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single node in a pipeline.
 *
 * @property id The unique identifier of the node.
 * @property pipelineId The ID of the pipeline this node belongs to.
 * @property type The type of the node as a string.
 * @property x The X coordinate of the node on the canvas.
 * @property y The Y coordinate of the node on the canvas.
 * @property label The display label of the node.
 * @property toolName The optional tool name associated with this node.
 * @property modelPath An optional path to a specific model file (.tflite) for this node.
 * @property conditionComplexity Threshold for task complexity.
 * @property conditionKeywords Comma-separated keywords for condition.
 * @property conditionPrompt Free-form prompt for condition classification.
 * @property systemPrompt An optional system prompt to configure the behavior of the node.
 * @property cloudProvider An optional provider for a CLOUD node.
 * @property clarificationTimeoutMs Timeout (in ms) for a CLARIFICATION node before it falls back
 * to a default answer. `null` means the engine's default is used.
 * @property contextConfig Per-node selection of pipeline context blocks
 * (chat history, original task, previous node output, long-term memory,
 * tool results) injected on every execution. Stored as JSON via the
 * `NodeContextConfig` Room TypeConverter; defaults to all flags `true`
 * for backward compatibility with rows created before Phase 15.
 */
@Entity(
    tableName = "pipeline_nodes",
    foreignKeys = [
        ForeignKey(
            entity = PipelineEntity::class,
            parentColumns = ["id"],
            childColumns = ["pipelineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pipelineId")],
)
data class NodeEntity(
    @PrimaryKey
    val id: String,
    val pipelineId: String,
    val type: String,
    val x: Float,
    val y: Float,
    val label: String,
    val toolName: String? = null,
    val modelPath: String? = null,
    val conditionComplexity: Int? = null,
    val conditionKeywords: String? = null,
    val conditionPrompt: String? = null,
    val systemPrompt: String? = null,
    val cloudProvider: String? = null,
    val clarificationTimeoutMs: Long? = null,
    @ColumnInfo(name = "context_config")
    val contextConfig: NodeContextConfig = NodeContextConfig.ALL_ENABLED,
)
