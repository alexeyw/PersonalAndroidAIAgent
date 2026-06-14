package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.engine.GraphExecutionEngine
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Provider

/**
 * Executor for [NodeType.PIPELINE][app.knotwork.android.domain.models.NodeType.PIPELINE]
 * nodes — the composition primitive that runs another pipeline graph as a
 * sub-step (a "function call" between pipelines).
 *
 * **Execution model.** The node names its callee through
 * [NodeModel.targetPipelineId]. This executor loads that graph via
 * [PipelineRepository] and runs it by invoking the very same
 * [GraphExecutionEngine] that is driving the parent run, passing the node's
 * input as the sub-pipeline's user prompt. The sub-pipeline's terminal
 * [AgentOrchestratorState.Completed] response becomes this node's
 * [NodeExecutionResult.outputText]; an [AgentOrchestratorState.Error] (or a
 * thrown exception) becomes [NodeExecutionResult.error], which the parent
 * engine surfaces as a run-level error.
 *
 * **Why recursive rather than an explicit engine-side stack.** The engine is a
 * `@Singleton` and stateless per run (every run-scoped value lives inside the
 * `invoke` flow), so re-entering it for the sub-pipeline is safe and keeps the
 * sub-run a first-class run with the same node dispatch, routing and HITL
 * semantics as a top-level run — no parallel "nested execution" code path to
 * keep in sync. The cycle that this introduces in the object graph
 * (engine → [NodeExecutorFactory] → this executor → engine) is broken with a
 * [Provider] so Hilt can construct the singletons. The recursion is bounded by
 * the depth ceiling below.
 *
 * **Depth safety.** [depth] carries the current nesting level; the recursive
 * call uses `depth + 1`. The authoritative protection against runaway nesting
 * and cycles is *static* — `PipelineCompositionValidator` rejects such
 * compositions before any run starts, because the call graph is known ahead of
 * time. The runtime ceiling here
 * ([SettingsRepository.pipelineMaxNestingDepth]) is only the safety net for a
 * graph edited in the window between validation and execution.
 *
 * **Scope.** Nested-run persistence (the parent/child run tree, nested trace
 * and resume across the sub-pipeline boundary) is intentionally out of scope
 * for the domain core: the sub-run is executed with a `null` run id and is not
 * separately persisted. Observability and resume across the boundary are the
 * subject of a later task.
 */
class PipelineNodeExecutor @Inject constructor(
    private val pipelineRepository: PipelineRepository,
    private val settingsRepository: SettingsRepository,
    private val engineProvider: Provider<GraphExecutionEngine>,
) : NodeExecutor {

    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
        runId: String?,
        depth: Int,
    ): Flow<NodeOutput> = flow {
        val targetId = node.targetPipelineId
        if (targetId.isNullOrBlank()) {
            emit(failure("PIPELINE node '${node.label}' has no target pipeline selected"))
            return@flow
        }

        val maxDepth = settingsRepository.pipelineMaxNestingDepth.first()
        if (depth + 1 > maxDepth) {
            emit(failure("Pipeline nesting depth exceeded the limit of $maxDepth"))
            return@flow
        }

        val targetGraph = try {
            pipelineRepository.getPipelineById(targetId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(failure("Failed to load target pipeline '$targetId': ${e.message ?: "unknown error"}"))
            return@flow
        }
        if (targetGraph == null) {
            emit(failure("Target pipeline '$targetId' not found"))
            return@flow
        }

        var finalResponse: String? = null
        var errorMessage: String? = null
        try {
            engineProvider.get().invoke(
                sessionId = sessionId,
                userPrompt = inputText,
                graph = targetGraph,
                runId = null,
                resume = null,
                depth = depth + 1,
            ).collect { state ->
                when (state) {
                    is AgentOrchestratorState.Completed -> finalResponse = state.finalResponse
                    is AgentOrchestratorState.Error -> errorMessage = state.message
                    else -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message ?: "Sub-pipeline execution failed"
        }

        val resolvedError = errorMessage
        val resolvedResponse = finalResponse
        when {
            resolvedError != null -> emit(failure(resolvedError))
            resolvedResponse != null -> emit(NodeOutput.Result(NodeExecutionResult(outputText = resolvedResponse)))
            else -> emit(failure("Sub-pipeline '${targetGraph.name}' produced no output"))
        }
    }

    /**
     * Wraps an error [message] as a terminal [NodeOutput.Result] so the parent
     * engine routes it through its standard node-error channel.
     */
    private fun failure(message: String): NodeOutput.Result = NodeOutput.Result(NodeExecutionResult(error = message))
}
