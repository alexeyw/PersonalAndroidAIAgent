package app.knotwork.android.domain.models

/**
 * Domain model describing a single image attached to a user [ChatMessage].
 *
 * The original picked/captured image is never stored: it is downscaled
 * (preserving aspect ratio) and re-encoded to JPEG into the on-device
 * attachment store before a [MessageAttachment] is minted. By the phase
 * contract the attachment belongs to the user message, but only the message
 * text travels along the pipeline graph — wiring the image into the first
 * inference node is a later concern (multimodal inference). One image per
 * message in this phase.
 *
 * @property path Store-relative path (the file name) of the stored image inside
 *   the attachment directory. Kept relative so the persisted reference survives
 *   the app's `filesDir` being a stable but absolute-path-varying location; the
 *   attachment store resolves it to an absolute [java.io.File] for loading and
 *   deletion.
 * @property mimeType MIME type of the stored image. Always `image/jpeg` in this
 *   phase because every ingested image is re-encoded to JPEG; modelled as a
 *   field so future formats do not require a schema change.
 * @property width Pixel width of the stored (already downscaled) image.
 * @property height Pixel height of the stored (already downscaled) image.
 */
data class MessageAttachment(val path: String, val mimeType: String, val width: Int, val height: Int)
