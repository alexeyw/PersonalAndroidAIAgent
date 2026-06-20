package app.knotwork.android.presentation.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
 * folds it into the catalog [DiscoverViewState] (formatting stats and the
 * "N files" hint with locale/plural rules the catalog stays free of) and
 * renders [DiscoverContent].
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

    val rows = uiState.models.map { it.toRow(fileCountLabel = fileCountLabel(it.litertFileCount)) }

    val viewState = remember(uiState, rows, networkError) {
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
                rows = rows,
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

/** Localised "N files" hint for the row's file count. */
@Composable
private fun fileCountLabel(count: Int): String = pluralStringResource(R.plurals.discover_file_count, count, count)

/** Folds a domain summary onto the catalog list row, building the stats line. */
private fun DiscoverableModelSummary.toRow(fileCountLabel: String): DiscoverModelRow = DiscoverModelRow(
    repoId = repoId,
    name = displayName,
    meta = buildMeta(),
    fileCountLabel = fileCountLabel,
    gated = gated,
)

/** Builds the `"↓ 12.4k · ♥ 86 · apache-2.0"` stats line (license omitted when absent). */
private fun DiscoverableModelSummary.buildMeta(): String = listOfNotNull(
    "↓ ${formatHfCount(downloads)}",
    "♥ ${formatHfCount(likes)}",
    license,
).joinToString(separator = " · ")
