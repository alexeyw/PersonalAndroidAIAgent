package app.knotwork.android.domain.models

/**
 * A reusable bundle of *instruction + tool restriction + context
 * configuration* — the on-device analogue of a "skill". Instead of copying a
 * system prompt between nodes and pipelines, the user describes a capability
 * once and reuses it: a future `NodeType.SKILL` node references a skill by
 * [id] and runs its [instruction] as an ordinary inference, but with the
 * skill's tool allowlist and default context applied.
 *
 * Two sub-kinds share this single type, distinguished only by [isBundled]:
 *
 * - **Bundled** skills ship inside the APK under `assets/presets/skills`
 *   (as JSON files) and are seeded into the `skills` table on launch with a
 *   stable [id] (the filename stem). They are read-only — the user can
 *   *duplicate* a bundled skill into an editable copy but cannot edit or
 *   delete the bundled original.
 * - **User** skills live in the same `skills` table with a UUID [id] and are
 *   fully mutable.
 *
 * The carried [instruction] is treated as a template: it may reference any
 * `$VARIABLE` placeholder registered in `di/PromptTemplateModule.kt`
 * (`$DATE`, `$TIME`, `$TOOLS`, `$MODEL`, `$MEMORY_SUMMARY`, `$LANG`,
 * `$LOCATION`, `$USER`, `$DEVICE`). Placeholder substitution is performed by
 * `PromptTemplateEngine` at execution time — skills only store the raw
 * template.
 *
 * @property id Stable identifier of the skill. UUID for user skills; the
 *   filename stem for bundled skills (e.g. `summarizer`) so each bundled file
 *   maps to exactly one skill id even across reinstalls.
 * @property name Display name surfaced in the Skill Library.
 * @property description Human-readable summary of what the skill does and when
 *   to pick it. Rendered under [name] in the library row.
 * @property instruction The template body (markdown / plain prose). May
 *   reference `$VARIABLE` placeholders from the registered set.
 * @property toolAllowlist Controls which tools the skill may use. The
 *   null-vs-empty distinction is load-bearing:
 *   - `null` → **all tools** (unrestricted; future tools included).
 *   - empty list → **no tools** (instruction-only).
 *   - non-empty list → exactly the listed tool ids (a subset).
 *   Tool restriction is enforced at the executor level in the SKILL-node task
 *   (5/7), not merely by prompt text.
 * @property contextConfig The default [NodeContextConfig] a SKILL node inherits
 *   when it references this skill (a node may override it).
 * @property isBundled `true` for read-only skills shipped in the APK, `false`
 *   for user skills. Bundled skills must never reach
 *   `SkillRepository.saveUserSkill` / `deleteUserSkill`.
 * @property createdAt Unix-millis timestamp at creation.
 * @property updatedAt Unix-millis timestamp of the last edit.
 */
data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val instruction: String,
    val toolAllowlist: List<String>?,
    val contextConfig: NodeContextConfig = NodeContextConfig.ALL_ENABLED,
    val isBundled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /**
     * Classifies this skill's [toolAllowlist] into the tri-state the UI
     * renders as distinct indicators, so "all tools" (unrestricted) can never
     * be confused with "no tools" (explicit empty allowlist).
     */
    val toolRestriction: SkillToolRestriction
        get() = when {
            toolAllowlist == null -> SkillToolRestriction.ALL
            toolAllowlist.isEmpty() -> SkillToolRestriction.NONE
            else -> SkillToolRestriction.SUBSET
        }
}

/**
 * The three meaningful states of a [Skill.toolAllowlist], surfaced as visually
 * distinct indicators in the library and as the master segmented control in
 * the editor.
 */
enum class SkillToolRestriction {
    /** No restriction — every tool is available, including ones added later. */
    ALL,

    /** Explicit empty allowlist — the skill runs instruction-only, no tools. */
    NONE,

    /** An explicit subset of tool ids. */
    SUBSET,
}
