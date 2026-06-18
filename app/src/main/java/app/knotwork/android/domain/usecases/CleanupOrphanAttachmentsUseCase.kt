package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.services.AttachmentStore
import javax.inject.Inject

/**
 * Deletes orphaned image-attachment files — files present in the attachment
 * store that no chat message references anymore.
 *
 * Attachment files are deleted eagerly when their owning message or session is
 * removed ([ChatRepository] does this), but that deletion is best-effort: a
 * failed file delete, a process death mid-delete, or a legacy row can leave a
 * file behind with no message pointing at it. This sweep is the backstop. It
 * diffs the files actually on disk ([AttachmentStore.listStoredPaths]) against
 * the set still referenced by messages
 * ([ChatRepository.getReferencedAttachmentPaths]) and deletes the difference.
 *
 * The pass is read-only with respect to the database — it never deletes a
 * message — so it can never race the engine or remove a live attachment.
 *
 * @property chatRepository Source of the referenced attachment paths.
 * @property attachmentStore Source of the stored files and the deletion sink.
 */
class CleanupOrphanAttachmentsUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val attachmentStore: AttachmentStore,
) {
    /**
     * Runs one orphan-cleanup pass.
     *
     * @return The number of orphaned files successfully deleted. Returns `0`
     *   when the store cannot be listed (best-effort: the next pass retries).
     */
    suspend operator fun invoke(): Int {
        val stored = attachmentStore.listStoredPaths().getOrElse { return 0 }
        if (stored.isEmpty()) {
            return 0
        }
        val referenced = chatRepository.getReferencedAttachmentPaths().toSet()
        val orphans = stored.filterNot { it in referenced }
        return orphans.count { attachmentStore.delete(it).isSuccess }
    }
}
