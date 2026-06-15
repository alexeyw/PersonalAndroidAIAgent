package app.knotwork.android.domain.models

/**
 * Whether one saved pipeline may be chosen as the target of a
 * [NodeType.PIPELINE] node in the pipeline currently being edited, and — when
 * it may not — why.
 *
 * Produced by
 * `app.knotwork.android.domain.services.PipelineCompositionValidator.classifyTargets`
 * and consumed by the editor's target picker. Keeping the classification in the
 * domain (rather than the catalog form) lets the same cross-pipeline
 * reachability logic that powers save-time composition validation drive the
 * picker's disabled rows.
 *
 * @property pipelineId stable id of the candidate pipeline.
 * @property name display name of the candidate pipeline.
 * @property selectable `true` when the candidate may be picked as the target.
 * @property reason why the candidate is not selectable; `null` when [selectable].
 */
data class PipelineTargetAvailability(
    val pipelineId: String,
    val name: String,
    val selectable: Boolean,
    val reason: Reason?,
) {
    /**
     * Why a candidate target cannot be selected. Each variant maps to a
     * distinct picker message because the user's remedy differs.
     */
    sealed interface Reason {
        /** The candidate is the pipeline being edited — a pipeline cannot run itself. */
        data object Self : Reason

        /**
         * Choosing the candidate would close a composition cycle: the candidate
         * transitively runs the pipeline being edited.
         *
         * @property culpritPipelineName display name of the pipeline in the
         * candidate's call graph that already runs the edited one — surfaced so
         * the picker can name the back-reference to break.
         */
        data class Cycle(val culpritPipelineName: String) : Reason

        /**
         * Choosing the candidate would nest deeper than the configured ceiling.
         *
         * @property limit the configured maximum nesting depth.
         */
        data class Depth(val limit: Int) : Reason
    }
}
