package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.constants.DefaultPrompts
import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ClarificationRequest
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeOutput
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.ClarificationRepository
import ai.agent.android.domain.usecases.LoadModelUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Executor for [NodeType.CLARIFICATION] nodes.
 *
 * The node generates a context-aware clarifying question (and an optional list of
 * answer options) on the fly using the local LLM engine, then suspends the pipeline
 * until the user replies via [ClarificationRepository] (or the timeout elapses and
 * the repository returns the default answer).
 *
 * Flow:
 * 1. Load the local model bound to the node (`node.modelPath`). On failure emit
 *    [AgentOrchestratorState.Error] and a [NodeExecutionResult] with the error.
 * 2. Build the LLM prompt from `node.systemPrompt` (instructions on what to ask)
 *    and `inputText` (upstream context), and stream tokens, surfacing progress as
 *    [AgentOrchestratorState.Thinking].
 * 3. Parse the LLM response as a JSON object `{ "question": ..., "options": [...] }`.
 *    A `null` or empty `options` array maps to a free-form clarification request.
 *    If parsing fails the raw text becomes the question with no options — we never
 *    block the pipeline on a malformed model output.
 * 4. Emit [AgentOrchestratorState.AwaitingClarification] and call
 *    [ClarificationRepository.requestAnswer], which suspends until the user submits
 *    a reply or the configured timeout elapses.
 * 5. Emit [NodeExecutionResult] with the user's answer as `outputText` for the next
 *    node downstream.
 */
class ClarificationNodeExecutor @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val loadModelUseCase: LoadModelUseCase,
    private val clarificationRepository: ClarificationRepository,
) : NodeExecutor {

    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
    ): Flow<NodeOutput> = flow {
        val instruction = node.systemPrompt
            ?: DefaultPrompts.CLARIFICATION_PROMPT
        val fullPrompt = "$instruction\n\nCONTEXT FROM PREVIOUS NODE:\n$inputText\n\nRESPONSE (JSON only):\n"

        val loadResult = loadModelUseCase(node.modelPath)
        if (loadResult is Result.Error) {
            val errorMsg = "Error loading local model for clarification node"
            emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
            emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
            return@flow
        }

        val accumulatedResponse = StringBuilder()
        try {
            llmEngine.generateResponseStream(fullPrompt).collect { token ->
                accumulatedResponse.append(token)
                emit(NodeOutput.State(AgentOrchestratorState.Thinking(accumulatedResponse.toString())))
            }
        } catch (e: CancellationException) {
            // Re-throw to preserve structured-concurrency cancellation. Catching `Exception`
            // below would otherwise swallow it and the caller would never see the cancel.
            throw e
        } catch (e: Exception) {
            Timber.tag(
                TAG,
            ).e(e, "[NODE_ERR] type=${node.type.name} id=${node.id} error during clarification generation")
            emit(
                NodeOutput.State(
                    AgentOrchestratorState.Error(
                        e.message ?: "Unknown error during clarification generation",
                    ),
                ),
            )
            emit(NodeOutput.Result(NodeExecutionResult(error = e.message)))
            return@flow
        }

        val rawText = accumulatedResponse.toString().trim()
        val (question, options) = parseClarificationJson(rawText)

        val request = ClarificationRequest(
            id = UUID.randomUUID().toString(),
            question = question,
            options = options,
            timeoutMs = node.clarificationTimeoutMs ?: DEFAULT_TIMEOUT_MS,
        )

        emit(NodeOutput.State(AgentOrchestratorState.AwaitingClarification(request)))
        val answer = clarificationRepository.requestAnswer(request)

        emit(NodeOutput.Result(NodeExecutionResult(outputText = answer)))
    }

    /**
     * Parses [text] as `{ "question": "...", "options": [...] }`.
     *
     * Lenient by design — the local LLM may wrap the JSON in a `````json ... ````` fence
     * or prepend/append filler text. We first try a fenced block, then the raw string,
     * and finally fall back to "use everything as the question" if no JSON object can
     * be extracted. An empty or missing `options` array becomes `null`, signalling
     * free-form input to the UI.
     */
    private fun parseClarificationJson(text: String): Pair<String, List<String>?> {
        val candidates = buildList {
            val fenced = JSON_BLOCK_REGEX.find(text)?.groups?.get(1)?.value?.trim()
            if (!fenced.isNullOrEmpty()) add(fenced)
            val firstBrace = text.indexOf('{')
            val lastBrace = text.lastIndexOf('}')
            if (firstBrace in 0 until lastBrace) add(text.substring(firstBrace, lastBrace + 1))
        }
        for (candidate in candidates) {
            try {
                val json = JSONObject(candidate)
                val question = json.optString("question").takeIf { it.isNotBlank() } ?: continue
                val optionsArray = json.optJSONArray("options")
                val options = if (optionsArray != null && optionsArray.length() > 0) {
                    (0 until optionsArray.length()).map { optionsArray.getString(it) }
                } else {
                    null
                }
                return question to options
            } catch (e: JSONException) {
                Timber.tag(TAG).w(e, "Failed to parse clarification JSON candidate, trying next")
            }
        }
        Timber.tag(TAG).w("Could not extract JSON from LLM clarification output; using raw text as question")
        return text to null
    }

    private companion object {
        const val TAG = "ClarificationNode"
        const val DEFAULT_TIMEOUT_MS = 60_000L
        private val JSON_BLOCK_REGEX = """```(?:json)?\s*(\{.*?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
    }
}
