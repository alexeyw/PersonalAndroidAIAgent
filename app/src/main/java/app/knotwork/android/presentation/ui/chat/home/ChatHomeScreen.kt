package app.knotwork.android.presentation.ui.chat.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.android.R
import app.knotwork.design.components.buttons.KnotworkPrimaryButton
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.chat.ChatContextAction
import app.knotwork.design.components.controls.KnotworkField
import app.knotwork.design.components.controls.KnotworkTextField
import app.knotwork.design.components.knotworkMarkdownColor
import app.knotwork.design.components.knotworkMarkdownTypography
import app.knotwork.design.components.misc.KnotworkSnackbar
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.screens.chat.ChatHomeCallbacks
import app.knotwork.design.screens.chat.ChatHomeContent
import app.knotwork.design.theme.KnotworkTheme
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Redesigned Knotwork chat home — the user-facing surface that wires up:
 *  - agent orchestrator, sessions, pipeline binding, token meter;
 *  - HITL approval gate + clarification;
 *  - console pane;
 *  - secondary affordances: new-chat / rename /
 *    favorite / import / model picker / overflow (export, delete,
 *    clear-console) and the deep-link to Settings + Models.
 *
 * Inset wiring:
 *  - `safeDrawing.horizontal` clears the side system bars in landscape.
 *  - `AppShellScaffold` already applies `.imePadding()` so the body
 *    follows the keyboard.
 *
 * @param viewModel the screen-scoped Hilt [ChatHomeViewModel].
 * @param onOpenSettings deep-link callback into the Settings route.
 * @param onOpenModels deep-link callback into the Models management route.
 * @param modifier optional layout modifier applied to the screen root.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHomeScreen(
    viewModel: ChatHomeViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenModels: () -> Unit = {},
) {
    // Single subscription to the consolidated screen state — the immutable
    // sub-structures (composer, console, pending, thread, model, tokens)
    // are handed down the tree as-is, so child composables skip when their
    // slice did not change.
    val screenState by viewModel.state.collectAsStateWithLifecycle()
    val messages = screenState.messages
    val currentSessionId = screenState.thread.currentSessionId
    val chatListState = rememberLazyListState()

    // Auto-scroll for the message list, re-armed on every thread switch.
    //  1. Opening a thread jumps to the very end — but only once the list has
    //     actually laid out its items. The `snapshotFlow { … totalItemsCount }`
    //     wait is essential: the list is composed only after the messages
    //     arrive (Empty → Idle), so scrolling in the same frame would hit an
    //     unmeasured list and silently no-op (the bug in the first attempt).
    //  2. After that baseline, each newly appended message (user or agent) is
    //     revealed per the spec — top-aligned when taller than the viewport,
    //     otherwise bottom-aligned. Nothing moves when the conversation already
    //     fits (the scroll calls clamp to the current position).
    LaunchedEffect(currentSessionId) {
        if (currentSessionId.isBlank()) return@LaunchedEffect
        var knownMessages = -1
        var knownItems = -1
        // Observe BOTH the message count and the rendered item count:
        //  - `messages.size` grows by one for each appended user/agent row (and
        //    stays correct even when a generating-loader item is swapped for the
        //    agent's final message, which leaves the rendered count flat);
        //  - `layoutInfo.totalItemsCount` also captures the trailing service rows
        //    (the generating loader and the error tile) that are NOT part of
        //    `messages`, so those scroll into view too.
        // On either growth (or a freshly opened thread) we scroll to the list's
        // real last item. `scrollToItem` is reliable on its own: a short last
        // item clamps to the bottom (bottom-aligned), a tall one aligns its top
        // to the viewport (top-aligned), and a list that already fits is a no-op.
        snapshotFlow { messages.size to chatListState.layoutInfo.totalItemsCount }
            .collect { (messageCount, itemCount) ->
                if (messageCount <= 0 || itemCount <= 0) {
                    knownMessages = messageCount
                    knownItems = itemCount
                    return@collect
                }
                val opened = knownMessages < 0
                val grew = messageCount > knownMessages || itemCount > knownItems
                if (opened || grew) {
                    chatListState.scrollToItem(itemCount - 1)
                }
                knownMessages = messageCount
                knownItems = itemCount
            }
    }

    var debugPickerExpanded by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var newThreadSheetVisible by remember { mutableStateOf(false) }
    var modelPickerVisible by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pipelineFallbackMessage = stringResource(R.string.errors_chat_pipeline_removed)
    val consoleLineCopiedMessage = stringResource(R.string.chat_snackbar_console_line_copied)
    val consoleAllCopiedMessage = stringResource(R.string.chat_snackbar_console_copied)
    val exportChooserTitle = stringResource(R.string.chat_export_chooser_title)
    val importFailedTemplate = stringResource(R.string.chat_import_failed)
    val importUnreadableMessage = stringResource(R.string.chat_import_unreadable)
    val messageCopiedMessage = stringResource(R.string.chat_snackbar_copied)
    val rateComingSoonMessage = stringResource(R.string.chat_message_rate_coming_soon)
    val savedToMemoryMessage = stringResource(R.string.chat_snackbar_saved_to_memory)
    val saveToMemoryFailedMessage = stringResource(R.string.chat_snackbar_save_to_memory_failed)

    LaunchedEffect(viewModel) {
        viewModel.pipelineFallbackEvents.collect {
            snackbarHostState.showSnackbar(message = pipelineFallbackMessage)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.consoleSnackbarEvents.collect { event ->
            val message = when (event) {
                ConsoleSnackbarEvent.LineCopied -> consoleLineCopiedMessage
                ConsoleSnackbarEvent.AllCopied -> consoleAllCopiedMessage
            }
            snackbarHostState.showSnackbar(message = message)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.exportEvents.collect { payload ->
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = MIME_JSON
                putExtra(Intent.EXTRA_SUBJECT, payload.sessionName)
                putExtra(Intent.EXTRA_TEXT, payload.json)
            }
            context.startActivity(Intent.createChooser(sendIntent, exportChooserTitle))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.importErrorEvents.collect { reason ->
            snackbarHostState.showSnackbar(message = importFailedTemplate.format(reason))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.memorySaveEvents.collect { event ->
            val message = when (event) {
                MemorySaveEvent.Saved -> savedToMemoryMessage
                MemorySaveEvent.Failed -> saveToMemoryFailedMessage
            }
            snackbarHostState.showSnackbar(message = message)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (json.isNullOrBlank()) {
                snackbarHostState.showSnackbar(message = importUnreadableMessage)
            } else {
                viewModel.importChatFromJson(json)
            }
        }
    }

    // Resolve every user-facing stub string up here so the mapping below
    // stays free of hardcoded strings — agent-status pills, drawer
    // sessions, and the empty-state suggestion cards all flow from
    // `strings_chat.xml`.
    val fixtures = rememberChatHomeFixtures()

    val viewState = screenState.toViewState(fixtures = fixtures)

    val callbacks = ChatHomeCallbacks(
        onComposerValueChange = viewModel::onComposerValueChange,
        onSend = viewModel::sendMessage,
        onStop = viewModel::stopGeneration,
        onOpenDrawer = viewModel::openDrawer,
        onCloseDrawer = viewModel::closeDrawer,
        onSelectThread = viewModel::selectThread,
        onNewThread = { newThreadSheetVisible = true },
        onOpenModelPicker = { modelPickerVisible = true },
        onOverflow = { overflowExpanded = true },
        onSamplePrompt = viewModel::onComposerValueChange,
        onConsoleSnapChange = viewModel::setConsoleSnap,
        onConsoleTabChange = viewModel::onConsoleTabChange,
        onConsoleFilterChange = viewModel::onConsoleFilterChange,
        onConsoleSearch = viewModel::toggleConsoleSearch,
        onConsoleSearchQueryChange = viewModel::onConsoleSearchQueryChange,
        onConsoleCopyLine = { line ->
            clipboardManager.setText(AnnotatedString(viewModel.buildConsoleLineCopyPayload(line)))
            viewModel.signalConsoleLineCopied()
        },
        onConsoleFilterByLineSource = viewModel::filterConsoleByLineSource,
        // The catalog applies `console.filter` + `console.searchQuery` itself
        // before rendering rows; the `Copy all` payload mirrors what the
        // user is actively looking at, so the screen reproduces the same
        // pre-filter here.
        onConsoleCopyAll = {
            val console = screenState.console
            val visible = visibleConsoleLogs(console.logs, console.filter, console.searchQuery)
            clipboardManager.setText(AnnotatedString(viewModel.buildConsoleAllCopyPayload(visible)))
            viewModel.signalConsoleAllCopied()
        },
        onConsoleClear = viewModel::requestConsoleClear,
        onCloseConsole = viewModel::closeConsole,
        onHitlAllowOnce = viewModel::approveTool,
        onHitlReject = viewModel::rejectTool,
        onHitlTypedConfirmChange = viewModel::onTypedConfirmChange,
        onClarificationReply = viewModel::submitClarificationReply,
        onErrorRetry = viewModel::retryAfterError,
        onTitleTripleTap = { debugPickerExpanded = true },
        onToggleFavorite = viewModel::toggleFavoriteCurrent,
        onEditThread = { threadId ->
            val session = screenState.thread.rows.firstOrNull { it.id == threadId }
            renameDraft = session?.title.orEmpty()
            renameTargetId = threadId
        },
        onImportChat = { importLauncher.launch(arrayOf(MIME_JSON)) },
        onOpenSettings = onOpenSettings,
        onSamplePromptCard = { card -> viewModel.onComposerValueChange(card.title) },
        // Tapping the agent-status pill above the composer opens the
        // console pane at the Partial snap — a one-tap drill-in affordance.
        onAgentStatusClick = { viewModel.openConsole() },
        onMessageContextAction = { rowId, action ->
            when (action) {
                ChatContextAction.Copy -> {
                    val text = viewModel.textForRow(rowId)
                    if (!text.isNullOrEmpty()) {
                        clipboardManager.setText(AnnotatedString(text))
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(message = messageCopiedMessage)
                        }
                    }
                }
                ChatContextAction.Rerun -> {
                    viewModel.textForRow(rowId)?.let(viewModel::onComposerValueChange)
                }
                ChatContextAction.SaveToMemory -> {
                    viewModel.saveMessageToMemory(rowId)
                }
                ChatContextAction.Rate -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(message = rateComingSoonMessage)
                    }
                }
            }
        },
    )

    // Inset wiring:
    //  - `AppShellScaffold` already wraps its Scaffold in `.imePadding()`,
    //    so the body + composer slide up with the keyboard in sync with
    //    the bottom-nav. Adding `.imePadding()` here would double-count.
    //  - `safeDrawing.horizontal` keeps the surface clear of the side
    //    system bars in landscape; the inner `Scaffold` inside
    //    `ChatHomeContent` already handles status-bar inset via its
    //    `TopAppBar` defaults.
    Box(
        contentAlignment = Alignment.TopStart,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        // Pull the Knotwork-themed markdown bindings once per recomposition
        // so the renderer lambda below doesn't re-resolve composition locals
        // on every emit. `knotworkMarkdownTypography` / `…Color` ride
        // `KnotworkTextStyles` + `KnotworkTheme.extended.surface{1,2,3}`,
        // matching markdown headings, body, code surfaces, and tables to the
        // surrounding chat surface tokens.
        val markdownTypography = knotworkMarkdownTypography()
        val markdownColors = knotworkMarkdownColor()
        ChatHomeContent(
            state = viewState,
            callbacks = callbacks,
            // Catalog stays free of any markdown dependency on the screen
            // side; the app wires the `com.mikepenz.markdown.m3.Markdown`
            // renderer here so agent bubbles get the Knotwork-themed
            // typography + colors for headings, lists, and code fences.
            markdownRenderer = { source ->
                Markdown(
                    content = source,
                    typography = markdownTypography,
                    colors = markdownColors,
                )
            },
            messageListState = chatListState,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // sp16 (bottom-nav) + sp8 (composer breathing room) = 96 dp.
                // No dedicated catalog "snackbar inset" token yet — composed
                // from the spacing scale so the value stays grounded.
                .padding(bottom = KnotworkTheme.spacing.sp16 + KnotworkTheme.spacing.sp8),
        ) { data ->
            KnotworkSnackbar(data = data)
        }
        ChatHomeDebugStatePicker(
            expanded = debugPickerExpanded,
            onDismiss = { debugPickerExpanded = false },
            onPick = { id ->
                // Console entries open the overlay; every other entry
                // forces the underlying chat state.
                val snap = debugConsoleSnapForId(id)
                if (snap != null) {
                    viewModel.openConsole(snap)
                } else {
                    debugStateForId(id)?.let(viewModel::forceState)
                }
            },
        )
        // Overflow menu — anchored to the top-end so it visually drops out
        // of the TopAppBar `⋮` icon. The catalog `onOverflow` callback is
        // parameter-less, so the screen owns the anchor + items.
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_overflow_export)) },
                    onClick = {
                        overflowExpanded = false
                        viewModel.exportCurrentSession()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_overflow_delete)) },
                    onClick = {
                        overflowExpanded = false
                        deleteDialogVisible = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_overflow_clear_console)) },
                    onClick = {
                        overflowExpanded = false
                        viewModel.requestConsoleClear()
                    },
                )
            }
        }
        if (screenState.consoleClearConfirmRequested) {
            AlertDialog(
                onDismissRequest = viewModel::dismissConsoleClear,
                title = { Text(stringResource(R.string.chat_console_clear_dialog_title)) },
                text = { Text(stringResource(R.string.chat_console_clear_dialog_text)) },
                confirmButton = {
                    KnotworkTextButton(
                        text = stringResource(R.string.chat_console_clear_dialog_confirm),
                        onClick = viewModel::confirmConsoleClear,
                    )
                },
                dismissButton = {
                    KnotworkTextButton(
                        text = stringResource(R.string.chat_console_clear_dialog_cancel),
                        onClick = viewModel::dismissConsoleClear,
                    )
                },
            )
        }
        if (deleteDialogVisible) {
            AlertDialog(
                onDismissRequest = { deleteDialogVisible = false },
                title = { Text(stringResource(R.string.chat_delete_dialog_title)) },
                text = { Text(stringResource(R.string.chat_delete_dialog_text)) },
                confirmButton = {
                    KnotworkTextButton(
                        text = stringResource(R.string.chat_delete_dialog_confirm),
                        destructive = true,
                        onClick = {
                            deleteDialogVisible = false
                            viewModel.deleteCurrentSession()
                        },
                    )
                },
                dismissButton = {
                    KnotworkTextButton(
                        text = stringResource(R.string.chat_delete_dialog_cancel),
                        onClick = { deleteDialogVisible = false },
                    )
                },
            )
        }
        renameTargetId?.let { targetId ->
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { renameTargetId = null },
                sheetState = sheetState,
            ) {
                RenameSessionSheetContent(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    onSave = {
                        viewModel.renameSession(targetId, renameDraft)
                        renameTargetId = null
                    },
                    onCancel = { renameTargetId = null },
                )
            }
        }
        if (newThreadSheetVisible) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { newThreadSheetVisible = false },
                sheetState = sheetState,
            ) {
                NewThreadPipelinePickerSheetContent(
                    pipelines = screenState.availablePipelines,
                    initialPipelineId = viewModel.currentPipelineId(),
                    onCancel = { newThreadSheetVisible = false },
                    onCreate = { pipelineId ->
                        newThreadSheetVisible = false
                        viewModel.createNewSessionWithPipeline(pipelineId)
                    },
                )
            }
        }
        if (modelPickerVisible) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { modelPickerVisible = false },
                sheetState = sheetState,
            ) {
                ModelPickerSheetContent(
                    models = screenState.model.installed.map { ModelPickerRow(id = it.id, name = it.name) },
                    activeId = screenState.model.activeId,
                    onPick = { id ->
                        modelPickerVisible = false
                        viewModel.pickModel(id)
                    },
                    onOpenModels = {
                        modelPickerVisible = false
                        onOpenModels()
                    },
                )
            }
        }
    }
}

