package app.knotwork.android.domain.models

/**
 * A validated request from a third-party automation app (Tasker, MacroDroid,
 * `adb`) to run one of the user's pipelines.
 *
 * Produced only by
 * [ParseExternalAutomationRequestUseCase][app.knotwork.android.domain.usecases.automation.ParseExternalAutomationRequestUseCase]:
 * every instance has already passed syntax validation, so consumers never
 * re-check for a missing target or an undecodable prompt. Being admitted is a
 * separate question, answered by
 * [AuthorizeExternalAutomationRequestUseCase][app.knotwork.android.domain.usecases.automation.AuthorizeExternalAutomationRequestUseCase]
 * — a well-formed request from an app the user never allowed is still refused.
 *
 * @property target Which pipeline the caller asked to run. Never absent: the
 *   binding is an allowlist, not a default, so a request that names no target is
 *   refused rather than silently pointed at the bound pipeline.
 * @property prompt The user message the pipeline runs on, already decoded from
 *   whichever wire form the caller used.
 * @property requestId Caller-minted correlation id, echoed back on the return
 *   callback and recorded in the journal. Required even for fire-and-forget
 *   calls: it is what makes a repeated broadcast recognisable as a repeat.
 * @property returnAction Broadcast action the terminal callback is sent with.
 *   Defaults to the contract's own result action when the caller omits it, so a
 *   half-specified callback is not a failure mode.
 * @property returnPackage Package the terminal callback is delivered to, as an
 *   explicit intent. `null` is a valid fire-and-forget request, not an error —
 *   the caller simply does not want to be told how the run ended.
 */
data class ExternalAutomationRequest(
    val target: ExternalAutomationTarget,
    val prompt: String,
    val requestId: String,
    val returnAction: String,
    val returnPackage: String? = null,
)

/**
 * How an [ExternalAutomationRequest] names the pipeline it wants to run.
 *
 * Two forms because two kinds of caller exist: a profile generated from the
 * app's own share sheet can carry the stable id, while a hand-written `adb`
 * one-liner is far easier to author against the name the user sees. Naming the
 * target both ways at once is refused rather than reconciled — a caller that
 * disagrees with itself is a caller whose intent is unknown.
 */
sealed interface ExternalAutomationTarget {

    /**
     * The pipeline is named by its stable identifier.
     *
     * @property pipelineId Id of the requested pipeline.
     */
    data class ById(val pipelineId: String) : ExternalAutomationTarget

    /**
     * The pipeline is named by its user-visible name.
     *
     * @property pipelineName Name of the requested pipeline, matched exactly.
     */
    data class ByName(val pipelineName: String) : ExternalAutomationTarget
}

/**
 * The pipeline the user allowed third-party automation to run, as the authorizer
 * sees it.
 *
 * Carries both coordinates of the one bound pipeline so a request naming it
 * either way ([ExternalAutomationTarget.ById] / [ExternalAutomationTarget.ByName])
 * can be checked without a repository lookup, keeping the security decision a
 * pure function.
 *
 * The type exists so the allowlist can widen later without touching the
 * authorizer's callers: today it holds exactly one pipeline, and a future
 * multi-pipeline allowlist changes this type and the membership test inside it,
 * nothing else.
 *
 * @property pipelineId Id of the bound pipeline.
 * @property pipelineName User-visible name of the bound pipeline.
 */
data class ExternalAutomationBinding(val pipelineId: String, val pipelineName: String) {

    /**
     * Whether [target] names the pipeline this binding allows.
     *
     * @param target The target carried by the incoming request.
     * @return `true` when the request may proceed against this binding.
     */
    fun allows(target: ExternalAutomationTarget): Boolean = when (target) {
        is ExternalAutomationTarget.ById -> target.pipelineId == pipelineId
        is ExternalAutomationTarget.ByName -> target.pipelineName == pipelineName
    }
}
