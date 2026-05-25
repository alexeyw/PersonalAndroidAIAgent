package ai.agent.android.presentation.ui.more

/**
 * Live counters surfaced as the per-row subtitles + footer pill on the
 * More tab. Every field is a pre-formatted display string so the catalog
 * renderer stays free of `String.format` and locale-handling.
 *
 * @property memorySubtitle e.g. `"1 248 chunks · 14.2 MB"`.
 * @property modelsSubtitle e.g. `"gemma-4-E2B · active"` or `"no model installed"`.
 * @property promptsSubtitle e.g. `"5 categories · 24 prompts"`.
 * @property tasksSubtitle e.g. `"2 running · 4 queued"` or `"none"`.
 * @property tasksBadge running tasks count surfaced in the trailing badge
 * pill on the Active tasks row.
 * @property metricsSubtitle constant `"tok/s · latency · battery"`.
 * @property settingsSubtitle constant `"system prompt · LLM params · keys"`.
 * @property aboutSubtitle e.g. `"v0.1 · build 1"`.
 * @property networkStatusText footer line, e.g. `"on-device · no network calls in last 14 m"`.
 * @property networkStatusOk drives the footer-dot colour.
 */
data class MoreUiState(
    val memorySubtitle: String = "—",
    val modelsSubtitle: String = "—",
    val promptsSubtitle: String = "—",
    val tasksSubtitle: String = "—",
    val tasksBadge: Int = 0,
    val metricsSubtitle: String = "tok/s · latency · battery",
    val settingsSubtitle: String = "system prompt · LLM params · keys",
    val aboutSubtitle: String = "—",
    val networkStatusText: String = "",
    val networkStatusOk: Boolean = true,
)
