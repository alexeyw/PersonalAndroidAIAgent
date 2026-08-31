package app.knotwork.design.screens.settings

/**
 * The provider picker's contents, already resolved.
 *
 * @property title Screen title.
 * @property backContentDescription Accessible label of the back control.
 * @property rows The providers on offer, in the order they are shown.
 */
data class ProviderPickerViewState(
    val title: String,
    val backContentDescription: String,
    val rows: List<ProviderPickerRowUi>,
)

/**
 * One provider on the picker.
 *
 * @property id Opaque identifier handed back on tap; `:app` maps it to its own
 *   provider type, so this module never learns that vocabulary.
 * @property title The provider's name.
 */
data class ProviderPickerRowUi(val id: String, val title: String)
