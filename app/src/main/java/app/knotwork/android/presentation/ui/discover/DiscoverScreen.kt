package app.knotwork.android.presentation.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.knotwork.android.R
import app.knotwork.android.domain.models.DiscoverableModelSummary
import app.knotwork.design.screens.discover.DiscoverCallbacks
import app.knotwork.design.screens.discover.DiscoverContent
import app.knotwork.design.screens.discover.DiscoverModelRow
import app.knotwork.design.screens.discover.DiscoverViewState
import app.knotwork.design.screens.discover.DiscoverVisualState

/**
 * App-side Discover list screen. Subscribes to [DiscoverViewModel.uiState],
 * folds it into the catalog [DiscoverViewState] (formatting the stats line; the
 * "N files" hint is rendered from a raw count by the catalog) and renders
 * [DiscoverContent].
 *
 * @param viewModel Hilt-injected [DiscoverViewModel].
 * @param onBack navigation callback for the top-bar back arrow.
 * @param onOpenModel navigation callback opening the detail screen for a repo id.
 * @param modifier optional layout modifier.
 */
@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenModel: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val networkError = stringResource(R.string.discover_error_network)

    // Build the view state (including the row mapping) inside remember keyed on
    // the data it derives from, so recompositions that don't change uiState
    // (e.g. the snackbar host) don't re-map every model. The "N files" hint is
    // formatted later, per visible row, by the catalog — toRow stays pure.
    val viewState = remember(uiState, networkError) {
        when (uiState.status) {
            DiscoverStatus.Loading ->
                DiscoverViewState(visualState = DiscoverVisualState.Loading, query = uiState.query)
            DiscoverStatus.Empty ->
                DiscoverViewState(visualState = DiscoverVisualState.Empty, query = uiState.query)
            DiscoverStatus.Error -> DiscoverViewState(
                visualState = DiscoverVisualState.Error,
                query = uiState.query,
                errorMessage = networkError,
            )
            DiscoverStatus.Populated -> DiscoverViewState(
                visualState = DiscoverVisualState.Populated,
                query = uiState.query,
                rows = uiState.models.map { it.toRow() },
                refreshing = uiState.refreshing,
            )
        }
    }

    DiscoverContent(
        state = viewState,
        modifier = modifier,
        callbacks = DiscoverCallbacks(
            onBack = onBack,
            onQueryChange = viewModel::onQueryChange,
            onSubmitSearch = viewModel::onSubmitSearch,
            onModelClick = onOpenModel,
            onRefresh = viewModel::onRefresh,
            onRetry = viewModel::onRetry,
        ),
    )
}

/** Folds a domain summary onto the catalog list row, building the stats line. */
private fun DiscoverableModelSummary.toRow(): DiscoverModelRow = DiscoverModelRow(
    repoId = repoId,
    name = displayName,
    meta = buildMeta(),
    fileCount = litertFileCount,
    gated = gated,
)

/** Builds the `"↓ 12.4k · ♥ 86 · apache-2.0"` stats line (license omitted when absent). */
private fun DiscoverableModelSummary.buildMeta(): String = listOfNotNull(
    "↓ ${formatHfCount(downloads)}",
    "♥ ${formatHfCount(likes)}",
    license,
).joinToString(separator = " · ")
