package ai.agent.android.presentation.ui.chat

/**
 * One-shot event payload produced by [ChatViewModel.exportChat] and consumed by [ChatScreen]
 * to launch an Android share sheet ([android.content.Intent.ACTION_SEND]) for the given JSON.
 *
 * @property sessionName Human-readable name of the exported session (used as share subject).
 * @property json Full JSON representation of the session (list of messages with role, text, timestamp).
 */
data class ChatExportPayload(val sessionName: String, val json: String)
