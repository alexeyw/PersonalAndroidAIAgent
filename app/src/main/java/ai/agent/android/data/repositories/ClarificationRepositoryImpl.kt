package ai.agent.android.data.repositories

import ai.agent.android.domain.models.ClarificationRequest
import ai.agent.android.domain.repositories.ClarificationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation of [ClarificationRepository].
 *
 * Tracks each pending request in a thread-safe map of [CompletableDeferred] keyed by
 * request id, mirroring the approval-resume pattern used in
 * [ai.agent.android.domain.engine.executors.ToolNodeExecutor]. The currently visible
 * request (if any) is exposed via a [MutableStateFlow] so the UI can react.
 *
 * No persistence: on process death any pending clarifications are lost; this is
 * intentional because the suspended pipeline coroutine is also lost in that case.
 */
@Singleton
class ClarificationRepositoryImpl @Inject constructor() : ClarificationRepository {

    private val _pendingRequest = MutableStateFlow<ClarificationRequest?>(null)
    override val pendingRequest: Flow<ClarificationRequest?> = _pendingRequest.asStateFlow()

    private val activeRequests = ConcurrentHashMap<String, CompletableDeferred<String>>()

    override suspend fun requestAnswer(request: ClarificationRequest): String {
        // Register the deferred BEFORE publishing the request so a fast UI submission
        // cannot be dropped between publish and registration.
        val deferred = CompletableDeferred<String>()
        activeRequests[request.id] = deferred
        _pendingRequest.value = request

        return try {
            withTimeout(request.timeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            val defaultAnswer = request.options?.firstOrNull().orEmpty()
            Timber.tag(TAG).w(
                "Clarification request %s timed out after %d ms; using default answer: %s",
                request.id,
                request.timeoutMs,
                defaultAnswer,
            )
            defaultAnswer
        } finally {
            activeRequests.remove(request.id)
            // Clear visible request only if it still points to ours: a concurrent
            // request may have superseded it on another coroutine.
            _pendingRequest.compareAndSet(request, null)
        }
    }

    override suspend fun submitClarification(requestId: String, answer: String) {
        val deferred = activeRequests[requestId]
        if (deferred == null) {
            Timber.tag(TAG).w(
                "submitClarification called for unknown or already-resolved request id: %s",
                requestId,
            )
            return
        }
        deferred.complete(answer)
    }

    private companion object {
        const val TAG = "Clarification"
    }
}
