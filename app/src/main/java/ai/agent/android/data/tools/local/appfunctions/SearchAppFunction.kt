package ai.agent.android.data.tools.local.appfunctions

import ai.agent.android.data.tools.local.SearchTool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Callee-side wrapper around the built-in `search_tool` tool.
 *
 * Annotated `@AppFunction`-annotated functions are exposed to other applications via the
 * system `AppFunctionManager`. This class is the entry point for external callers — its
 * sole responsibility is to validate the externally-supplied arguments and delegate to
 * the existing [SearchTool] implementation that the agent itself uses internally. Keeping
 * the wrapper thin guarantees the callee path cannot diverge from the caller path: both
 * hit the same Wikipedia query code with the same truncation rules.
 *
 * **Risk classification:** read-only (no user data is touched, no side effects on device
 * state). External callers therefore do not need a Human-in-the-Loop confirmation —
 * matching the in-agent risk for `search_tool` set in `ToolRepositoryImpl`.
 *
 * **Not annotated with `androidx.appfunctions.service.AppFunction` (yet).** That annotation
 * triggers KSP-generated dispatch infrastructure (`AppFunctionServiceDelegate`) that
 * conflicts with the manual routing in [ai.agent.android.data.tools.local.AgentAppFunctionService].
 * The XML-driven discovery surface for external callers will be wired up in a follow-up
 * (Phase 20 task 7/7); until then, callees that already know `functionIdentifier =
 * "search_tool"` can invoke it directly through the manual dispatch path.
 */
@Singleton
class SearchAppFunction @Inject constructor(private val searchTool: SearchTool) {

    /**
     * Executes a Wikipedia search on behalf of an external caller.
     *
     * @param query Non-blank search term. Blank input raises [IllegalArgumentException],
     *   which the calling [ai.agent.android.data.tools.local.appfunctions.AppFunctionRouter]
     *   translates into a typed "invalid argument" outcome for the platform AppFunctions API.
     * @param lang Two-letter language code matching the language of [query]. Defaults to
     *   `"en"` so callers can omit it.
     * @return The article extract (truncated or LLM-summarized) returned by
     *   [SearchTool.executeSearch].
     */
    suspend fun invoke(query: String, lang: String = DEFAULT_LANG): String {
        require(query.isNotBlank()) { "search_tool requires a non-blank 'query' argument" }
        val effectiveLang = lang.ifBlank { DEFAULT_LANG }
        return searchTool.executeSearch(query, effectiveLang)
    }

    private companion object {
        const val DEFAULT_LANG = "en"
    }
}
