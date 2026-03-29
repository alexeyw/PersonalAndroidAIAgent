package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.toList
import javax.inject.Inject

/**
 * Use case for evaluating an [NodeType.IF_CONDITION] node in a pipeline.
 *
 * This use case determines whether the pipeline execution should proceed
 * along the "True" branch or the "False" branch based on the node's configuration.
 *
 * @property llmInferenceEngine The engine used to evaluate free-form prompt conditions via LLM.
 */
class EvaluateIfConditionUseCase @Inject constructor(
    private val llmInferenceEngine: LlmInferenceEngine
) {

    /**
     * Evaluates the condition against the provided input text.
     *
     * The evaluation is done in the following priority:
     * 1. **Keywords:** If [NodeModel.conditionKeywords] is provided, returns true if the input contains any of the keywords (case-insensitive).
     * 2. **Complexity:** If [NodeModel.conditionComplexity] is provided, returns true if the input length exceeds the complexity threshold.
     * 3. **Prompt:** If [NodeModel.conditionPrompt] is provided, uses the LLM to classify the input. Returns true if the LLM responds positively.
     * 
     * If no configuration is provided, it defaults to false.
     *
     * @param node The [NodeModel] containing the condition configuration. Must be of type [NodeType.IF_CONDITION].
     * @param inputText The text (context or user message) to evaluate.
     * @return `true` if the condition is met, `false` otherwise.
     */
    suspend operator fun invoke(node: NodeModel, inputText: String): Boolean {
        require(node.type == NodeType.IF_CONDITION) { "Node must be an IF_CONDITION type" }

        if (inputText.isBlank()) return false

        // 1. Check keywords
        val keywordsStr = node.conditionKeywords
        if (!keywordsStr.isNullOrBlank()) {
            val keywords = keywordsStr.split(",").map { it.trim().lowercase() }
            val lowerInput = inputText.lowercase()
            if (keywords.any { lowerInput.contains(it) }) {
                return true
            }
        }

        // 2. Check complexity (e.g., text length threshold)
        val complexityThreshold = node.conditionComplexity
        if (complexityThreshold != null && complexityThreshold > 0) {
            if (inputText.length >= complexityThreshold) {
                return true
            }
        }

        // 3. Evaluate using LLM if a prompt is provided
        val conditionPrompt = node.conditionPrompt
        if (!conditionPrompt.isNullOrBlank()) {
            val prompt = """
                Evaluate the following text against the condition: "$conditionPrompt".
                Text: "$inputText"
                Reply strictly with 'true' or 'false'.
            """.trimIndent()
            
            return try {
                val tokens = mutableListOf<String>()
                llmInferenceEngine.generateResponseStream(prompt).collect { tokens.add(it) }
                val responseText = tokens.joinToString("").trim().lowercase()
                responseText.contains("true")
            } catch (e: Exception) {
                false
            }
        }

        return false
    }
}
