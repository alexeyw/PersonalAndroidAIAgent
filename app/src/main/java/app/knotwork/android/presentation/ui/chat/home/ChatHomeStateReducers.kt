package app.knotwork.android.presentation.ui.chat.home

/**
 * Shared pure transformers over [ChatHomeScreenState] used by both the
 * [ChatHomeViewModel] core (send / lifecycle) and several of its delegates
 * (HITL, reattach). Kept as `internal` top-level extensions — rather than on
 * any single owner — because they encode cross-cutting visual/pending rules
 * that no one delegate owns, and they must compose into each caller's single
 * atomic `state.update { ... }` block (never their own emission) so multi-field
 * transitions remain one flow emission.
 */

/**
 * Resting (non-overlay) visual for this snapshot — `Interrupted` while an
 * interrupted-run snapshot is pending (the status card must survive transient
 * overlays like the drawer; dropping to Idle would strand the Resume / Discard
 * actions until the next thread switch), otherwise `Empty` / `Idle` by
 * message-list presence. Derived from the receiver (never `_state.value`) so
 * calls inside a `state.update` lambda stay consistent with the snapshot being
 * transformed.
 */
internal fun ChatHomeScreenState.restingVisual(): ChatHomeUiState = when {
    pending.interrupted != null -> ChatHomeUiState.Interrupted
    messages.isEmpty() -> ChatHomeUiState.Empty
    else -> ChatHomeUiState.Idle
}

/**
 * Pure transformer: drops every pending HITL / clarification snapshot and
 * resets the typed-confirm input. Composed into the caller's `state.update`
 * block (never its own emission) so multi-field transitions remain a single
 * atomic flow emission.
 */
internal fun ChatHomeScreenState.withPendingCleared(): ChatHomeScreenState = copy(
    pending = ChatHomePendingState(),
    composer = composer.copy(typedConfirm = ""),
)
