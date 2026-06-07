package app.knotwork.android.domain.models

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

    /**
     * Error indicating an INPUT node has no outgoing connections.
     */
    data object DisconnectedInput : PipelineValidationError()

    /**
     * Error indicating an OUTPUT node has no incoming connections.
     */
    data object DisconnectedOutput : PipelineValidationError()

    /**
     * Error indicating there are nodes not reachable from an INPUT node.
     */
    data object UnreachableNode : PipelineValidationError()

    /**
     * Error indicating there are nodes that do not lead to an OUTPUT node.
     */
    data object DeadEndNode : PipelineValidationError()

    /**
     * Error indicating that a node's [NodeContextConfig] has every flag
     * disabled, which would cause the node executor to receive an empty
     * input string at runtime and produce a meaningless answer or fail.
     *
     * This validation is only applied to nodes whose [NodeType] consumes
     * upstream pipeline context. The starting [NodeType.INPUT] node is
     * exempt because it has no upstream data to consume.
     *
     * @property nodeId The unique identifier of the node whose context
     * configuration is empty. Used by the presentation layer to look up
     * the node label and surface a human-readable error to the user.
     */
    data class NodeEmptyContext(val nodeId: String) : PipelineValidationError()
}
