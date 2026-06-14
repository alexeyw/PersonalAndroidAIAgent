package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.ClarificationOutcome
import app.knotwork.android.domain.models.ClarificationRequest
import app.knotwork.android.domain.models.ExecutionScope
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PendingInteraction
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.ClarificationRepository
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import app.knotwork.android.domain.services.ClarificationNotifier
import app.knotwork.android.domain.usecases.LoadModelUseCase
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
 *
 * The live wait is only the first phase of a two-phase protocol. When it
 * times out on a persisted run, the executor parks the run instead of
 * fabricating an answer: the generated question becomes a durable
 * `PendingInteraction`, an "Agent needs your input" notification deep-links
 * back into the chat, and the flow ends with
 * [AgentOrchestratorState.SuspendedInBackground] while the run record keeps
 * its `WAITING_CLARIFICATION` status. The user's later answer is recorded
 * onto the record and the run is resumed from its checkpoint; this executor
 * then consumes the record one-shot — returning the recorded answer directly,
 * without re-running question inference. Runs without a persistent record
 * (editor test runs) keep the legacy default-answer timeout fallback.
 */
class ClarificationNodeExecutor @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val loadModelUseCase: LoadModelUseCase,
    private val clarificationRepository: ClarificationRepository,
    private val pendingInteractionRepository: PendingInteractionRepository,
    private val clarificationNotifier: ClarificationNotifier,
) : NodeExecutor {

    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
        runId: String?,
        scope: ExecutionScope,
    ): Flow<NodeOutput> = flow {
        // A resumed run carries the user's one-shot answer for the question
        // this node parked on — return it directly, without re-running
        // question inference. The record never survives its first
        // consumption attempt; an unanswered record (a resume that did not
        // come through the answer path) falls through to a fresh question.
        val parkedAnswer = consumeParkedAnswer(runId)
        if (parkedAnswer != null) {
            emit(NodeOutput.Result(NodeExecutionResult(outputText = parkedAnswer)))
            return@flow
        }

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
            sessionId = sessionId,
            question = question,
            options = options,
            timeoutMs = node.clarificationTimeoutMs ?: DEFAULT_TIMEOUT_MS,
        )

        emit(NodeOutput.State(AgentOrchestratorState.AwaitingClarification(request)))
        when (val outcome = clarificationRepository.requestAnswer(request)) {
            is ClarificationOutcome.Answered ->
                emit(NodeOutput.Result(NodeExecutionResult(outputText = outcome.answer)))
            is ClarificationOutcome.TimedOut -> {
                if (runId != null && parkRun(runId, sessionId, question, options)) {
                    // Two-phase wait, second phase: the run parks on its
                    // durable pending record. No NodeOutput.Result on
                    // purpose — the engine stops the walk and the run record
                    // stays WAITING_CLARIFICATION.
                    emit(
                        NodeOutput.State(
                            AgentOrchestratorState.SuspendedInBackground(PendingInteractionKind.CLARIFICATION),
                        ),
                    )
                } else {
                    // Non-persisted runs (editor test runs) and storage
                    // failures keep the legacy default-answer fallback.
                    val defaultAnswer = request.options?.firstOrNull().orEmpty()
                    Timber.tag(TAG).w("Clarification timed out; using default answer: %s", defaultAnswer)
                    emit(NodeOutput.Result(NodeExecutionResult(outputText = defaultAnswer)))
                }
            }
        }
    }

    /**
     * Consumes the parked clarification record of a resumed run, one-shot.
     *
     * @param runId Id of the executing run, or `null` for non-persisted runs.
     * @return The recorded answer when present, or `null` when a fresh
     *   question must be asked (no record, or an unanswered record).
     */
    private suspend fun consumeParkedAnswer(runId: String?): String? {
        if (runId == null) return null
        val parked = pendingInteractionRepository.getForRun(runId) ?: return null
        if (parked.kind != PendingInteractionKind.CLARIFICATION) return null
        pendingInteractionRepository.delete(runId)
        return parked.answer
    }

    /**
     * Parks the run in its persistent waiting phase: persists the generated
     * question as a [PendingInteraction] and, when durable, posts the
     * "Agent needs your input" notification deep-linking back into the chat.
     *
     * @param runId Id of the persisted run being parked.
     * @param sessionId Id of the owning chat session.
     * @param question The generated clarifying question.
     * @param options Optional answer choices of the question.
     * @return `true` when the park is durable; `false` when the caller must
     *   fall back to the default answer.
     */
    private suspend fun parkRun(runId: String, sessionId: String, question: String, options: List<String>?): Boolean {
        val saved = pendingInteractionRepository.save(
            PendingInteraction(
                runId = runId,
                sessionId = sessionId,
                kind = PendingInteractionKind.CLARIFICATION,
                question = question,
                options = options,
                requestedAt = System.currentTimeMillis(),
            ),
        )
        if (saved) {
            clarificationNotifier.sendPersistentClarificationRequest(runId, sessionId, question)
        }
        return saved
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
