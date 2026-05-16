@file:Suppress(
    "MatchingDeclarationName", // File hosts ChatHomeContent and its helper composables.
)

package app.knotwork.design.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkSecondaryButton
import app.knotwork.design.components.chat.ChatComposer
import app.knotwork.design.components.chat.ChatContent
import app.knotwork.design.components.chat.ChatMessage
import app.knotwork.design.components.chips.KnotworkChip
import app.knotwork.design.components.chips.Risk
import app.knotwork.design.components.console.ConsolePane
import app.knotwork.design.components.misc.KnotworkLoader
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Horizontal padding around chat-message rows (per `screens/README.md §C1`). */
private val ChatHorizontalPadding = 16.dp

/** Vertical gap between consecutive chat-message rows. */
private val ChatRowGap = 12.dp

/** Width of the drawer overlay panel (Material spec for navigation drawers). */
private val DrawerWidth = 320.dp

/** Alpha of the scrim painted over the chat surface while the drawer is open. */
private const val DRAWER_SCRIM_ALPHA = 0.32f

/** Alpha of the scrim painted over the chat surface while the console pane is expanded. */
private const val CONSOLE_SCRIM_ALPHA = 0.20f

/** Approximate fill ratio of the loader bubble inside the body width (Generating state). */
private const val LOADER_BUBBLE_WIDTH_FRACTION = 0.4f

/**
 * Stateless Knotwork Chat home — the primary user-facing surface. Drives the
 * 9 documented states (`compose/screens/README.md §C1`) deterministically
 * from [state]; the caller (`:app/ChatHomeScreen`) owns navigation, IME
 * insets, deep-link arguments, and the real ViewModel wiring.
 *
 * Layout (per spec):
 *  - [TopAppBar] with thread title (and model picker beneath) on the left;
 *    threads / overflow on the right.
 *  - Body `LazyColumn<ChatHomeMessageRow>` with 16 dp horizontal padding and
 *    12 dp inter-row gap.
 *  - Pinned [ChatComposer] in the Scaffold's `bottomBar`.
 *  - Drawer overlay rendered as a slide-in panel when
 *    [ChatHomeVisualState.DrawerOpen]; backed by a scrim of `0.32`.
 *  - Console overlay rendered as a docked bottom sheet when
 *    [ChatHomeVisualState.ConsoleExpanded].
 *
 * **Stateless** — every callback is hoisted via [callbacks]; the composable
 * never owns its own `remember`-d state beyond the recompose-only pieces
 * (focus, scroll position) that snapshot tests pin via the surrounding
 * test rule.
 *
 * @param state immutable visual snapshot — see [ChatHomeViewState].
 * @param callbacks bundle of one-shot event handlers; defaults to no-op.
 * @param modifier optional layout modifier applied to the screen root.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHomeContent(
    state: ChatHomeViewState,
    modifier: Modifier = Modifier,
    callbacks: ChatHomeCallbacks = noopChatHomeCallbacks(),
) {
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = { ChatHomeTopBar(state = state, callbacks = callbacks) },
            bottomBar = { ChatHomeBottomBar(state = state, callbacks = callbacks) },
            // The outer `AppShellScaffold` already accounts for the system
            // navigation bar and the in-app bottom nav via its own inner
            // padding. Letting this Scaffold default to `safeDrawing` would
            // add a second nav-bar-height of padding to the composer slot
            // and leave a visible strip beneath the input. The TopAppBar
            // here still handles status-bar inset via its own Material3
            // default `windowInsets`.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            ChatHomeBody(state = state, callbacks = callbacks, padding = padding)
        }
        if (state.visualState == ChatHomeVisualState.DrawerOpen) {
            ChatHomeDrawerOverlay(state = state, callbacks = callbacks)
        }
        if (state.visualState == ChatHomeVisualState.ConsoleExpanded) {
            ChatHomeConsoleOverlay(state = state, callbacks = callbacks)
        }
    }
}

/** TopAppBar with title (thread name + model line) and trailing actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatHomeTopBar(state: ChatHomeViewState, callbacks: ChatHomeCallbacks) {
    val tripleTapHint = stringResource(R.string.knotwork_chat_home_title_debug_hint)
    TopAppBar(
        title = {
            Column(
                modifier = Modifier
                    .semantics { contentDescription = tripleTapHint }
                    .clickableTripleTap(callbacks.onTitleTripleTap),
            ) {
                Text(
                    text = state.threadTitle,
                    style = KnotworkTextStyles.TitleMd,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SmartToy,
                        contentDescription = null,
                        tint = KnotworkTheme.extended.onSurfaceMuted,
                        modifier = Modifier.size(KnotworkTheme.spacing.sp3),
                    )
                    Text(
                        text = state.modelName,
                        style = KnotworkTextStyles.MonoSm,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = callbacks.onOpenDrawer) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = stringResource(R.string.knotwork_chat_home_action_threads),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        actions = {
            IconButton(onClick = callbacks.onOpenModelPicker) {
                Icon(
                    imageVector = Icons.Outlined.SmartToy,
                    contentDescription = stringResource(R.string.knotwork_chat_home_action_model_picker),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = callbacks.onOverflow) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.knotwork_chat_home_action_overflow),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/**
 * Pinned [ChatComposer] sitting at the bottom of the surface. Disabled
 * (interactive but not actionable) when the surface is awaiting a HITL
 * confirmation — the buttons remain visible so the user understands the
 * surface is paused, but typing is allowed so the user can prepare the
 * next turn.
 */
