package app.knotwork.android.presentation.ui.chat.home

import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.models.MessageAttachment
import app.knotwork.android.domain.services.AttachmentStore
import app.knotwork.android.domain.usecases.EntryInferenceKind
import app.knotwork.android.domain.usecases.ResolveEntryInferenceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Image-attachment delegate of [ChatHomeViewModel].
 *
 * Owns the composer image attachment (`composer.attachment`), the image-source
 * chooser sheet (`sourceChooserVisible`), and the full-screen image viewer
 * (`imageViewer`) — the leaf slices of [ChatHomeScreenState] it writes — plus
 * the `attachmentErrorEvents` one-shot. The multimodal **pre-flight**
 * ([preflightBlockReason]) is exposed publicly because the send path
 * ([ChatHomeViewModel.sendMessage]) consults it before enqueueing an image
 * message; everything else is self-contained.
 *
 * Shares the ViewModel's [scope] and single [state] reducer (see
 * `docs/architecture.md` §1.2).
 *
 * @property scope The ViewModel's [androidx.lifecycle.viewModelScope].
 * @property state The ViewModel's single source-of-truth state flow (shared reducer).
 * @property attachmentStore Ingests / downscales / deletes attachment files and
 *   resolves absolute paths for the preview / viewer.
 * @property resolveEntryInferenceUseCase Resolves where a pipeline's first
 *   inference runs (on-device vision sink / cloud / none) for the pre-flight.
 * @property sessions Provider of the live session cache (the pre-flight reads the
 *   active session's pipeline binding); supplied by the ViewModel which owns the cache.
 */
class ChatHomeAttachmentDelegate(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ChatHomeScreenState>,
    private val attachmentStore: AttachmentStore,
    private val resolveEntryInferenceUseCase: ResolveEntryInferenceUseCase,
    private val sessions: () -> List<ChatSession>,
) {

    private val _attachmentErrorEvents: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)

    /**
     * One-shot signal raised when ingesting a picked/captured image fails. The
     * screen surfaces a transient `KnotworkSnackbar`; deliberately not modelled
     * as a [ChatHomeUiState.Error] so a failed attachment never clobbers the
     * surface's main visual state (e.g. an in-flight `Generating` run).
     */
    val attachmentErrorEvents: SharedFlow<Unit> = _attachmentErrorEvents.asSharedFlow()

    /**
     * Resolves whether an image message must be blocked before it is enqueued,
     * returning the user-facing block reason or `null` when the send may proceed.
     *
     * Three guards, in precedence order:
     * 1. The run starts on a `CLOUD` node — attachments are never sent off-device
     *    ([CLOUD_ATTACHMENT_BLOCKED_MESSAGE]).
     * 2. The active local model is not marked vision-capable — it cannot read an
     *    image ([MODEL_NO_VISION_MESSAGE]); the most common, most actionable fix.
     * 3. The pipeline has no on-device step that could receive the image (no
     *    reachable vision sink), so the picture would be silently ignored
     *    ([PIPELINE_NO_VISION_MESSAGE]).
     *
     * @return The block reason, or `null` to allow the send.
     */
    suspend fun preflightBlockReason(): String? {
        val sessionId = state.value.thread.currentSessionId
        val pipelineId = sessions().firstOrNull { it.id == sessionId }?.pipelineId
        val entryKind = resolveEntryInferenceUseCase(pipelineId)
        if (entryKind == EntryInferenceKind.CLOUD) return CLOUD_ATTACHMENT_BLOCKED_MESSAGE
        val activeSupportsVision = state.value.model.let { model ->
            model.installed.firstOrNull { it.id == model.activeId }?.supportsVision == true
        }
        if (!activeSupportsVision) return MODEL_NO_VISION_MESSAGE
        if (entryKind == EntryInferenceKind.NONE) return PIPELINE_NO_VISION_MESSAGE
        return null
    }

    /** Opens the image-source chooser sheet (Photo library / Camera). */
    fun onAttachClicked() {
        state.update { it.copy(sourceChooserVisible = true) }
    }

    /** Dismisses the image-source chooser sheet without a choice. */
    fun dismissSourceChooser() {
        state.update { it.copy(sourceChooserVisible = false) }
    }

    /**
     * Ingests a picked/captured image: marks the composer attachment
     * [ComposerAttachmentDraft.Processing], downscales + re-encodes it through
     * [AttachmentStore], then settles to [ComposerAttachmentDraft.Ready] (or
     * clears the draft and surfaces an error on failure).
     *
     * @param uri content URI string of the picked/captured image.
     */
    fun onImagePicked(uri: String) {
        // Discard any prior pending attachment's file (it was never sent) before
        // replacing the draft, so a re-pick doesn't leave an orphan behind.
        val replacedPath = (state.value.composer.attachment as? ComposerAttachmentDraft.Ready)?.attachment?.path
        state.update {
            it.copy(
                sourceChooserVisible = false,
                composer = it.composer.copy(attachment = ComposerAttachmentDraft.Processing),
            )
        }
        scope.launch {
            if (replacedPath != null) {
                attachmentStore.delete(replacedPath)
            }
            val stored = attachmentStore.ingestUri(uri).getOrNull()
            if (stored != null) {
                val ready = ComposerAttachmentDraft.Ready(
                    attachment = stored,
                    absolutePath = attachmentStore.absolutePathFor(stored.path),
                    detail = attachmentDetailLabel(stored.width, stored.height, attachmentStore.sizeBytes(stored.path)),
                )
                state.update { it.copy(composer = it.composer.copy(attachment = ready)) }
            } else {
                // Transient failure — surface a snackbar rather than flipping the
                // whole surface to Error (which would clobber an in-flight run).
                state.update { it.copy(composer = it.composer.copy(attachment = null)) }
                _attachmentErrorEvents.tryEmit(Unit)
            }
        }
    }

    /** Removes the pending composer attachment without sending it, deleting its file. */
    fun removeAttachment() {
        val path = (state.value.composer.attachment as? ComposerAttachmentDraft.Ready)?.attachment?.path
        state.update { it.copy(composer = it.composer.copy(attachment = null)) }
        if (path != null) {
            scope.launch { attachmentStore.delete(path) }
        }
    }

    /**
     * Opens the full-screen viewer for [attachment]. The existence check and
     * size read run off the main thread (via [AttachmentStore]) so tapping a
     * bubble never blocks the UI; the viewer renders the calm "no longer
     * available" state when retention has cleared the file.
     *
     * @param attachment the attachment of the tapped message bubble.
     */
    fun openImageViewer(attachment: MessageAttachment) {
        val absolutePath = attachmentStore.absolutePathFor(attachment.path)
        scope.launch {
            val exists = attachmentStore.exists(attachment.path)
            val detail =
                attachmentDetailLabel(attachment.width, attachment.height, attachmentStore.sizeBytes(attachment.path))
            state.update {
                it.copy(
                    imageViewer = ImageViewerTarget(
                        model = if (exists) absolutePath else null,
                        fileName = attachment.path,
                        dimensionsLabel = detail,
                        isMissing = !exists,
                    ),
                )
            }
        }
    }

    /** Closes the full-screen image viewer. */
    fun dismissImageViewer() {
        state.update { it.copy(imageViewer = null) }
    }

    /**
     * Builds the mono dimensions/size label shown on the composer preview and
     * the viewer top bar, e.g. `712×1536 · 84 KB`. Pure: [sizeBytes] is read by
     * the caller off the main thread. Falls back to dimensions only when the
     * size is unknown.
     */
    private fun attachmentDetailLabel(width: Int, height: Int, sizeBytes: Long): String {
        val sizeKb = (sizeBytes / BYTES_PER_KB).toInt()
        val dimensions = "$width×$height"
        return if (sizeKb > 0) "$dimensions · $sizeKb KB" else dimensions
    }

    companion object {
        /**
         * Surfaced when the user attaches an image but the active local model is
         * not marked vision-capable. The run is blocked before it starts so the
         * native inference layer never receives an image it cannot decode; the
         * draft and attachment are preserved so the user can switch models and
         * resend. Calm, non-alarmist copy in line with the attachment UX.
         */
        const val MODEL_NO_VISION_MESSAGE: String =
            "This model can't read images. Turn on image support for it on the Models screen, " +
                "or switch to a vision-capable model."

        /**
         * Surfaced when the user attaches an image but the bound pipeline starts
         * on a CLOUD node. Attachments stay on-device by design, so the run is
         * blocked before it starts rather than silently dropping the image.
         */
        const val CLOUD_ATTACHMENT_BLOCKED_MESSAGE: String =
            "Images stay on your device and aren't sent to cloud models. Use a pipeline that starts " +
                "with an on-device step to send a picture."

        /**
         * Surfaced when the user attaches an image but the bound pipeline has no
         * on-device step that could read it (no reachable `LITE_RT` node that
         * carries the original task). Blocking is honest: the engine would
         * otherwise drop the image silently while the run looks normal.
         */
        const val PIPELINE_NO_VISION_MESSAGE: String =
            "This pipeline has no on-device step that can read an image. Pick a pipeline with an " +
                "on-device model step to send a picture."

        /** Divisor used to render an attachment's file size in kilobytes. */
        private const val BYTES_PER_KB: Long = 1024L
    }
}
