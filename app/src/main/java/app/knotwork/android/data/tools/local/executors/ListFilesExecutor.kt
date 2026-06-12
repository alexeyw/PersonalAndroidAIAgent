package app.knotwork.android.data.tools.local.executors

import app.knotwork.android.domain.models.ToolExecutionContext
import app.knotwork.android.domain.models.WorkspaceError
import app.knotwork.android.domain.models.WorkspaceResult
import app.knotwork.android.domain.repositories.LocalToolExecutor
import app.knotwork.android.domain.services.AgentWorkspace
import org.json.JSONObject
import javax.inject.Inject

/**
 * [LocalToolExecutor] for the built-in `list_files` tool (READ_ONLY).
 *
 * Lists the workspace's files (recursively) as a stable, path-sorted, capped
 * listing — one line per file with its size and last-modified timestamp. An
 * optional `path` argument narrows the listing to a single sub-directory; it is
 * validated through the workspace containment gate so a traversal attempt is
 * refused with a typed observation rather than escaping the sandbox.
 *
 * @property workspace The jailed file sandbox whose listing is rendered.
 */
class ListFilesExecutor @Inject constructor(private val workspace: AgentWorkspace) : LocalToolExecutor {

    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String, context: ToolExecutionContext): String {
        val json = JSONObject(arguments)
        val rawPath = json.optString("path", "").trim()
        val prefix = rawPath.takeUnless { it.isEmpty() || it == "." || it == "/" }

        // When a sub-directory is requested, run it through the containment gate first
        // so an out-of-workspace path is reported precisely instead of silently listing
        // the whole tree.
        if (prefix != null) {
            when (val resolved = workspace.resolve(prefix)) {
                is WorkspaceResult.Failure -> return errorMessage(prefix, resolved.error)
                is WorkspaceResult.Success -> Unit
            }
        }

        return when (val listing = workspace.list()) {
            is WorkspaceResult.Failure -> errorMessage(rawPath, listing.error)
            is WorkspaceResult.Success -> {
                val files = if (prefix == null) {
                    listing.value
                } else {
                    val normalized = prefix.trimEnd('/')
                    listing.value.filter {
                        it.relativePath == normalized || it.relativePath.startsWith("$normalized/")
                    }
                }
                val emptyMessage = if (prefix == null) {
                    "Workspace is empty."
                } else {
                    "No files under '$prefix'."
                }
                WorkspaceListingFormat.render(files, emptyMessage)
            }
        }
    }

    /** Maps a [WorkspaceError] to a concise observation string for the agent. */
    private fun errorMessage(path: String, error: WorkspaceError): String = when (error) {
        WorkspaceError.PathOutsideWorkspace -> "Error: path '$path' is outside the workspace."
        WorkspaceError.NotFound -> "Error: '$path' not found."
        WorkspaceError.NotAText,
        WorkspaceError.TooLarge,
        WorkspaceError.AlreadyExists,
        WorkspaceError.QuotaExceeded,
        -> "Error: could not list '$path'."
    }

    companion object {
        /** Tool name as exposed to the LLM and used as the DI map key. */
        const val TOOL_NAME = "list_files"

        /** Human-facing label for the browser editor's tool dropdown. */
        const val TOOL_LABEL: String = "List Files"

        /** Human-readable description steering the model on when/how to call. */
        const val DESCRIPTION: String =
            "Lists files in the agent's private workspace, one per line with size and last-modified time. " +
                "Pass an optional 'path' to list only a sub-directory. Use this to discover what files exist " +
                "before reading or writing them."

        /** JSON-schema of the accepted arguments. */
        val PARAMETERS: String = """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "Optional workspace-relative sub-directory to list. Omit to list the whole workspace." }
              }
            }
        """.trimIndent()
    }
}
