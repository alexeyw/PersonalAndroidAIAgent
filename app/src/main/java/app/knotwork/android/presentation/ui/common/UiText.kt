package app.knotwork.android.presentation.ui.common

import androidx.annotation.StringRes

/**
 * Resolvable text container decoupled from `android.content.Context`.
 *
 * `ViewModel`s, use cases, and any other layer that cannot (or must not) hold a
 * reference to `Context` produce instances of [UiText] and place them into
 * `UiState`. The presentation layer resolves them to a concrete `String` at
 * render time via [UiText.asString] (Composable) or
 * [android.content.Context.resolve] (non-Composable contexts such as
 * notifications and services).
 *
 * Three concrete variants are supported:
 *  - [Resource] — references a static `R.string.*` identifier with optional
 *    positional format arguments.
 *  - [Dynamic] — wraps a runtime-produced `String` (e.g. a server-provided
 *    error message that has no translation).
 *  - [Empty] — represents the absence of any user-visible text; resolves to
 *    an empty string. Useful as a default in `UiState` fields that previously
 *    held `""`.
 */
sealed interface UiText {
    /**
     * Reference to a translated string resource.
     *
     * @property id Stable `R.string.*` identifier of the message.
     * @property args Positional arguments fed to `Context.getString(id, *args)`.
     *   Use `emptyList()` when the resource has no `%1$s`/`%1$d` placeholders.
     */
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /**
     * Wraps a runtime-produced string that has no translation lookup.
     *
     * Use sparingly: server-/SDK-provided error messages, free-form names
     * coming from external systems, and similar values where a translation
     * key does not exist.
     */
    data class Dynamic(val text: String) : UiText

    /** Absence of any user-visible text. Resolves to `""`. */
    data object Empty : UiText

    /**
     * Composite text built by concatenating resolved [parts] with
     * [separator]. Used by validation surfaces that must surface multiple
     * messages in a single Snackbar without forcing the resource layer to
     * enumerate every possible combination.
     *
     * @property parts Ordered child texts; each is resolved independently
     *   before joining.
     * @property separator Glue inserted between every pair of consecutive
     *   parts. Defaults to `", "`.
     */
    data class Joined(val parts: List<UiText>, val separator: String = ", ") : UiText

    companion object {
        /** Convenience factory for a resource with no format arguments. */
        operator fun invoke(@StringRes id: Int): UiText = Resource(id)

        /** Convenience factory for a resource with positional arguments. */
        fun of(@StringRes id: Int, vararg args: Any): UiText = Resource(id, args.toList())
    }
}
