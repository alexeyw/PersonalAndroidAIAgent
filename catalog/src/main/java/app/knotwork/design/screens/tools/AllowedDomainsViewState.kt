package app.knotwork.design.screens.tools

/**
 * Live feedback for the "add a host" field of the Allowed-domains editor.
 *
 * The host module computes this from `HttpRequestPolicy.normalizeDomain` plus
 * the current list, and the catalog renders the matching helper line (and the
 * field's error border). The Add button is enabled only for
 * [NormalizedPreview].
 */
sealed interface AddHostState {
    /** Field empty or unevaluated — shows the neutral helper. */
    data object Idle : AddHostState

    /**
     * The input is a valid host that is not already on the list; it will be
     * stored as [normalized]. Shows the green "Will be added as …" preview and
     * enables the Add button.
     */
    data class NormalizedPreview(val normalized: String) : AddHostState

    /** The input is not a valid host — shows the destructive-toned error. */
    data object Invalid : AddHostState

    /**
     * The input normalises to a host already on the list ([existing]) — shows
     * the amber duplicate notice. Caught after normalisation, not on the raw
     * string.
     */
    data class Duplicate(val existing: String) : AddHostState
}

/**
 * Immutable input to `AllowedDomainsContent` — the standalone editor for the
 * `http_request` domain allowlist.
 *
 * @property hosts the allowlisted hosts in display order (already normalised).
 *   Empty drives the dedicated empty state (the tool-is-off explainer).
 * @property addInput the current raw text in the add-a-host field.
 * @property addState live validation/preview feedback for [addInput].
 */
data class AllowedDomainsViewState(
    val hosts: List<String> = emptyList(),
    val addInput: String = "",
    val addState: AddHostState = AddHostState.Idle,
)

/**
 * Callback bundle for `AllowedDomainsContent`.
 *
 * @property onBack navigate back to the Tools screen.
 * @property onInfo open the contextual explanation (top-bar info icon).
 * @property onAddInputChange the add-field text changed.
 * @property onAddSubmit commit the currently-previewed host to the allowlist.
 * @property onRemoveHost remove the given host from the allowlist.
 */
class AllowedDomainsCallbacks(
    val onBack: () -> Unit = {},
    val onInfo: () -> Unit = {},
    val onAddInputChange: (String) -> Unit = {},
    val onAddSubmit: () -> Unit = {},
    val onRemoveHost: (host: String) -> Unit = {},
)

/** Convenience factory returning a callbacks bundle that ignores every event. */
fun noopAllowedDomainsCallbacks(): AllowedDomainsCallbacks = AllowedDomainsCallbacks()
