package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.SharedPayload
import javax.inject.Inject

/**
 * Pure mapper from the raw fields of an incoming `ACTION_SEND` share intent to a
 * normalised [SharedPayload].
 *
 * The Android `Intent` is never referenced here: the share activity reads the
 * action, MIME type, extra text and extra stream URI off the intent and passes
 * them as plain values, so all the branching (text vs image, MIME validation)
 * stays JVM-unit-testable. This is the share-side analogue of how the rest of
 * the engine keeps framework-bound parsing at the very edge.
 */
class ParseSharedContentUseCase @Inject constructor() {

    /**
     * Normalises the raw share-intent fields into a [SharedPayload].
     *
     * @param mimeType The intent's `type` (e.g. `text/plain`, `image/jpeg`),
     *   or `null` when the sender supplied none.
     * @param text The `EXTRA_TEXT` value, or `null`. Blank text is dropped.
     * @param streamUri The `EXTRA_STREAM` content-URI string, or `null`.
     * @return A [SharedPayload]; the image reference is kept only when the MIME
     *   type advertises an image, guarding against a non-image stream sneaking
     *   in through a mistyped share.
     */
    operator fun invoke(mimeType: String?, text: String?, streamUri: String?): SharedPayload {
        val normalisedText = text?.trim()?.takeIf { it.isNotEmpty() }
        // MIME types are case-insensitive (RFC 2045), and the intent-filter
        // matches them case-insensitively, so normalise before the prefix test.
        val isImage = mimeType?.lowercase()?.startsWith(IMAGE_MIME_PREFIX) == true
        val imageUri = streamUri?.takeIf { it.isNotBlank() && isImage }
        return SharedPayload(text = normalisedText, imageUri = imageUri)
    }

    private companion object {
        /** MIME prefix of any sharable image type (`image/jpeg`, `image/png`, …). */
        const val IMAGE_MIME_PREFIX = "image/"
    }
}
