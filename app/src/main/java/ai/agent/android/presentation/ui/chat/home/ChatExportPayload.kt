package ai.agent.android.presentation.ui.chat.home

/**
 * One-shot event payload produced by the chat-home ViewModel when the user
 * triggers an export from the TopAppBar overflow menu. Consumed by the
 * screen-level `LaunchedEffect` that fires an Android share sheet
 * ([android.content.Intent.ACTION_SEND]) carrying the JSON document.
 *
 * @property sessionName Human-readable name of the exported session
 *   (forwarded as the share subject).
 * @property json Pretty-printed JSON representation of the session — a list
 *   of messages with role, text, and timestamp.
 */
data class ChatExportPayload(val sessionName: String, val json: String)
