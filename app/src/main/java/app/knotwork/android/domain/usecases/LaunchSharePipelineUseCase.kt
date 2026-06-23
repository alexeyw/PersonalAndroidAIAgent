package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.models.EntrySurface
import app.knotwork.android.domain.models.MessageAttachment
import app.knotwork.android.domain.models.RunOrigin
import app.knotwork.android.domain.models.SharedPayload
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.services.AttachmentStore
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Launches the pipeline bound to the share target over the content the user
 * shared into the app via `ACTION_SEND`.
 *
 * The share activity brings the app to the foreground, so unlike the tile this
 * uses the interactive queue ([AgentOrchestratorUseCase]) and returns the id of
 * the session it created so the caller can deep-link the user straight into the
 * live run. A fresh session is always created (bound to the share pipeline) so
 * a share never pollutes the active chat.
 *
 * Shared images are ingested through [AttachmentStore] exactly like a composer
 * attachment; per the multimodal contract only the text travels the graph while
 * the image rides the user message. An image-only share runs the same
 * image-only default instruction the composer uses.
 *
 * When no pipeline is bound the surface is inert ([ShareLaunchResult.NotConfigured]);
 * an empty share is dropped ([ShareLaunchResult.NothingShared]).
 */
class LaunchSharePipelineUseCase @Inject constructor(
    private val resolveSurfacePipeline: ResolveSurfacePipelineUseCase,
    private val chatRepository: ChatRepository,
    private val attachmentStore: AttachmentStore,
    private val agentOrchestrator: AgentOrchestratorUseCase,
) {

    /**
     * Resolves the share binding and, when present and the payload is non-empty,
     * creates a bound session and enqueues a run over the shared content.
     *
     * @param payload The normalised shared content (text and/or image URI).
     * @return [ShareLaunchResult.Launched] with the new session id,
     *   [ShareLaunchResult.NotConfigured] when nothing is bound, or
     *   [ShareLaunchResult.NothingShared] when the payload had no content.
     */
    suspend operator fun invoke(payload: SharedPayload): ShareLaunchResult {
        if (payload.isEmpty) return ShareLaunchResult.NothingShared
        val pipelineId = resolveSurfacePipeline(EntrySurface.SHARE) ?: return ShareLaunchResult.NotConfigured

        val attachment = payload.imageUri?.let { ingestImage(it) }
        val hasText = !payload.text.isNullOrBlank()
        // Image-only share with a failed ingest leaves us nothing to run.
        if (!hasText && attachment == null) return ShareLaunchResult.NothingShared

        val sessionId = UUID.randomUUID().toString()
        chatRepository.saveSession(
            ChatSession(
                id = sessionId,
                name = sessionName(payload.text, attachment != null),
                updatedAt = System.currentTimeMillis(),
                pipelineId = pipelineId,
            ),
        )

        // Image-only message: the bubble shows just the thumbnail (empty display
        // content) while the internal default instruction travels the graph.
        val prompt = payload.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: DefaultPrompts.IMAGE_ONLY_DEFAULT_INSTRUCTION
        val displayContent = if (hasText) null else ""

        agentOrchestrator(
            sessionId = sessionId,
            userPrompt = prompt,
            pipelineId = pipelineId,
            attachment = attachment,
            displayContent = displayContent,
            origin = RunOrigin.SHARE,
        )
        return ShareLaunchResult.Launched(sessionId)
    }

    /** Best-effort image ingest; a failure degrades to a text-only (or empty) share. */
    private suspend fun ingestImage(uri: String): MessageAttachment? =
        attachmentStore.ingestUri(uri).getOrElse { error ->
            Timber.w(error, "Failed to ingest shared image; continuing without it.")
            null
        }

    /** Derives a session name from the shared text, falling back to an image label. */
    private fun sessionName(text: String?, hasImage: Boolean): String {
        val firstLine = text?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
        return when {
            !firstLine.isNullOrEmpty() -> firstLine.take(SESSION_NAME_MAX_LENGTH)
            hasImage -> SHARED_IMAGE_SESSION_NAME
            else -> SHARED_SESSION_NAME
        }
    }

    private companion object {
        /** Max characters of shared text used for the auto-generated session name. */
        const val SESSION_NAME_MAX_LENGTH = 40

        /** Session name for an image-only share. */
        const val SHARED_IMAGE_SESSION_NAME = "Shared image"

        /** Session name fallback when no readable text or image is present. */
        const val SHARED_SESSION_NAME = "Shared content"
    }
}

/** Outcome of handling an incoming share. */
sealed interface ShareLaunchResult {
    /**
     * A run was enqueued in a freshly created session.
     *
     * @property sessionId The new session to deep-link the user into.
     */
    data class Launched(val sessionId: String) : ShareLaunchResult

    /** No pipeline is bound to the share target; the caller should inform the user. */
    data object NotConfigured : ShareLaunchResult

    /** The share carried nothing actionable (no text and no ingestable image). */
    data object NothingShared : ShareLaunchResult
}
