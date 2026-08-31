package app.knotwork.design.screens.pipelines

/**
 * Everything the preset manager renders, already resolved.
 *
 * No domain preset, no category enum and no tab enum cross this boundary:
 * `:app` owns what a preset is and what its categories are, and turns them into
 * rows, chips and tabs here. A new category is a new [PresetCategoryToneUi]
 * value and nothing else.
 *
 * @property title Screen title.
 * @property subtitle The counts line under it.
 * @property backContentDescription Accessible label of the back control.
 * @property tabs Bundled / Mine, with their counts.
 * @property chips Category filters, the reset chip first.
 * @property rows Presets under the active tab and chip.
 * @property emptyText Shown instead of the list when [rows] is empty; its
 *   wording differs per tab, which is why it arrives resolved.
 * @property actionLabels The overflow's words. On the state rather than on each
 *   row: they are the same for every row, and repeating them per row invites
 *   two of them to disagree.
 * @property rename The rename dialog, when one is open.
 * @property delete The delete confirmation, when one is open.
 */
data class PresetManagerViewState(
    val title: String,
    val subtitle: String,
    val backContentDescription: String,
    val tabs: List<PresetTabUi>,
    val chips: List<PresetChipUi>,
    val rows: List<PresetRowUi>,
    val emptyText: String,
    val actionLabels: PresetRowActionLabels,
    val rename: PresetRenameDialogUi? = null,
    val delete: PresetDeleteDialogUi? = null,
)

/**
 * The overflow's words.
 *
 * @property rename Rename action.
 * @property export Export action.
 * @property delete Delete action.
 */
data class PresetRowActionLabels(val rename: String, val export: String, val delete: String)

/**
 * One tab.
 *
 * @property id Opaque identifier handed back on tap.
 * @property label The tab's word, without its count.
 * @property count Presets under it.
 * @property selected Whether it is the active tab.
 */
data class PresetTabUi(val id: String, val label: String, val count: Int, val selected: Boolean)

/**
 * One category filter chip.
 *
 * @property id Opaque identifier, or `null` for the "All" reset chip.
 * @property label The chip's word.
 * @property count Presets it would show.
 * @property selected Whether it is the active filter.
 */
data class PresetChipUi(val id: String?, val label: String, val count: Int, val selected: Boolean)

/**
 * One preset row.
 *
 * @property id Opaque identifier handed back with every action.
 * @property name The preset's name.
 * @property description Its description; blank hides the line.
 * @property flowPreview One-line rendering of the graph's shape.
 * @property categoryLabel The category's word.
 * @property categoryTone Which accent the category badge takes.
 * @property canRename Bundled presets are read-only, so their overflow offers
 *   only Export — the actions are absent rather than disabled, because a
 *   disabled item still asks the reader to work out why.
 * @property canDelete As [canRename].
 */
data class PresetRowUi(
    val id: String,
    val name: String,
    val description: String,
    val flowPreview: String,
    val categoryLabel: String,
    val categoryTone: PresetCategoryToneUi,
    val canRename: Boolean,
    val canDelete: Boolean,
)

/**
 * Accent of a category badge.
 *
 * A mirror of `:app`'s category vocabulary rather than the vocabulary itself,
 * for the same reason `RunTerminationToneUi` mirrors termination kinds: this
 * module decides how a tone looks, never what the categories are. The hues
 * reuse node and signal colours, both hue-locked across themes, so a badge
 * stays legible in either palette.
 */
enum class PresetCategoryToneUi {
    /** Runs entirely on-device. */
    Local,

    /** Calls a cloud provider. */
    Cloud,

    /** Mixes both. */
    Hybrid,

    /** Built around a tool call. */
    Tool,

    /** Multi-step research shape. */
    Research,

    /** Anything else. */
    Other,
}

/**
 * The rename dialog.
 *
 * @property initialName Name the field opens with.
 * @property title Dialog title.
 * @property label Field label.
 * @property confirmLabel Confirm action.
 * @property cancelLabel Dismiss action.
 */
data class PresetRenameDialogUi(
    val initialName: String,
    val title: String,
    val label: String,
    val confirmLabel: String,
    val cancelLabel: String,
)

/**
 * The delete confirmation.
 *
 * @property title Dialog title.
 * @property body The sentence naming the preset.
 * @property confirmLabel Confirm action.
 * @property cancelLabel Dismiss action.
 */
data class PresetDeleteDialogUi(val title: String, val body: String, val confirmLabel: String, val cancelLabel: String)

/**
 * Callback bag for [PresetManagerContent].
 *
 * @property onBack Pop back.
 * @property onTabSelected A tab was tapped, by id.
 * @property onCategorySelected A chip was tapped; `null` is the reset chip.
 * @property onRename Open the rename dialog for a preset id.
 * @property onDelete Open the delete confirmation for a preset id.
 * @property onExport Export a preset id.
 * @property onRenameConfirm The rename dialog was confirmed with a new name.
 * @property onRenameDismiss The rename dialog was dismissed.
 * @property onDeleteConfirm The delete confirmation was accepted.
 * @property onDeleteDismiss The delete confirmation was dismissed.
 */
data class PresetManagerCallbacks(
    val onBack: () -> Unit = {},
    val onTabSelected: (String) -> Unit = {},
    val onCategorySelected: (String?) -> Unit = {},
    val onRename: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onExport: (String) -> Unit = {},
    val onRenameConfirm: (String) -> Unit = {},
    val onRenameDismiss: () -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
    val onDeleteDismiss: () -> Unit = {},
)
