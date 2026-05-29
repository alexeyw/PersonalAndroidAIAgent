package app.knotwork.design.components.chat

/**
 * Actions exposed by the long-press context menu on a [ChatMessage].
 *
 * Matches `compose/components/README.md` §Chat surface: long-press opens a
 * small dropdown with copy / re-run / rate / save-to-memory. The catalog
 * component only surfaces the user's choice via `onContextAction(action)` —
 * the screen decides what each action does (copy to clipboard, replay the
 * prompt, open the rating sheet, persist the message to long-term memory).
 */
enum class ChatContextAction {
    /** Copy the message text to the clipboard. */
    Copy,

    /** Re-run the message (replay the user prompt or regenerate the assistant reply). */
    Rerun,

    /** Open the thumbs-up / down rating sheet for this assistant turn. */
    Rate,

    /** Persist the message text to long-term memory as a manual entry. */
    SaveToMemory,
}