/**
 * Body of the rename-session `ModalBottomSheet`. Captured separately so
 * the screen file stays scannable and so the input field can be hoisted
 * for testing.
 */
@Composable
private fun RenameSessionSheetContent(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = KnotworkTheme.spacing.sp6,
                vertical = KnotworkTheme.spacing.sp4,
            )
            .navigationBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.chat_rename_sheet_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp3))
        KnotworkField(
            label = stringResource(R.string.chat_rename_sheet_label),
        ) {
            KnotworkTextField(
                value = value,
                onValueChange = onValueChange,
            )
        }
        Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp3))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Row {
                KnotworkTextButton(
                    text = stringResource(R.string.chat_rename_sheet_cancel),
                    onClick = onCancel,
                )
                Spacer(modifier = Modifier.padding(horizontal = KnotworkTheme.spacing.sp1))
                KnotworkPrimaryButton(
                    text = stringResource(R.string.chat_rename_sheet_save),
                    onClick = onSave,
                    enabled = value.trim().isNotEmpty(),
                )
            }
        }
    }
}

/**
 * Body of the new-thread pipeline picker `ModalBottomSheet`. Pre-selects
 * the user's current pipeline binding so a "create" tap creates a chat
 * bound to the same pipeline by default.
 *
 * @param pipelines pipelines the user can pick from.
 * @param initialPipelineId pipeline id to pre-select (`null` = inherit
 *   default).
 * @param onCancel callback for the trailing Cancel button.
 * @param onCreate callback fired with the picked pipeline id (or `null`
 *   for the default pipeline).
 */
