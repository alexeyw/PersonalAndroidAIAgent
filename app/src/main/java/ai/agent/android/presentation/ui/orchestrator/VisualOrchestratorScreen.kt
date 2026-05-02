package ai.agent.android.presentation.ui.orchestrator

import ai.agent.android.domain.models.NodeType
import ai.agent.android.presentation.ui.components.PromptPreviewBottomSheet
import ai.agent.android.presentation.ui.components.VariableChipsRow
import ai.agent.android.presentation.ui.components.insertAtCursor
import ai.agent.android.presentation.ui.orchestrator.components.DraggableNode
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

/**
 * The main screen for the Visual Orchestrator.
 * Contains an infinite canvas to visually connect Koog agents, LiteRT models, and Tools.
 *
 * @param viewModel The ViewModel providing the orchestrator state.
 * @param onBack Callback when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualOrchestratorScreen(
    viewModel: OrchestratorViewModel = hiltViewModel(),
    onNavigateToPrompts: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var showNodeMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var connectingFromNodeId by remember { mutableStateOf<String?>(null) }
    var connectingIsOutput by remember { mutableStateOf<Boolean?>(null) }
    var connectingLabel by remember { mutableStateOf<String?>(null) }
    var configuringNodeId by remember { mutableStateOf<String?>(null) }
    var editingConnectionId by remember { mutableStateOf<String?>(null) }

    var showLoadMenu by remember { mutableStateOf(false) }
    var showPromptLibrary by remember { mutableStateOf(false) }
    
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var nodeSizes by remember { mutableStateOf(mapOf<String, IntSize>()) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            try {
                contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(viewModel.exportPipelineToJson().toByteArray())
                }
            } catch (e: Exception) {
                // Ignore or handle
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                contentResolver.openInputStream(it)?.use { stream ->
                    val json = stream.bufferedReader().readText()
                    viewModel.importPipelineFromJson(json)
                }
            } catch (e: Exception) {
                // Ignore or handle
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.currentPipeline.id, canvasSize) {
        if (uiState.nodes.isNotEmpty() && canvasSize.width > 0 && canvasSize.height > 0) {
            val minX = uiState.nodes.minOf { it.x }
            val maxX = uiState.nodes.maxOf { it.x + 200f } // Add width for node
            val minY = uiState.nodes.minOf { it.y }
            val maxY = uiState.nodes.maxOf { it.y + 100f } // Add height for node
            
            val graphWidth = maxX - minX
            val graphHeight = maxY - minY
            
            val padding = 100f
            val availableWidth = canvasSize.width - padding * 2
            val availableHeight = canvasSize.height - padding * 2
            
            if (graphWidth > 0 && graphHeight > 0) {
                val scaleX = availableWidth / graphWidth
                val scaleY = availableHeight / graphHeight
                val targetScale = minOf(scaleX, scaleY).coerceIn(0.1f, 1f)
                
                scale = targetScale
                
                val scaledGraphWidth = graphWidth * targetScale
                val scaledGraphHeight = graphHeight * targetScale
                
                val offsetX = (canvasSize.width - scaledGraphWidth) / 2f - (minX * targetScale)
                val offsetY = (canvasSize.height - scaledGraphHeight) / 2f - (minY * targetScale)
                
                panOffset = Offset(offsetX, offsetY)
            }
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

    if (editingConnectionId != null) {
        val connection = uiState.connections.find { it.id == editingConnectionId }
        if (connection != null) {
            var connLabel by remember(connection) { mutableStateOf(connection.label ?: "") }
            AlertDialog(
                onDismissRequest = { editingConnectionId = null },
                title = { Text("Connection Properties") },
                text = {
                    Column {
                        androidx.compose.material3.OutlinedTextField(
                            value = connLabel,
                            onValueChange = { connLabel = it },
                            label = { Text("Label (e.g., Intent Name)") },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateConnectionLabel(connection.id, connLabel.takeIf { it.isNotBlank() })
                        editingConnectionId = null
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            viewModel.removeConnection(connection.id)
                            editingConnectionId = null
                        }) {
                            Text("Delete Connection", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { editingConnectionId = null }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        } else {
            editingConnectionId = null
        }
    }

    if (configuringNodeId != null) {
        val node = uiState.nodes.find { it.id == configuringNodeId }
        if (node != null) {
            var systemPromptValue by remember(node) {
                mutableStateOf(
                    TextFieldValue(
                        text = node.systemPrompt ?: "",
                        selection = TextRange((node.systemPrompt ?: "").length),
                    ),
                )
            }
            var complexity by remember(node) { mutableStateOf(node.conditionComplexity?.toString() ?: "") }
            var keywords by remember(node) { mutableStateOf(node.conditionKeywords ?: "") }
            var prompt by remember(node) { mutableStateOf(node.conditionPrompt ?: "") }

            if (showPromptLibrary) {
                ai.agent.android.presentation.ui.orchestrator.components.PromptLibraryDialog(
                    prompts = uiState.promptTemplates.filter { it.category == node.type.name },
                    onPromptSelected = { text ->
                        systemPromptValue = TextFieldValue(text = text, selection = TextRange(text.length))
                        showPromptLibrary = false
                    },
                    onDismissRequest = { showPromptLibrary = false }
                )
            }

            AlertDialog(
                onDismissRequest = { configuringNodeId = null },
                title = { Text(if (node.type == NodeType.IF_CONDITION) "Configure IF Condition" else "Configure Node") },
                text = {
                    Column {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            TextButton(onClick = { showPromptLibrary = true }) {
                                Text("Load from Library")
                            }
                            TextButton(onClick = {
                                val current = systemPromptValue.text
                                if (current.isNotBlank()) {
                                    viewModel.savePromptTemplate("${node.label} Template", current, node.type.name)
                                    viewModel.clearError() // Just in case, might want a success message
                                }
                            }) {
                                Text("Save to Library")
                            }
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.OutlinedTextField(
                                value = systemPromptValue,
                                onValueChange = { systemPromptValue = it },
                                label = { Text("System Prompt") },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 8.dp),
                            )
                            IconButton(
                                onClick = { viewModel.requestPromptPreview(systemPromptValue.text) },
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "Preview prompt",
                                )
                            }
                        }
                        VariableChipsRow(
                            variables = uiState.availableVariables,
                            onChipClick = { token ->
                                systemPromptValue = systemPromptValue.insertAtCursor(token)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        )
                        if (node.type == NodeType.IF_CONDITION) {
                            androidx.compose.material3.OutlinedTextField(
                                value = complexity,
                                onValueChange = { complexity = it },
                                label = { Text("Complexity Threshold (Int)") },
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            androidx.compose.material3.OutlinedTextField(
                                value = keywords,
                                onValueChange = { keywords = it },
                                label = { Text("Keywords (comma separated)") },
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            androidx.compose.material3.OutlinedTextField(
                                value = prompt,
                                onValueChange = { prompt = it },
                                label = { Text("LLM Condition Prompt") },
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateNodeConfiguration(
                            node.id,
                            if (node.type == NodeType.IF_CONDITION) complexity.toIntOrNull() else null,
                            if (node.type == NodeType.IF_CONDITION) keywords.takeIf { it.isNotBlank() } else null,
                            if (node.type == NodeType.IF_CONDITION) prompt.takeIf { it.isNotBlank() } else null,
                            systemPromptValue.text.takeIf { it.isNotBlank() }
                        )
                        configuringNodeId = null
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { configuringNodeId = null }) {
                        Text("Cancel")
                    }
                }
            )
        } else {
            configuringNodeId = null
        }
    }

    val previewState = uiState.previewState
    if (previewState is PromptPreviewState.Ready) {
        PromptPreviewBottomSheet(
            segments = previewState.segments,
            onDismiss = { viewModel.dismissPromptPreview() },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Visual Orchestrator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToPrompts) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = "Prompt Library")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Toolbar row for actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Box {
                    Button(
                        onClick = { showNodeMenu = true },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Node", modifier = Modifier.padding(end = 4.dp))
                        Text("Add Node")
                    }
                    DropdownMenu(
                        expanded = showNodeMenu,
                        onDismissRequest = { showNodeMenu = false }
                    ) {
                        NodeType.entries.forEach { nodeType ->
                            val isProvider = nodeType == NodeType.CLOUD
                            
                            val hasKey = if (isProvider) {
                                uiState.providerKeys.values.any { it }
                            } else true
                            
                            if (!isProvider || hasKey) {
                                DropdownMenuItem(
                                    text = { Text(nodeType.name) },
                                    onClick = {
                                        val centerX = (-panOffset.x + 400f) / scale
                                        val centerY = (-panOffset.y + 400f) / scale
                                        viewModel.addNode(nodeType, centerX, centerY)
                                        showNodeMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { viewModel.applyBasePreset() },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Base Preset")
                }

                Box {
                    Button(
                        onClick = { showLoadMenu = true },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = "Load Pipeline", modifier = Modifier.padding(end = 4.dp))
                        Text("Load")
                    }
                    DropdownMenu(
                        expanded = showLoadMenu,
                        onDismissRequest = { showLoadMenu = false }
                    ) {
                        if (uiState.savedPipelines.isEmpty()) {
                            DropdownMenuItem(text = { Text("No saved pipelines") }, onClick = { showLoadMenu = false })
                        } else {
                            uiState.savedPipelines.forEach { pipeline ->
                                DropdownMenuItem(
                                    text = { Text(pipeline.name.ifBlank { "Unnamed (${pipeline.id.take(4)})" }) },
                                    onClick = {
                                        viewModel.loadPipeline(pipeline.id)
                                        showLoadMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { viewModel.saveCurrentPipeline() },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    androidx.compose.material3.BadgedBox(
                        badge = {
                            if (uiState.validationErrors.isNotEmpty()) {
                                androidx.compose.material3.Badge {
                                    Text(uiState.validationErrors.size.toString())
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Save Pipeline")
                    }
                    Text("Save")
                }

                Button(
                    onClick = { exportLauncher.launch("pipeline.json") },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Export JSON")
                }

                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Import JSON")
                }

                Button(
                    onClick = { showClearDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear Pipeline", modifier = Modifier.padding(end = 4.dp))
                    Text("Clear")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.DarkGray.copy(alpha = 0.1f))
                    .onGloballyPositioned { coordinates ->
                        canvasSize = coordinates.size
                    }
                    .pointerInput(uiState.connections, nodeSizes, scale, panOffset) {
                        detectTapGestures { offset ->
                            val logicX = (offset.x - panOffset.x) / scale
                            val logicY = (offset.y - panOffset.y) / scale
                            val clickLogicPoint = Offset(logicX, logicY)
                            val threshold = 12.dp.toPx() / scale

                            val clickedConnection = uiState.connections.find { conn ->
                                val source = uiState.nodes.find { it.id == conn.sourceNodeId }
                                val target = uiState.nodes.find { it.id == conn.targetNodeId }
                                if (source != null && target != null) {
                                    val sourceSize = nodeSizes[source.id] ?: IntSize(0, 0)
                                    val targetSize = nodeSizes[target.id] ?: IntSize(0, 0)
                                    if (sourceSize.width > 0 && targetSize.width > 0) {
                                        val portRadiusPx = 8.dp.toPx()
                                        val startX = source.x + if (source.type == NodeType.OUTPUT) sourceSize.width.toFloat() / 2f else sourceSize.width.toFloat() - portRadiusPx
                                        val startY = source.y + sourceSize.height.toFloat() / 2f
                                        val endX = target.x + if (target.type == NodeType.INPUT) targetSize.width.toFloat() / 2f else portRadiusPx
                                        val endY = target.y + targetSize.height.toFloat() / 2f
                                        isPointNearCubicBezier(
                                            clickLogicPoint,
                                            Offset(startX, startY),
                                            Offset(startX + 100f, startY),
                                            Offset(endX - 100f, endY),
                                            Offset(endX, endY),
                                            threshold
                                        )
                                    } else false
                                } else false
                            }
                            if (clickedConnection != null) {
                                editingConnectionId = clickedConnection.id
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(0.1f, 5f)
                            val p = panOffset + pan
                            panOffset = centroid - (centroid - p) * (newScale / scale)
                            scale = newScale
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = panOffset.x
                            translationY = panOffset.y
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                ) {
                    val density = LocalDensity.current
                    val portRadiusPx = with(density) { 8.dp.toPx() }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val nodes = uiState.nodes
                        val connections = uiState.connections

                        connections.forEach { conn ->
                            val source = nodes.find { it.id == conn.sourceNodeId }
                            val target = nodes.find { it.id == conn.targetNodeId }

                            if (source != null && target != null) {
                                val sourceSize = nodeSizes[source.id] ?: IntSize(0, 0)
                                val targetSize = nodeSizes[target.id] ?: IntSize(0, 0)

                                if (sourceSize.width == 0 || targetSize.width == 0) return@forEach

                                val startX = source.x + if (source.type == NodeType.OUTPUT) sourceSize.width.toFloat() / 2f else sourceSize.width.toFloat() - portRadiusPx
                                val startY = source.y + sourceSize.height.toFloat() / 2f
                                
                                val endX = target.x + if (target.type == NodeType.INPUT) targetSize.width.toFloat() / 2f else portRadiusPx
                                val endY = target.y + targetSize.height.toFloat() / 2f

                                val path = Path().apply {
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
                                
                                val arrowTipX = endX - portRadiusPx
                                val arrowPath = Path().apply {
                                    moveTo(arrowTipX, endY)
                                    lineTo(arrowTipX - 24f, endY - 14f)
                                    lineTo(arrowTipX - 24f, endY + 14f)
                                    close()
                                }
                                drawPath(
                                    path = arrowPath,
                                    color = Color.Gray,
                                    style = androidx.compose.ui.graphics.drawscope.Fill
                                )
                            }
                        }
                    }

                    // Draw labels for connections
                    uiState.connections.forEach { conn ->
                        val source = uiState.nodes.find { it.id == conn.sourceNodeId }
                        val target = uiState.nodes.find { it.id == conn.targetNodeId }
                        if (source != null && target != null && conn.label != null) {
                            val sourceSize = nodeSizes[source.id] ?: IntSize(0, 0)
                            val targetSize = nodeSizes[target.id] ?: IntSize(0, 0)

                            if (sourceSize.width > 0 && targetSize.width > 0) {
                                val startX = source.x + sourceSize.width.toFloat() - portRadiusPx
                                val startY = source.y + sourceSize.height.toFloat() / 2f
                                val endX = target.x + portRadiusPx
                                val endY = target.y + targetSize.height.toFloat() / 2f
                                
                                val midX = (startX + endX) / 2f
                                val midY = (startY + endY) / 2f

                                Text(
                                    text = conn.label,
                                    color = if (conn.label == "True") Color(0xFF4CAF50) else Color(0xFFF44336),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    modifier = Modifier
                                        .offset { IntOffset(midX.roundToInt(), midY.roundToInt() - 20) }
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                        .padding(4.dp)
                                )
                            }
                        }
                    }

                    uiState.nodes.forEach { node ->
                        DraggableNode(
                            node = node,
                            isConnecting = connectingFromNodeId == node.id,
                            connectingIsOutput = connectingIsOutput ?: true,
                            connectingLabel = connectingLabel,
                            modifier = Modifier
                                .offset { IntOffset(node.x.roundToInt(), node.y.roundToInt()) }
                                .onGloballyPositioned { coordinates ->
                                    if (nodeSizes[node.id] != coordinates.size) {
                                        nodeSizes = nodeSizes + (node.id to coordinates.size)
                                    }
                                },
                            onPositionDelta = { id, dx, dy ->
                                viewModel.moveNode(id, dx, dy)
                            },
                            onConnectClick = { isOutput, label ->
                                if (connectingFromNodeId == null) {
                                    connectingFromNodeId = node.id
                                    connectingIsOutput = isOutput
                                    connectingLabel = label
                                } else if (connectingFromNodeId != node.id && connectingIsOutput != isOutput) {
                                    val sourceId = if (connectingIsOutput == true) connectingFromNodeId!! else node.id
                                    val targetId = if (connectingIsOutput == false) connectingFromNodeId!! else node.id
                                    val connLabel = if (connectingIsOutput == true) connectingLabel else label
                                    
                                    val newConnectionId = viewModel.addConnection(sourceId, targetId, connLabel)
                                    val sourceNode = uiState.nodes.find { it.id == sourceId }
                                    if (sourceNode?.type == NodeType.INTENT_ROUTER && newConnectionId != null) {
                                        editingConnectionId = newConnectionId
                                    }
                                    
                                    connectingFromNodeId = null
                                    connectingIsOutput = null
                                    connectingLabel = null
                                } else {
                                    connectingFromNodeId = null
                                    connectingIsOutput = null
                                    connectingLabel = null
                                }
                            },
                            onDeleteClick = {
                                if (connectingFromNodeId == node.id) {
                                    connectingFromNodeId = null
                                    connectingIsOutput = null
                                    connectingLabel = null
                                }
                                viewModel.removeNode(node.id)
                            },
                            onConfigureClick = {
                                configuringNodeId = node.id
                            },
                            availableTools = uiState.availableTools,
                            onToolSelected = viewModel::updateNodeTool,
                            onCloudProviderSelected = viewModel::updateNodeCloudProvider
                        )
                    }
                }
            }
        }
    }
}

private fun isPointNearCubicBezier(
    point: Offset,
    p0: Offset,
    p1: Offset,
    p2: Offset,
    p3: Offset,
    threshold: Float
): Boolean {
    val steps = 20
    var prev = p0
    for (i in 1..steps) {
        val t = i / steps.toFloat()
        val u = 1 - t
        val current = Offset(
            u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x,
            u * u * u * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y
        )
        if (distanceFromPointToLineSegment(point, prev, current) <= threshold) {
            return true
        }
        prev = current
    }
    return false
}

private fun distanceFromPointToLineSegment(p: Offset, v: Offset, w: Offset): Float {
    val l2 = (v.x - w.x) * (v.x - w.x) + (v.y - w.y) * (v.y - w.y)
    if (l2 == 0f) return (p - v).getDistance()
    var t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2
    t = Math.max(0f, Math.min(1f, t))
    val projection = Offset(v.x + t * (w.x - v.x), v.y + t * (w.y - v.y))
    return (p - projection).getDistance()
}
