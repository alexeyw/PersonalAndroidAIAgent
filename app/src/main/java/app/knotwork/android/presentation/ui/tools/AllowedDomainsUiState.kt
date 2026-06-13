package app.knotwork.android.presentation.ui.tools

/**
 * Live feedback for the add-a-host field of the Allowed-domains editor,
 * computed by [AllowedDomainsViewModel] from `HttpRequestPolicy.normalizeDomain`
 * and the current list. Mapped 1:1 to the catalog's `AddHostState` in
 * `AllowedDomainsScreen`.
 */
sealed interface AddHostFeedback {
    /** Field empty — neutral helper. */
    data object Idle : AddHostFeedback

    /** Input is a valid, new host; it will be stored as [normalized]. */
    data class Valid(val normalized: String) : AddHostFeedback

    /** Input is not a valid host. */
    data object Invalid : AddHostFeedback

    /** Input normalises to [existing], already on the list. */
    data class Duplicate(val existing: String) : AddHostFeedback
}

/**
 * UI state for the Allowed-domains editor screen.
 *
 * @property hosts the allowlisted hosts in stored order (already normalised).
 * @property addInput current raw text in the add field.
 * @property addFeedback live validation/preview for [addInput].
 */
data class AllowedDomainsUiState(
    val hosts: List<String> = emptyList(),
    val addInput: String = "",
    val addFeedback: AddHostFeedback = AddHostFeedback.Idle,
)
