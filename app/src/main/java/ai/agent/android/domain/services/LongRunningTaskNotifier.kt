package ai.agent.android.domain.services

/**
 * Posts informational notifications when a backgrounded pipeline run
 * exceeds the long-running threshold. Powers the runtime side of the
 * Settings → Notifications → "Long-running tasks" toggle.
 *
 * The toggle's user-controlled flag is read from
 * `SettingsRepository.longRunningTaskNotificationsEnabled` by the
 * implementation — callers do NOT need to gate themselves. If the user
 * has the flag off, [notify] is a no-op.
 */
interface LongRunningTaskNotifier {

    /**
     * Registers the underlying `NotificationChannel` so the system knows
     * about it on the very first post. Idempotent — safe to call from
     * `App.onCreate`.
     */
    fun registerChannel()

    /**
     * Surfaces a notification announcing that the given pipeline has been
     * running longer than the configured threshold. Implementations MUST
     * short-circuit when the user's notification toggle is off.
     *
     * @param pipelineName Human-readable pipeline label shown as the
     *   notification title.
     * @param elapsedMs Elapsed wall-clock duration of the run in
     *   milliseconds. Used by the text body ("running for 12 s").
     */
    suspend fun notify(pipelineName: String, elapsedMs: Long)
}
