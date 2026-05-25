package ai.agent.android.presentation.ui.more

import ai.agent.android.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.knotwork.design.screens.more.MoreContent
import app.knotwork.design.screens.more.MoreRow
import app.knotwork.design.screens.more.MoreStrings
import app.knotwork.design.screens.more.MoreViewState

/**
 * Landing screen of the "More" bottom-nav tab. Renders the Knotwork
 * [MoreContent] surface with seven navigation rows, live counters, and a
 * footer status pill summarising recent outbound network activity.
 */
@Composable
fun MoreScreen(
    onNavigateToMemory: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToMonitoring: () -> Unit,
    onNavigateToTaskMonitor: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrompts: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState.toViewState(
        titleMemory = stringResource(R.string.more_row_memory),
        titleModels = stringResource(R.string.more_row_models),
        titlePrompts = stringResource(R.string.more_row_prompts),
        titleTasks = stringResource(R.string.more_row_task_monitor),
        titleMetrics = stringResource(R.string.more_row_monitoring),
        titleSettings = stringResource(R.string.more_row_settings),
        titleAbout = stringResource(R.string.more_row_about),
        onMemory = onNavigateToMemory,
        onModels = onNavigateToModels,
        onPrompts = onNavigateToPrompts,
        onTasks = onNavigateToTaskMonitor,
        onMetrics = onNavigateToMonitoring,
        onSettings = onNavigateToSettings,
        onAbout = onNavigateToAbout,
    )
    MoreContent(
        state = state,
        modifier = Modifier.testTag(MORE_ROOT_TEST_TAG),
        strings = MoreStrings(
            title = stringResource(R.string.nav_tab_more),
            subtitle = stringResource(R.string.more_subtitle),
            searchCd = stringResource(R.string.more_search_cd),
        ),
    )
}

/** Build the catalog view state from the live UI state + localized labels. */
@Suppress("LongParameterList") // Mapper bundles localised strings + navigation lambdas.
internal fun MoreUiState.toViewState(
    titleMemory: String,
    titleModels: String,
    titlePrompts: String,
    titleTasks: String,
    titleMetrics: String,
    titleSettings: String,
    titleAbout: String,
    onMemory: () -> Unit,
    onModels: () -> Unit,
    onPrompts: () -> Unit,
    onTasks: () -> Unit,
    onMetrics: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
): MoreViewState = MoreViewState(
    rows = listOf(
        MoreRow(
            id = "memory",
            title = titleMemory,
            subtitle = memorySubtitle,
            icon = Icons.Outlined.Psychology,
            onClick = onMemory,
        ),
        MoreRow(
            id = "models",
            title = titleModels,
            subtitle = modelsSubtitle,
            icon = Icons.Outlined.Memory,
            onClick = onModels,
        ),
        MoreRow(
            id = "prompts",
            title = titlePrompts,
            subtitle = promptsSubtitle,
            icon = Icons.Outlined.Tune,
            onClick = onPrompts,
        ),
        MoreRow(
            id = "tasks",
            title = titleTasks,
            subtitle = tasksSubtitle,
            icon = Icons.Outlined.History,
            badge = tasksBadge,
            onClick = onTasks,
        ),
        MoreRow(
            id = "metrics",
            title = titleMetrics,
            subtitle = metricsSubtitle,
            icon = Icons.Outlined.Bolt,
            onClick = onMetrics,
        ),
        MoreRow(
            id = "settings",
            title = titleSettings,
            subtitle = settingsSubtitle,
            icon = Icons.Outlined.Settings,
            onClick = onSettings,
        ),
        MoreRow(
            id = "about",
            title = titleAbout,
            subtitle = aboutSubtitle,
            icon = Icons.Outlined.Info,
            onClick = onAbout,
        ),
    ),
    networkStatus = networkStatusText.takeIf { it.isNotEmpty() },
    networkStatusOk = networkStatusOk,
)

/** Stable test tag for the More screen root — used by instrumented tests. */
const val MORE_ROOT_TEST_TAG: String = "more_root"
