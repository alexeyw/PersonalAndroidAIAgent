package ai.agent.android.data.engine

import ai.agent.android.domain.engine.CloudLlmModelResolver
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data-layer implementation of [CloudLlmModelResolver].
 *
 * Owns the per-provider default model identifiers and the Ollama context-window/model
 * lookup so that `CloudLlmNodeExecutor` (in the domain layer) never imports
 * `KoogModelMapper` or other data-layer constants directly.
 */
@Singleton
class KoogCloudLlmModelResolver @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository,
) : CloudLlmModelResolver {

    /**
     * Resolves the Koog [LLModel] to use for a given provider.
     *
     * @param provider Lowercase provider key.
     * @param configuredModelId The user-selected model id (nullable — falls back to provider default).
     * @return The Koog [LLModel] cast to [Any] for the domain boundary.
     */
    override suspend fun resolveModel(provider: String, configuredModelId: String?): Any {
        return when (provider.lowercase()) {
            "openai" -> KoogModelMapper.getOpenAIModel(configuredModelId ?: OpenAIModels.Chat.GPT5_4.id)
            "anthropic" -> KoogModelMapper.getAnthropicModel(configuredModelId ?: AnthropicModels.Sonnet_4_5.id)
            "google", "gemini" -> KoogModelMapper.getGoogleModel(configuredModelId ?: GoogleModels.Gemini3_Flash_Preview.id)
            "deepseek" -> KoogModelMapper.getDeepSeekModel(configuredModelId ?: DeepSeekModels.DeepSeekChat.id)
            "ollama" -> LLModel(
                provider = LLMProvider.Ollama,
                id = configuredModelId ?: apiKeyRepository.getOllamaModelName().first() ?: "llama3",
                capabilities = listOf(LLMCapability.Completion),
                contextLength = apiKeyRepository.getOllamaContextWindowSize().first().toLong(),
            )
            else -> KoogModelMapper.getGoogleModel(configuredModelId ?: GoogleModels.Gemini3_Flash_Preview.id)
        }
    }
}
