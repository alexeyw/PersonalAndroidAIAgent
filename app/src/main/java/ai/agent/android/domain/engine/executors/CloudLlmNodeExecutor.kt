package ai.agent.android.domain.engine.executors

import ai.agent.android.data.engine.KoogClientFactory
import ai.agent.android.data.engine.KoogModelMapper
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MetricsRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.RetrieveRelevantMemoryUseCase
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber
import javax.inject.Inject

class CloudLlmNodeExecutor @Inject constructor(
    private val toolRepository: ToolRepository,
    private val chatRepository: ChatRepository,
    private val getContextWindowUseCase: GetContextWindowUseCase,
    private val retrieveRelevantMemoryUseCase: RetrieveRelevantMemoryUseCase,
    private val settingsRepository: SettingsRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val metricsRepository: MetricsRepository,
    private val koogClientFactory: KoogClientFactory
) : NodeExecutor {

    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String
    ): Flow<Any> = flow {
        val tools = toolRepository.getAvailableTools()
        val toolsDescription = tools.joinToString("\n") { "- ${it.name}: ${it.description} | Params: ${it.parameters}" }
        val systemPromptPrefix = settingsRepository.systemPromptPrefix.first()
        val toolUsageInstructionTemplate = settingsRepository.toolUsageInstruction.first()
        val toolUsageInstruction = if (toolUsageInstructionTemplate.contains("%s")) {
            toolUsageInstructionTemplate.replace("%s", toolsDescription)
        } else {
            "$toolUsageInstructionTemplate\n\n$toolsDescription"
        }

        val baseSystemPrompt = "$systemPromptPrefix\n$toolUsageInstruction\n"
        val contextWindow = getContextWindowUseCase(sessionId)
        
        val relevantMemories = retrieveRelevantMemoryUseCase(originalPrompt)
        val memoryContext = if (relevantMemories.isNotEmpty()) {
            "RELEVANT LONG-TERM MEMORIES:\n" + relevantMemories.joinToString("\n") { "- ${it.text}" } + "\n\n"
        } else {
            ""
        }

        val fullPrompt = "$baseSystemPrompt\n\n$memoryContext$contextWindow\nAGENT: "
        
        val startTime = System.currentTimeMillis()

        var selectedProvider = node.cloudProvider ?: "auto"
        if (selectedProvider == "auto") {
            val googleKey = apiKeyRepository.getGoogleKey().first()
            val anthropicKey = apiKeyRepository.getAnthropicKey().first()
            val openAIKey = apiKeyRepository.getOpenAIKey().first()
            val deepSeekKey = apiKeyRepository.getDeepSeekKey().first()

            selectedProvider = when {
                !googleKey.isNullOrBlank() -> "google"
                !anthropicKey.isNullOrBlank() -> "anthropic"
                !openAIKey.isNullOrBlank() -> "openai"
                !deepSeekKey.isNullOrBlank() -> "deepseek"
                else -> "none"
            }
        }
        
        val responseStream = when (selectedProvider) {
            "openai" -> {
                val client = koogClientFactory.createOpenAIExecutor()
                val modelName = apiKeyRepository.getOpenAIModel().first() ?: OpenAIModels.Chat.GPT5_4.id
                client?.executeStreaming(prompt("default") { user(fullPrompt) }, KoogModelMapper.getOpenAIModel(modelName))
                    ?.mapNotNull { (it as? StreamFrame.TextDelta)?.text } ?: flowOf("Error: OpenAI not configured")
            }
            "anthropic" -> {
                val client = koogClientFactory.createAnthropicExecutor()
                val modelName = apiKeyRepository.getAnthropicModel().first() ?: AnthropicModels.Sonnet_4_5.id
                client?.executeStreaming(prompt("default") { user(fullPrompt) }, KoogModelMapper.getAnthropicModel(modelName))
                    ?.mapNotNull { (it as? StreamFrame.TextDelta)?.text } ?: flowOf("Error: Anthropic not configured")
            }
            "google" -> {
                val client = koogClientFactory.createGoogleExecutor()
                val modelName = apiKeyRepository.getGoogleModel().first() ?: GoogleModels.Gemini3_Flash_Preview.id
                client?.executeStreaming(prompt("default") { user(fullPrompt) }, KoogModelMapper.getGoogleModel(modelName))
                    ?.mapNotNull { (it as? StreamFrame.TextDelta)?.text } ?: flowOf("Error: Google not configured")
            }
            "deepseek" -> {
                val client = koogClientFactory.createDeepSeekExecutor()
                val modelName = apiKeyRepository.getDeepSeekModel().first() ?: DeepSeekModels.DeepSeekChat.id
                client?.executeStreaming(prompt("default") { user(fullPrompt) }, KoogModelMapper.getDeepSeekModel(modelName))
                    ?.mapNotNull { (it as? StreamFrame.TextDelta)?.text } ?: flowOf("Error: DeepSeek not configured")
            }
            else -> flowOf("Error: No cloud provider configured or selected")
        }
        
        val accumulatedResponse = StringBuilder()
        var emittedThinking = false
        var approximateTokenCount = 0

        try {
            responseStream.collect { token ->
                accumulatedResponse.append(token)
                approximateTokenCount += token.length / 4 + 1
                
                if (!emittedThinking) {
                    emit(AgentOrchestratorState.Thinking(accumulatedResponse.toString()))
                    emittedThinking = true
                } else {
                    emit(AgentOrchestratorState.Answering(accumulatedResponse.toString()))
                }
            }
        } catch (e: Exception) {
            Timber.tag("PipelineDebug").e(e, "[NODE_ERR] type=${node.type.name} id=${node.id} error in CloudLlmNodeExecutor generation")
            emit(AgentOrchestratorState.Error(e.message ?: "Unknown error during LLM generation"))
            emit(NodeExecutionResult(error = e.message))
            return@flow
        }
        
        val endTime = System.currentTimeMillis()
        metricsRepository.updateMetrics(endTime - startTime, approximateTokenCount)

        val fullResponseText = accumulatedResponse.toString().trim()
        
        kotlinx.coroutines.delay(1000)

        emit(NodeExecutionResult(outputText = fullResponseText))
    }
}
