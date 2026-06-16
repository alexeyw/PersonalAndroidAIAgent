package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.constants.PipelineExecutionDefaults
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.engine.structured.CollectingRepairListener
import app.knotwork.android.domain.engine.structured.EngineStructuredInferenceClient
import app.knotwork.android.domain.engine.structured.GateResult
import app.knotwork.android.domain.engine.structured.RepairListener
import app.knotwork.android.domain.engine.structured.StructuredOutputGate
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.ConsoleEventType
import app.knotwork.android.domain.models.ExecutionScope
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.usecases.LoadModelUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

/**
 * Executor shared by the three "system reasoning" node types — [NodeType.INTENT_ROUTER],
 * [NodeType.DECOMPOSITION], and [NodeType.EVALUATION].
 *
 * They all share the same shape: run the local model with the node's `systemPrompt`
 * (or the matching [DefaultPrompts.System.SYSTEM_FALLBACK]) over the upstream context
 * `inputText`, stream tokens as orchestrator state, and turn the reply into a typed
 * control signal. Where they differ is the **shape** of that signal, and each now runs
 * through the validate-and-repair [StructuredOutputGate] rather than ad-hoc parsing:
 *
 *  - [NodeType.EVALUATION] — a constrained `{"Pass","Retry","Fail"}` token exposed as the
 *    [NodeExecutionResult.routingKey].
 *  - [NodeType.INTENT_ROUTER] — a constrained token drawn from the node's own outgoing
 *    edge labels ([ExecutionScope.routingChoices]); the matched label becomes the routing
 *    key. With no labelled edges there is nothing to constrain, so the node runs a plain
 *    inference and the engine falls back to the first outgoing edge.
 *  - [NodeType.DECOMPOSITION] — a JSON array of sub-task strings, re-encoded as the node
 *    output for the downstream `QUEUE_PROCESSOR`.
 *
 * **Per-node failure policy.** Each repair attempt is surfaced as a
 * [ConsoleEventType.StructuredOutputRepair] console line (which the engine also counts in
 * the repair metric). When the gate ultimately fails:
 *  - EVALUATION / INTENT_ROUTER keep routing by their default branch (`routingKey = null`
 *    → first outgoing edge) but emit a [ConsoleEventType.Error] line — no more silent forks.
 *  - DECOMPOSITION fails the run with a clear error: a corrupted sub-task list is worse
 *    than stopping.
 *
 * Repairs are an internal mechanic of the node: they consume the node's repair budget,
 * never pipeline steps.
 */
