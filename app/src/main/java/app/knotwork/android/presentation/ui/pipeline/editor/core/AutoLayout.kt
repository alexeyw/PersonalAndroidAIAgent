package app.knotwork.android.presentation.ui.pipeline.editor.core

import app.knotwork.android.domain.models.PipelineGraph
import kotlin.math.max

/**
 * Pure-Kotlin Sugiyama-style hierarchical auto-layout for a [PipelineGraph].
 *
 * Three-pass algorithm:
 *  1. **Layering** — longest-path from each [app.knotwork.android.domain.models.NodeType.INPUT].
 *     Disconnected nodes (without any predecessor) land on layer 0; nodes reachable from
 *     several inputs collapse onto the deepest layer that respects all predecessors.
 *     Back-edges (cycles such as a QUEUE_PROCESSOR re-iteration loop) are ignored for
 *     layering, so a cyclic graph lays out without overflowing the resolution stack.
 *  2. **Ordering** — within each layer the nodes are sorted by the median index of their
 *     incoming neighbours on the layer above (single-pass median heuristic; sufficient
 *     for the small graphs the editor handles).
 *  3. **Coordinates** — nodes get evenly-spaced X positions (centred at zero) on their
 *     layer and a fixed-stride Y per layer; both X and Y are snapped to [CanvasTransform.GRID_PX]
 *     so the result lines up with the drag-and-drop snap grid.
 *
 * The function also returns the per-node depth so callers can apply the spec's 80 ms
 * stagger when animating to the new positions.
 *
 * Pure Kotlin — no Compose dependency — so the layout is JVM-testable.
 */
object AutoLayout {

    /**
     * Default vertical step between layers, in canvas-space px **at density 1.0**.
     *
     * Callers that render dp-sized node cards must pass a density-scaled gap to [compute]
     * instead (1 canvas-unit maps to 1 screen-px per [CanvasTransform], but a `NodeCard`
     * is sized in dp and therefore occupies `cardHeightDp × density` canvas-px on screen —
     * a fixed-px gap would let the cards overlap on any high-density display). This constant
     * is the fallback used by JVM tests, which have no [androidx.compose.ui.unit.Density].
     */
    const val LAYER_GAP_Y: Float = 144f

    /**
     * Default horizontal step between siblings on the same layer, in canvas-space px **at
     * density 1.0**. See [LAYER_GAP_Y] for why on-device callers pass a density-scaled gap
     * to [compute] rather than relying on this baseline.
     */
    const val SIBLING_GAP_X: Float = 216f

    /**
     * Result of one layout pass.
     *
     * @property positions canvas-space `(x, y)` per node id; values are snapped to
     * [CanvasTransform.GRID_PX].
     * @property depths zero-based layer index per node id. Used by the editor to schedule
     * a per-depth 80 ms stagger when animating the layout transition.
     */
    data class Result(val positions: Map<String, Pair<Float, Float>>, val depths: Map<String, Int>)

    /**
     * Computes positions for every node in [graph].
     *
     * @param siblingGapPx horizontal centre-to-centre step between siblings on a layer, in
     * canvas-space px. On-device callers pass a density-scaled value (card width + margin,
     * in dp, converted through the current [androidx.compose.ui.unit.Density]) so the spacing
     * tracks the real on-screen footprint of the dp-sized node cards; defaults to the
     * density-1 baseline [SIBLING_GAP_X] for tests.
     * @param layerGapPx vertical centre-to-centre step between layers, in canvas-space px.
     * Same density contract as [siblingGapPx]; defaults to [LAYER_GAP_Y].
     * @return a [Result] populated for every node id in [graph]. The map is empty when
     * [graph] has no nodes.
     */
    fun compute(graph: PipelineGraph, siblingGapPx: Float = SIBLING_GAP_X, layerGapPx: Float = LAYER_GAP_Y): Result {
        if (graph.nodes.isEmpty()) return Result(emptyMap(), emptyMap())
        val depths = computeDepths(graph)
        val byLayer = depths.entries
            .groupBy({ it.value }, { it.key })
            .toSortedMap()
        val byLayerOrdered = orderWithinLayers(byLayer, graph)
        val positions = assignCoordinates(byLayerOrdered, siblingGapPx, layerGapPx)
        return Result(positions = positions, depths = depths)
    }

