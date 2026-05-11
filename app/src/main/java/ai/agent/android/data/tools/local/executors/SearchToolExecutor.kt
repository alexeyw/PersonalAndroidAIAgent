package ai.agent.android.data.tools.local.executors

import ai.agent.android.data.tools.local.SearchTool
import ai.agent.android.domain.repositories.LocalToolExecutor
import org.json.JSONObject
import javax.inject.Inject

/**
 * [LocalToolExecutor] implementation for the built-in `search_tool` tool.
 *
 * Parses the JSON arguments (`query`, `lang`) and delegates to [SearchTool] which hits
 * the Wikipedia API. Returns an inline error message when `query` is blank instead of
 * silently issuing an empty request.
 */
class SearchToolExecutor @Inject constructor(private val searchTool: SearchTool) : LocalToolExecutor {

    override val toolName: String = SearchTool.TOOL_NAME

    override suspend fun execute(arguments: String): String {
        val json = JSONObject(arguments)
        val query = json.optString("query", "")
        val lang = json.optString("lang", "en")
        if (query.isBlank()) {
            return "Error: Missing 'query' argument."
        }
        return searchTool.executeSearch(query, lang)
    }
}
