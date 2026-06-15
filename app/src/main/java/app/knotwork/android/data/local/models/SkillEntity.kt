package app.knotwork.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.knotwork.android.domain.models.NodeContextConfig

/**
 * Room entity backing the skill catalogue (`skills` table, schema v36).
 *
 * Unlike the prompt/pipeline preset tables (which hold user rows only and
 * lazy-load their bundled set from assets), the `skills` table holds **both**
 * kinds, distinguished by [isBundled]: bundled skills are seeded into the table
 * with stable ids on launch (see `SkillRepository.seedBundledSkills`), user
 * skills carry a UUID id.
 *
 * @property id Stable identifier — filename stem for bundled skills, UUID for
 *   user skills.
 * @property name Display name surfaced in the Skill Library.
 * @property description Free-form description shown under [name].
 * @property instruction The raw instruction template. May contain `$VARIABLE`
 *   placeholders resolved by `PromptTemplateEngine` at execution time.
 * @property toolAllowlistCsv The skill's tool restriction, encoded so the
 *   null-vs-empty distinction survives a round-trip:
 *   - `null` → **all tools** (unrestricted).
 *   - `""` → **no tools** (explicit empty allowlist).
 *   - `"a,b"` → exactly those tool ids (a subset).
 *   Encoded/decoded with `TagsCsv`.
 * @property contextConfig The default [NodeContextConfig] a SKILL node inherits
 *   when it references this skill. Persisted as JSON via the registered
 *   `Converters` type converter (same column shape as `pipeline_nodes`).
 * @property isBundled `true` for read-only bundled skills, `false` for user
 *   skills.
 * @property createdAt Unix-millis timestamp at creation.
 * @property updatedAt Unix-millis timestamp of the last edit; the library sorts
 *   user skills by this column descending so the most recently edited surfaces
 *   first.
 */
@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val instruction: String,
    val toolAllowlistCsv: String?,
    val contextConfig: NodeContextConfig,
    val isBundled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
