package ai.agent.android.data.tools.local

import ai.agent.android.data.engine.KoogClientFactory
import ai.agent.android.domain.engine.TextEmbeddingEngine
import ai.agent.android.domain.repositories.MemoryRepository
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.Dispatchers
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
 */
class DelegateTaskTool @Inject constructor(
    private val koogClientFactory: KoogClientFactory,
    private val memoryRepository: MemoryRepository,
    private val textEmbeddingEngine: TextEmbeddingEngine
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
     * @param targetModel The identifier for the external model to use. Supported values: "anthropic", "openai", "google", "deepseek", "ollama". Defaults to "anthropic".
     * @return A summary string detailing the outcome of the delegation, including whether it succeeded, timed out, or encountered an error. This summary is returned back to the calling agent.
     */
    suspend fun executeDelegation(
        taskDescription: String,
        targetModel: String = "anthropic"
    ): String = withContext(Dispatchers.IO) {
        val client = when (targetModel.lowercase()) {
            "anthropic" -> koogClientFactory.createAnthropicExecutor()
            "openai" -> koogClientFactory.createOpenAIExecutor()
            "google", "gemini" -> koogClientFactory.createGoogleExecutor()
            "deepseek" -> koogClientFactory.createDeepSeekExecutor()
            "ollama" -> koogClientFactory.createOllamaExecutor()
            else -> return@withContext "Error: Unsupported target model '$targetModel'. Supported models: anthropic, openai, google, deepseek, ollama."
        }

        if (client == null) {
            return@withContext "Error: Client for '$targetModel' could not be initialized. Please check if the API key or configuration is provided."
        }

        return@withContext try {
            val defaultModelId = when (targetModel.lowercase()) {
                "anthropic" -> "claude-3-5-sonnet-20241022"
                "openai" -> "gpt-4o"
                "google", "gemini" -> "gemini-2.0-flash" // Widely available and fast model
                "deepseek" -> "deepseek-chat"
                "ollama" -> "llama3"
                else -> "default"
            }
            
            // Bypass client.models() to avoid Koog's internal metadata flags which might 
            // falsely mark a model as not supporting chat completions.
            val model = LLModel(client.llmProvider(), defaultModelId)
            
            // Apply a 60-second timeout for the external API call
            val result = withTimeoutOrNull(60_000L) {
                client.execute(prompt("default") { user(taskDescription) }, model)
            }

            if (result == null) {
                "Error: Task delegation to '$targetModel' timed out after 60 seconds."
            } else {
                // Task succeeded. Generate embedding for the result.
                val responseText = result.firstOrNull()?.content ?: "Empty response received."
                val embedding = textEmbeddingEngine.generateEmbedding(responseText)
                
                // Save to long-term memory so the local agent can recall it later
                memoryRepository.saveMemory(responseText, embedding)

                "Success: Task completed by '$targetModel' and saved to memory. Summary of response: ${responseText.take(100)}..."
            }
        } catch (e: Exception) {
            "Error: Task delegation failed due to an exception: ${e.message}"
        }
    }
}