@Composable
private fun ChatHomeBottomBar(state: ChatHomeViewState, callbacks: ChatHomeCallbacks) {
    ChatComposer(
        value = state.composerValue,
        onValueChange = callbacks.onComposerValueChange,
        onSend = callbacks.onSend,
        onStop = callbacks.onStop,
        onVoice = callbacks.onVoice,
        onAttach = callbacks.onAttach,
        state = state.composerState,
    )
}

/** Body LazyColumn — empty state, idle conversation, or trailing loader/clarification/HITL/error. */
@Composable
private fun ChatHomeBody(state: ChatHomeViewState, callbacks: ChatHomeCallbacks, padding: PaddingValues) {
    when (state.visualState) {
        ChatHomeVisualState.Empty -> ChatHomeEmptyBody(state = state, callbacks = callbacks, padding = padding)
        else -> ChatHomeMessageList(state = state, callbacks = callbacks, padding = padding)
    }
}

/** Centered empty-state body with sample-prompt chips. */
@Composable
private fun ChatHomeEmptyBody(state: ChatHomeViewState, callbacks: ChatHomeCallbacks, padding: PaddingValues) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = ChatHorizontalPadding),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Chat,
            contentDescription = null,
            tint = KnotworkTheme.extended.onSurfaceMuted,
            modifier = Modifier.size(KnotworkTheme.spacing.sp16),
        )
        Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp3))
        Text(
            text = stringResource(R.string.knotwork_chat_home_empty_title),
            style = KnotworkTextStyles.TitleMd,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp1))
        Text(
            text = stringResource(R.string.knotwork_chat_home_empty_subtitle),
            style = KnotworkTextStyles.BodyBase,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
        if (state.samplePrompts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp4))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            ) {
                state.samplePrompts.forEach { prompt ->
                    KnotworkChip(
                        label = prompt,
                        onClick = { callbacks.onSamplePrompt(prompt) },
                    )
                }
            }
        }
    }
}

/**
 * Renders the chronological history plus any trailing state-specific tile
 * (loader, HITL card, clarification card, error tile).
 */
@Composable
private fun ChatHomeMessageList(state: ChatHomeViewState, callbacks: ChatHomeCallbacks, padding: PaddingValues) {
    // Compose the Scaffold-provided insets with the surface's own 16dp
    // horizontal padding into a single `contentPadding` value. Applying
    // them via `Modifier.padding` would shrink the LazyColumn's
    // touch/scroll area and push the scrollbar inset away from the screen
    // edge — passing them through `contentPadding` keeps the full-width
    // scroll surface and only pads the rendered items.
    val mergedContentPadding = PaddingValues(
        start = padding.calculateStartPadding(LocalLayoutDirection.current) + ChatHorizontalPadding,
        end = padding.calculateEndPadding(LocalLayoutDirection.current) + ChatHorizontalPadding,
        top = padding.calculateTopPadding() + KnotworkTheme.spacing.sp2,
        bottom = padding.calculateBottomPadding() + KnotworkTheme.spacing.sp2,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = mergedContentPadding,
        verticalArrangement = Arrangement.spacedBy(ChatRowGap),
    ) {
        items(items = state.messages, key = { it.id }) { row ->
            ChatMessage(
                role = row.role,
                content = row.content,
                metadata = row.metadata,
                pendingTypedConfirm = state.pendingTypedConfirm,
                onTypedConfirmChange = callbacks.onHitlTypedConfirmChange,
                allowOnceEnabled = allowOnceEnabledFor(row.content, state.pendingTypedConfirm),
                onAllowOnce = callbacks.onHitlAllowOnce,
                onAllowAlways = callbacks.onHitlAllowAlways,
                onReject = callbacks.onHitlReject,
                onClarificationReply = callbacks.onClarificationReply,
                onErrorRetry = callbacks.onErrorRetry,
            )
        }
        if (state.visualState == ChatHomeVisualState.Generating) {
            item { GeneratingLoaderBubble() }
        }
        if (state.visualState == ChatHomeVisualState.Error && state.errorMessage != null) {
            item { ChatHomeErrorTile(message = state.errorMessage, onRetry = callbacks.onErrorRetry) }
        }
    }
}

