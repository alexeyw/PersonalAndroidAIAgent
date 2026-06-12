package app.knotwork.android.presentation.receivers

/**
 * Typed enumeration of the broadcast `Intent` actions emitted by the tool-approval
 * notification.
 *
 * Each entry pairs the user-facing notification button (Approve / Deny) with the wire
 * action string the system delivers to [AgentApprovalReceiver]. The wire format is
 * preserved verbatim from the previous untyped `const val`s so already-pending
 * `PendingIntent`s registered before the refactor keep dispatching to the correct
 * branch.
 *
 * Centralising both ends of the round-trip here lets the receiver dispatch through an
 * exhaustive `when` over [ApprovalAction] instead of comparing free-form strings, while
 * the sender keeps using a single named constant per action.
 */
enum class ApprovalAction(val action: String) {
    /** User tapped "Approve" in the approval notification — proceed with the tool call. */
    APPROVE("app.knotwork.android.ACTION_APPROVE"),

    /** User tapped "Deny" in the approval notification — cancel the pending tool call. */
    DENY("app.knotwork.android.ACTION_DENY"),

    /**
     * Delete-intent of a persistent (parked-run) notification fired by a user
     * swipe — re-post the notification from the pending-interaction record.
     * Android 14+ lets the user dismiss ongoing notifications, but a parked
     * run's notification is its primary recovery surface and must persist
     * until the request is settled or expires.
     */
    REPOST("app.knotwork.android.ACTION_REPOST_PENDING"),
    ;

    companion object {
        /**
         * Parses an incoming `Intent.action` string into a typed [ApprovalAction].
         *
         * @param action The raw action carried by the broadcast intent. `null` and
         *        unknown values both return `null` so the receiver can choose to
         *        ignore the broadcast rather than misroute it.
         */
        fun fromAction(action: String?): ApprovalAction? = entries.firstOrNull { it.action == action }
    }
}
