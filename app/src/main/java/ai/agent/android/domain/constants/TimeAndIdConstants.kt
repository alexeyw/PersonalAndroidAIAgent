package ai.agent.android.domain.constants

/**
 * Cross-module numeric constants for time-unit conversion and notification-id
 * partitioning.
 *
 * Grouped together because each of the three values is a "shared math constant"
 * referenced by multiple unrelated callers (metrics, UI countdown formatting,
 * approval notification routing). Centralising them prevents the same literal
 * from drifting across files.
 */
object TimeAndIdConstants {
    /** Number of milliseconds in one second. */
    const val MS_PER_SECOND: Long = 1_000L

    /** Number of milliseconds in one minute. */
    const val MS_PER_MINUTE: Long = 60_000L

    /**
     * Number of milliseconds in one day. Used by the long-term memory
     * compaction worker to translate a "consolidate chunks older than N days"
     * age window into an absolute timestamp cutoff.
     */
    const val MS_PER_DAY: Long = 86_400_000L

    /**
     * Size of the int-range used to derive an Android notification id from an
     * agent-internal hash. Both the publishing side
     * ([ai.agent.android.presentation.notifications.ApprovalNotificationManager])
     * and the receiving side
     * ([ai.agent.android.presentation.receivers.AgentApprovalReceiver]) must
     * agree on this value, otherwise the receiver cannot recover the id that
     * the manager emitted.
     */
    const val NOTIFICATION_ID_RANGE: Int = 1_000
}
