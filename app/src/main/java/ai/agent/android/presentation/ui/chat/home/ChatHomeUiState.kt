package ai.agent.android.presentation.ui.chat.home

import app.knotwork.design.components.chips.Risk

/**
 * Sealed UI state for the redesigned chat home. Drives the 9-state visual
 * matrix.
 *
 * The dark-theme variant is cross-cutting (driven by `isSystemInDarkTheme`
 * via `KnotworkTheme`) and is therefore not modelled as a separate state.
 */
sealed interface ChatHomeUiState {

    /**
     * Cold-start sentinel emitted before the chat repository finishes
     * delivering the first message snapshot. Distinct from [Empty]: while
     * `Loading`, the surface shows a centered progress indicator (no empty
     * placeholder copy) so users don't see the "no messages yet" hero flash
     * for a frame on every app launch.
     */
    data object Loading : ChatHomeUiState

    /** No messages in the active thread; empty state with sample-prompt chips. */
    data object Empty : ChatHomeUiState

    /** History present, no in-flight request. Default after first send. */
    data object Idle : ChatHomeUiState

    /** The assistant is producing tokens; composer morphs to stop. */
    data object Generating : ChatHomeUiState

    /**
     * A tool call awaits user approval. The bubble at the tail of the
     * conversation hosts the HITL confirmation card whose visuals depend
     * on the [risk] tier.
     *
     * @property risk risk tier driving the card border + buttons.
     */
    data class HitlConfirm(val risk: Risk) : ChatHomeUiState

    /** The assistant needs more info; trailing clarification card. */
    data object Clarification : ChatHomeUiState

    /**
     * Inline error tile + retry CTA. The user-visible message is carried
     * here so the surface can render the failure cause without consulting
     * any other source of truth.
     *
     * @property message user-visible error description.
     */
    data class Error(val message: String) : ChatHomeUiState

    /** Alternate nav drawer over the chat surface. */
    data object DrawerOpen : ChatHomeUiState
}
