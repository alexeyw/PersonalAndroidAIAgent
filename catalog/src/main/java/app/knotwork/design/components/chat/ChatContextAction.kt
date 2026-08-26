package app.knotwork.design.components.chat

/**
 * Actions exposed by the long-press context menu on a [ChatMessage].
 *
 * Chat surface behaviour: long-press opens a
 * small dropdown with copy / re-run / save-to-memory, plus report on
 * model-authored messages. The catalog component only surfaces the user's
 * choice via `onContextAction(action)` — the screen decides what each action
 * does (copy to clipboard, replay the prompt, persist the message to
 * long-term memory, raise the report dialog).
 */
enum class ChatContextAction {
    /** Copy the message text to the clipboard. */
    Copy,

    /** Re-run the message (replay the user prompt or regenerate the assistant reply). */
    Rerun,

    /** Persist the message text to long-term memory as a manual entry. */
    SaveToMemory,

    /**
     * Flag a model-authored message as offensive or unsafe.
     *
     * Offered on assistant and tool messages only — a user's own text is not
     * model output and reporting it to the maintainer would be meaningless.
     */
    Report,
}
