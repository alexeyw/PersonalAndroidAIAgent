package ai.agent.android.domain.models

/**
 * Represents a validation error in a pipeline graph.
 */
sealed class PipelineValidationError {
    /**
     * Error indicating the pipeline is missing an INPUT node.
     */
    data object MissingInput : PipelineValidationError()

    /**
     * Error indicating the pipeline is missing an OUTPUT node.
     */
    data object MissingOutput : PipelineValidationError()

    /**
     * Error indicating the pipeline has multiple INPUT nodes.
     */
    data object MultipleInputs : PipelineValidationError()

    /**
     * Error indicating the pipeline has multiple OUTPUT nodes.
     */
    data object MultipleOutputs : PipelineValidationError()

    /**
     * Error indicating the pipeline graph contains cycles.
     */
    data object HasCycles : PipelineValidationError()
}
