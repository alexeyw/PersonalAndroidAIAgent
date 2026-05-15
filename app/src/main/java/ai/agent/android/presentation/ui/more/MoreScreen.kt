package ai.agent.android.presentation.ui.more

import ai.agent.android.R
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

/**
 * Landing screen of the "More" bottom-nav tab.
 *
 * Per `decisions.md §12`, secondary destinations (Memory / Models / Task
 * Monitor / Settings / About) sit under this tab — moving them off the
 * top-level surface keeps the four-tab IA the design system targets.
 *
 * Phase 21 / Task 4 keeps the body as a plain Material3 [ListItem] list so
 * we don't pre-commit to the Knotwork list-row design; Task 10's "Other
 * screens" pass swaps these rows for the Knotwork list-row primitive
 * (defined in Task 5) and bumps the leading icons to the Knotwork glyph
 * set.
 *
 * Navigation callbacks (one per row) keep the screen agnostic of route
 * literals — every consumer in
 * [AppNavGraph][ai.agent.android.presentation.ui.navigation.AppNavGraph]
 * passes a lambda that invokes `navController.navigate(NavRoutes.X)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onNavigateToMemory: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToMonitoring: () -> Unit,
    onNavigateToTaskMonitor: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrompts: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_tab_more)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .testTag(MORE_ROOT_TEST_TAG),
        ) {
            MoreRow(R.string.more_row_memory, "more_row_memory", onNavigateToMemory)
            HorizontalDivider()
            MoreRow(R.string.more_row_models, "more_row_models", onNavigateToModels)
            HorizontalDivider()
            MoreRow(R.string.more_row_prompts, "more_row_prompts", onNavigateToPrompts)
            HorizontalDivider()
            MoreRow(R.string.more_row_task_monitor, "more_row_task_monitor", onNavigateToTaskMonitor)
            HorizontalDivider()
            MoreRow(R.string.more_row_monitoring, "more_row_monitoring", onNavigateToMonitoring)
            HorizontalDivider()
            MoreRow(R.string.more_row_settings, "more_row_settings", onNavigateToSettings)
            HorizontalDivider()
            MoreRow(R.string.more_row_about, "more_row_about", onNavigateToAbout)
        }
    }
}

@Composable
private fun MoreRow(@StringRes titleRes: Int, tag: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(titleRes)) },
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag),
    )
}

/** Stable test tag for the More screen root — used by instrumented tests. */
const val MORE_ROOT_TEST_TAG: String = "more_root"
