package ai.agent.android.presentation.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.screens.tools.McpServerConfigCallbacks
import app.knotwork.design.screens.tools.McpServerConfigContent

/**
 * Stateful entry point for the standalone MCP server configuration
 * screen. Both Add (no `originalUrl` nav argument) and Edit
 * (`originalUrl` matches the row being edited) modes are routed
 * through this composable; `McpServerConfigViewModel.form.editingUrl`
 * distinguishes them visually.
 */
@Composable
fun McpServerConfigScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: McpServerConfigViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(event) {
        if (event is McpServerConfigViewModel.Event.Saved) {
            viewModel.consumeEvent()
            onDone()
        }
    }

    val callbacks = McpServerConfigCallbacks(
        onUrlChange = viewModel::onUrlChange,
        onNameChange = viewModel::onNameChange,
        onTransportSelect = viewModel::onTransportSelect,
        onHeaderAdd = viewModel::onHeaderAdd,
        onHeaderChange = viewModel::onHeaderChange,
        onHeaderRemove = viewModel::onHeaderRemove,
        onSubmit = viewModel::onSubmit,
        onCancel = onCancel,
    )

    McpServerConfigContent(
        form = form,
        callbacks = callbacks,
        modifier = modifier.testTag(tag = MCP_SERVER_CONFIG_ROOT_TEST_TAG),
    )
}

/** TestTag applied to the MCP server config screen root. */
internal const val MCP_SERVER_CONFIG_ROOT_TEST_TAG = "mcp_server_config_root"
