package ai.agent.android.presentation.ui.more

import ai.agent.android.BuildConfig
import ai.agent.android.domain.engine.TaskQueueManager
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.NetworkActivityTracker
import ai.agent.android.domain.repositories.PromptRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject

/**
 * Aggregates the live counters surfaced on the More tab.
 *
 * The screen itself is mostly navigation — but the per-row subtitles
 * (memory chunks count, active model name, active-task count, etc.) are
 * live so the user can tell at a glance whether anything is running.
 *
 * Re-emission frequency is throttled at the source (each upstream flow
 * only emits on real change), so the combined flow stays cheap.
 */
@HiltViewModel
class MoreViewModel @Inject constructor(
    memoryRepository: MemoryRepository,
    private val localModelRepository: LocalModelRepository,
    private val promptRepository: PromptRepository,
    private val taskQueueManager: TaskQueueManager,
    private val networkActivityTracker: NetworkActivityTracker,
) : ViewModel() {

    val uiState: StateFlow<MoreUiState> = combine(
        memoryRepository.observeStats(),
        localModelRepository.getAllModels(),
        promptRepository.getAllPrompts(),
        taskQueueManager.activeSessionsState,
        networkActivityTracker.lastOutboundAt,
    ) { memStats, models, prompts, activeSessions, lastNetwork ->
        val active = models.firstOrNull { it.isActive }
        val runningCount = activeSessions.values.count {
            it is AgentOrchestratorState.Thinking ||
                it is AgentOrchestratorState.Answering
        }
        val queuedCount = activeSessions.values.count { it is AgentOrchestratorState.Idle }
        MoreUiState(
            memorySubtitle = formatMemoryStats(memStats.chunkCount, memStats.totalBytes),
            modelsSubtitle = if (active != null) "${active.name} · active" else "no model installed",
            promptsSubtitle = formatPromptsStats(prompts.map { it.category }.distinct().size, prompts.size),
            tasksSubtitle = if (runningCount == 0 && queuedCount == 0) {
                "none"
            } else {
                "$runningCount running · $queuedCount queued"
            },
            tasksBadge = runningCount,
            aboutSubtitle = "v${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
            networkStatusText = formatNetworkStatus(System.currentTimeMillis(), lastNetwork),
            networkStatusOk = lastNetwork == null ||
                (System.currentTimeMillis() - lastNetwork) > FRESH_NETWORK_WINDOW_MS,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_STOP_TIMEOUT_MS),
        initialValue = MoreUiState(),
    )

    private companion object {
        /**
         * Outbound-call timestamps younger than this window flip the footer
         * indicator from `on-device` (green) to `online · cloud enabled`
         * (warn). 60 s ≈ "in this session".
         */
        const val FRESH_NETWORK_WINDOW_MS: Long = 60_000L

        /** WhileSubscribed grace period — see TaskMonitorViewModel. */
        const val STATE_STOP_TIMEOUT_MS: Long = 5_000L
    }
}

/**
 * Format a memory-stats line for the More tab subtitle.
 *
 * @param chunkCount total chunk count.
 * @param totalBytes approximate on-disk size in bytes.
 */
internal fun formatMemoryStats(chunkCount: Int, totalBytes: Long): String {
    val count = if (chunkCount > GROUPED_THRESHOLD) {
        chunkCount.toString().reversed().chunked(size = GROUP_DIGITS).joinToString(separator = " ").reversed()
    } else {
        chunkCount.toString()
    }
    val size = when {
        totalBytes <= 0L -> "0 MB"
        totalBytes < MEGABYTE -> "${(totalBytes / KILOBYTE)} KB"
        else -> String.format(Locale.US, "%.1f MB", totalBytes.toDouble() / MEGABYTE)
    }
    return "$count chunks · $size"
}

/**
 * Format a prompt-stats line for the More tab subtitle.
 *
 * @param categoriesCount number of distinct categories.
 * @param promptsCount total number of stored prompts.
 */
internal fun formatPromptsStats(categoriesCount: Int, promptsCount: Int): String =
    "$categoriesCount categories · $promptsCount prompts"

/**
 * Compute the network-status footer text from the [lastOutboundAt]
 * timestamp emitted by [NetworkActivityTracker].
 *
 * - `null` → `on-device · no network calls yet`
 * - older than 60 s → `on-device · no network calls in last N m`
 * - newer than 60 s → `online · cloud enabled`
 *
 * @param now epoch milliseconds at which the message is computed.
 * @param lastOutboundAt epoch milliseconds of the most recent outbound
 * call, or `null` if none has been recorded since process start.
 */
internal fun formatNetworkStatus(now: Long, lastOutboundAt: Long?): String {
    if (lastOutboundAt == null) {
        return "on-device · no network calls yet"
    }
    val elapsedMs = (now - lastOutboundAt).coerceAtLeast(minimumValue = 0L)
    return if (elapsedMs < FRESH_WINDOW_MS) {
        "online · cloud enabled"
    } else {
        val minutes = elapsedMs / MS_PER_MINUTE
        "on-device · no network calls in last $minutes m"
    }
}

private const val KILOBYTE = 1024L
private const val MEGABYTE = KILOBYTE * 1024L
private const val FRESH_WINDOW_MS = 60_000L
private const val MS_PER_MINUTE = 60_000L
private const val GROUPED_THRESHOLD = 999
private const val GROUP_DIGITS = 3
