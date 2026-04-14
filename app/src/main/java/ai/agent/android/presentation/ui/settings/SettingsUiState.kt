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
 * @property openAiKey The OpenAI API key.
 * @property openAiModel The OpenAI model name.
 * @property anthropicKey The Anthropic API key.
 * @property anthropicModel The Anthropic model name.
 * @property googleKey The Google API key.
 * @property googleModel The Google model name.
 * @property deepSeekKey The DeepSeek API key.
 * @property deepSeekModel The DeepSeek model name.
 * @property ollamaBaseUrl The Ollama local base URL.
 * @property ollamaModel The Ollama model name.
 * @property ollamaContextWindow The Ollama context window size.
 */
data class SettingsUiState(
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val maxContextLength: Int = 4096,
    val systemPromptPrefix: String = "",
    val requiresUserConfirmation: Boolean = true,
    val openAiKey: String = "",
    val openAiModel: String = "",
    val anthropicKey: String = "",
    val anthropicModel: String = "",
    val googleKey: String = "",
    val googleModel: String = "",
    val deepSeekKey: String = "",
    val deepSeekModel: String = "",
    val ollamaBaseUrl: String = "",
    val ollamaModel: String = "",
    val ollamaContextWindow: String = "4096",
    val localModelBackend: String = "CPU"
)
