package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.CloudProvider
import ai.agent.android.domain.models.RoutingDecision
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.NetworkStateRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case that decides which LLM engine should process the current user prompt.
 * It uses simple heuristics based on keywords and checks network state / API keys.
 */
@Singleton
class TaskRouterUseCase @Inject constructor(
    private val networkStateRepository: NetworkStateRepository,
    private val apiKeyRepository: ApiKeyRepository,
) {

    // Keywords that suggest the prompt involves private data or system functions
    private val privateDataKeywords =
        listOf("contact", "call", "sms", "email", "calendar", "alarm", "setting", "private", "system")

    // Keywords that suggest heavy reasoning or coding
    private val complexTaskKeywords =
        listOf("code", "programming", "architect", "complex", "analyze", "explain", "refactor")

    /**
     * Determines the best routing decision for the given prompt.
     *
     * @param prompt The user's input prompt.
     * @return The [RoutingDecision] indicating which engine to use.
     */
    // Reason: each `return` is one ordered routing decision (private data →
    // local; complex + cloud key → cloud provider; complex offline → local;
    // default). The decision tree is the logic — collapsing it into a single
    // exit would force nested `when`s without clarifying the contract.
    @Suppress("ReturnCount")
    suspend operator fun invoke(prompt: String): RoutingDecision {
        val lowerPrompt = prompt.lowercase()

        // 1. Check for private/local operations -> strictly LocalLiteRT
        if (privateDataKeywords.any { lowerPrompt.contains(it) }) {
            return RoutingDecision.LocalLiteRT
        }

        val networkState = networkStateRepository.networkState.value

        // 2. Complex tasks handling
        if (complexTaskKeywords.any { lowerPrompt.contains(it) }) {
            // Prefer Cloud if keys are available and we have internet
            if (networkState.isConnected) {
                val openAIKey = apiKeyRepository.getOpenAIKey().first()
                if (!openAIKey.isNullOrBlank()) {
                    return RoutingDecision.CloudLLM(CloudProvider.OPENAI)
                }

                val googleKey = apiKeyRepository.getGoogleKey().first()
                if (!googleKey.isNullOrBlank()) {
                    return RoutingDecision.CloudLLM(CloudProvider.GOOGLE)
                }

                val anthropicKey = apiKeyRepository.getAnthropicKey().first()
                if (!anthropicKey.isNullOrBlank()) {
                    return RoutingDecision.CloudLLM(CloudProvider.ANTHROPIC)
                }

                val deepSeekKey = apiKeyRepository.getDeepSeekKey().first()
                if (!deepSeekKey.isNullOrBlank()) {
                    return RoutingDecision.CloudLLM(CloudProvider.DEEPSEEK)
                }
            }

            // If Wi-Fi is connected, try Ollama
            if (networkState.isWifiConnected) {
                val ollamaUrl = apiKeyRepository.getOllamaBaseUrl().first()
                if (!ollamaUrl.isNullOrBlank()) {
                    return RoutingDecision.LocalOllama
                }
            }
        }

        // Default fallback to LocalLiteRT
        return RoutingDecision.LocalLiteRT
    }
}
