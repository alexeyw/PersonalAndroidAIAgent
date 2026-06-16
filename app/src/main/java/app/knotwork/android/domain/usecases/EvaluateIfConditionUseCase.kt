package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.engine.structured.EngineStructuredInferenceClient
import app.knotwork.android.domain.engine.structured.GateResult
import app.knotwork.android.domain.engine.structured.RepairListener
import app.knotwork.android.domain.engine.structured.StructuredOutputGate
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for evaluating an [NodeType.IF_CONDITION] node in a pipeline.
 *
 * This use case determines whether the pipeline execution should proceed
 * along the "True" branch or the "False" branch based on the node's configuration.
 *
 * For the LLM-classified path the model's verdict is run through the
 * [StructuredOutputGate] as a constrained `{"True","False"}` token: a reply that
 * contains neither word is handed back to the model for a bounded number of
 * repair re-inferences before the gate gives up. **Per-node failure policy:**
 * an exhausted gate (or an inference error) does **not** fail the run — the
 * node keeps its `False` default branch — but the failure is reported via
 * [Outcome.gateFailed] so the executor can surface a console Error and the
 * repair attempts are counted; there are no more silent forks.
 *
 * @property llmInferenceEngine The engine used to evaluate free-form prompt conditions via LLM.
 * @property structuredOutputGate Validate-and-repair gate wrapping the LLM verdict.
 * @property settingsRepository Source of the configured repair ceiling.
 */
class EvaluateIfConditionUseCase @Inject constructor(
    private val llmInferenceEngine: LlmInferenceEngine,
    private val structuredOutputGate: StructuredOutputGate,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Outcome of an IF_CONDITION evaluation.
     *
     * @property value The branch decision: `true` → "True" edge, `false` → "False" edge.
     * @property gateFailed `true` only when the LLM path ran the structured-output
     *   gate and it exhausted its repairs (or the inference errored), so the
     *   executor can emit a console Error for the silent-fork-free fallback. Always
     *   `false` for the keyword / complexity / no-config paths (no gate involved).
     */
    data class Outcome(val value: Boolean, val gateFailed: Boolean = false)

    /**
     * Evaluates the condition against the provided input text.
     *
     * The evaluation is done in the following priority:
     * 1. **Keywords:** If [NodeModel.conditionKeywords] is provided, returns true if the input contains any of the keywords (case-insensitive).
     * 2. **Complexity:** If [NodeModel.conditionComplexity] is provided, returns true if the input length exceeds the complexity threshold.
     * 3. **Prompt:** If [NodeModel.conditionPrompt] is provided, uses the LLM (via the gate) to classify the input.
     *
     * If no configuration is provided, it defaults to false.
     *
     * @param node The [NodeModel] containing the condition configuration. Must be of type [NodeType.IF_CONDITION].
     * @param inputText The text (context or user message) to evaluate.
     * @param repairListener Sink the gate reports repair attempts to; defaults to
     *   [RepairListener.NONE]. The executor passes a buffering listener so it can
     *   surface each attempt as a console line.
     * @return The [Outcome] carrying the branch decision and whether the gate failed.
     */
    suspend operator fun invoke(
        node: NodeModel,
        inputText: String,
        repairListener: RepairListener = RepairListener.NONE,
    ): Outcome {
        require(node.type == NodeType.IF_CONDITION) { "Node must be an IF_CONDITION type" }

        if (inputText.isBlank()) return Outcome(value = false)

        // 1. Check keywords
        val keywordsStr = node.conditionKeywords
        if (!keywordsStr.isNullOrBlank()) {
            val keywords = keywordsStr.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            if (keywords.isNotEmpty()) {
                val lowerInput = inputText.lowercase()
                if (keywords.any { lowerInput.contains(it) }) {
                    return Outcome(value = true)
                }
            }
        }

        // 2. Check complexity (e.g., text length threshold)
        val complexityThreshold = node.conditionComplexity
        if (complexityThreshold != null && complexityThreshold > 0) {
            if (inputText.length >= complexityThreshold) {
                return Outcome(value = true)
            }
        }

        // 3. Evaluate using LLM (through the gate) if a prompt is provided
        val conditionPrompt = node.conditionPrompt
        if (!conditionPrompt.isNullOrBlank()) {
            return evaluateWithGate(node, conditionPrompt, inputText, repairListener)
        }

        return Outcome(value = false)
    }

    /**
     * Runs the LLM classification through the [StructuredOutputGate] as a
     * constrained `{"True","False"}` token and maps the result onto an [Outcome].
     *
     * @param node The IF_CONDITION node (its label keys the repair metric / console line).
     * @param conditionPrompt The user-authored condition to classify against.
     * @param inputText The upstream text being classified.
     * @param repairListener Sink for repair attempts during the gate run.
     * @return `True`/`False` on a valid verdict; the `False` default with
     *   [Outcome.gateFailed] set when the gate exhausts its repairs or errors.
     */
    private suspend fun evaluateWithGate(
        node: NodeModel,
        conditionPrompt: String,
        inputText: String,
        repairListener: RepairListener,
    ): Outcome {
        val prompt = DefaultPrompts.renderTemplate(
            DefaultPrompts.IfCondition.EVALUATION_TEMPLATE,
            mapOf(
                "CONDITION_PROMPT" to conditionPrompt,
                "INPUT_TEXT" to inputText,
            ),
        )
        val maxRepairs = settingsRepository.structuredOutputMaxRepairs.first()

        val result = try {
            structuredOutputGate.runToken(
                inference = EngineStructuredInferenceClient(llmInferenceEngine),
                prompt = prompt,
                allowed = setOf(TOKEN_TRUE, TOKEN_FALSE),
                nodeName = node.label,
                maxRepairs = maxRepairs,
                listener = repairListener,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An inference-layer failure is a gate failure for policy purposes:
            // keep the default branch but mark it so the executor surfaces it.
            return Outcome(value = false, gateFailed = true)
        }

        return when (result) {
            is GateResult.Success -> Outcome(value = result.value == TOKEN_TRUE)
            is GateResult.Failed -> Outcome(value = false, gateFailed = true)
        }
    }

    private companion object {
        /** Canonical accepted verdict tokens for the constrained classification. */
        const val TOKEN_TRUE = "True"
        const val TOKEN_FALSE = "False"
    }
}
