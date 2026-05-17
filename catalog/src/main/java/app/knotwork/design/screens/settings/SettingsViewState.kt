package app.knotwork.design.screens.settings

/**
 * Visual variant of the settings surface. Mirrors
 * `compose/screens/README.md §C7 · Settings`.
 */
enum class SettingsVisualState {
    /** Initial DataStore + repository read in flight. */
    Loading,

    /** Standard surface; sections render their controls. */
    Default,

    /** A row is persisting an async change; control turns read-only. */
    PendingChange,

    /** Inline validation error on one of the rows. */
    ValidationError,

    /** Some change requires an app restart to take effect. */
    RestartRequired,

    /** Destructive-action confirm dialog is up. */
    DestructiveAction,

    /** Failed to load a remote setting; inline error tile inside a section. */
    Error,
}

/** Logical settings section. Each owns a render slot inside `SettingsContent`. */
enum class SettingsSection(val titleResKey: String) {
    Appearance(titleResKey = "knotwork_settings_section_appearance"),
    Models(titleResKey = "knotwork_settings_section_models"),
    Privacy(titleResKey = "knotwork_settings_section_privacy"),
    Memory(titleResKey = "knotwork_settings_section_memory"),
    Mcp(titleResKey = "knotwork_settings_section_mcp"),
    About(titleResKey = "knotwork_settings_section_about"),
}

/**
 * Per-row state slice surfaced inside a settings section. Kept generic
 * because the legacy app-side surface owns the actual controls (text
 * fields, sliders, dropdowns). The catalog cares only about the visual
 * frame.
 *
 * @property id stable identity used as the `LazyColumn` key.
 * @property title row title.
 * @property subtitle optional secondary line.
 * @property pendingChange `true` when an async persistence is in flight —
 * drives the right-aligned `KnotworkLoader`.
 * @property validationError optional inline validation error rendered in
 * the helper-text slot beneath the row.
 */
data class SettingsRowState(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val pendingChange: Boolean = false,
    val validationError: String? = null,
)

/** Per-section block surfaced by `SettingsContent`. */
data class SettingsSectionBlock(
    val section: SettingsSection,
    val rows: List<SettingsRowState>,
    val errorMessage: String? = null,
)

/**
 * Top-level input to `SettingsContent`. Mirrors
 * `compose/screens/README.md §C7`.
 */
data class SettingsViewState(
    val visualState: SettingsVisualState,
    val sections: List<SettingsSectionBlock> = emptyList(),
    val restartRequiredMessage: String? = null,
    val destructiveActionMessage: String? = null,
) {
    init {
        require(
            (visualState == SettingsVisualState.RestartRequired) == (restartRequiredMessage != null),
        ) {
            "restartRequiredMessage must be non-null iff visualState == RestartRequired"
        }
        require(
            (visualState == SettingsVisualState.DestructiveAction) == (destructiveActionMessage != null),
        ) {
            "destructiveActionMessage must be non-null iff visualState == DestructiveAction"
        }
    }
}

@Suppress("LongParameterList")
class SettingsCallbacks(
    val onRestart: () -> Unit = {},
    val onDestructiveConfirm: () -> Unit = {},
    val onDestructiveCancel: () -> Unit = {},
)

/** Convenience factory returning a callbacks bundle that ignores every event. */
fun noopSettingsCallbacks(): SettingsCallbacks = SettingsCallbacks()
