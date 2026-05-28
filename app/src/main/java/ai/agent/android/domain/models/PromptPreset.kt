package ai.agent.android.domain.models

/**
 * A reusable, pre-built system-prompt template that can be applied to any
 * compatible LLM-driven node (LITE_RT, CLOUD, OUTPUT, SUMMARY,
 * INTENT_ROUTER, DECOMPOSITION, EVALUATION, CLARIFICATION).
 *
 * Two sub-kinds share this single type, distinguished only by [isBundled]:
 *
 * - **Bundled** presets ship inside the APK under
 *   `assets/presets/prompts` (as JSON files) and are read-only. They form
 *   the curated starter catalogue surfaced in the Prompt Library.
 * - **User** presets live in Room (`prompt_presets` table, schema v25) and
 *   are mutable — the user can save the currently-edited node `systemPrompt`
 *   as a preset for later reuse via `SavePromptAsPresetUseCase`.
 *
 * The carried [systemPrompt] is treated as a template: it may reference any
 * `$VARIABLE` placeholder registered in `di/PromptTemplateModule.kt`
 * (`$DATE`, `$TIME`, `$TOOLS`, `$MODEL`, `$MEMORY_SUMMARY`, `$LANG`,
 * `$LOCATION`, `$USER`, `$DEVICE`). Placeholder substitution is performed
 * by `PromptTemplateEngine` at execution time — presets only store the raw
 * template.
 *
 * @property id Stable identifier of the preset. UUID for user presets; the
 *   filename stem for bundled presets (e.g. `litert_concise_assistant`) so
 *   each bundled file maps to exactly one preset id even across reinstalls.
 * @property name Display name surfaced in the picker UI.
 * @property description Human-readable summary of what the preset does and
 *   when to pick it. Rendered under [name] in the picker card.
 * @property nodeType The [NodeType] this preset targets. The Prompt Library
 *   filters by current node type so the user only sees compatible presets.
 *   Must be one of the LLM-driven types — non-LLM types (`INPUT`, `TOOL`,
 *   `IF_CONDITION`, `QUEUE_PROCESSOR`) never run a system prompt and so
 *   cannot have presets.
 * @property systemPrompt The template body. May reference `$VARIABLE`
 *   placeholders from the registered set. Trimmed before persistence.
 * @property tags Free-form labels used for filter-by-tag in the picker.
 *   Lower-case, kebab-case by convention (e.g. `concise`, `reasoning`,
 *   `json`).
 * @property isBundled `true` for read-only presets shipped in the APK,
 *   `false` for user-saved presets persisted in Room. Bundled presets must
 *   never reach `PromptPresetRepository.saveUserPreset` /
 *   `deleteUserPreset`.
 */
data class PromptPreset(
    val id: String,
    val name: String,
    val description: String,
    val nodeType: NodeType,
    val systemPrompt: String,
    val tags: List<String> = emptyList(),
    val isBundled: Boolean,
)
