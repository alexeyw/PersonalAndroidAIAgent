package ai.agent.android.presentation.ui.chat

import ai.agent.android.domain.models.ConsoleEvent
import ai.agent.android.domain.models.ConsoleEventType

/**
 * User-selectable category filter applied to the expanded console log
 * (Phase 17.5). Mapped 1-to-1 onto [ConsoleEventType] except for [All],
 * which short-circuits the predicate. The enum is the single source of
 * truth for the chip row inside [ConsoleFullLogSheet]: the chip label is
 * read from [label], and event filtering goes through [matches].
 *
 * Pure Kotlin (no Compose / Android imports) so the predicate can be unit
 * tested without an instrumented runtime.
 *
 * @property label Human-readable chip label rendered above the log list.
 */
enum class ConsoleLogFilter(val label: String) {
    /** Pass-through filter: every event matches. */
    All("All"),

    /** Keeps only [ConsoleEventType.NodeExecution] entries. */
    Nodes("Nodes"),

    /** Keeps only [ConsoleEventType.ToolCall] entries. */
    Tools("Tools"),

    /** Keeps only [ConsoleEventType.MemoryAccess] entries. */
    Memory("Memory"),

    /** Keeps only [ConsoleEventType.Error] entries. */
    Errors("Errors"),
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
    ConsoleLogFilter.Nodes -> event.type is ConsoleEventType.NodeExecution
    ConsoleLogFilter.Tools -> event.type is ConsoleEventType.ToolCall
    ConsoleLogFilter.Memory -> event.type is ConsoleEventType.MemoryAccess
    ConsoleLogFilter.Errors -> event.type is ConsoleEventType.Error
}
