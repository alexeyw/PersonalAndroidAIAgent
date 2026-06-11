package app.knotwork.android.domain.services

/**
 * Posts user-facing notifications announcing the outcome of a scheduled
 * background run ("Task completed" / "Task failed"). Powers the runtime side
 * of the Settings → Notifications → "Scheduled task results" toggle.
 *
 * Without these notifications a scheduler-origin run that finishes while the
 * app is closed would succeed silently: the result lands in the bound chat
 * session, but nothing tells the user to look there. Each notification
 * deep-links into the session so a tap opens the conversation with the
 * freshly landed messages.
 *
 * The toggle's user-controlled flag is read from
 * `SettingsRepository.scheduledTaskNotificationsEnabled` by the
 * implementation — callers do NOT need to gate themselves. If the user has
 * the flag off (or the POST_NOTIFICATIONS permission is missing), both
 * notify methods are no-ops.
 */
interface ScheduledTaskNotifier {

    /**
     * Registers the underlying `NotificationChannel` so the system knows
     * about it on the very first post. Idempotent — safe to call from
     * `App.onCreate`.
     */
    fun registerChannel()

    /**
     * Announces a successfully completed scheduled run.
     *
     * @param sessionId Chat session the run landed its result in; the
     *   notification's tap action deep-links into this session.
     * @param resultPreview First line of the final agent answer, shown as the
     *   notification body so the user can triage without opening the app.
     */
    suspend fun notifyCompleted(sessionId: String, resultPreview: String)

    /**
     * Announces a failed scheduled run.
     *
     * @param sessionId Chat session the run was bound to; the notification's
     *   tap action deep-links into this session.
     * @param reason Human-readable failure reason recorded on the persistent
     *   run record, shown as the notification body.
     */
    suspend fun notifyFailed(sessionId: String, reason: String)
}
