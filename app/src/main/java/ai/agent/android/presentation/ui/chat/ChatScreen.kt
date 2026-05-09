package ai.agent.android.presentation.ui.chat

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The main Chat screen composable.
 *
 * @param viewModel The [ChatViewModel] that manages the UI state.
 * @param onBack Callback when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val context = LocalContext.current

    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var menuExpanded by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val json = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (json.isNullOrBlank()) {
                    snackbarHostState.showSnackbar("Could not read selected file")
                } else {
                    viewModel.importChat(json)
                    drawerState.close()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportEvents.collect { payload ->
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, payload.sessionName)
                putExtra(Intent.EXTRA_TEXT, payload.json)
            }
            val chooser = Intent.createChooser(sendIntent, "Export chat").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.setChatVisible(true)
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.setChatVisible(false)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setChatVisible(false)
        }
    }

    // Show error message if present
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // One-shot Snackbar for the deleted-pipeline fallback (Phase 17.2): the
    // chat was silently rebound to the default pipeline because the bound
    // pipeline disappeared from the library.
    LaunchedEffect(uiState.pipelineFallbackMessage) {
        uiState.pipelineFallbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearPipelineFallback()
        }
    }

    // Auto-scroll to the bottom when new messages arrive or generation starts
    LaunchedEffect(uiState.messages.size, uiState.isGenerating, uiState.clarificationCards.size) {
        if (uiState.messages.isNotEmpty() || uiState.isGenerating || uiState.clarificationCards.isNotEmpty()) {
            val targetIndex =
                uiState.messages.size + uiState.clarificationCards.size + if (uiState.isGenerating) 2 else 1
            if (targetIndex > 0) {
                coroutineScope.launch {
                    listState.scrollToItem(targetIndex)
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ChatDrawerContent(
                    sessions = uiState.sessions,
                    currentSessionId = uiState.currentSessionId,
                    onNewChat = {
                        viewModel.requestNewSession()
                        coroutineScope.launch { drawerState.close() }
                    },
                    onImportChat = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    onSessionSelected = { sessionId ->
                        viewModel.switchSession(sessionId)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onDeleteSession = { sessionId ->
                        viewModel.deleteSession(sessionId)
                    },
                    onRenameSession = { sessionId, newName ->
                        viewModel.renameSession(sessionId, newName)
                    },
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                val currentSession = uiState.sessions.find { it.id == uiState.currentSessionId }
                val title = currentSession?.name ?: "Agent Chat"
                
                TopAppBar(
                    title = {
                        Column {
                            Text(title)
                            uiState.currentPipelineName?.let { pipelineName ->
                                Text(
                                    text = "Pipeline: $pipelineName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                )
                            }
                            if (uiState.maxContextSize > 0) {
                                val contextPercentage = (uiState.contextSize.toFloat() / uiState.maxContextSize.toFloat()).coerceIn(0f, 1f)
                                val color = when {
                                    contextPercentage > 0.9f -> MaterialTheme.colorScheme.error
                                    contextPercentage > 0.7f -> androidx.compose.ui.graphics.Color(0xFFFFA000) // Orange
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                }
                                Text(
                                    text = "Context: ${uiState.contextSize} / ${uiState.maxContextSize}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu"
                            )
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Chat settings") },
                                    onClick = {
                                        menuExpanded = false
                                        if (uiState.currentSessionId.isNotBlank()) {
                                            viewModel.openChatSettings()
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export chat") },
                                    onClick = {
                                        menuExpanded = false
                                        val sessionId = uiState.currentSessionId
                                        if (sessionId.isNotBlank()) {
                                            viewModel.exportChat(sessionId)
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Upload, contentDescription = null)
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    itemsIndexed(uiState.messages) { index, message ->
                        if (uiState.messages.lastIndex == index) {
                            ChatMessageItem(message = message, isGenerating = uiState.isGenerating)
                        } else {
                            ChatMessageItem(message = message)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (uiState.pipelineTrace.isNotEmpty()) {
                        item {
                            PipelineTraceCard(steps = uiState.pipelineTrace)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (uiState.currentStep != null && uiState.isGenerating) {
                        item {
                            val step = uiState.currentStep!!
                            Text(
                                text = "Step ${step.stepIndex} of ${step.totalSteps ?: "?"}: ${step.nodeName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    items(uiState.clarificationCards, key = { it.id }) { card ->
                        ClarificationCard(
                            model = card,
                            onAnswer = { answer -> viewModel.submitClarification(card.id, answer) },
                            onTimeout = { defaultAnswer ->
                                viewModel.markClarificationTimedOut(card.id, defaultAnswer)
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    val hasPendingClarification = uiState.clarificationCards.any {
                        it.status == ClarificationCardUiModel.Status.PENDING
                    }
                    if (uiState.orchestratorState != null && uiState.isGenerating && !hasPendingClarification) {
                        item {
                            AgentThoughtIndicator(
                                state = uiState.orchestratorState!!,
                                onApprove = { viewModel.resumeWithApproval(true) },
                                onDeny = { viewModel.resumeWithApproval(false) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                uiState.inlineError?.let { inlineError ->
                    InlineErrorBanner(
                        text = inlineError,
                        onDismiss = { viewModel.clearInlineError() },
                    )
                }

                ChatInputBar(
                    inputText = inputText,
                    onInputTextChanged = { newValue ->
                        if (newValue.text != inputText.text) {
                            viewModel.clearInlineError()
                        }
                        inputText = newValue
                    },
                    onSendClicked = {
                        if (inputText.text.isNotBlank()) {
                            viewModel.sendMessage(inputText.text)
                            if (viewModel.uiState.value.inlineError == null) {
                                inputText = TextFieldValue("")
                            }
                        }
                    },
                    onStopClicked = { viewModel.stopGeneration() },
                    isGenerating = uiState.isGenerating
                )
            }
        }
    }

    uiState.newChatPipelinePrompt?.let { prompt ->
        NewChatPipelineSheet(
            pipelines = uiState.availablePipelines,
            initialSelection = prompt.preselectedPipelineId,
            onDismiss = { viewModel.dismissNewChatPrompt() },
            onConfirm = { selected -> viewModel.confirmNewSession(selected) },
        )
    }

    uiState.chatSettingsDialog?.let { dialog ->
        ChatSettingsDialog(
            pipelines = uiState.availablePipelines,
            selectedPipelineId = dialog.selectedPipelineId,
            onSelectPipeline = { viewModel.updateChatSettingsSelection(it) },
            onDismiss = { viewModel.dismissChatSettings() },
            onConfirm = { viewModel.confirmChatSettings() },
        )
    }

    if (uiState.pipelineSwitchConfirm != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPipelineSwitchConfirm() },
            title = { Text("Generation in progress") },
            text = {
                Text("A response is being generated. Cancel it and switch the pipeline?")
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmPipelineSwitchCancelGeneration() }) {
                    Text("Cancel and switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPipelineSwitchConfirm() }) {
                    Text("Wait")
                }
            },
        )
    }
}

/**
 * Modal bottom sheet shown when the user creates a new chat. Lets the user
 * pick which pipeline to bind to the chat (or "Use default" to leave it
 * unbound). The list of pipelines comes from
 * [ChatUiState.availablePipelines]; the application-wide default (first
 * entry) is the implicit choice when [initialSelection] is `null`.
 *
 * @param pipelines Lightweight projections of every pipeline available in
 *   the library.
 * @param initialSelection Pipeline id pre-selected when the sheet opens, or
 *   `null` to start with "Use default" highlighted.
 * @param onDismiss Invoked when the user dismisses the sheet without
 *   confirming.
 * @param onConfirm Invoked with the chosen pipeline id (or `null` for the
 *   "Use default" option) when the user taps "Create".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatPipelineSheet(
    pipelines: List<PipelineSummary>,
    initialSelection: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var selected by remember(initialSelection) { mutableStateOf(initialSelection) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Choose pipeline for the new chat",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    PipelineChoiceRow(
                        title = "Use default pipeline",
                        selected = selected == null,
                        onClick = { selected = null },
                    )
                }
                items(pipelines, key = { it.id }) { pipeline ->
                    PipelineChoiceRow(
                        title = pipeline.name,
                        selected = selected == pipeline.id,
                        onClick = { selected = pipeline.id },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onConfirm(selected) }) { Text("Create") }
            }
        }
    }
}

/**
 * Alert dialog opened from the TopAppBar `⋮` menu that lets the user rebind
 * the active chat to a different pipeline (or to the default).
 *
 * @param pipelines Lightweight projections of every pipeline available in
 *   the library.
 * @param selectedPipelineId Currently highlighted pipeline id (`null` for
 *   "Use default").
 * @param onSelectPipeline Invoked when the user picks a different pipeline
 *   inside the dialog (does not commit anything yet).
 * @param onDismiss Invoked when the user dismisses the dialog without
 *   confirming.
 * @param onConfirm Invoked when the user taps "Save" — the ViewModel
 *   decides whether to apply the change immediately or raise a
 *   pipeline-switch confirmation if a generation is in flight.
 */
@Composable
private fun ChatSettingsDialog(
    pipelines: List<PipelineSummary>,
    selectedPipelineId: String?,
    onSelectPipeline: (String?) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat settings") },
        text = {
            Column {
                Text(
                    text = "Pipeline",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        PipelineChoiceRow(
                            title = "Use default pipeline",
                            selected = selectedPipelineId == null,
                            onClick = { onSelectPipeline(null) },
                        )
                    }
                    items(pipelines, key = { it.id }) { pipeline ->
                        PipelineChoiceRow(
                            title = pipeline.name,
                            selected = selectedPipelineId == pipeline.id,
                            onClick = { onSelectPipeline(pipeline.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Single tappable row in the pipeline picker (used by both the new-chat
 * bottom sheet and the chat-settings dialog). Renders a radio-button plus a
 * pipeline display name; the entire row is clickable to keep the touch
 * target large.
 */
@Composable
private fun PipelineChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ChatDrawerContent(
    sessions: List<ChatSession>,
    currentSessionId: String,
    onNewChat: () -> Unit,
    onImportChat: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
) {
    var sessionToRename by remember { mutableStateOf<ChatSession?>(null) }
    var renameText by remember { mutableStateOf("") }

    if (sessionToRename != null) {
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text("Rename Chat") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Chat Name") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenameSession(sessionToRename!!.id, renameText)
                        }
                        sessionToRename = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Chat Sessions",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        HorizontalDivider()
        
        NavigationDrawerItem(
            label = { Text("New Chat") },
            selected = false,
            onClick = onNewChat,
            icon = { Icon(Icons.Default.Add, contentDescription = "New Chat") },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        NavigationDrawerItem(
            label = { Text("Import Chat") },
            selected = false,
            onClick = onImportChat,
            icon = { Icon(Icons.Default.Download, contentDescription = "Import Chat") },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        HorizontalDivider()

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(sessions) { session ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        NavigationDrawerItem(
                            label = { Text(session.name) },
                            selected = session.id == currentSessionId,
                            onClick = { onSessionSelected(session.id) }
                        )
                    }
                    IconButton(onClick = { 
                        sessionToRename = session
                        renameText = session.name 
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename Session")
                    }
                    IconButton(onClick = { onDeleteSession(session.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Session")
                    }
                }
            }
        }
    }
}

/**
 * Composable for displaying a single chat message.
 *
 * @param message The [ChatMessage] to display.
 */
@Composable
fun ChatMessageItem(
    isGenerating: Boolean = false,
    message: ChatMessage
) {
    val isUser = message.role == Role.USER
    val isSystem = message.role == Role.SYSTEM

    val backgroundColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        isSystem -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val alignment = when {
        isUser -> Alignment.CenterEnd
        isSystem -> Alignment.Center
        else -> Alignment.CenterStart
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            if (isSystem) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (isUser) {
                Text(
                    text = message.content,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                if (isGenerating) {
                    Text(
                        text = message.content,
                    )
                } else {
                    Markdown(
                        content = message.content,
                        typography = com.mikepenz.markdown.m3.markdownTypography(
                            h1 = MaterialTheme.typography.titleLarge,
                            h2 = MaterialTheme.typography.titleMedium,
                            h3 = MaterialTheme.typography.titleSmall,
                            h4 = MaterialTheme.typography.bodyLarge,
                            h5 = MaterialTheme.typography.bodyMedium,
                            h6 = MaterialTheme.typography.bodySmall,
                            text = MaterialTheme.typography.bodyMedium
                        )
                    )
                }
            }
        }
    }
}

/**
 * Composable for the input area.
 *
 * @param inputText The current text in the input field.
 * @param onInputTextChanged Callback when the text changes.
 * @param onSendClicked Callback when the send button is clicked.
 * @param onStopClicked Callback when the stop button is clicked during active generation.
 * @param isGenerating Indicates if the agent is generating a response.
 */
@Composable
fun ChatInputBar(
    inputText: TextFieldValue,
    onInputTextChanged: (TextFieldValue) -> Unit,
    onSendClicked: () -> Unit,
    onStopClicked: () -> Unit,
    isGenerating: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputTextChanged,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask the agent...") },
            enabled = !isGenerating,
            maxLines = 4
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (isGenerating) {
            IconButton(onClick = onStopClicked) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop generation",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else {
            IconButton(
                onClick = onSendClicked,
                enabled = inputText.text.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send"
                )
            }
        }
    }
}

/**
 * Persistent inline banner displayed above the chat input to surface recoverable
 * preconditions (for example, when no model is loaded and the user tries to send a message).
 *
 * @param text The message to display to the user.
 * @param onDismiss Callback invoked when the user dismisses the banner.
 */
@Composable
fun InlineErrorBanner(
    text: String,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}