/** Loader bubble shown while the assistant is producing tokens. */
@Composable
private fun GeneratingLoaderBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .fillMaxWidth(LOADER_BUBBLE_WIDTH_FRACTION)
                .clip(KnotworkTheme.shapes.md)
                .background(color = KnotworkTheme.extended.chatBotBg)
                .padding(horizontal = KnotworkTheme.spacing.sp3, vertical = KnotworkTheme.spacing.sp3),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            ) {
                KnotworkLoader()
                Text(
                    text = stringResource(R.string.knotwork_chat_home_generating_label),
                    style = KnotworkTextStyles.BodySm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }
    }
}

/** Inline error tile rendered in the trailing position of the conversation. */
@Composable
private fun ChatHomeErrorTile(message: String, onRetry: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(color = KnotworkTheme.extended.surface1)
            .border(
                border = BorderStroke(width = 1.dp, color = KnotworkTheme.extended.signalError),
                shape = KnotworkTheme.shapes.md,
            )
            .padding(KnotworkTheme.spacing.sp4),
    ) {
        Text(
            text = stringResource(R.string.knotwork_chat_home_error_title),
            style = KnotworkTextStyles.TitleMd,
            color = KnotworkTheme.extended.signalError,
        )
        Text(
            text = message,
            style = KnotworkTextStyles.BodyBase,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(modifier = Modifier.align(Alignment.End)) {
            KnotworkSecondaryButton(
                text = stringResource(R.string.knotwork_chat_home_error_retry),
                onClick = onRetry,
            )
        }
    }
}

