package ai.agent.android.presentation.ui.taskmonitor

import ai.agent.android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Screen displaying the list of active chat sessions and WorkManager background tasks.
 *
 * @param viewModel The ViewModel providing the task monitor state.
 * @param modifier The modifier for this composable.
 * @param onNavigateToChat Callback to navigate to a specific chat session.
 * @param onBack Callback when the back button is pressed.
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.taskmonitor_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Filters
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(TaskFilterType.entries.toTypedArray()) { filterType ->
                    FilterChip(
                        selected = uiState.filter == filterType,
                        onClick = { viewModel.onFilterChanged(filterType) },
                        label = { Text(stringResource(filterType.displayNameRes)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.taskmonitor_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onCancel = { viewModel.onCancelTaskClicked(it) },
                            onNavigateToChat = { sessionId ->
                                viewModel.onOpenChatClicked(sessionId) {
                                    onNavigateToChat(sessionId)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Displays a single task or session in a card format.
 *
 * @param task The task data model to display.
 * @param onCancel Callback invoked when the cancel button is clicked.
 * @param onNavigateToChat Callback invoked when the 'Open Chat' button is clicked.
 */
@Composable
fun TaskCard(task: TaskItem, onCancel: (String) -> Unit, onNavigateToChat: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (task.type == TaskType.SESSION) Icons.Default.Info else Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.taskmonitor_status, task.status.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = when (task.status) {
                            TaskStatus.RUNNING -> Color(0xFF4CAF50) // Green
                            TaskStatus.FAILED -> MaterialTheme.colorScheme.error
                            TaskStatus.QUEUED -> MaterialTheme.colorScheme.tertiary
                            TaskStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (task.pipelineStage != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.taskmonitor_stage, task.pipelineStage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            // Progress indicator if running and progress is available
            if (task.status == TaskStatus.RUNNING && task.progress != null) {
                Spacer(modifier = Modifier.height(12.dp))
                if (task.progress < 0f) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Action Buttons
            if (task.type == TaskType.SESSION || task.status in listOf(TaskStatus.QUEUED, TaskStatus.RUNNING)) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (task.type == TaskType.SESSION) {
                        Button(onClick = { onNavigateToChat(task.id) }) {
                            Text(stringResource(R.string.taskmonitor_open_chat))
                        }
                    } else if (task.type == TaskType.BACKGROUND_WORK &&
                        task.status in listOf(TaskStatus.QUEUED, TaskStatus.RUNNING)
                    ) {
                        Button(
                            onClick = { onCancel(task.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                }
            }
        }
    }
}
