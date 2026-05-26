package app.knotwork.design.screens.monitoring

/** Visual variant of the Monitoring surface. */
enum class MonitoringVisualState { Loading, Empty, Default, Error }

/**
 * Pre-formatted stat cell rendered in the metrics grid.
 *
 * @property label uppercase metric label (e.g. `"INFERENCE TIME"`).
 * @property value formatted value (e.g. `"42 ms"`).
 */
data class MonitoringStat(val label: String, val value: String)

/** One row of the "Time per node type" breakdown. */
data class NodeBreakdownRow(val nodeType: String, val totalLabel: String)

/** Pre-formatted system log line surfaced on the recent-logs list. */
data class MonitoringLogLine(val timestamp: String, val message: String)

/**
 * Top-level immutable input to `MonitoringContent`.
 */
data class MonitoringViewState(
    val visualState: MonitoringVisualState,
    val powerSavingActive: Boolean = false,
    val stats: List<MonitoringStat> = emptyList(),
    val totalExecutionLine: String? = null,
    val perNodeBreakdown: List<NodeBreakdownRow> = emptyList(),
    val logs: List<MonitoringLogLine> = emptyList(),
    val errorMessage: String? = null,
) {
    init {
        require((visualState == MonitoringVisualState.Error) == (errorMessage != null)) {
            "errorMessage must be non-null iff visualState == Error"
        }
    }
}

/** One-shot callbacks consumed by `MonitoringContent`. */
class MonitoringCallbacks(val onBack: () -> Unit = {}, val onRetry: () -> Unit = {})

/** Convenience factory returning a no-op callback bundle. */
fun noopMonitoringCallbacks(): MonitoringCallbacks = MonitoringCallbacks()
