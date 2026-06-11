package app.knotwork.android.domain.models

/**
 * Engine-side context that accompanies a tool invocation.
 *
 * Tool arguments themselves come from the LLM as a JSON string and therefore
 * can never be trusted to carry trustworthy identifiers. Values that the
 * execution environment knows authoritatively — such as which chat session
 * the enclosing pipeline run belongs to — travel through this context object
 * instead, so executors that need them (e.g. `schedule_task` binding a
 * scheduled run back to the originating session) read them from a source the
 * model cannot spoof.
 *
 * @property sessionId Id of the chat session whose pipeline run invoked the
 *   tool, or `null` when the invocation has no session affiliation (e.g.
 *   direct execution from a tool-detail debug surface).
 */
data class ToolExecutionContext(val sessionId: String? = null) {
    /** Well-known context instances. */
    companion object {
        /** Context carrying no environment information. */
        val EMPTY: ToolExecutionContext = ToolExecutionContext()
    }
}
