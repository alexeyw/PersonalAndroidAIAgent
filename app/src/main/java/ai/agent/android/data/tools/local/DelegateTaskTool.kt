package ai.agent.android.data.tools.local

import ai.agent.android.data.engine.KoogClientFactory
import ai.agent.android.data.engine.KoogModelMapper
import ai.agent.android.domain.engine.TextEmbeddingEngine
import ai.agent.android.domain.models.CloudProvider
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.MemoryRepository
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * A specialized tool designed for the main (local) AI agent to delegate complex or
 * specialized tasks to powerful external Large Language Models (LLMs) such as Claude,
 * OpenAI, or Gemini, through the KoogClientFactory.
 *
 * This class exposes a function that the local agent can call. When called,
 * it routes the prompt to the specified external model, awaits the response asynchronously,
 * generates a semantic text embedding for the resulting response, and finally saves
 * both the text and its embedding into the local long-term memory via [MemoryRepository].
 *
 * This allows the local agent to remain lightweight and responsive, while offloading
 * computationally expensive or highly specialized reasoning to cloud models.
 *
 * @property koogClientFactory A factory used to instantiate the appropriate external LLM client.
 * @property memoryRepository The repository responsible for persisting long-term memories.
 * @property textEmbeddingEngine The engine used to convert text into vector embeddings for semantic search.
 * @property apiKeyRepository The repository responsible for persisting selected model configurations.
 */
class DelegateTaskTool @Inject constructor(
    private val koogClientFactory: KoogClientFactory,
    private val memoryRepository: MemoryRepository,
    private val textEmbeddingEngine: TextEmbeddingEngine,
    private val apiKeyRepository: ApiKeyRepository,
) {

    /**
     * Executes the task delegation process.
     *
     * It performs the following steps:
     * 1. Validates the target model.
     * 2. Instantiates the client for the target model.
     * 3. Sends the prompt to the external model with a 60-second timeout to avoid blocking.
     * 4. Processes the response, generates an embedding, and saves it to the memory repository.
     *
     * The entire operation is wrapped in a [Dispatchers.IO] context to prevent blocking
     * the main thread or the agent's Foreground Service.
     *
     * @param taskDescription A detailed explanation of the task to be delegated. This will be used as the prompt for the external LLM.
     * @param targetModel The identifier for the external model to use. Supported values: "anthropic", "openai", "google", "deepseek", "ollama". Defaults to "google".
     * @return A summary string detailing the outcome of the delegation, including whether it succeeded, timed out, or encountered an error. This summary is returned back to the calling agent.
     */
    suspend fun executeDelegation(taskDescription: String, targetModel: String = CloudProvider.GOOGLE.id): String =
        withContext(Dispatchers.IO) {
            // Tool arguments arrive as raw JSON strings produced by the LLM, so the
            // provider id is parsed (with legacy aliases) on the way in. Unknown ids
            // surface as a typed error rather than silently falling through.
            val provider = CloudProvider.fromId(targetModel)
                ?: return@withContext "Error: Unsupported target model '$targetModel'." +
                    " Supported models: anthropic, openai, google, deepseek, ollama."

            val client = when (provider) {
                CloudProvider.ANTHROPIC -> koogClientFactory.createAnthropicExecutor()
                CloudProvider.OPENAI -> koogClientFactory.createOpenAIExecutor()
                CloudProvider.GOOGLE -> koogClientFactory.createGoogleExecutor()
                CloudProvider.DEEPSEEK -> koogClientFactory.createDeepSeekExecutor()
                CloudProvider.OLLAMA -> koogClientFactory.createOllamaExecutor()
            }

            if (client == null) {
                return@withContext "Error: Client for '${provider.id}' could not be initialized. " +
                    "Please check if the API key or configuration is provided."
            }

            return@withContext try {
                val model = when (provider) {
                    CloudProvider.ANTHROPIC -> KoogModelMapper.getAnthropicModel(
                        apiKeyRepository.getAnthropicModel().first() ?: AnthropicModels.Sonnet_4_5.id,
                    )

                    CloudProvider.OPENAI -> KoogModelMapper.getOpenAIModel(
                        apiKeyRepository.getOpenAIModel().first() ?: OpenAIModels.Chat.GPT5_4.id,
                    )

                    CloudProvider.GOOGLE -> KoogModelMapper.getGoogleModel(
                        apiKeyRepository.getGoogleModel().first() ?: GoogleModels.Gemini3_Flash_Preview.id,
                    )

                    CloudProvider.DEEPSEEK -> KoogModelMapper.getDeepSeekModel(
                        apiKeyRepository.getDeepSeekModel().first() ?: DeepSeekModels.DeepSeekChat.id,
                    )

                    CloudProvider.OLLAMA -> LLModel(
                        provider = LLMProvider.Ollama,
                        id = apiKeyRepository.getOllamaModelName().first() ?: "llama3",
                        capabilities = listOf(
                            LLMCapability.Completion,
                        ),
                        contextLength = apiKeyRepository.getOllamaContextWindowSize().first().toLong(),
                    )
                }

                // Apply a 60-second timeout for the external API call
                val result = withTimeoutOrNull(LLM_CALL_TIMEOUT_MS) {
                    val stream = client.executeStreaming(prompt("default") { user(taskDescription) }, model)
                    stream.mapNotNull { frame -> (frame as? StreamFrame.TextDelta)?.text }.toList().joinToString("")
                }

                if (result.isNullOrBlank()) {
                    "Error: Task delegation to '${provider.id}' timed out or returned empty after 60 seconds."
                } else {
                    // Task succeeded. Generate embedding for the result.
                    val responseText = result
                    val embedding = textEmbeddingEngine.generateEmbedding(responseText)

                    // Save to long-term memory so the local agent can recall it later
                    memoryRepository.saveMemory(responseText, embedding)

                    "Success: Task completed by '${provider.id}' and saved to memory. " +
                        "Summary of response: ${responseText.take(RESPONSE_PREVIEW_CHAR_LIMIT)}..."
                }
            } catch (e: Exception) {
                "Error: Task delegation failed due to an exception: ${e.message}"
            }
        }

    private companion object {
        /** Maximum wall-clock time, in milliseconds, allowed for a single delegated cloud-LLM call. */
        const val LLM_CALL_TIMEOUT_MS: Long = 60_000L

        /** Maximum number of characters of the delegated response previewed in the success message. */
        const val RESPONSE_PREVIEW_CHAR_LIMIT: Int = 100
    }
}