    // ─── Pass 1 — longest-path layering ──────────────────────────────────────

    private fun computeDepths(graph: PipelineGraph): Map<String, Int> {
        val inbound = mutableMapOf<String, MutableList<String>>()
        graph.nodes.forEach { inbound[it.id] = mutableListOf() }
        graph.connections.forEach { c ->
            inbound.getOrPut(c.targetNodeId) { mutableListOf() }.add(c.sourceNodeId)
        }
        val depth = mutableMapOf<String, Int>()
        // Nodes currently on the resolution stack. The editor permits legitimate
        // back-edges (e.g. a QUEUE_PROCESSOR re-iteration loop, an EVALUATION → Retry
        // edge), which make the predecessor graph cyclic. Re-entering a node that is
        // already being resolved means we followed such a back-edge: ignore it for
        // layering instead of recursing forever (which would `StackOverflowError`).
        val onStack = mutableSetOf<String>()
        fun resolve(id: String): Int {
            depth[id]?.let { return it }
            if (!onStack.add(id)) return 0
            val result = inbound[id].orEmpty()
                .filter { it != id } // drop self-loops outright
                .maxOfOrNull { resolve(it) }
                ?.plus(1) ?: 0
            onStack.remove(id)
            depth[id] = result
            return result
        }
        graph.nodes.forEach { resolve(it.id) }
        return depth
    }

    // ─── Pass 2 — median heuristic within each layer ─────────────────────────

    private fun orderWithinLayers(byLayer: Map<Int, List<String>>, graph: PipelineGraph): Map<Int, List<String>> {
        if (byLayer.isEmpty()) return emptyMap()
        val ordered = mutableMapOf<Int, List<String>>()
        // Sort the seed layer deterministically by node id so a fresh graph always lays
        // out the same way; downstream layers inherit the ordering through the median.
        ordered[0] = byLayer[0]?.sorted().orEmpty()
        val maxDepth = byLayer.keys.max()
        for (depth in 1..maxDepth) {
            val above = ordered[depth - 1].orEmpty()
            val aboveIndex = above.withIndex().associate { it.value to it.index }
            val children = byLayer[depth].orEmpty()
            ordered[depth] = children.sortedWith(
                compareBy(
                    { node ->
                        val parents = graph.connections
                            .filter { it.targetNodeId == node }
                            .mapNotNull { aboveIndex[it.sourceNodeId] }
                        if (parents.isEmpty()) Float.MAX_VALUE else medianOf(parents)
                    },
                    // Stable tiebreaker so the test corpus is deterministic.
                    { it },
                ),
            )
        }
        return ordered
    }

    private fun medianOf(values: List<Int>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid].toFloat()
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2f
        }
    }

    // ─── Pass 3 — coordinates with grid snapping ─────────────────────────────

    private fun assignCoordinates(
        byLayer: Map<Int, List<String>>,
        siblingGapPx: Float,
        layerGapPx: Float,
    ): Map<String, Pair<Float, Float>> {
        if (byLayer.isEmpty()) return emptyMap()
        val widest = byLayer.values.maxOfOrNull { it.size } ?: 0
        val positions = mutableMapOf<String, Pair<Float, Float>>()
        byLayer.forEach { (depth, layerNodes) ->
            val layerWidth = max(1, layerNodes.size)
            val startX = -siblingGapPx * (layerWidth - 1) / 2f
            layerNodes.forEachIndexed { index, id ->
                val rawX = startX + index * siblingGapPx
                val rawY = depth * layerGapPx
                positions[id] = CanvasTransform.snapToGrid(rawX) to CanvasTransform.snapToGrid(rawY)
            }
        }
        // Suppress unused so Kotlin doesn't warn when "widest" is read only by tests
        // through the result map's structure (kept as a guard in case future logic needs it).
        @Suppress("UNUSED_VARIABLE")
        val _widestCount = widest
        return positions
    }
}
