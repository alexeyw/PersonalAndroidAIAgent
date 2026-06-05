package ai.agent.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity backing the user-saved pipeline-preset catalogue introduced
 * in schema v24.
 *
 * Bundled presets live in `assets/presets/pipelines` and never reach this
 * table — only presets created via `SavePipelineAsPresetUseCase` are
 * persisted here. Storing the graph as a JSON blob keeps preset rows
 * self-contained (no FK explosion into `pipeline_nodes` /
 * `pipeline_connections`), which simplifies migration when the pipeline
 * schema evolves: the existing `PipelinePresetJsonSerializer` already
 * knows how to round-trip the stored JSON through whatever version of the
 * pipeline schema is current.
 *
 * @property id Stable identifier (UUID).
 * @property name Display name surfaced in the picker.
 * @property description Free-form description shown under [name].
 * @property categoryKey Wire-form key of the `PresetCategory`
 *   (`local`, `cloud`, `hybrid`, `tool`, `research`, `other`). Unknown
 *   values decode to `OTHER` via `PresetCategory.fromKey`, so renames on
 *   the Kotlin side do not invalidate previously-saved rows.
 * @property graphJson JSON-serialised `PipelineGraph` produced by
 *   `PipelinePresetJsonSerializer.serialize(...)`. Carries the entire
 *   embedded graph plus the redundant preset fields — the serializer is
 *   the single source of truth for the wire shape.
 * @property tagsCsv Comma-separated tag list (lower-case, kebab-case).
 *   Stored as a flat string rather than a JSON array because the picker
 *   only needs whole-list reads; queries by tag are filtered in-memory.
 * @property createdAt Unix-millis timestamp at insert time; the picker
 *   sorts user presets by this column descending so the most-recently
 *   saved entry surfaces first.
 */
@Entity(tableName = "pipeline_presets")
data class PipelinePresetEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val categoryKey: String,
    val graphJson: String,
    val tagsCsv: String,
    val createdAt: Long,
)
