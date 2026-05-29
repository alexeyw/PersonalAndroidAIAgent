package ai.agent.android.domain.services

import ai.agent.android.domain.constants.TimeAndIdConstants
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.MemoryExtractionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Application-scoped listener that kicks off long-term memory auto-extraction
 * after a pipeline run finishes.
 *
 * The chat layer calls [onPipelineCompleted] when it observes the terminal
 * `Completed` orchestrator state. This coordinator then, per session:
 *  - **debounces** by [DEBOUNCE_MS] — back-to-back completions for the same
 *    session (e.g. a multi-turn burst) collapse into a single extraction once
 *    the conversation goes quiet, instead of re-running the model every turn;
 *  - **short-circuits** when the `autoExtractEnabled` setting is off;
 *  - fetches the session's messages and delegates to [MemoryExtractionUseCase].
 *
 * It owns its own [CoroutineScope] (a [SupervisorJob] on [Dispatchers.Default])
 * so an in-flight extraction survives the chat `ViewModel` being cleared — the
 * pass is fire-and-forget background work, not tied to any screen's lifecycle.
 * The scope is overridable (via [scope]) so tests can drive it with a test
 * dispatcher and virtual time.
 *
 * @property settingsRepository Source of the auto-extract toggle.
 * @property chatRepository Source of the session's messages to mine.
 * @property memoryExtractionUseCase The extraction pass itself.
 */
@Singleton
class MemoryAutoExtractionCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val chatRepository: ChatRepository,
    private val memoryExtractionUseCase: MemoryExtractionUseCase,
) {

    /**
     * Scope the debounced extraction jobs run in. Defaults to an
     * application-lifetime supervisor scope; tests replace it with a test scope
     * to control timing deterministically.
     */
    internal var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** In-flight (debouncing) extraction job per session, so a newer completion can cancel the older. */
    private val pendingJobs = ConcurrentHashMap<String, Job>()

    /**
     * Schedules a debounced memory-extraction pass for [sessionId].
     *
     * Safe to call on every `Completed` state: a fresh call cancels any
     * still-debouncing job for the same session and restarts the timer, so only
     * the last completion in a burst triggers extraction. No-op for a blank id.
     *
     * @param sessionId The chat session whose conversation just completed.
     */
    fun onPipelineCompleted(sessionId: String) {
        if (sessionId.isBlank()) return

        pendingJobs[sessionId]?.cancel()
        pendingJobs[sessionId] = scope.launch {
            try {
                delay(DEBOUNCE_MS)
                // Read the toggle at extraction time so a user flipping it off
                // during the debounce window still cancels the work.
                if (!settingsRepository.autoExtractEnabled.first()) return@launch

                val messages = chatRepository.getMessagesForSession(sessionId).first()
                memoryExtractionUseCase(sessionId, messages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("MemoryExtraction").w(e, "Auto-extraction pass failed for session %s", sessionId)
            } finally {
                // Remove only if the map still points at *this* job — a newer
                // completion may have already replaced it, and a cancelled older
                // job must not evict its successor's entry (which would defeat
                // the debounce dedup).
                pendingJobs.remove(sessionId, coroutineContext[Job])
            }
        }
    }

    private companion object {
        /**
         * Quiet period after a completion before extraction runs. 30 s matches
         * the debounce called out in the Phase 25 plan: long enough to coalesce
         * a multi-turn burst, short enough that facts land well before the
         * model is unloaded by `AgentIdleManager` (5 min idle).
         */
        const val DEBOUNCE_MS: Long = 30L * TimeAndIdConstants.MS_PER_SECOND
    }
}
