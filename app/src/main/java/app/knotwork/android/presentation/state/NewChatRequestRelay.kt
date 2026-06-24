package app.knotwork.android.presentation.state

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide one-shot bus carrying a "start a fresh chat" request from the
 * `knotwork://new-chat` launcher shortcut to the chat home surface.
 *
 * The chat home reads its session once on init (from
 * `SettingsRepository.currentChatSessionId`), so a deep link cannot create a new
 * chat just by navigating — something has to actively invoke
 * `ChatHomeThreadsDelegate.createNewSessionWithPipeline`. The shortcut handler
 * ([request]) signals that, and the `CHAT_TAB` composable drains it.
 *
 * Backed by a [Channel.CONFLATED] rather than a `replay = 0` SharedFlow on
 * purpose: on a cold launch the request is emitted *before* the chat screen (and
 * its collector) exists, so the value must be **buffered** until the collector
 * arrives — and **consumed exactly once** so a later config-change recomposition
 * does not spawn a second empty chat. Conflation keeps only the latest pending
 * request and never suspends the sender.
 */
@Singleton
class NewChatRequestRelay @Inject constructor() {

    private val channel = Channel<Unit>(capacity = Channel.CONFLATED)

    /** Stream drained by the chat home; each element is one "create a new chat" request. */
    val requests: Flow<Unit> = channel.receiveAsFlow()

    /** Publishes a single new-chat request. Non-suspending fire-and-forget. */
    fun request() {
        channel.trySend(Unit)
    }
}
