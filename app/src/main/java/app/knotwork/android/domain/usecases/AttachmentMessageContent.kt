package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.DefaultPrompts

/**
 * Single source of truth for the "message with an image attachment" content
 * contract, shared by the chat composer and the share target so the two paths
 * cannot drift.
 *
 * The rule: a non-empty caption travels the graph verbatim and is what the
 * bubble shows; an **image-only** message (empty caption) instead sends the
 * internal [DefaultPrompts.IMAGE_ONLY_DEFAULT_INSTRUCTION] down the graph while
 * the bubble shows nothing (empty [Resolved.displayContent]).
 */
object AttachmentMessageContent {

    /**
     * The prompt that travels the pipeline graph and the text persisted on the
     * user message bubble.
     *
     * @property prompt Text fed into the pipeline as the user message.
     * @property displayContent Text persisted on the saved message, or `null`
     *   to persist [prompt] verbatim (the common, captioned case). `""` for an
     *   image-only message so the bubble shows just the thumbnail.
     */
    data class Resolved(val prompt: String, val displayContent: String?)

    /**
     * Resolves the prompt / display content for a message that carries an image.
     *
     * @param caption The user's caption, already trimmed; empty means image-only.
     * @return The [Resolved] prompt + display content per the contract above.
     */
    fun resolve(caption: String): Resolved = if (caption.isEmpty()) {
        Resolved(prompt = DefaultPrompts.IMAGE_ONLY_DEFAULT_INSTRUCTION, displayContent = "")
    } else {
        Resolved(prompt = caption, displayContent = null)
    }
}
