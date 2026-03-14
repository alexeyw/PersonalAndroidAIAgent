package ai.agent.android.presentation.ui.settings

/**
 * Represents the UI state for the Settings screen.
 *
 * @property temperature The sampling temperature for generation.
 * @property topK The top-k sampling parameter for generation.
 * @property topP The top-p sampling parameter for generation.
 * @property maxContextLength The maximum allowed context length (tokens/characters).
 * @property systemPromptPrefix The main system prompt instructions for the agent.
 * @property requiresUserConfirmation Whether the user must confirm critical actions (Human-in-the-loop).
 */
data class SettingsUiState(
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val maxContextLength: Int = 4096,
    val systemPromptPrefix: String = "",
    val requiresUserConfirmation: Boolean = true
)
