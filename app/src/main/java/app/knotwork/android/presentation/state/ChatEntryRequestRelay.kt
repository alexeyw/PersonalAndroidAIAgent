package app.knotwork.android.presentation.state

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a `knotwork://chat…` / `knotwork://new-chat` entry surface asks the single
 * chat home to do once it is on screen.
 */
sealed interface ChatEntryRequest {
    /** Start a brand-new empty session (the "New chat" launcher shortcut). */
    data object NewChat : ChatEntryRequest

    /** Switch the chat home to an existing session (recent-chat shortcut, share, notification). */
    data class OpenThread(val threadId: String) : ChatEntryRequest
}

/**
 * Process-wide one-shot bus carrying a [ChatEntryRequest] from a deep-link /
 * shortcut handler to the single chat-home surface.
 *
 * The chat *is* the home: there is one chat destination
 * ([app.knotwork.android.presentation.ui.navigation.NavRoutes.CHAT_TAB]), not a
 * separate per-thread screen, so a deep link can only change **which session**
 * the home shows — never add a second back-stack entry (Back from the chat
 * always closes the app, exactly like a normal launch). The home reads its
 * session once on init, so navigation alone cannot switch or create a session;
 * the handler routes the intent home and posts it here, and the `CHAT_TAB`
 * composable drains it (`selectThread` / `createNewSessionWithPipeline`).
 *
 * Backed by a [Channel.CONFLATED] so a request emitted on a cold launch — before
 * the chat screen and its collector exist — is **buffered** until the collector
 * mounts and is **consumed exactly once** (a config-change recomposition must not
 * replay it and switch/spawn a session again). Conflation keeps only the latest
 * pending request (one per launch in practice) and never suspends the sender.
 */
@Singleton
class ChatEntryRequestRelay @Inject constructor() {

    private val channel = Channel<ChatEntryRequest>(capacity = Channel.CONFLATED)

    /** Stream drained by the chat home; each element is one entry request. */
    val requests: Flow<ChatEntryRequest> = channel.receiveAsFlow()

    /** Posts a "start a fresh chat" request. Non-suspending fire-and-forget. */
    fun newChat() {
        channel.trySend(ChatEntryRequest.NewChat)
    }

    /** Posts a "switch to [threadId]" request. Non-suspending fire-and-forget. */
    fun openThread(threadId: String) {
        channel.trySend(ChatEntryRequest.OpenThread(threadId))
    }
}
