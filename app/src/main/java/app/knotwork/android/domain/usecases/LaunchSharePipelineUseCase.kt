package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.models.EntrySurface
import app.knotwork.android.domain.models.MessageAttachment
import app.knotwork.android.domain.models.RunOrigin
import app.knotwork.android.domain.models.SharedPayload
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.services.AttachmentStore
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Launches the pipeline bound to the share target over the content the user
 * shared into the app via `ACTION_SEND`.
 *
 * The share activity brings the app to the foreground, so unlike the tile this
 * uses the interactive queue ([AgentOrchestratorUseCase]) and returns the id of
 * the session it ran in so the caller can deep-link the user straight into the
 * live run.
 *
 * **Session reuse.** When [SettingsRepository.shareReuseSession] is `true` (the
 * default) every share accumulates in one reusable **Shared** chat
 * ([SHARED_INBOX_SESSION_ID]) — new shares are appended, keeping the history in
 * one legible place. When it is `false` a fresh, auto-named session is created
 * per share (the original behaviour). Either way the active chat is never
 * polluted — shares land in the Shared chat or a brand-new one, not the chat the
 * user was in.
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
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Resolves the share binding and, when present and the payload is non-empty,
     * creates a bound session and enqueues a run over the shared content.
     *
     * @param payload The normalised shared content (text and/or image URI).
     * @param reusedSessionName Localised name for the single **Shared** chat used
     *   when session reuse is on (the caller resolves it from a string resource —
     *   the domain layer keeps no user-visible literals).
     * @param imageSessionName Localised name for an image-only share's session
     *   (used only in per-share mode).
     * @param contentSessionName Localised name fallback when no readable text or
     *   image is present (used only in per-share mode).
     * @return [ShareLaunchResult.Launched] with the session id,
     *   [ShareLaunchResult.NotConfigured] when nothing is bound, or
     *   [ShareLaunchResult.NothingShared] when the payload had no content.
     */
    suspend operator fun invoke(
        payload: SharedPayload,
        reusedSessionName: String,
        imageSessionName: String,
        contentSessionName: String,
    ): ShareLaunchResult {
        if (payload.isEmpty) return ShareLaunchResult.NothingShared
        val pipelineId = resolveSurfacePipeline(EntrySurface.SHARE) ?: return ShareLaunchResult.NotConfigured

        val attachment = payload.imageUri?.let { ingestImage(it) }
        val hasText = !payload.text.isNullOrBlank()
        // Image-only share with a failed ingest leaves us nothing to run.
        if (!hasText && attachment == null) return ShareLaunchResult.NothingShared

        val session = resolveSession(
            payload = payload,
            hasImage = attachment != null,
            pipelineId = pipelineId,
            reusedSessionName = reusedSessionName,
            imageSessionName = imageSessionName,
            contentSessionName = contentSessionName,
        )
        chatRepository.saveSession(session)

        // Prompt / display content follow the shared image-only contract.
        val content = AttachmentMessageContent.resolve(payload.text?.trim().orEmpty())

        agentOrchestrator(
            sessionId = session.id,
            userPrompt = content.prompt,
            pipelineId = pipelineId,
            attachment = attachment,
            displayContent = content.displayContent,
            origin = RunOrigin.SHARE,
        )
        return ShareLaunchResult.Launched(session.id)
    }

    /**
     * Picks the session the share runs in. With reuse on, the single Shared chat
     * ([SHARED_INBOX_SESSION_ID]) is reused if it still exists (re-pointing its
     * binding to the current share pipeline) or created; with reuse off, a fresh
     * auto-named session is created per share.
     */
    private suspend fun resolveSession(
        payload: SharedPayload,
        hasImage: Boolean,
        pipelineId: String,
        reusedSessionName: String,
        imageSessionName: String,
        contentSessionName: String,
    ): ChatSession {
        if (!settingsRepository.shareReuseSession.first()) {
            return ChatSession.create(
                name = sessionName(payload.text, hasImage, imageSessionName, contentSessionName),
                pipelineId = pipelineId,
            )
        }
        val existing = chatRepository.getSessionById(SHARED_INBOX_SESSION_ID)
        return existing?.copy(pipelineId = pipelineId)
            ?: ChatSession.create(id = SHARED_INBOX_SESSION_ID, name = reusedSessionName, pipelineId = pipelineId)
    }

    /** Best-effort image ingest; a failure degrades to a text-only (or empty) share. */
    private suspend fun ingestImage(uri: String): MessageAttachment? =
        attachmentStore.ingestUri(uri).getOrElse { error ->
            Timber.w(error, "Failed to ingest shared image; continuing without it.")
            null
        }

    /** Derives a session name from the shared text, falling back to a caller-localised label. */
    private fun sessionName(
        text: String?,
        hasImage: Boolean,
        imageSessionName: String,
        contentSessionName: String,
    ): String {
        // Collapse all whitespace (including line breaks) into single spaces so the
        // whole shared payload — not just its first line — contributes to a clean,
        // informative single-line title. A shared article often opens with a short
        // header line; flowing the following text in makes the title far more useful.
        val normalized = text?.trim()?.replace(WHITESPACE_RUN, " ").orEmpty()
        return when {
            normalized.isNotEmpty() ->
                if (normalized.length > SESSION_NAME_MAX_LENGTH) {
                    normalized.take(SESSION_NAME_MAX_LENGTH).trimEnd() + SESSION_NAME_SUFFIX
                } else {
                    normalized
                }
            hasImage -> imageSessionName
            else -> contentSessionName
        }
    }

    /** Constants for the share launch flow, incl. the reserved Shared-chat id. */
    companion object {
        /**
         * Reserved, stable id of the single **Shared** chat that accumulates every
         * share when session reuse is on. A fixed id (not a random UUID) lets the
         * chat be found and reused across shares, and re-created with the same id
         * if the user deletes it.
         */
        const val SHARED_INBOX_SESSION_ID = "shared-inbox"

        /** Max characters of shared text used for the auto-generated session name. */
        private const val SESSION_NAME_MAX_LENGTH = 60

        /** Suffix appended when the shared text is longer than [SESSION_NAME_MAX_LENGTH]. */
        private const val SESSION_NAME_SUFFIX = "…"

        /** Matches a run of one or more whitespace characters (spaces, tabs, newlines). */
        private val WHITESPACE_RUN = Regex("\\s+")
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
