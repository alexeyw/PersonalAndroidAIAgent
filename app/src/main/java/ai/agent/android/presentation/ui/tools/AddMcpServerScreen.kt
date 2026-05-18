package ai.agent.android.presentation.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.screens.tools.AddMcpServerCallbacks
import app.knotwork.design.screens.tools.AddMcpServerContent
import app.knotwork.design.screens.tools.AddMcpServerViewState

/**
 * Add-MCP-server screen — Phase 21 / Task 10.
 *
 * Shares [ToolsViewModel] with [ToolsScreen] so committing a new server
 * URL is reflected immediately in the parent list. URL validation lives
 * here: only fully-formed `http(s)://` URLs commit; otherwise the catalog
 * surface renders the inline error and disables the submit button.
 */
@Composable
fun AddMcpServerScreen(
    onCancel: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ToolsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var localUrlError by remember { mutableStateOf<String?>(null) }

    val viewState = AddMcpServerViewState(
        url = uiState.newMcpUrlInput,
        urlError = localUrlError,
        submitting = false,
    )

    val callbacks = AddMcpServerCallbacks(
        onUrlChange = { value ->
            viewModel.onMcpUrlInputChanged(url = value)
            localUrlError = validateUrl(input = value)
        },
        onSubmit = {
            val current = uiState.newMcpUrlInput
            val error = validateUrl(input = current, requireNonEmpty = true)
            if (error == null) {
                viewModel.addMcpServer()
                onSubmitted()
            } else {
                localUrlError = error
            }
        },
        onCancel = onCancel,
    )

    AddMcpServerContent(
        state = viewState,
        callbacks = callbacks,
        modifier = modifier.testTag(tag = ADD_MCP_ROOT_TEST_TAG),
    )
}

/**
 * Cheapest URL validation that catches the common mistakes (typo
 * "htttps", missing scheme) without depending on `java.net.URI` (which
 * accepts a much wider grammar than what the MCP client can actually
 * connect to). Returns `null` when the URL passes; the localised error
 * message otherwise.
 *
 * @param requireNonEmpty when `true`, an empty input is reported as an
 * error; when `false` (the default, used during typing), empty inputs
 * pass silently so the user is not yelled at while typing.
 */
private fun validateUrl(input: String, requireNonEmpty: Boolean = false): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return if (requireNonEmpty) URL_REQUIRED_MESSAGE else null
    }
    val lower = trimmed.lowercase()
    if (!lower.startsWith(prefix = "http://") && !lower.startsWith(prefix = "https://")) {
        return URL_SCHEME_REQUIRED_MESSAGE
    }
    val afterScheme = trimmed.substringAfter(delimiter = "://", missingDelimiterValue = "")
    if (afterScheme.isEmpty() || afterScheme.startsWith(prefix = "/")) {
        return URL_HOST_REQUIRED_MESSAGE
    }
    return null
}

/** TestTag applied to the add-MCP-server screen root. */
internal const val ADD_MCP_ROOT_TEST_TAG = "add_mcp_server_root"

private const val URL_REQUIRED_MESSAGE = "Enter a server URL."
private const val URL_SCHEME_REQUIRED_MESSAGE = "URL must start with http:// or https://."
private const val URL_HOST_REQUIRED_MESSAGE = "URL needs a host name."
