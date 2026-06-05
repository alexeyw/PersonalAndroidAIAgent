package app.knotwork.design.components.pipelineeditor

/**
 * Outbound port descriptor. Drives the bottom-edge port row in [NodeCard]
 * and pre-populates `EdgeLabel`s once a connection is drawn.
 *
 * The sealed hierarchy enumerates every canonical port label found in the
 * spec (`Default / True / False / Item / Done / Pass / Retry / Fail`) plus
 * [Custom] for `IntentRouter`'s arbitrary class names — that keeps the
 * common cases statically typed while still allowing intent-router class
 * labels to be free-form strings.
 *
 * @property label human-visible label rendered under the port dot.
 */
sealed class OutboundPort(val label: String) {
    /** Single unlabelled out-port used by every type with one output. */
    data object Default : OutboundPort(label = "")

    /** `True` branch on [NodeType.IF_CONDITION]. */
    data object True : OutboundPort(label = "True")

    /** `False` branch on [NodeType.IF_CONDITION]. */
    data object False : OutboundPort(label = "False")

    /** Per-item branch on [NodeType.QUEUE_PROCESSOR]. */
    data object Item : OutboundPort(label = "Item")

    /** Post-drain branch on [NodeType.QUEUE_PROCESSOR]. */
    data object Done : OutboundPort(label = "Done")

    /** Approved branch on [NodeType.EVALUATION]. */
    data object Pass : OutboundPort(label = "Pass")

    /** Retry branch on [NodeType.EVALUATION] — emitted only when `maxRetries > 0`. */
    data object Retry : OutboundPort(label = "Retry")

    /** Final-failure branch on [NodeType.EVALUATION]. */
    data object Fail : OutboundPort(label = "Fail")

    /**
     * Free-form labelled port used by [NodeType.INTENT_ROUTER] (one per
     * declared class) and reserved for any future node type that exposes
     * caller-defined branches.
     *
     * @property name the class / branch label rendered under the port and
     * on the edge.
     */
    data class Custom(val name: String) : OutboundPort(label = name)
}

/**
 * Port layout for one node: the count of inbound ports and the ordered
 * list of outbound ports.
 *
 * The two collections are kept in different shapes on purpose:
 *  - inbound is just a count — labels never appear on the inbound side.
 *  - outbound is a list of typed [OutboundPort]s — labels render under
 *    each dot when `outbound.size > 1`.
 *
 * Build via [forType] so callers stay in sync without
 * re-declaring port lists at every call site.
 *
 * @property inbound count of inbound dots (always 0 or 1 for the current
 * spec; 0 only for [NodeType.INPUT]).
 * @property outbound ordered outbound port descriptors. Empty for
 * [NodeType.OUTPUT].
 */
data class NodePorts(val inbound: Int = 1, val outbound: List<OutboundPort> = listOf(OutboundPort.Default)) {
    companion object {
        /**
         * Builds the canonical [NodePorts] layout for [type], optionally
         * parameterised on type-specific data needed to enumerate the
         * outbound ports.
         *
         * This factory is the single source of truth — the canvas, the
         * edge labels, and the validator all read the same port enumeration
         * without re-deriving it.
         *
         * @param type the node type to size for.
         * @param intentClasses class names for [NodeType.INTENT_ROUTER];
         * ignored for every other type. One [OutboundPort.Custom] is
         * emitted per non-blank entry.
         * @param maxRetries maximum retry count for [NodeType.EVALUATION];
         * ignored for every other type. The `Retry` port is omitted when
         * `maxRetries <= 0` so the editor doesn't surface a port that the
         * runtime will never emit.
         * @return a [NodePorts] descriptor.
         */
        fun forType(type: NodeType, intentClasses: List<String> = emptyList(), maxRetries: Int = 0): NodePorts =
            when (type) {
                NodeType.INPUT -> NodePorts(
                    inbound = 0,
                    outbound = listOf(OutboundPort.Default),
                )
                NodeType.OUTPUT -> NodePorts(
                    inbound = 1,
                    outbound = emptyList(),
                )
                NodeType.IF_CONDITION -> NodePorts(
                    outbound = listOf(OutboundPort.True, OutboundPort.False),
                )
                NodeType.QUEUE_PROCESSOR -> NodePorts(
                    outbound = listOf(OutboundPort.Item, OutboundPort.Done),
                )
                NodeType.EVALUATION -> NodePorts(
                    outbound = buildList {
                        add(OutboundPort.Pass)
                        if (maxRetries > 0) add(OutboundPort.Retry)
                        add(OutboundPort.Fail)
                    },
                )
                NodeType.INTENT_ROUTER -> NodePorts(
                    outbound = intentClasses
                        .filter { it.isNotBlank() }
                        .map { OutboundPort.Custom(name = it) },
                )
                NodeType.LITE_RT,
                NodeType.CLOUD,
                NodeType.CLARIFICATION,
                NodeType.TOOL,
                NodeType.DECOMPOSITION,
                NodeType.SUMMARY,
                -> NodePorts() // default: 1 in, 1 unlabelled out.
            }
    }
}
