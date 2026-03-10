package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case responsible for generating a prompt string from the recent chat history,
 * ensuring that the total length of the prompt does not exceed the maximum allowed
 * context length.
 *
 * @property chatRepository The repository to fetch chat history.
 * @property settingsRepository The repository to fetch context length limits.
 */
class GetContextWindowUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Retrieves the chat history for the given session ID, truncates old messages
     * to fit within the configured maximum context length, and formats the remaining
     * messages into a single prompt string.
     *
     * @param sessionId The unique ID of the chat session.
     * @return A formatted string containing the most recent messages that fit into the context window.
     */
    suspend operator fun invoke(sessionId: String): String {
        val messages = chatRepository.getMessagesForSession(sessionId).first()
        val maxLength = settingsRepository.maxContextLength.first()

        val formattedMessages = mutableListOf<String>()
        var currentLength = 0

        // Iterate from newest to oldest
        for (message in messages.reversed()) {
            val formattedMessage = formatMessage(message)
            // Add +1 for a newline separator if it's not the first message being added
            val separatorLength = if (formattedMessages.isNotEmpty()) 1 else 0
            
            if (currentLength + formattedMessage.length + separatorLength <= maxLength) {
                formattedMessages.add(0, formattedMessage)
                currentLength += formattedMessage.length + separatorLength
            } else {
                // If adding this message exceeds the limit, stop adding older messages.
                break
            }
        }

        return formattedMessages.joinToString("\n")
    }

    private fun formatMessage(message: ChatMessage): String {
        val prefix = when (message.role) {
            Role.USER -> "USER: "
            Role.AGENT -> "AGENT: "
            Role.SYSTEM -> "SYSTEM: "
        }
        return "$prefix${message.content}"
    }
}