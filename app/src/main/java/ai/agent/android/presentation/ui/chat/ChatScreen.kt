package ai.agent.android.presentation.ui.chat

import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch

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

    var inputText by remember { mutableStateOf(TextFieldValue("")) }

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

    // Auto-scroll to the bottom when new messages arrive or generation starts
    LaunchedEffect(uiState.messages.size, uiState.isGenerating) {
        if (uiState.messages.isNotEmpty() || uiState.isGenerating) {
            val targetIndex =
                uiState.messages.size + if (uiState.isGenerating) 2 else 1
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
                        viewModel.createNewSession()
                        coroutineScope.launch { drawerState.close() }
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
                    }
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

                    if (uiState.orchestratorState != null && uiState.isGenerating) {
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

                ChatInputBar(
                    inputText = inputText,
                    onInputTextChanged = { inputText = it },
                    onSendClicked = {
                        if (inputText.text.isNotBlank()) {
                            viewModel.sendMessage(inputText.text)
                            inputText = TextFieldValue("")
                        }
                    },
                    isGenerating = uiState.isGenerating
                )
            }
        }
    }
}

@Composable
fun ChatDrawerContent(
    sessions: List<ChatSession>,
    currentSessionId: String,
    onNewChat: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit
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
 * @param isGenerating Indicates if the agent is generating a response.
 */
@Composable
fun ChatInputBar(
    inputText: TextFieldValue,
    onInputTextChanged: (TextFieldValue) -> Unit,
    onSendClicked: () -> Unit,
    isGenerating: Boolean
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
        IconButton(
            onClick = onSendClicked,
            enabled = !isGenerating && inputText.text.isNotBlank()
        ) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send"
                )
            }
        }
    }
}