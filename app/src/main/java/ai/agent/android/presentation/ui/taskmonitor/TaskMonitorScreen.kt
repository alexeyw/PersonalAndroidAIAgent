package ai.agent.android.presentation.ui.taskmonitor

import ai.agent.android.R
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.knotwork.design.screens.taskmonitor.TaskFilterKind
import app.knotwork.design.screens.taskmonitor.TaskMonitorCallbacks
import app.knotwork.design.screens.taskmonitor.TaskMonitorContent
import app.knotwork.design.screens.taskmonitor.TaskMonitorDetail
import app.knotwork.design.screens.taskmonitor.TaskMonitorDetailSheetBody
import app.knotwork.design.screens.taskmonitor.TaskMonitorRow
import app.knotwork.design.screens.taskmonitor.TaskMonitorStrings
import app.knotwork.design.screens.taskmonitor.TaskMonitorViewState
import app.knotwork.design.screens.taskmonitor.TaskMonitorVisualState
import app.knotwork.design.screens.taskmonitor.TaskRowStatus

/**
 * Slim app-side Task Monitor mapper. Subscribes to
 * [TaskMonitorViewModel.uiState] and renders [TaskMonitorContent] with an
 * optional row-detail `ModalBottomSheet`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskMonitorScreen(
    viewModel: TaskMonitorViewModel,
    modifier: Modifier = Modifier,
    onNavigateToChat: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = taskMonitorStrings()
    val viewState = remember(uiState) { uiState.toViewState() }
    val callbacks = TaskMonitorCallbacks(
        onBack = onBack,
        onFilterChanged = { viewModel.onFilterChanged(it.toAppFilter()) },
        onRowClick = viewModel::openDetails,
        onRowCancel = viewModel::onCancelTaskClicked,
        onDetailDismiss = viewModel::closeDetails,
        onDetailOpenChat = { sessionId ->
            viewModel.onOpenChatClicked(sessionId) {
                viewModel.closeDetails()
                onNavigateToChat(sessionId)
            }
        },
        onRetry = {},
    )

    TaskMonitorContent(state = viewState, modifier = modifier, strings = strings, callbacks = callbacks)

    val detail = viewState.expandedDetail
    if (detail != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = viewModel::closeDetails, sheetState = sheetState) {
            TaskMonitorDetailSheetBody(detail = detail, strings = strings, callbacks = callbacks)
        }
    }
}

internal fun TaskMonitorState.toViewState(): TaskMonitorViewState {
    val rows = tasks.map { it.toRow() }
    val visualState = when {
        isLoading -> TaskMonitorVisualState.Loading
        rows.isEmpty() -> TaskMonitorVisualState.Empty
        else -> TaskMonitorVisualState.Default
    }
    val detail = detailTaskId?.let { id ->
        tasks.firstOrNull { it.id == id }?.let { task ->
            TaskMonitorDetail(
                id = task.id,
                title = task.title,
                subtitle = task.pipelineStage,
                status = task.status.toCatalog(),
                logs = emptyList(),
            )
        }
    }
    return TaskMonitorViewState(
        visualState = visualState,
        filter = filter.toCatalog(),
        rows = rows,
        expandedDetail = detail,
    )
}

private fun TaskItem.toRow(): TaskMonitorRow = TaskMonitorRow(
    id = id,
    title = title,
    subtitle = pipelineStage,
    status = status.toCatalog(),
    progress = progress?.takeIf { it >= 0f },
    isCancellable = type == TaskType.BACKGROUND_WORK && (status == TaskStatus.RUNNING || status == TaskStatus.QUEUED),
)

private fun TaskStatus.toCatalog(): TaskRowStatus = when (this) {
    TaskStatus.RUNNING -> TaskRowStatus.Running
    TaskStatus.QUEUED -> TaskRowStatus.Queued
    TaskStatus.COMPLETED -> TaskRowStatus.Success
    TaskStatus.FAILED -> TaskRowStatus.Failed
}

private fun TaskFilterType.toCatalog(): TaskFilterKind = when (this) {
    TaskFilterType.ALL -> TaskFilterKind.All
    TaskFilterType.ACTIVE -> TaskFilterKind.Active
    TaskFilterType.BACKGROUND -> TaskFilterKind.Background
    TaskFilterType.COMPLETED -> TaskFilterKind.Completed
}

private fun TaskFilterKind.toAppFilter(): TaskFilterType = when (this) {
    TaskFilterKind.All -> TaskFilterType.ALL
    TaskFilterKind.Active -> TaskFilterType.ACTIVE
    TaskFilterKind.Background -> TaskFilterType.BACKGROUND
    TaskFilterKind.Completed -> TaskFilterType.COMPLETED
}

@Composable
private fun taskMonitorStrings(): TaskMonitorStrings = TaskMonitorStrings(
    title = stringResource(R.string.taskmonitor_screen_title),
    backCd = stringResource(R.string.common_back),
    cancelCd = stringResource(R.string.common_cancel),
    emptyTitle = stringResource(R.string.taskmonitor_empty_title),
    emptySubtitle = stringResource(R.string.taskmonitor_empty_subtitle),
    errorTitle = stringResource(R.string.taskmonitor_error_title),
    errorRetry = stringResource(R.string.common_retry),
    detailDismiss = stringResource(R.string.common_close),
    detailOpenChat = stringResource(R.string.taskmonitor_open_chat),
    detailNoLogs = stringResource(R.string.taskmonitor_detail_no_logs),
)