@Composable
private fun NewThreadPipelinePickerSheetContent(
    pipelines: List<PipelineSummary>,
    initialPipelineId: String?,
    onCancel: () -> Unit,
    onCreate: (String?) -> Unit,
) {
    // `null` represents the "Use default pipeline" option, mirroring the
    // ChatSession.pipelineId semantics: null means "inherit the
    // application-wide default". The picker always surfaces this option
    // regardless of whether the library has pipelines, so the user can
    // intentionally leave the binding unset.
    var selectedId by remember(initialPipelineId, pipelines) {
        mutableStateOf(initialPipelineId)
    }
    val useDefaultLabel = stringResource(R.string.chat_new_thread_sheet_use_default)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = KnotworkTheme.spacing.sp6,
                vertical = KnotworkTheme.spacing.sp4,
            )
            .navigationBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.chat_new_thread_sheet_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp3))
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            PipelinePickerRow(
                label = useDefaultLabel,
                selected = selectedId == null,
                onClick = { selectedId = null },
            )
            pipelines.forEach { pipeline ->
                PipelinePickerRow(
                    label = pipeline.name,
                    selected = pipeline.id == selectedId,
                    onClick = { selectedId = pipeline.id },
                )
            }
        }
        Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp3))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Row {
                KnotworkTextButton(
                    text = stringResource(R.string.chat_new_thread_sheet_cancel),
                    onClick = onCancel,
                )
                Spacer(modifier = Modifier.padding(horizontal = KnotworkTheme.spacing.sp1))
                KnotworkPrimaryButton(
                    text = stringResource(R.string.chat_new_thread_sheet_create),
                    onClick = { onCreate(selectedId) },
                )
            }
        }
    }
}

