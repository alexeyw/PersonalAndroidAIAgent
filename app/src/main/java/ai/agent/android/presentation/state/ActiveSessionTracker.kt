package ai.agent.android.presentation.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A singleton tracker that holds the ID of the currently visible and active chat session.
 * 
 * This is primarily used to determine if the user is actively viewing a specific chat
 * session on their screen. For example, it helps suppress push notifications for
 * Human-in-the-loop approvals when the inline approval UI is already visible.
 */
@Singleton
class ActiveSessionTracker @Inject constructor() {
    private val _activeSessionId = MutableStateFlow<String?>(null)
    
    /**
     * A [StateFlow] representing the ID of the currently active chat session.
     * Emits `null` if no chat session is currently active or visible.
     */
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    /**
     * Updates the active session ID.
     *
     * @param sessionId The ID of the session that is now active, or null if a session is deactivated.
     */
    fun setActiveSessionId(sessionId: String?) {
        _activeSessionId.value = sessionId
    }
}
