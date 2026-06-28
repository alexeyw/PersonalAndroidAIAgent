package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.engine.structured.CollectingRepairListener
import app.knotwork.android.domain.models.ConsoleEventType
import app.knotwork.android.domain.models.ExecutionScope
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.usecases.EvaluateIfConditionUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Executor for [NodeType.IF_CONDITION][app.knotwork.android.domain.models.NodeType.IF_CONDITION]
 * branching nodes.
 *
 * Delegates the boolean evaluation to [EvaluateIfConditionUseCase] (keyword match,
 * complexity heuristics, or LLM-based judgement depending on the node's configuration)
 * and forwards the upstream `inputText` unchanged so that downstream branches still see
 * the original payload. The branch decision is published via
 * [NodeExecutionResult.conditionResult][app.knotwork.android.domain.models.NodeExecutionResult.conditionResult],
 * which `GraphExecutionEngine` reads to pick the next connection.
 *
 * **Structured-output policy.** The LLM path runs through the validate-and-repair
 * gate inside the use case. Each repair attempt is surfaced as a
 * [ConsoleEventType.StructuredOutputRepair] console line (which the engine also
 * counts in the repair metric). When the gate ultimately fails, the node keeps
 * its `False` default branch — a graceful fork rather than a halted run — but
 * emits a [ConsoleEventType.Error] line so the fallback is observable instead of
 * silent. Repairs are an internal node mechanic: they consume repair budget, not
 * pipeline steps.
 */
class IfConditionNodeExecutor @Inject constructor(private val evaluateIfConditionUseCase: EvaluateIfConditionUseCase) :
    NodeExecutor {
    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
        runId: String?,
        scope: ExecutionScope,
    ): Flow<NodeOutput> = kotlinx.coroutines.flow.flow {
        val repairListener = CollectingRepairListener()
        // The run carries an image when the shared delivery holder is present, regardless of
        // whether a downstream vision node has consumed it yet — so an IF node that forks on
        // image presence sees it even when it runs before the vision sink.
        //
        // Limitation: the delivery holder is per-execution and not persisted, so a *resumed*
        // run (one that paused at a HITL gate) sees `imageDelivery == null`. An image-branching
        // IF node positioned AFTER such a gate therefore evaluates `hasImage == false` on resume.
        // In practice image-branching nodes route the picture early — before any HITL gate — so
        // they replay from the trace rather than re-executing live; the gap only bites the
        // unusual "branch on image after a human-in-the-loop pause" topology.
        val hasImage = scope.imageDelivery != null
        val outcome = evaluateIfConditionUseCase(node, inputText, hasImage, repairListener)

        // Surface each buffered repair attempt; the engine renders it and bumps
        // the per-node repair counter.
        repairListener.attempts.forEach { attempt ->
            emit(NodeOutput.Console(ConsoleEventType.StructuredOutputRepair, attempt.consoleMessage()))
        }
        if (outcome.gateFailed) {
            emit(
                NodeOutput.Console(
                    ConsoleEventType.Error,
                    "IF_CONDITION '${node.label}' could not classify input; defaulting to the False branch",
                ),
            )
        }

        emit(NodeOutput.Result(NodeExecutionResult(conditionResult = outcome.value, outputText = inputText)))
    }
}
