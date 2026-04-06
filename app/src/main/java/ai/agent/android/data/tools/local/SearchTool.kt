package ai.agent.android.data.tools.local

import ai.agent.android.domain.models.AgentTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A local tool that performs a simple web search using the Wikipedia API.
 * This is used for queries that require fetching external data.
 */
@Singleton
class SearchTool @Inject constructor() {

    companion object {
        const val TOOL_NAME = "search_tool"
        const val TOOL_DESCRIPTION = "Searches Wikipedia for up-to-date information about a topic. Use this when you need facts or data to answer a user's question."
        const val TOOL_PARAMETERS = """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "The topic to search for" }
              },
              "required": ["query"]
            }
        """
    }

    /**
     * Returns the [AgentTool] representation of this tool.
     */
    fun asAgentTool(): AgentTool {
        return AgentTool(
            name = TOOL_NAME,
            description = TOOL_DESCRIPTION,
            parameters = TOOL_PARAMETERS.trimIndent()
        )
    }

    /**
     * Executes the search query against Wikipedia.
     *
     * @param query The search query.
     * @return A summary of the search results as a string.
     */
    suspend fun executeSearch(query: String): String = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://en.wikipedia.org/w/api.php?action=query&format=json&prop=extracts&exintro=true&explaintext=true&titles=${encodedQuery}"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                val queryObj = jsonResponse.getJSONObject("query")
                val pagesObj = queryObj.getJSONObject("pages")
                val firstKey = pagesObj.keys().next()

                if (firstKey == "-1") {
                    return@withContext "No results found for '$query'."
                }

                val page = pagesObj.getJSONObject(firstKey)
                val extract = page.optString("extract", "No summary available.")
                
                // Limit the extract to avoid token explosion
                if (extract.length > 1000) {
                    return@withContext extract.substring(0, 1000) + "..."
                }
                return@withContext extract
            } else {
                return@withContext "Failed to fetch data: HTTP ${connection.responseCode}"
            }
        } catch (e: Exception) {
            return@withContext "Error executing search: ${e.message}"
        }
    }
}
