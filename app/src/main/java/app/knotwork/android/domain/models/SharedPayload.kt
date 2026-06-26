package app.knotwork.android.domain.models

/**
 * The content extracted from an incoming `ACTION_SEND` share intent, normalised
 * into the two things the agent can act on: a text body and/or a single image
 * reference.
 *
 * Modelling the parse result as a pure value (rather than reaching into an
 * Android `Intent` deep in the activity) keeps the share-handling logic
 * JVM-unit-testable: the activity only extracts the raw intent fields and hands
 * them to [app.knotwork.android.domain.usecases.ParseSharedContentUseCase].
 *
 * @property text The shared plain text, trimmed; `null` when the share carried
 *   no usable text (e.g. an image-only share).
 * @property imageUri The content-URI string of a shared image, or `null` when
 *   no image was shared. The caller ingests it through
 *   [app.knotwork.android.domain.services.AttachmentStore.ingestUri].
 */
data class SharedPayload(val text: String?, val imageUri: String?) {
    /**
     * Whether the payload carries anything actionable. An empty share (no text
     * and no image) is dropped without launching a run.
     */
    val isEmpty: Boolean
        get() = text.isNullOrBlank() && imageUri.isNullOrBlank()
}