/**
 * Single row in the new-thread pipeline picker. The whole row is
 * clickable so the touch target spans the full sheet width — relying on
 * the RadioButton alone leaves a thin strip that fails the 48dp
 * accessibility guideline.
 */
@Composable
private fun PipelinePickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            RadioButton(selected = selected, onClick = onClick)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

/** Minimal projection of a local model row in the model-picker sheet. */
private data class ModelPickerRow(val id: Long, val name: String)

/**
 * Body of the model-picker `ModalBottomSheet`. Empty list shows a single
 * "Open Models" pill that deep-links to the Models tab via [onOpenModels].
 */
@Composable
private fun ModelPickerSheetContent(
    models: List<ModelPickerRow>,
    activeId: Long?,
    onPick: (Long) -> Unit,
    onOpenModels: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = KnotworkTheme.spacing.sp6,
                vertical = KnotworkTheme.spacing.sp4,
            )
            .navigationBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.chat_model_picker_sheet_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp3))
        if (models.isEmpty()) {
            Text(text = stringResource(R.string.chat_model_picker_empty))
            Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp3))
            KnotworkPrimaryButton(
                text = stringResource(R.string.chat_model_picker_open_models),
                onClick = onOpenModels,
            )
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                models.forEach { model ->
                    ListItem(
                        headlineContent = { Text(model.name) },
                        trailingContent = if (model.id == activeId) {
                            {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription =
                                    stringResource(R.string.chat_model_picker_active_cd),
                                )
                            }
                        } else {
                            {
                                Icon(
                                    imageVector = AppIcons.Circle,
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(model.id) },
                    )
                }
            }
        }
    }
}

/**
 * Reproduces the catalog's filter + substring-search pass over [logs] so
 * the `Copy all` clipboard payload mirrors exactly what the user sees in
 * the Logs tab. The catalog `ConsoleLogsBody` performs the same two-stage
 * filter; duplicating it here is the cheapest cut while the catalog's
 * filter helpers stay internal.
 *
 * @param logs Raw aggregated log lines.
 * @param filter Active source-set filter.
 * @param searchQuery Inline-search query (`null` means hidden — no
 *   substring filter is applied).
 * @return Lines currently visible to the user, in the same order as
 *   rendered.
 */
internal fun visibleConsoleLogs(
    logs: List<app.knotwork.design.components.console.ConsoleLine>,
    filter: app.knotwork.design.components.console.ConsoleFilter,
    searchQuery: String?,
): List<app.knotwork.design.components.console.ConsoleLine> {
    val sourceFiltered = logs.filter(filter::matches)
    if (searchQuery.isNullOrEmpty()) return sourceFiltered
    return sourceFiltered.filter { it.text.contains(searchQuery, ignoreCase = true) }
}

/** MIME type used by both the export share-sheet and the import file picker. */
private const val MIME_JSON: String = "application/json"
