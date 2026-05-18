package ai.agent.android.presentation.ui.chat.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.screens.chat.ChatHomeCallbacks
import app.knotwork.design.screens.chat.ChatHomeContent

/**
 * Redesigned Knotwork chat home — the user-facing surface introduced in
 * Phase 21 / Task 8. Subscribes to [ChatHomeViewModel.state] (plus its
 * companion `StateFlow`s for the composer / title / model) and forwards
 * events back to the VM through a `ChatHomeCallbacks` bundle.
 *
 * The stateless rendering lives in `:catalog` (`ChatHomeContent`) so the
 * screen file stays thin: it just maps `ChatHomeUiState → ChatHomeViewState`
 * via [toViewState] and threads the debug state picker.
 *
 * Insets handling:
 *  - `imePadding()` is applied to the whole surface so the composer
 *    follows the keyboard (the catalog content does not own insets).
 *  - `safeDrawing.horizontal` is applied so the bottom bar does not draw
 *    under the system navigation in landscape.
 *
 * @param viewModel the screen-scoped Hilt [ChatHomeViewModel].
 * @param modifier optional layout modifier applied to the screen root.
 */
@Composable
fun ChatHomeScreen(viewModel: ChatHomeViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val threadTitle by viewModel.threadTitle.collectAsStateWithLifecycle()
    val modelName by viewModel.modelName.collectAsStateWithLifecycle()
    val composerValue by viewModel.composerValue.collectAsStateWithLifecycle()
    val pendingTypedConfirm by viewModel.pendingTypedConfirm.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val consoleSearchQuery by viewModel.consoleSearchQuery.collectAsStateWithLifecycle()
    val consoleFilter by viewModel.consoleFilter.collectAsStateWithLifecycle()

    var debugPickerExpanded by remember { mutableStateOf(false) }

    // Resolve every user-facing stub string up here so the mapping below
    // stays free of hardcoded strings — agent-status pills, drawer
    // sessions, and the empty-state suggestion cards all flow from
    // `strings_chat.xml`.
    val fixtures = rememberChatHomeFixtures()

    val viewState = uiState.toViewState(
        threadTitle = threadTitle,
        modelName = modelName,
        fixtures = fixtures,
        messages = messages,
        composerValue = composerValue,
        pendingTypedConfirm = pendingTypedConfirm,
        consoleSearchQuery = consoleSearchQuery,
        consoleFilter = consoleFilter,
    )

    val callbacks = remember(viewModel) {
        ChatHomeCallbacks(
            onComposerValueChange = viewModel::onComposerValueChange,
            onSend = viewModel::sendMessage,
            onStop = viewModel::stopGeneration,
            onOpenDrawer = viewModel::openDrawer,
            onCloseDrawer = viewModel::closeDrawer,
            onSelectThread = viewModel::selectThread,
            onNewThread = { /* stub: real new-thread wiring lands with the orchestrator integration */ },
            onOpenModelPicker = { /* stub: model picker sheet ships with Task 10 */ },
            onOverflow = { /* stub: overflow menu ships with the orchestrator integration */ },
            onSamplePrompt = viewModel::onComposerValueChange,
            onConsoleSnapChange = viewModel::setConsoleSnap,
            onConsoleTabChange = { /* stub: tab persistence ships with the orchestrator integration */ },
            onConsoleFilterChange = viewModel::onConsoleFilterChange,
            onConsoleSearch = viewModel::toggleConsoleSearch,
            onConsoleSearchQueryChange = viewModel::onConsoleSearchQueryChange,
            onConsoleCopyLine = { /* stub: clipboard wiring ships with the orchestrator integration */ },
            onConsoleFilterByLineSource = viewModel::filterConsoleByLineSource,
            onConsoleCopyAll = { /* stub: clipboard wiring ships with the orchestrator integration */ },
            onConsoleClear = { /* stub: clear-confirmation ships with the orchestrator integration */ },
            onCloseConsole = viewModel::closeConsole,
            onHitlAllowOnce = { viewModel.forceState(ChatHomeUiState.Idle) },
            onHitlReject = { viewModel.forceState(ChatHomeUiState.Idle) },
            onHitlTypedConfirmChange = viewModel::onTypedConfirmChange,
            onClarificationReply = { _ -> viewModel.forceState(ChatHomeUiState.Idle) },
            onErrorRetry = { viewModel.forceState(ChatHomeUiState.Idle) },
            onTitleTripleTap = { debugPickerExpanded = true },
            onToggleFavorite = { /* stub: favorite persistence ships with the orchestrator integration */ },
            onEditThread = { /* stub: rename sheet ships with the orchestrator integration */ },
            onImportChat = { /* stub: import-from-JSON sheet ships with the orchestrator integration */ },
            onOpenSettings = { /* stub: nav-graph deep-link to Settings ships in a follow-up */ },
            onSamplePromptCard = { card -> viewModel.onComposerValueChange(card.title) },
        )
    }

    // Inset wiring (Phase 21 / Task 8 review fixes):
    //  - `AppShellScaffold` already wraps its Scaffold in `.imePadding()`,
    //    so the body + composer slide up with the keyboard in sync with
    //    the bottom-nav. Adding `.imePadding()` here would double-count.
    //  - `AppShellScaffold` also passes a bottom-bar-aware inner padding,
    //    so `.navigationBarsPadding()` would leave a visible strip below
    //    the composer.
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
        ChatHomeContent(state = viewState, callbacks = callbacks)
        ChatHomeDebugStatePicker(
            expanded = debugPickerExpanded,
            onDismiss = { debugPickerExpanded = false },
            onPick = { id ->
                debugStateForId(id)?.let(viewModel::forceState)
            },
        )
    }
}
