package app.knotwork.android.domain.engine

import app.knotwork.android.domain.models.ChatHistorySummary
import app.knotwork.android.domain.models.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The decision, for one node execution, of how a session's chat history should
 * be presented in the node's context: an optional summarised prefix plus the
 * verbatim live window.
 *
 * @property earlierSummary Prose summary of the conversation older than
 *   [liveWindow], or `null` when nothing is summarised (compression off, history
 *   under budget, or no summary computed yet).
 * @property liveWindow The messages rendered verbatim under `--- Chat History ---`.
 * @property droppedUncoveredCount Number of messages that were neither covered
 *   by [earlierSummary] nor kept in [liveWindow] — i.e. messages that aged out
 *   of the window but a fresh summary has not yet absorbed. Surfaced so the
 *   engine can log the (bounded, transient) gap.
 * @property truncatedWithoutSummary `true` when the history exceeded the budget
 *   but no summary was available, so the older tail was dropped outright rather
 *   than summarised. Drives the "summary pending" console event.
 */
data class ChatHistoryView(
    val earlierSummary: String?,
    val liveWindow: List<ChatMessage>,
    val droppedUncoveredCount: Int,
    val truncatedWithoutSummary: Boolean,
) {
    companion object {
        /** The view for a node that does not request chat history at all. */
        val EMPTY = ChatHistoryView(
            earlierSummary = null,
            liveWindow = emptyList(),
            droppedUncoveredCount = 0,
            truncatedWithoutSummary = false,
        )
    }
}

/**
 * Pure, clock-free planner that decides how a session's chat history is sliced
 * into a summarised prefix and a verbatim live window for a single node's
 * context. It performs **no** I/O and **no** summarisation — the background
 * [app.knotwork.android.domain.usecases.CompressChatHistoryUseCase] produces the
 * [ChatHistorySummary]; this planner only chooses what to render given the
 * messages, the cached summary, and the user's settings.
 *
 * Contract (single rule, exhaustively unit-tested):
 *
 *  - Compression disabled, or the history is within [thresholdTokens]: render
 *    the **full** history verbatim and no summary block — exactly the legacy
 *    behaviour.
 *  - Over budget **with** a cached summary: render the summary plus the messages
 *    after the covered prefix, bounded to the last [liveWindowSize] messages.
 *    Messages that aged out of the window since the summary was last computed
 *    are dropped ([droppedUncoveredCount] > 0) until a later pass folds them in.
 *  - Over budget **without** a summary: drop the older tail outright and render
 *    only the last [liveWindowSize] messages, flagged as
 *    [ChatHistoryView.truncatedWithoutSummary]. This keeps the budget bounded
 *    even before the background pass has produced a summary.
 *
 * The bound is by **message count** (the live window), so the rendered history
 * is always at most the summary plus [liveWindowSize] (+ any messages that
 * arrived since the last compression) messages — never the unbounded full
 * history once over budget.
 */
@Singleton
class ChatHistoryWindowPlanner @Inject constructor() {

    /**
     * Plans the chat-history view for one node execution.
     *
     * @param messages The full, unfiltered session history in chronological
     *   order (the same source the engine reads for context).
     * @param summary The cached summary of an older prefix, or `null` if none
     *   has been computed yet.
     * @param compressionEnabled Whether the user enabled history compression.
     * @param thresholdTokens The approximate-token budget above which the
     *   history is compressed.
     * @param liveWindowSize How many of the most recent messages to keep
     *   verbatim.
     * @return The [ChatHistoryView] describing what to render.
     */
    fun plan(
        messages: List<ChatMessage>,
        summary: ChatHistorySummary?,
        compressionEnabled: Boolean,
        thresholdTokens: Int,
        liveWindowSize: Int,
    ): ChatHistoryView {
        if (messages.isEmpty()) return ChatHistoryView.EMPTY

        if (!compressionEnabled || approxTokenCount(messages) <= thresholdTokens) {
            // Under budget (or disabled): behave exactly as before — full
            // history, no summary block.
            return ChatHistoryView(
                earlierSummary = null,
                liveWindow = messages,
                droppedUncoveredCount = 0,
                truncatedWithoutSummary = false,
            )
        }

        val window = liveWindowSize.coerceAtLeast(MIN_WINDOW)
        // Only honour the coverage cursor when there is usable summary text to
        // stand in for the covered prefix — otherwise a blank/missing summary
        // would silently drop those messages with nothing representing them.
        val summaryText = summary?.summary?.takeIf { it.isNotBlank() }
        val covered = if (summaryText != null) {
            // Clamp defensively against a stale cursor that outruns a
            // shrunk/cleared history.
            summary.coveredMessageCount.coerceIn(0, messages.size)
        } else {
            0
        }

        // Everything after the covered prefix is verbatim candidate content;
        // keep only the most recent `window` of it.
        val remainder = messages.drop(covered)
        val liveWindow = if (remainder.size > window) remainder.takeLast(window) else remainder
        val droppedUncovered = remainder.size - liveWindow.size

        return ChatHistoryView(
            earlierSummary = summaryText,
            liveWindow = liveWindow,
            droppedUncoveredCount = droppedUncovered,
            truncatedWithoutSummary = summaryText == null && droppedUncovered > 0,
        )
    }

    companion object {
        /**
         * Smallest live window the planner will honour, guarding against a
         * setting (or test) of 0 that would drop every message.
         */
        private const val MIN_WINDOW = 1

        /**
         * Approximate characters per token — the project-wide GPT-style rule of
         * thumb (1 token ≈ 4 chars). Kept here as the single source of truth for
         * the compression token budget so the planner and the background
         * compressor agree on when a history is "over budget".
         */
        const val CHARS_PER_TOKEN: Int = 4

        /**
         * Rough token-count estimate for a list of messages, summing each
         * message's content length over [CHARS_PER_TOKEN]. A deliberately cheap
         * heuristic — exact tokenisation depends on the runtime model and is not
         * worth the cost for a budget comparison.
         *
         * @param messages The messages to estimate.
         * @return The approximate total token count, never negative.
         */
        fun approxTokenCount(messages: List<ChatMessage>): Int =
            (messages.sumOf { it.content.length } / CHARS_PER_TOKEN).coerceAtLeast(0)
    }
}
