package ai.agent.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity backing the user-saved prompt-preset catalogue introduced
 * in schema v25.
 *
 * Bundled presets live in `assets/presets/prompts` and never reach this
 * table — only presets created via `SavePromptAsPresetUseCase` are
 * persisted here.
 *
 * @property id Stable identifier (UUID).
 * @property name Display name surfaced in the picker.
 * @property description Free-form description shown under [name].
 * @property nodeTypeKey Wire-form key of the target `NodeType` (e.g.
 *   `LITE_RT`, `OUTPUT`). Stored as the enum's `name` so renames on the
 *   Kotlin side surface as a parse failure rather than silently retargeting
 *   a saved preset.
 * @property systemPrompt The raw prompt template body. May contain
 *   `$VARIABLE` placeholders that `PromptTemplateEngine` resolves at
 *   execution time.
 * @property tagsCsv Comma-separated tag list (lower-case, kebab-case).
 *   Stored as a flat string rather than a JSON array because the picker
 *   only needs whole-list reads; queries by tag are filtered in-memory.
 * @property createdAt Unix-millis timestamp at insert time; the picker
 *   sorts user presets by this column descending so the most-recently
 *   saved entry surfaces first.
 */
@Entity(tableName = "prompt_presets")
data class PromptPresetEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val nodeTypeKey: String,
    val systemPrompt: String,
    val tagsCsv: String,
    val createdAt: Long,
)
