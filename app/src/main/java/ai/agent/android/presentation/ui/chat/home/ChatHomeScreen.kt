package ai.agent.android.presentation.ui.chat.home

import ai.agent.android.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.components.misc.KnotworkSnackbar
import app.knotwork.design.screens.chat.ChatHomeCallbacks
import app.knotwork.design.screens.chat.ChatHomeContent

/**
 * Redesigned Knotwork chat home — the user-facing surface introduced in
 * Phase 21 / Task 8 and wired to the real backend in Phase 22 / Task 1.
 *
 * Subscribes to [ChatHomeViewModel.state] (plus its companion
 * `StateFlow`s for composer / title / model / pipelineName / tokens /
 * messages) and forwards events back to the VM through a
 * `ChatHomeCallbacks` bundle. The stateless rendering lives in
 * `:catalog` (`ChatHomeContent`) so the screen file stays thin: it just
 * maps `ChatHomeUiState → ChatHomeViewState` via [toViewState] and
 * threads the debug state picker.
 *
 * Insets handling:
 *  - `safeDrawing.horizontal` keeps the surface clear of the side
 *    system bars in landscape.
 *  - `AppShellScaffold` already applies `.imePadding()` so the body
 *    follows the keyboard.
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
    val pipelineName by viewModel.pipelineName.collectAsStateWithLifecycle()
    val tokensUsed by viewModel.tokensUsed.collectAsStateWithLifecycle()
    val tokensMax by viewModel.tokensMax.collectAsStateWithLifecycle()

    var debugPickerExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val pipelineFallbackMessage = stringResource(R.string.errors_chat_pipeline_removed)

    LaunchedEffect(viewModel) {
        viewModel.pipelineFallbackEvents.collect {
            snackbarHostState.showSnackbar(message = pipelineFallbackMessage)
        }
    }

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
        pipelineName = pipelineName ?: PIPELINE_NAME_PLACEHOLDER,
        tokensUsed = tokensUsed,
        tokensMax = tokensMax,
    )

    val callbacks = remember(viewModel) {
        ChatHomeCallbacks(
            onComposerValueChange = viewModel::onComposerValueChange,
            onSend = viewModel::sendMessage,
            onStop = viewModel::stopGeneration,
            onOpenDrawer = viewModel::openDrawer,
            onCloseDrawer = viewModel::closeDrawer,
            onSelectThread = viewModel::selectThread,
            onNewThread = { /* stub: real new-thread wiring ships with Phase 22 / Task 4 */ },
            onOpenModelPicker = { /* stub: model picker sheet ships with Phase 22 / Task 4 */ },
            onOverflow = { /* stub: overflow menu ships with Phase 22 / Task 4 */ },
            onSamplePrompt = viewModel::onComposerValueChange,
            onConsoleSnapChange = viewModel::setConsoleSnap,
            onConsoleTabChange = { /* stub: console wiring ships with Phase 22 / Task 3 */ },
            onConsoleFilterChange = viewModel::onConsoleFilterChange,
            onConsoleSearch = viewModel::toggleConsoleSearch,
            onConsoleSearchQueryChange = viewModel::onConsoleSearchQueryChange,
            onConsoleCopyLine = { /* stub: console wiring ships with Phase 22 / Task 3 */ },
            onConsoleFilterByLineSource = viewModel::filterConsoleByLineSource,
            onConsoleCopyAll = { /* stub: console wiring ships with Phase 22 / Task 3 */ },
            onConsoleClear = { /* stub: console wiring ships with Phase 22 / Task 3 */ },
            onCloseConsole = viewModel::closeConsole,
            onHitlAllowOnce = { viewModel.forceState(ChatHomeUiState.Idle) },
            onHitlReject = { viewModel.forceState(ChatHomeUiState.Idle) },
            onHitlTypedConfirmChange = viewModel::onTypedConfirmChange,
            onClarificationReply = { _ -> viewModel.forceState(ChatHomeUiState.Idle) },
            onErrorRetry = { viewModel.forceState(ChatHomeUiState.Idle) },
            onTitleTripleTap = { debugPickerExpanded = true },
            onToggleFavorite = { /* stub: favorite persistence ships with Phase 22 / Task 4 */ },
            onEditThread = { /* stub: rename sheet ships with Phase 22 / Task 4 */ },
            onImportChat = { /* stub: import-from-JSON sheet ships with Phase 22 / Task 4 */ },
            onOpenSettings = { /* stub: nav-graph deep-link to Settings ships in a follow-up */ },
            onSamplePromptCard = { card -> viewModel.onComposerValueChange(card.title) },
        )
    }

    // Inset wiring (Phase 21 / Task 8 review fixes):
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
        ChatHomeContent(state = viewState, callbacks = callbacks)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = SNACKBAR_BOTTOM_INSET_DP.dp),
        ) { data ->
            KnotworkSnackbar(data = data)
        }
        ChatHomeDebugStatePicker(
            expanded = debugPickerExpanded,
            onDismiss = { debugPickerExpanded = false },
            onPick = { id ->
                debugStateForId(id)?.let(viewModel::forceState)
            },
        )
    }
}

/**
 * Bottom inset of the floating SnackbarHost relative to the screen edge.
 * Hand-tuned so the snackbar clears the composer + bottom-nav stack
 * without colliding with the inline error pill (when present). A
 * dedicated catalog spacing token is introduced once Phase 22 / Task 5
 * (Chat design audit) covers snackbar placement.
 */
private const val SNACKBAR_BOTTOM_INSET_DP: Int = 96

/**
 * Fallback subtitle rendered when the pipeline library is still empty
 * (no pipelines have been created yet). Matches the catalog default in
 * `ChatHomeViewState.pipelineName` so the TopAppBar subtitle does not
 * jump between values once a pipeline is created.
 */
private const val PIPELINE_NAME_PLACEHOLDER: String = "default"
