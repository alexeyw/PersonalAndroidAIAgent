package ai.agent.android.presentation.ui.orchestrator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.agent.android.domain.models.NodeType
import ai.agent.android.presentation.ui.orchestrator.components.DraggableNode

/**
 * The main screen for the Visual Orchestrator.
 * Contains an infinite canvas to visually connect Koog agents, LiteRT models, and Tools.
 */
@Composable
fun VisualOrchestratorScreen(
    viewModel: OrchestratorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var showNodeMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var connectingFromNodeId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Pipeline") },
            text = { Text("Are you sure you want to clear the entire pipeline? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearPipeline()
                    showClearDialog = false
                    panOffset = Offset.Zero
                    scale = 1f
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(modifier = Modifier
                .padding(8.dp)
                .horizontalScroll(rememberScrollState())
            ) {
                Box {
                    Button(onClick = { showNodeMenu = true }) {
                        Text("Add Node")
                    }
                    DropdownMenu(
                        expanded = showNodeMenu,
                        onDismissRequest = { showNodeMenu = false }
                    ) {
                        NodeType.entries.forEach { nodeType ->
                            DropdownMenuItem(
                                text = { Text(nodeType.name) },
                                onClick = {
                                    // Calculate center of screen relative to pan/zoom to place new node
                                    val centerX = (-panOffset.x + 300f) / scale
                                    val centerY = (-panOffset.y + 300f) / scale
                                    viewModel.addNode(nodeType, centerX, centerY)
                                    showNodeMenu = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.padding(start = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Graph")
                }
                
                Button(onClick = { viewModel.saveCurrentPipeline() }, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Save Pipeline")
                }
                
                if (connectingFromNodeId != null) {
                    Text(
                        text = "Select target node to connect...",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.DarkGray.copy(alpha = 0.1f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.1f, 5f)
                        panOffset += pan
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val nodes = uiState.nodes
                val connections = uiState.connections

                connections.forEach { conn ->
                    val source = nodes.find { it.id == conn.sourceNodeId }
                    val target = nodes.find { it.id == conn.targetNodeId }

                    if (source != null && target != null) {
                        val path = Path().apply {
                            val startX = source.x * scale + panOffset.x + 200f // Adjusted for node width
                            val startY = source.y * scale + panOffset.y + 80f  // Adjusted for node height
                            val endX = target.x * scale + panOffset.x
                            val endY = target.y * scale + panOffset.y + 80f

                            moveTo(startX, startY)
                            cubicTo(
                                startX + 100f, startY,
                                endX - 100f, endY,
                                endX, endY
                            )
                        }
                        drawPath(
                            path = path,
                            color = Color.Gray,
                            style = Stroke(width = 4.dp.toPx() * scale) // Scale stroke width too
                        )
                    }
                }
            }

            uiState.nodes.forEach { node ->
                DraggableNode(
                    node = node,
                    scale = scale,
                    panOffset = panOffset,
                    isConnecting = connectingFromNodeId == node.id,
                    onPositionChanged = { id, x, y ->
                        viewModel.updateNodePosition(id, x, y)
                    },
                    onConnectClick = {
                        if (connectingFromNodeId == null) {
                            connectingFromNodeId = node.id
                        } else if (connectingFromNodeId != node.id) {
                            viewModel.addConnection(connectingFromNodeId!!, node.id)
                            connectingFromNodeId = null
                        } else {
                            connectingFromNodeId = null // Cancel if clicked again
                        }
                    },
                    onDeleteClick = {
                        if (connectingFromNodeId == node.id) connectingFromNodeId = null
                        viewModel.removeNode(node.id)
                    }
                )
            }
        }
    }
}
