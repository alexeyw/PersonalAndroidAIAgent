package app.knotwork.android.presentation.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.screens.tools.AddHostState
import app.knotwork.design.screens.tools.AllowedDomainsCallbacks
import app.knotwork.design.screens.tools.AllowedDomainsContent
import app.knotwork.design.screens.tools.AllowedDomainsViewState

/**
 * Stateful entry point for the standalone Allowed-domains editor reached from
 * the `http_request` row on the Tools screen. Maps [AllowedDomainsViewModel]
 * state to the catalog [AllowedDomainsContent] and wires the add / remove /
 * back callbacks.
 *
 * @param onBack pop back to the Tools screen.
 * @param modifier layout modifier from the nav host.
 * @param viewModel injected editor ViewModel.
 */
@Composable
fun AllowedDomainsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AllowedDomainsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val viewState = AllowedDomainsViewState(
        hosts = uiState.hosts,
        addInput = uiState.addInput,
        addState = uiState.addFeedback.toCatalogState(),
    )

    val callbacks = AllowedDomainsCallbacks(
        onBack = onBack,
        onInfo = { /* The screen body already explains the allowlist; no extra sheet today. */ },
        onAddInputChange = viewModel::onAddInputChange,
        onAddSubmit = viewModel::onAddSubmit,
        onRemoveHost = viewModel::onRemoveHost,
    )

    AllowedDomainsContent(
        state = viewState,
        callbacks = callbacks,
        modifier = modifier.testTag(tag = ALLOWED_DOMAINS_ROOT_TEST_TAG),
    )
}

/** Maps the app-side feedback to the catalog's add-field state. */
private fun AddHostFeedback.toCatalogState(): AddHostState = when (this) {
    AddHostFeedback.Idle -> AddHostState.Idle
    is AddHostFeedback.Valid -> AddHostState.NormalizedPreview(normalized)
    AddHostFeedback.Invalid -> AddHostState.Invalid
    is AddHostFeedback.Duplicate -> AddHostState.Duplicate(existing)
}

/** TestTag applied to the allowed-domains screen root. */
internal const val ALLOWED_DOMAINS_ROOT_TEST_TAG = "allowed_domains_screen_root"