class SystemNodeExecutor @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val loadModelUseCase: LoadModelUseCase,
    @Suppress("unused") private val chatRepository: ChatRepository,
    private val structuredOutputGate: StructuredOutputGate,
    private val settingsRepository: SettingsRepository,
) : NodeExecutor {

    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
        runId: String?,
        scope: ExecutionScope,
    ): Flow<NodeOutput> = flow {
        val nodeSystemPrompt = node.systemPrompt ?: DefaultPrompts.System.SYSTEM_FALLBACK
        val fullPrompt = "$nodeSystemPrompt\n\nUSER: $inputText\nAGENT: "

        val loadResult = loadModelUseCase(node.modelPath)
        if (loadResult is Result.Error) {
            val errorMsg = "Error loading local model for system node"
            emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
            emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
            return@flow
        }

        val maxRepairs = settingsRepository.structuredOutputMaxRepairs.first()
        // The last inference attempt's streamed text. Reset on every repair (see
        // [resettingListener]) so that — after the gate returns — it holds exactly
        // the text of the attempt that finally validated, usable as the node's
        // human-readable output without leaking earlier malformed attempts.
        val streamed = StringBuilder()
        val collected = CollectingRepairListener()
        val resettingListener = RepairListener { name, attempt, max ->
            streamed.setLength(0)
            collected.onRepairAttempt(name, attempt, max)
        }
        val client = EngineStructuredInferenceClient(llmEngine) { token ->
            streamed.append(token)
            emit(NodeOutput.State(AgentOrchestratorState.Thinking(streamed.toString())))
        }

        try {
            when (node.type) {
                NodeType.DECOMPOSITION ->
                    runDecomposition(node, fullPrompt, maxRepairs, client, collected, resettingListener)
                NodeType.EVALUATION ->
                    runRouting(
                        node,
                        fullPrompt,
                        EVALUATION_VERDICTS,
                        streamed,
                        maxRepairs,
                        client,
                        collected,
                        resettingListener,
                    )
                NodeType.INTENT_ROUTER ->
                    runIntentRouter(node, fullPrompt, scope, streamed, maxRepairs, client, collected, resettingListener)
                else -> error("SystemNodeExecutor cannot run node type ${node.type}")
            }
        } catch (e: CancellationException) {
            // Preserve structured-concurrency cancellation: a broad `catch (Exception)`
            // would silently swallow cancellation and leave the parent coroutine running.
            throw e
        } catch (e: Exception) {
            Timber.tag("PipelineDebug")
                .e(e, "[NODE_ERR] type=${node.type.name} id=${node.id} error in SystemNodeExecutor generation")
            emit(NodeOutput.State(AgentOrchestratorState.Error(e.message ?: "Unknown error")))
            emit(NodeOutput.Result(NodeExecutionResult(error = e.message)))
        }
    }

    /**
     * Runs a [NodeType.DECOMPOSITION] node: validates the reply as a JSON array of
     * sub-task strings and re-encodes the validated list as the node output (so the
     * downstream `QUEUE_PROCESSOR` always receives canonical JSON). On gate failure the
     * **run is failed** — a corrupted sub-task list is worse than stopping.
     */
    private suspend fun FlowCollector<NodeOutput>.runDecomposition(
        node: NodeModel,
        prompt: String,
        maxRepairs: Int,
        client: EngineStructuredInferenceClient,
        collected: CollectingRepairListener,
        listener: RepairListener,
    ) {
        val result = structuredOutputGate.runJson(
            inference = client,
            prompt = prompt,
            serializer = ListSerializer(String.serializer()),
            nodeName = node.label,
            maxRepairs = maxRepairs,
            listener = listener,
        )
        emitRepairs(collected)
        when (result) {
            is GateResult.Success -> {
                val canonical = json.encodeToString(ListSerializer(String.serializer()), result.value)
                emitResultAfterDelay(NodeExecutionResult(outputText = canonical))
            }
            is GateResult.Failed -> {
                val message = "DECOMPOSITION '${node.label}' did not produce a valid sub-task list " +
                    "after ${result.repairs} repair attempt(s): ${result.lastError}"
                emit(NodeOutput.Console(ConsoleEventType.Error, message))
                emit(NodeOutput.Result(NodeExecutionResult(error = message)))
            }
        }
    }

    /**
     * Runs a constrained-token routing node ([NodeType.EVALUATION]) whose accepted keys are
     * the fixed [allowed] set. On success the canonical token becomes the routing key; on
     * gate failure the node keeps its default branch (`routingKey = null` → first outgoing
     * edge) and emits a console Error.
     */
    private suspend fun FlowCollector<NodeOutput>.runRouting(
        node: NodeModel,
        prompt: String,
        allowed: Set<String>,
        streamed: StringBuilder,
        maxRepairs: Int,
        client: EngineStructuredInferenceClient,
        collected: CollectingRepairListener,
        listener: RepairListener,
    ) {
        val result = structuredOutputGate.runToken(
            inference = client,
            prompt = prompt,
            allowed = allowed,
            nodeName = node.label,
            maxRepairs = maxRepairs,
            listener = listener,
        )
        emitRepairs(collected)
        val outputText = streamed.toString().trim().ifEmpty { NO_RESPONSE }
        val routingKey = when (result) {
            is GateResult.Success -> result.value
            is GateResult.Failed -> {
                emit(
                    NodeOutput.Console(
                        ConsoleEventType.Error,
                        "${node.type.name} '${node.label}' produced no recognised verdict " +
                            "after ${result.repairs} repair attempt(s); routing to the default branch",
                    ),
                )
                null
            }
        }
        emitResultAfterDelay(NodeExecutionResult(outputText = outputText, routingKey = routingKey))
    }

    /**
     * Runs a [NodeType.INTENT_ROUTER] node. When the node has labelled outgoing edges the
     * reply is gated as a constrained token drawn from those labels; the matched label
     * becomes the routing key. With no labelled edges there is nothing to constrain, so a
     * plain inference runs and the routing key is the raw reply (the engine falls back to
     * the first outgoing edge). The router's output text is a control signal the engine
     * does not forward downstream, but it is kept for the trace.
     */
    private suspend fun FlowCollector<NodeOutput>.runIntentRouter(
        node: NodeModel,
        prompt: String,
        scope: ExecutionScope,
        streamed: StringBuilder,
        maxRepairs: Int,
        client: EngineStructuredInferenceClient,
        collected: CollectingRepairListener,
        listener: RepairListener,
    ) {
        val choices = scope.routingChoices.toSet()
        if (choices.isEmpty()) {
            // No labelled edges to choose between: run the model once (unconstrained)
            // and let the engine fall back to the first outgoing edge.
            val raw = client.infer(prompt, temperature = null)
            val outputText = raw.trim().ifEmpty { NO_RESPONSE }
            emitResultAfterDelay(NodeExecutionResult(outputText = outputText, routingKey = outputText))
            return
        }
        val result = structuredOutputGate.runToken(
            inference = client,
            prompt = prompt,
            allowed = choices,
            nodeName = node.label,
            maxRepairs = maxRepairs,
            listener = listener,
        )
        emitRepairs(collected)
        val outputText = streamed.toString().trim().ifEmpty { NO_RESPONSE }
        val routingKey = when (result) {
            is GateResult.Success -> result.value
            is GateResult.Failed -> {
                emit(
                    NodeOutput.Console(
                        ConsoleEventType.Error,
                        "INTENT_ROUTER '${node.label}' did not select a known route " +
                            "after ${result.repairs} repair attempt(s); routing to the default branch",
                    ),
                )
                null
            }
        }
        emitResultAfterDelay(NodeExecutionResult(outputText = outputText, routingKey = routingKey))
    }

    /** Emits one [NodeOutput.Console] per buffered repair attempt. */
    private suspend fun FlowCollector<NodeOutput>.emitRepairs(collected: CollectingRepairListener) {
        collected.attempts.forEach { attempt ->
            emit(NodeOutput.Console(ConsoleEventType.StructuredOutputRepair, attempt.consoleMessage()))
        }
    }

    /**
     * Applies the customary pre-result UX delay and emits the terminal result.
     *
     * @param result The node's terminal [NodeExecutionResult].
     */
    private suspend fun FlowCollector<NodeOutput>.emitResultAfterDelay(result: NodeExecutionResult) {
        kotlinx.coroutines.delay(PipelineExecutionDefaults.NODE_RESULT_EMIT_DELAY_MS)
        emit(NodeOutput.Result(result))
    }

    private companion object {
        /** Lenient JSON used only to re-encode a validated sub-task list as canonical output. */
        val json = Json { isLenient = true }

        /** Accepted verdict tokens for an [NodeType.EVALUATION] node. */
        val EVALUATION_VERDICTS = setOf("Pass", "Retry", "Fail")

        /** Placeholder output when the model streamed nothing. */
        const val NO_RESPONSE = "No response generated by model."
    }
}
