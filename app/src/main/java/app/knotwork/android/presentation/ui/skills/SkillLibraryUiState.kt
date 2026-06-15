package app.knotwork.android.presentation.ui.skills

import app.knotwork.android.domain.models.NodeContextConfig
import app.knotwork.android.domain.models.Skill
import app.knotwork.android.presentation.ui.common.UiText
import app.knotwork.design.components.chips.Risk
import app.knotwork.design.screens.skills.SkillLibraryTab
import app.knotwork.design.screens.skills.SkillToolMode

/**
 * One selectable tool surfaced in the editor's Restrict list, projected from
 * the active tool catalogue (`ToolRepository.getAvailableTools`).
 *
 * @property id stable tool id.
 * @property risk catalog risk tier for the trailing pill, or `null` when the
 *   tool declares no risk.
 * @property description short human description.
 */
data class SkillToolCatalogEntry(val id: String, val risk: Risk?, val description: String)

/**
 * In-progress editor draft. Holds the editable fields plus the tri-state tool
 * selection and context flags; the ViewModel projects this to the catalog
 * `SkillEditorUi` and, on save, back into a domain [Skill].
 *
 * @property id `null` for a new draft, non-null when editing an existing user
 *   skill.
 * @property createdAt original creation timestamp, preserved across an edit
 *   (0 for a new draft — the repository stamps it on first save).
 * @property fromBundled `true` when this draft is an editable copy of a bundled
 *   skill (drives the editor subtitle).
 * @property name current Name value.
 * @property description current Description value.
 * @property instruction current Instruction value.
 * @property toolMode tri-state allowlist mode.
 * @property selectedToolIds tool ids selected while [toolMode] is
 *   [SkillToolMode.Restrict].
 * @property contextConfig the five context flags.
 */
data class SkillEditorDraft(
    val id: String?,
    val createdAt: Long,
    val fromBundled: Boolean,
    val name: String,
    val description: String,
    val instruction: String,
    val toolMode: SkillToolMode,
    val selectedToolIds: Set<String>,
    val contextConfig: NodeContextConfig,
) {
    /** Save is enabled once name and instruction are both non-blank. */
    val canSave: Boolean get() = name.isNotBlank() && instruction.isNotBlank()
}

/**
 * Delete-confirmation target. Surfaced when the user taps Delete on a user
 * skill; carries the pipelines that reference it so the dialog can warn.
 *
 * @property id the skill id to delete.
 * @property name the skill name (shown in the dialog title).
 * @property dependentPipelineNames pipelines referencing the skill through a
 *   SKILL node (empty until the SKILL node ships).
 */
data class SkillDeleteTarget(val id: String, val name: String, val dependentPipelineNames: List<String>)

/**
 * Screen state for the Skill Library. Bundled and user skills are kept
 * separate so the two tabs render their own counts and overflow rules; the
 * editor and delete dialog are modelled as nullable sub-states.
 *
 * @property isLoading initial fetch in progress.
 * @property tab the selected sub-catalogue.
 * @property bundledSkills read-only bundled skills.
 * @property userSkills user-authored skills.
 * @property openMenuSkillId id of the row whose overflow menu is open.
 * @property editor non-null when the full-screen editor is open.
 * @property deleteTarget non-null when the delete dialog is open.
 * @property errorMessage transient error banner.
 * @property availableVariables `$VAR` tokens offered in the editor Insert row.
 * @property availableTools the active tool catalogue for the Restrict list.
 */
data class SkillLibraryUiState(
    val isLoading: Boolean = true,
    val tab: SkillLibraryTab = SkillLibraryTab.Bundled,
    val bundledSkills: List<Skill> = emptyList(),
    val userSkills: List<Skill> = emptyList(),
    val openMenuSkillId: String? = null,
    val editor: SkillEditorDraft? = null,
    val deleteTarget: SkillDeleteTarget? = null,
    val errorMessage: UiText? = null,
    val availableVariables: List<String> = emptyList(),
    val availableTools: List<SkillToolCatalogEntry> = emptyList(),
)
