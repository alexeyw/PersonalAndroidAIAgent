package ai.agent.android.presentation.ui.chat.legacy

import ai.agent.android.R
import ai.agent.android.domain.models.ConsoleEvent
import ai.agent.android.domain.models.ConsoleEventType
import androidx.annotation.StringRes

/**
 * User-selectable category filter applied to the expanded console log
 * (Phase 17.5). Mapped 1-to-1 onto [ConsoleEventType] except for [All],
 * which short-circuits the predicate. The enum is the single source of
 * truth for the chip row inside [ConsoleFullLogSheet]: the chip label is
 * resolved from [labelRes] at render time (so localised strings live in
 * `strings_chat.xml`), and event filtering goes through [matches].
 *
 * Pure Kotlin (no Compose imports) so the predicate can be unit tested
 * without an instrumented runtime — only the resource id is exposed; the
 * actual lookup happens in the composable that draws the chip.
 *
 * @property labelRes Resource id of the localised chip label rendered above
 *   the log list.
 */
enum class ConsoleLogFilter(@StringRes val labelRes: Int) {
    /** Pass-through filter: every event matches. */
    All(R.string.chat_console_filter_all),

    /** Keeps only [ConsoleEventType.NodeExecution] entries. */
    Nodes(R.string.chat_console_filter_nodes),

    /** Keeps only [ConsoleEventType.ToolCall] entries. */
    Tools(R.string.chat_console_filter_tools),

    /** Keeps only [ConsoleEventType.MemoryAccess] entries. */
    Memory(R.string.chat_console_filter_memory),

    /** Keeps only [ConsoleEventType.Error] entries. */
    Errors(R.string.chat_console_filter_errors),
}

/**
 * Returns whether [event] survives this filter.
 *
 * [ConsoleLogFilter.All] always returns `true`. Every other value matches
 * exactly one [ConsoleEventType] and rejects the rest, which keeps the
 * filter UI semantically obvious — the user never sees an event that
 * doesn't belong to the chip they picked.
 *
 * Note that [ConsoleEventType.SystemMessage] events have no dedicated
 * chip; they appear only under the [ConsoleLogFilter.All] view, which is
 * intentional — system-level lifecycle messages are noise once the user
 * narrows the log to a specific category.
 */
fun ConsoleLogFilter.matches(event: ConsoleEvent): Boolean = when (this) {
    ConsoleLogFilter.All -> true
    // `ConsoleEventType` is a sealed interface whose every variant is a
    // `data object`, so each type is a singleton and identity equality
    // (`==`) is the canonical comparison — `is` would still compile but
    // reads as if the variants carried per-instance data.
    ConsoleLogFilter.Nodes -> event.type == ConsoleEventType.NodeExecution
    ConsoleLogFilter.Tools -> event.type == ConsoleEventType.ToolCall
    ConsoleLogFilter.Memory -> event.type == ConsoleEventType.MemoryAccess
    ConsoleLogFilter.Errors -> event.type == ConsoleEventType.Error
}