/** Slide-in drawer overlay listing chat threads. */
@Composable
private fun ChatHomeDrawerOverlay(state: ChatHomeViewState, callbacks: ChatHomeCallbacks) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Black.copy(alpha = DRAWER_SCRIM_ALPHA))
            // The scrim itself is the dismiss surface — tapping anywhere
            // outside the drawer panel collapses the overlay. `indication =
            // null` keeps it visually inert (no ripple on the scrim).
            .scrimClickable(onClick = callbacks.onCloseDrawer),
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            color = KnotworkTheme.extended.surface1,
            tonalElevation = KnotworkTheme.elevation.el2,
            modifier = Modifier
                .width(DrawerWidth)
                .fillMaxHeight()
                // Reserve the system status-bar inset inside the panel so
                // the "Threads" header doesn't collide with the device's
                // clock / status icons.
                .windowInsetsPadding(WindowInsets.systemBars)
                // Absorb pointer events inside the panel so a tap on the
                // drawer surface does not bubble up to the scrim's dismiss
                // handler.
                .absorbClicks(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.knotwork_chat_home_drawer_threads_header),
                    style = KnotworkTextStyles.TitleLg,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(KnotworkTheme.spacing.sp4),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KnotworkTheme.spacing.sp4),
                ) {
                    KnotworkChip(
                        label = stringResource(R.string.knotwork_chat_home_drawer_new_thread),
                        onClick = {
                            callbacks.onNewThread()
                            callbacks.onCloseDrawer()
                        },
                    )
                }
                Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp2))
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(items = state.threads, key = { it.id }) { thread ->
                        ChatHomeDrawerThreadRow(
                            row = thread,
                            onClick = {
                                callbacks.onSelectThread(thread.id)
                                callbacks.onCloseDrawer()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** One thread row inside the drawer overlay. */
@Composable
private fun ChatHomeDrawerThreadRow(row: ChatHomeThreadRow, onClick: () -> Unit) {
    val container = if (row.selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = container)
            .clickable(onClick = onClick)
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp3),
    ) {
        Text(
            text = row.title,
            style = KnotworkTextStyles.TitleMd,
            color = if (row.selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Text(
            text = row.subtitle,
            style = KnotworkTextStyles.BodySm,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
    }
}

/** [ConsolePane] overlay anchored to the bottom of the chat surface. */
@Composable
private fun ChatHomeConsoleOverlay(state: ChatHomeViewState, callbacks: ChatHomeCallbacks) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(color = Color.Black.copy(alpha = CONSOLE_SCRIM_ALPHA))
                // Tapping the scrim above the console pane collapses it
                // back to the underlying chat surface. Without this the
                // tap would fall through to the chat list and could
                // trigger long-press menus on hidden bubbles.
                .scrimClickable(onClick = callbacks.onCloseConsole),
        )
        // Absorb taps inside the console pane so they don't reach the
        // scrim above; the pane has its own header / drag-handle actions.
        Box(modifier = Modifier.absorbClicks()) {
            ConsolePane(
                snap = state.console.snap,
                onSnapChange = callbacks.onConsoleSnapChange,
                tab = state.console.tab,
                onTabChange = callbacks.onConsoleTabChange,
                logs = state.console.logs,
                vars = state.console.vars,
                traces = state.console.traces,
                filter = state.console.filter,
                onFilterChange = callbacks.onConsoleFilterChange,
                onSearch = callbacks.onConsoleSearch,
                onCopyAll = callbacks.onConsoleCopyAll,
                onClear = callbacks.onConsoleClear,
            )
        }
    }
}

/**
 * Triple-tap gesture wrapper used on the TopAppBar title row. In debug
 * builds this opens the state-picker overlay; release builds wire the
 * callback to a no-op so the gesture is inert.
 *
 * Lives as a `Modifier` extension rather than a separate composable so the
 * gesture coexists with the existing semantics on the title `Column`.
 */
private fun Modifier.clickableTripleTap(onTripleTap: () -> Unit): Modifier = this.pointerInput(onTripleTap) {
    var lastTapMs = 0L
    var tapCount = 0
    detectTapGestures(
        onTap = {
            val now = System.currentTimeMillis()
            tapCount = if (now - lastTapMs <= TRIPLE_TAP_TIMEOUT_MS) tapCount + 1 else 1
            lastTapMs = now
            if (tapCount >= TRIPLE_TAP_COUNT) {
                tapCount = 0
                onTripleTap()
            }
        },
    )
}

/** Tap-count required to fire the debug state picker. */
private const val TRIPLE_TAP_COUNT = 3

/** Maximum gap between two consecutive taps to keep them in the same triple-tap sequence. */
private const val TRIPLE_TAP_TIMEOUT_MS = 400L

/**
 * Marks a scrim layer as the dismiss surface for the overlay it covers.
 * Wraps `Modifier.clickable` with a discarded `MutableInteractionSource`
 * and `indication = null` so the scrim swallows the tap silently — no
 * ripple, no touch feedback, and the click never falls through to the
 * surface underneath.
 */
@Composable
private fun Modifier.scrimClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )
}

/**
 * Marks a panel as a click-absorbing surface — taps on the panel are
 * consumed locally so they don't reach the surrounding scrim and trigger
 * its dismiss handler. Used inside [ChatHomeDrawerOverlay] and
 * [ChatHomeConsoleOverlay] so panel-internal controls (chips, drag
 * handles, tab strips) keep working without dismissing the overlay.
 */
@Composable
private fun Modifier.absorbClicks(): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = { /* no-op: just absorb the tap */ },
    )
}

/**
 * Per-row resolution of the `allowOnceEnabled` flag passed into
 * [ChatMessage] for HITL confirmations. Mirrors the rule documented in
 * `compose/components/README.md §HitlConfirmationCard`:
 *  - Readonly → auto-allowed (CTA hidden by the card).
 *  - Sensitive → always enabled.
 *  - Destructive → enabled only when the typed confirmation reads "yes".
 *
 * Non-confirmation content rows return `true` since the value is ignored by
 * the card dispatch in [ChatMessage].
 */
internal fun allowOnceEnabledFor(content: ChatContent, pendingTypedConfirm: String): Boolean {
    val confirmation = content as? ChatContent.Confirmation ?: return true
    return when (confirmation.model.risk) {
        Risk.Readonly, Risk.Sensitive -> true
        Risk.Destructive -> pendingTypedConfirm.trim().equals("yes", ignoreCase = true)
    }
}
