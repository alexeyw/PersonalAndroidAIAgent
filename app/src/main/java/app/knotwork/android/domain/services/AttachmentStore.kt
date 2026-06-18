package app.knotwork.android.domain.services

import app.knotwork.android.domain.models.MessageAttachment

/**
 * On-device store for image attachments of chat messages.
 *
 * The store owns a private directory (`files/attachments/`) inside app storage.
 * It never keeps the original picked/captured image: [ingest] decodes the
 * supplied bytes, corrects EXIF orientation, downscales **preserving the aspect
 * ratio** so the longest side fits the model-coordinated cap, re-encodes to
 * JPEG, and writes that single derived file. The returned [MessageAttachment]
 * carries the store-relative path (file name) persisted on the message; the
 * absolute path for loading is resolved on demand via [absolutePathFor].
 *
 * Retention: [delete] removes a single file (called when its owning message or
 * session is deleted), and [listStoredPaths] enumerates every stored file so a
 * background sweep can delete the ones no message references anymore.
 */
interface AttachmentStore {
    /**
     * Ingests raw image bytes: decode → EXIF-correct → aspect-preserving
     * downscale → JPEG re-encode → write into the attachment directory.
     *
     * @param bytes The original image bytes (e.g. read from a picked/captured
     *   content URI). Held in memory only for the duration of the call.
     * @return [Result.success] with the stored [MessageAttachment] (relative
     *   path, `image/jpeg`, downscaled pixel dimensions), or [Result.failure]
     *   when the bytes cannot be decoded as an image or writing fails.
     */
    suspend fun ingest(bytes: ByteArray): Result<MessageAttachment>

    /**
     * Reads the bytes behind a content URI (a picked gallery image or a camera
     * capture) and ingests them via [ingest].
     *
     * @param uri content URI string of the picked/captured image.
     * @return [Result.success] with the stored [MessageAttachment], or
     *   [Result.failure] when the URI cannot be read or decoded.
     */
    suspend fun ingestUri(uri: String): Result<MessageAttachment>

    /**
     * Deletes a single stored attachment file. Succeeds (no-op) when the file
     * is already absent so retention is idempotent.
     *
     * @param path The store-relative path (file name) returned by [ingest].
     * @return [Result.success] when the file is gone, [Result.failure] on an
     *   unexpected I/O error.
     */
    suspend fun delete(path: String): Result<Unit>

    /**
     * Enumerates the store-relative paths of every file currently in the
     * attachment directory. Used by the orphan-cleanup sweep to diff against
     * the set of paths still referenced by messages.
     *
     * @return [Result.success] with the stored file names, or [Result.failure]
     *   when the directory cannot be read.
     */
    suspend fun listStoredPaths(): Result<List<String>>

    /**
     * Resolves a store-relative attachment path to an absolute filesystem path
     * suitable for an image loader. Pure path arithmetic — does not touch disk
     * and does not verify the file exists.
     *
     * @param path The store-relative path (file name) from a [MessageAttachment].
     * @return The absolute path of the file inside the attachment directory.
     */
    fun absolutePathFor(path: String): String
}
