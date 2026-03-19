package ai.agent.android.presentation.ui.orchestrator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.agent.android.domain.models.NodeType
import ai.agent.android.presentation.ui.orchestrator.components.DraggableNode
import kotlin.math.roundToInt

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
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(modifier = Modifier.padding(8.dp)) {
                Button(onClick = { viewModel.addNode(NodeType.LITE_RT, 100f, 100f) }) {
                    Text("Add Local")
                }
                Button(onClick = { viewModel.addNode(NodeType.DEEPSEEK, 150f, 150f) }, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Add DeepSeek")
                }
                Button(onClick = { viewModel.saveCurrentPipeline() }, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Save")
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
                        scale = (scale * zoom).coerceIn(0.5f, 3f)
                        offset += pan
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
                            val startX = source.x * scale + offset.x + 150f // approx center offset
                            val startY = source.y * scale + offset.y + 50f
                            val endX = target.x * scale + offset.x - 50f
                            val endY = target.y * scale + offset.y + 50f

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
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                }
            }

            uiState.nodes.forEach { node ->
                DraggableNode(
                    node = node,
                    scale = scale,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (node.x * scale + offset.x).roundToInt(),
                                (node.y * scale + offset.y).roundToInt()
                            )
                        },
                    onPositionChanged = { id, x, y ->
                        viewModel.updateNodePosition(id, x, y)
                    }
                )
            }
        }
    }
}
