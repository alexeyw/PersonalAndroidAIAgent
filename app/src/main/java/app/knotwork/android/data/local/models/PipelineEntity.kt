package app.knotwork.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.knotwork.android.domain.models.PipelineSamplePrompt

/**
 * Room entity representing a saved visual orchestrator pipeline.
 *
 * @property id The unique identifier of the pipeline.
 * @property name The display name of the pipeline.
 * @property updatedAt Timestamp of the last update.
 * @property samplePrompts Starter ("quick action") prompts this pipeline
 *   suggests on the new-chat empty state. Persisted as a JSON string via the
 *   `Converters` type converter; defaults to an empty list.
 * @property memoryRetrievalQuery Long-term-memory search key declared for this
 *   pipeline's background runs, or `null` when none is declared. Nullable rather
 *   than empty-defaulted so "never declared" and "declared as empty" stay the
 *   same state; see `PipelineGraph.memoryRetrievalQuery`.
 */
@Entity(tableName = "pipelines")
data class PipelineEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val samplePrompts: List<PipelineSamplePrompt> = emptyList(),
    val memoryRetrievalQuery: String? = null,
)
