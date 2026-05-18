package ai.agent.android.presentation.ui.chat.home

import app.knotwork.design.components.chips.Risk
import app.knotwork.design.components.console.ConsoleSnap

/**
 * Sealed UI state for the redesigned chat home (`compose/screens/README.md
 * §C1 · Chat (home)`). Drives the 9-state visual matrix locked-in by Phase
 * 21 / Task 8.
 *
 * The state is **deterministic** — the stub [ChatHomeViewModel] flips
 * between variants for the debug state picker and the real orchestrator
 * wiring lands in a follow-up task after v0.1.
 *
 * The dark-theme variant is cross-cutting (driven by `isSystemInDarkTheme`
 * via `KnotworkTheme`) and is therefore not modelled as a separate state.
 */
sealed interface ChatHomeUiState {

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

    /**
     * Console pane expanded over the chat surface.
     *
     * @property snap snap position the pane should adopt on first render.
     */
    data class ConsoleExpanded(val snap: ConsoleSnap) : ChatHomeUiState
}
