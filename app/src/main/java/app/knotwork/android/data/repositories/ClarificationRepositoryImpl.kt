package app.knotwork.android.data.repositories

import app.knotwork.android.domain.models.ClarificationRequest
import app.knotwork.android.domain.repositories.ClarificationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
 * [app.knotwork.android.domain.engine.executors.ToolNodeExecutor]. The full set of
 * currently pending requests is exposed via a [MutableStateFlow] so the UI can render
 * every active question — concurrent pipelines may issue overlapping requests and each
 * must remain answerable.
 *
 * No persistence: on process death any pending clarifications are lost; this is
 * intentional because the suspended pipeline coroutine is also lost in that case.
 */
@Singleton
class ClarificationRepositoryImpl @Inject constructor() : ClarificationRepository {

    private val _pendingRequests = MutableStateFlow<List<ClarificationRequest>>(emptyList())
    override val pendingRequests: Flow<List<ClarificationRequest>> = _pendingRequests.asStateFlow()

    private val activeRequests = ConcurrentHashMap<String, CompletableDeferred<String>>()

    override suspend fun requestAnswer(request: ClarificationRequest): String {
        // Register the deferred BEFORE publishing the request so a fast UI submission
        // cannot be dropped between publish and registration.
        val deferred = CompletableDeferred<String>()
        activeRequests[request.id] = deferred
        _pendingRequests.update { current -> current + request }

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
            _pendingRequests.update { current -> current.filterNot { it.id == request.id } }
        }
    }

    override suspend fun submitClarification(requestId: String, answer: String): Boolean {
        val deferred = activeRequests[requestId]
        if (deferred == null) {
            Timber.tag(TAG).w(
                "submitClarification called for unknown or already-resolved request id: %s",
                requestId,
            )
            return false
        }
        // CompletableDeferred.complete returns false when the deferred was already
        // completed (e.g. by the withTimeout in requestAnswer firing first). The UI
        // relies on this distinction to avoid displaying "you answered" after the
        // pipeline already consumed the default answer.
        return deferred.complete(answer)
    }

    private companion object {
        const val TAG = "Clarification"
    }
}
