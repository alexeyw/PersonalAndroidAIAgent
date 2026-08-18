package app.knotwork.android.domain.models

/**
 * A raw, not-yet-interpreted call arriving at the external-automation entry
 * point: an action plus the parameters it carried, flattened to strings.
 *
 * This is the boundary type that keeps the contract's validation in `domain`.
 * The framework-side receiver does exactly one thing — copy an `Intent`'s action
 * and its extras into this shape — and every decision about what the call means
 * is then made by a pure function over it. Without the split, the rules that
 * decide what a third-party app is allowed to run would only be testable on a
 * device.
 *
 * @property action The action the caller invoked, verbatim. Not assumed to be a
 *   known one: an unrecognised action is a refusal the parser reports, not a
 *   case the receiver filters out silently.
 * @property extras The call's parameters keyed by the contract's extra keys.
 *   Values are nullable because a platform `Intent` yields `null` for an absent
 *   key, and the parser treats absent and blank identically.
 */
data class ExternalAutomationInvocation(val action: String, val extras: Map<String, String?>) {

    /**
     * Reads one extra, normalising "absent" and "present but blank" to `null`.
     *
     * Blank is treated as absent throughout the contract because the callers are
     * shell scripts and automation apps building intents from templates: an
     * unfilled variable arrives as an empty string far more often than anyone
     * means to pass one.
     *
     * @param key One of the contract's extra keys.
     * @return The trimmed value, or `null` when absent or blank.
     */
    fun value(key: String): String? = extras[key]?.trim()?.takeIf { it.isNotEmpty() }
}
