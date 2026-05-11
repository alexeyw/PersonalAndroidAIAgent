package ai.agent.android.presentation.ui.orchestrator

import ai.agent.android.R
import ai.agent.android.domain.constants.SettingsDefaults
import ai.agent.android.domain.constants.TimeAndIdConstants
import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.usesContextConfig
import ai.agent.android.presentation.ui.common.asString
import ai.agent.android.presentation.ui.components.PromptPreviewBottomSheet
import ai.agent.android.presentation.ui.components.VariableChipsRow
import ai.agent.android.presentation.ui.components.insertAtCursor
import ai.agent.android.presentation.ui.orchestrator.components.DraggableNode
import ai.agent.android.presentation.ui.orchestrator.components.NodeContextConfigSection
import ai.agent.android.presentation.ui.orchestrator.components.PromptLibraryDialog
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    onBack: () -> Unit = {},
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

    var showPromptLibrary by remember { mutableStateOf(false) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var nodeSizes by remember { mutableStateOf(mapOf<String, IntSize>()) }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
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

    val errorText = uiState.errorMessage?.asString()
    LaunchedEffect(errorText) {
        errorText?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.currentPipeline.id, canvasSize) {
        if (uiState.nodes.isNotEmpty() && canvasSize.width > 0 && canvasSize.height > 0) {
            val minX = uiState.nodes.minOf { it.x }
            val maxX = uiState.nodes.maxOf { it.x + NODE_WIDTH_ESTIMATE_PX }
            val minY = uiState.nodes.minOf { it.y }
            val maxY = uiState.nodes.maxOf { it.y + NODE_HEIGHT_ESTIMATE_PX }

            val graphWidth = maxX - minX
            val graphHeight = maxY - minY

            val padding = FIT_VIEW_PADDING_PX
            val availableWidth = canvasSize.width - padding * 2
            val availableHeight = canvasSize.height - padding * 2

            if (graphWidth > 0 && graphHeight > 0) {
                val scaleX = availableWidth / graphWidth
                val scaleY = availableHeight / graphHeight
                val targetScale = minOf(scaleX, scaleY).coerceIn(MIN_CANVAS_SCALE, 1f)

                scale = targetScale

                val scaledGraphWidth = graphWidth * targetScale
                val scaledGraphHeight = graphHeight * targetScale

                val offsetX = (canvasSize.width - scaledGraphWidth) / 2f - (minX * targetScale)
                val offsetY = (canvasSize.height - scaledGraphHeight) / 2f - (minY * targetScale)

                panOffset = Offset(offsetX, offsetY)
            }
        }
    }

    uiState.pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingImport() },
            title = { Text(stringResource(R.string.orchestrator_dialog_schema_mismatch_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.orchestrator_dialog_schema_mismatch_text,
                    ) + " (found schemaVersion=${pending.foundVersion}, expected ${pending.expectedVersion})",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPendingImport() }) {
                    Text(stringResource(R.string.orchestrator_dialog_import_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPendingImport() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.orchestrator_dialog_clear_title)) },
            text = { Text(stringResource(R.string.orchestrator_dialog_clear_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearPipeline()
                    showClearDialog = false
                    panOffset = Offset.Zero
                    scale = 1f
                }) {
                    Text(stringResource(R.string.common_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (editingConnectionId != null) {
        val connection = uiState.connections.find { it.id == editingConnectionId }
        if (connection != null) {
            var connLabel by remember(connection) { mutableStateOf(connection.label ?: "") }
            AlertDialog(
                onDismissRequest = { editingConnectionId = null },
                title = { Text(stringResource(R.string.orchestrator_dialog_connection_props_title)) },
                text = {
                    Column {
                        androidx.compose.material3.OutlinedTextField(
                            value = connLabel,
                            onValueChange = { connLabel = it },
                            label = { Text(stringResource(R.string.orchestrator_dialog_connection_label)) },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateConnectionLabel(connection.id, connLabel.takeIf { it.isNotBlank() })
                        editingConnectionId = null
                    }) {
                        Text(stringResource(R.string.common_save))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            viewModel.removeConnection(connection.id)
                            editingConnectionId = null
                        }) {
                            Text(
                                stringResource(R.string.orchestrator_dialog_connection_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(onClick = { editingConnectionId = null }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                },
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
            // Clarification: timeout is edited as seconds for ergonomics; we round-trip
            // the configured millis (or the engine default 60_000) into the field.
            var clarificationTimeoutSeconds by remember(node) {
                val timeoutMs = node.clarificationTimeoutMs ?: SettingsDefaults.CLARIFICATION_TIMEOUT_MS_DEFAULT
                mutableStateOf((timeoutMs / TimeAndIdConstants.MS_PER_SECOND).toString())
            }
            // Per-node context flags edited as local state; the "nodeInput"
            // flag is intentionally not exposed — `OrchestratorViewModel`
            // forces it to true when persisting, and the UI shows it as a
            // locked-on row inside `NodeContextConfigSection`.
            var ctxOriginalTask by remember(node) { mutableStateOf(node.contextConfig.originalTask) }
            var ctxChatHistory by remember(node) { mutableStateOf(node.contextConfig.chatHistory) }
            var ctxLongTermMemory by remember(node) { mutableStateOf(node.contextConfig.longTermMemory) }
            var ctxToolResults by remember(node) { mutableStateOf(node.contextConfig.toolResults) }

            // The "Input Data" section is only meaningful for nodes whose
            // executor is fed by `NodeContextBuilder`. Whether OUTPUT
            // qualifies depends on its system prompt — which the user is
            // editing right now — so we re-derive the visibility on every
            // recomposition from the in-flight `systemPromptValue` rather
            // than the persisted node. Hoisted out of the dialog body so the
            // confirm-button lambda can read the same value.
            val showContextSection = node.copy(
                systemPrompt = systemPromptValue.text.takeIf { it.isNotBlank() },
            ).usesContextConfig()

            if (showPromptLibrary) {
                PromptLibraryDialog(
                    prompts = uiState.promptTemplates.filter { it.category == node.type.name },
                    onPromptSelected = { text ->
                        systemPromptValue = TextFieldValue(text = text, selection = TextRange(text.length))
                        showPromptLibrary = false
                    },
                    onDismissRequest = { showPromptLibrary = false },
                )
            }

            AlertDialog(
                onDismissRequest = { configuringNodeId = null },
                title = {
                    Text(
                        stringResource(
                            when (node.type) {
                                NodeType.IF_CONDITION -> R.string.orchestrator_dialog_configure_if
                                NodeType.CLARIFICATION -> R.string.orchestrator_dialog_configure_clarification
                                else -> R.string.orchestrator_dialog_configure_node
                            },
                        ),
                    )
                },
                text = {
                    // The dialog body can grow with several optional sections (variable chips,
                    // IF_CONDITION fields, CLARIFICATION timeout). Wrap in verticalScroll so the
                    // tail of the column stays reachable instead of being clipped behind the
                    // confirm/dismiss buttons.
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            TextButton(onClick = { showPromptLibrary = true }) {
                                Text(stringResource(R.string.common_load))
                            }
                            TextButton(onClick = {
                                val current = systemPromptValue.text
                                if (current.isNotBlank()) {
                                    viewModel.savePromptTemplate("${node.label} Template", current, node.type.name)
                                    viewModel.clearError() // Just in case, might want a success message
                                }
                            }) {
                                Text(stringResource(R.string.common_save))
                            }
                            TextButton(onClick = { viewModel.requestPromptPreview(systemPromptValue.text) }) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                Text(stringResource(R.string.orchestrator_dialog_preview))
                            }
                        }
                        androidx.compose.material3.OutlinedTextField(
                            value = systemPromptValue,
                            onValueChange = { systemPromptValue = it },
                            label = {
                                Text(
                                    stringResource(
                                        if (node.type == NodeType.CLARIFICATION) {
                                            R.string.orchestrator_dialog_clarification_instruction
                                        } else {
                                            R.string.orchestrator_dialog_system_prompt
                                        },
                                    ),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
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
                                label = { Text(stringResource(R.string.orchestrator_dialog_complexity_threshold)) },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            androidx.compose.material3.OutlinedTextField(
                                value = keywords,
                                onValueChange = { keywords = it },
                                label = { Text(stringResource(R.string.orchestrator_dialog_keywords)) },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            androidx.compose.material3.OutlinedTextField(
                                value = prompt,
                                onValueChange = { prompt = it },
                                label = { Text(stringResource(R.string.orchestrator_dialog_llm_condition_prompt)) },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (node.type == NodeType.CLARIFICATION) {
                            androidx.compose.material3.OutlinedTextField(
                                value = clarificationTimeoutSeconds,
                                onValueChange = { new ->
                                    clarificationTimeoutSeconds = new.filter { it.isDigit() }
                                },
                                label = { Text(stringResource(R.string.orchestrator_dialog_reply_timeout)) },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (showContextSection) {
                            NodeContextConfigSection(
                                originalTask = ctxOriginalTask,
                                chatHistory = ctxChatHistory,
                                longTermMemory = ctxLongTermMemory,
                                toolResults = ctxToolResults,
                                onOriginalTaskChange = { ctxOriginalTask = it },
                                onChatHistoryChange = { ctxChatHistory = it },
                                onLongTermMemoryChange = { ctxLongTermMemory = it },
                                onToolResultsChange = { ctxToolResults = it },
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
                            systemPromptValue.text.takeIf { it.isNotBlank() },
                        )
                        if (node.type == NodeType.CLARIFICATION) {
                            val timeoutMs = clarificationTimeoutSeconds
                                .toLongOrNull()
                                ?.takeIf { it > 0L }
                                ?.let { it * TimeAndIdConstants.MS_PER_SECOND }
                            viewModel.updateNodeClarificationTimeout(node.id, timeoutMs)
                        }
                        // Persist the context flags. `nodeInput` is sent as
                        // `false` even though the UI shows it locked-on: the
                        // ViewModel uses `config.isEmpty()` to surface the
                        // "at least one source" snackbar, and forcing
                        // `nodeInput = true` here would mask that signal when
                        // the user toggled every other flag off. The
                        // ViewModel re-applies `nodeInput = true` as part of
                        // its sanitisation, so the persisted config remains
                        // safe.
                        if (showContextSection) {
                            viewModel.updateNodeContextConfig(
                                node.id,
                                NodeContextConfig(
                                    chatHistory = ctxChatHistory,
                                    originalTask = ctxOriginalTask,
                                    nodeInput = false,
                                    longTermMemory = ctxLongTermMemory,
                                    toolResults = ctxToolResults,
                                ),
                            )
                        }
                        configuringNodeId = null
                    }) {
                        Text(stringResource(R.string.common_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { configuringNodeId = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        } else {
            configuringNodeId = null
        }
    }

    val previewState = uiState.previewState
    if (previewState !is PromptPreviewState.Hidden) {
        PromptPreviewBottomSheet(
            segments = (previewState as? PromptPreviewState.Ready)?.segments,
            onDismiss = { viewModel.dismissPromptPreview() },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.orchestrator_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToPrompts) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.orchestrator_prompt_library_cd),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Toolbar row for actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Box {
                    Button(
                        onClick = { showNodeMenu = true },
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.orchestrator_add_node_cd),
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text(stringResource(R.string.orchestrator_add_node))
                    }
                    DropdownMenu(
                        expanded = showNodeMenu,
                        onDismissRequest = { showNodeMenu = false },
                    ) {
                        NodeType.entries.forEach { nodeType ->
                            val isProvider = nodeType == NodeType.CLOUD

                            val hasKey = if (isProvider) {
                                uiState.providerKeys.values.any { it }
                            } else {
                                true
                            }

                            if (!isProvider || hasKey) {
                                DropdownMenuItem(
                                    text = { Text(nodeType.name) },
                                    onClick = {
                                        val centerX = (-panOffset.x + 400f) / scale
                                        val centerY = (-panOffset.y + 400f) / scale
                                        viewModel.addNode(nodeType, centerX, centerY)
                                        showNodeMenu = false
                                    },
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { viewModel.applyBasePreset() },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(stringResource(R.string.orchestrator_base_preset))
                }

                Button(
                    onClick = { viewModel.saveCurrentPipeline() },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    androidx.compose.material3.BadgedBox(
                        badge = {
                            if (uiState.validationErrors.isNotEmpty()) {
                                androidx.compose.material3.Badge {
                                    Text(uiState.validationErrors.size.toString())
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.orchestrator_save_pipeline_cd),
                        )
                    }
                    Text(stringResource(R.string.orchestrator_save_pipeline))
                }

                Button(
                    onClick = { exportLauncher.launch("pipeline.json") },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(stringResource(R.string.orchestrator_export_json))
                }

                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(stringResource(R.string.orchestrator_import_json))
                }

                Button(
                    onClick = { showClearDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.orchestrator_clear_cd),
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(stringResource(R.string.orchestrator_clear))
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
                                        val startX =
                                            source.x +
                                                if (source.type ==
                                                    NodeType.OUTPUT
                                                ) {
                                                    sourceSize.width.toFloat() / 2f
                                                } else {
                                                    sourceSize.width.toFloat() -
                                                        portRadiusPx
                                                }
                                        val startY = source.y + sourceSize.height.toFloat() / 2f
                                        val endX =
                                            target.x +
                                                if (target.type ==
                                                    NodeType.INPUT
                                                ) {
                                                    targetSize.width.toFloat() / 2f
                                                } else {
                                                    portRadiusPx
                                                }
                                        val endY = target.y + targetSize.height.toFloat() / 2f
                                        isPointNearCubicBezier(
                                            clickLogicPoint,
                                            Offset(startX, startY),
                                            Offset(startX + BEZIER_CONTROL_X_OFFSET_PX, startY),
                                            Offset(endX - BEZIER_CONTROL_X_OFFSET_PX, endY),
                                            Offset(endX, endY),
                                            threshold,
                                        )
                                    } else {
                                        false
                                    }
                                } else {
                                    false
                                }
                            }
                            if (clickedConnection != null) {
                                editingConnectionId = clickedConnection.id
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE)
                            val p = panOffset + pan
                            panOffset = centroid - (centroid - p) * (newScale / scale)
                            scale = newScale
                        }
                    },
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
                        },
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

                                val startX =
                                    source.x +
                                        if (source.type ==
                                            NodeType.OUTPUT
                                        ) {
                                            sourceSize.width.toFloat() / 2f
                                        } else {
                                            sourceSize.width.toFloat() -
                                                portRadiusPx
                                        }
                                val startY = source.y + sourceSize.height.toFloat() / 2f

                                val endX =
                                    target.x +
                                        if (target.type ==
                                            NodeType.INPUT
                                        ) {
                                            targetSize.width.toFloat() / 2f
                                        } else {
                                            portRadiusPx
                                        }
                                val endY = target.y + targetSize.height.toFloat() / 2f

                                val path = Path().apply {
                                    moveTo(startX, startY)
                                    cubicTo(
                                        startX + BEZIER_CONTROL_X_OFFSET_PX,
                                        startY,
                                        endX - BEZIER_CONTROL_X_OFFSET_PX,
                                        endY,
                                        endX,
                                        endY,
                                    )
                                }
                                drawPath(
                                    path = path,
                                    color = Color.Gray,
                                    style = Stroke(width = 4.dp.toPx()),
                                )

                                val arrowTipX = endX - portRadiusPx
                                val arrowPath = Path().apply {
                                    moveTo(arrowTipX, endY)
                                    lineTo(arrowTipX - ARROW_TIP_OFFSET_X_PX, endY - ARROW_TIP_OFFSET_Y_PX)
                                    lineTo(arrowTipX - ARROW_TIP_OFFSET_X_PX, endY + ARROW_TIP_OFFSET_Y_PX)
                                    close()
                                }
                                drawPath(
                                    path = arrowPath,
                                    color = Color.Gray,
                                    style = androidx.compose.ui.graphics.drawscope.Fill,
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
                                    color = if (conn.label == "True") {
                                        colorResource(R.color.pipeline_connection_true_label)
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                midX.roundToInt(),
                                                midY.roundToInt() - CONNECTION_LABEL_OFFSET_Y_PX,
                                            )
                                        }
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                        .padding(4.dp),
                                )
                            }
                        }
                    }

                    uiState.nodes.forEach { node ->
                        DraggableNode(
                            node = node,
                            isConnecting = connectingFromNodeId == node.id,
                            connectingIsOutput = connectingIsOutput ?: true,
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
                            onCloudProviderSelected = viewModel::updateNodeCloudProvider,
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
    threshold: Float,
): Boolean {
    val steps = BEZIER_HIT_SAMPLES
    var prev = p0
    for (i in 1..steps) {
        val t = i / steps.toFloat()
        val u = 1 - t
        val current = Offset(
            u * u * u * p0.x +
                CUBIC_BEZIER_TERM_COEFFICIENT * u * u * t * p1.x +
                CUBIC_BEZIER_TERM_COEFFICIENT * u * t * t * p2.x +
                t * t * t * p3.x,
            u * u * u * p0.y +
                CUBIC_BEZIER_TERM_COEFFICIENT * u * u * t * p1.y +
                CUBIC_BEZIER_TERM_COEFFICIENT * u * t * t * p2.y +
                t * t * t * p3.y,
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

// region Canvas geometry constants
// All values are in raw canvas pixels (`Float`) or raw `Int` pixel offsets —
// they live in the same coordinate space as the `Offset`/`IntOffset` math
// performed inside `Canvas` and `Modifier.offset { }` blocks, where Compose's
// `Dp` abstraction does not apply. Centralised here so the layout maths is
// auditable in one place.

/** Approximate width of a draggable node card, used when fitting the graph into the canvas viewport. */
private const val NODE_WIDTH_ESTIMATE_PX: Float = 200f

/** Approximate height of a draggable node card, used when fitting the graph into the canvas viewport. */
private const val NODE_HEIGHT_ESTIMATE_PX: Float = 100f

/** Padding (each side) preserved around the fitted graph so node cards never touch the canvas edge. */
private const val FIT_VIEW_PADDING_PX: Float = 100f

/** Minimum scale factor allowed when the user pinch-zooms or auto-fits the canvas. */
private const val MIN_CANVAS_SCALE: Float = 0.1f

/** Maximum scale factor allowed when the user pinch-zooms the canvas. */
private const val MAX_CANVAS_SCALE: Float = 5f

/** Horizontal offset of the cubic Bezier control points from each connection endpoint. */
private const val BEZIER_CONTROL_X_OFFSET_PX: Float = 100f

/** Horizontal distance from the arrow tip to the base of the arrowhead. */
private const val ARROW_TIP_OFFSET_X_PX: Float = 24f

/** Vertical half-height of the arrowhead. */
private const val ARROW_TIP_OFFSET_Y_PX: Float = 14f

/** Number of points sampled along a connection's Bezier curve when testing for a tap-hit. */
private const val BEZIER_HIT_SAMPLES: Int = 20

/** Constant 3 used as the coefficient of the middle terms of the cubic Bezier polynomial. */
private const val CUBIC_BEZIER_TERM_COEFFICIENT: Int = 3

/** Vertical offset, in canvas pixels, applied so the connection label sits clear of the bezier curve. */
private const val CONNECTION_LABEL_OFFSET_Y_PX: Int = 20

// endregion
