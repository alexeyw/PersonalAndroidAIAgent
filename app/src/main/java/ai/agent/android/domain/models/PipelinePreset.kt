package ai.agent.android.domain.models

/**
 * A reusable, pre-built pipeline template that can be materialised into a
 * concrete [PipelineGraph] via `LoadPipelineFromPresetUseCase` or persisted
 * back into the user catalogue via `SavePipelineAsPresetUseCase`.
 *
 * Two sub-kinds share this single type, distinguished only by [isBundled]:
 *
 * - **Bundled** presets ship inside the APK under
 *   `assets/presets/pipelines/*.json` and are read-only. They form the
 *   curated starter catalogue visible on first launch.
 * - **User** presets live in Room (`pipeline_presets` table, schema v24) and
 *   are mutable — the user can save the currently-edited graph as a preset
 *   for later reuse, rename, or delete it.
 *
 * The embedded [graph] is a *template*: its `id` and the ids of every node /
 * connection are placeholders. When the user instantiates a preset, every id
 * is regenerated so the new pipeline can coexist with the source preset
 * without primary-key collisions.
 *
 * @property id Stable identifier of the preset (UUID for user presets; the
 *   filename stem — e.g. `local_only_qa` — for bundled presets, so each
 *   bundled file maps to exactly one preset id even across reinstalls).
 * @property name Display name surfaced in the picker UI.
 * @property description Human-readable summary of what the preset does and
 *   when to pick it. Rendered under [name] in the picker card.
 * @property category Bucket used to group presets in the picker. See
 *   [PresetCategory].
 * @property graph The template graph the preset materialises into. Its `id`
 *   and the ids of its nodes / connections must be regenerated on
 *   instantiation.
 * @property tags Free-form labels used for filter-by-tag in the picker.
 *   Lower-case, kebab-case by convention (e.g. `react`, `tools`,
 *   `multi-step`).
 * @property isBundled `true` for read-only presets shipped in the APK,
 *   `false` for user-saved presets persisted in Room. Bundled presets must
 *   never reach `PipelinePresetRepository.saveUserPreset` /
 *   `deleteUserPreset`.
 */
data class PipelinePreset(
    val id: String,
    val name: String,
    val description: String,
    val category: PresetCategory,
    val graph: PipelineGraph,
    val tags: List<String> = emptyList(),
    val isBundled: Boolean,
)

/**
 * Coarse categorisation surfaced as picker chips so the user can narrow the
 * list of presets to the kind of pipeline they have in mind.
 *
 * The wire form ([key]) is the lower-case enum name. Persisted both in
 * Room (`PipelinePresetEntity.categoryKey`) and in the bundled JSON files
 * (`"category": "local"`), which keeps the catalogue editable by hand and
 * stable across renames of the enum constants on the Kotlin side.
 *
 * Unknown wire keys decode to [OTHER] rather than throwing, so a bundled
 * file authored against a future enum value still loads — it just lands in
 * the catch-all bucket until the app version catches up.
 */
enum class PresetCategory(val key: String) {
    /** Runs entirely on-device through a single LiteRT model. */
    LOCAL("local"),

    /** Routes through a remote cloud LLM provider. */
    CLOUD("cloud"),

    /** Mixes local and cloud nodes (e.g. router → cloud, summary → local). */
    HYBRID("hybrid"),

    /** Built around tool / AppFunction invocation. */
    TOOL("tool"),

    /** Multi-step decomposition / research pipelines. */
    RESEARCH("research"),

    /** Anything that does not fit one of the categories above. */
    OTHER("other"),
    ;

    companion object {
        /**
         * Decodes [key] to its [PresetCategory], falling back to [OTHER] for
         * unknown values so a bundled JSON authored against a future enum
         * value still loads.
         *
         * @param key The wire-form key, typically read from JSON or the
         *   `categoryKey` Room column. Matched case-insensitively.
         * @return The matching category, or [OTHER] when [key] is `null`,
         *   blank, or not recognised.
         */
        fun fromKey(key: String?): PresetCategory {
            if (key.isNullOrBlank()) return OTHER
            val normalised = key.trim().lowercase()
            return entries.firstOrNull { it.key == normalised } ?: OTHER
        }
    }
}
