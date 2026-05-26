package ai.agent.android.data.engine

import ai.agent.android.domain.engine.CloudLlmModelResolver
import ai.agent.android.domain.models.CloudProvider
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
 * Reads the user-configured model id for each provider from [ApiKeyRepository] and
 * substitutes a per-provider default when nothing is set. Centralising this here keeps
 * `CloudLlmNodeExecutor` free of both data-layer constants (`KoogModelMapper`,
 * `OpenAIModels`, …) and the per-provider `apiKeyRepository.get*Model()` branching.
 */
@Singleton
class KoogCloudLlmModelResolver @Inject constructor(private val apiKeyRepository: ApiKeyRepository) :
    CloudLlmModelResolver {

    /**
     * Resolves the Koog [LLModel] to use for a given provider. Exhaustive on
     * [CloudProvider] so the compiler flags any unhandled future provider.
     *
     * @param provider The typed [CloudProvider] to resolve a model object for.
     * @return The Koog [LLModel] cast to [Any] for the domain boundary.
     */
    override suspend fun resolveModel(provider: CloudProvider): Any = when (provider) {
        CloudProvider.OPENAI -> KoogModelMapper.getOpenAIModel(
            apiKeyRepository.getOpenAIModel().first() ?: OpenAIModels.Chat.GPT5_4.id,
        )
        CloudProvider.ANTHROPIC -> KoogModelMapper.getAnthropicModel(
            apiKeyRepository.getAnthropicModel().first() ?: AnthropicModels.Sonnet_4_5.id,
        )
        CloudProvider.GOOGLE -> KoogModelMapper.getGoogleModel(
            apiKeyRepository.getGoogleModel().first() ?: GoogleModels.Gemini3_Flash_Preview.id,
        )
        CloudProvider.DEEPSEEK -> KoogModelMapper.getDeepSeekModel(
            apiKeyRepository.getDeepSeekModel().first() ?: DeepSeekModels.DeepSeekV4Flash.id,
        )
        CloudProvider.OLLAMA -> LLModel(
            provider = LLMProvider.Ollama,
            id = apiKeyRepository.getOllamaModelName().first() ?: "llama3",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = apiKeyRepository.getOllamaContextWindowSize().first().toLong(),
        )
    }
}
