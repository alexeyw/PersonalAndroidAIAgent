package app.knotwork.design.components.chat

/**
 * Immutable payload of an [InterruptedRunCard] — the status card surfaced in
 * the chat stream when the session's most recent pipeline run was interrupted
 * by a process death (Doze, OOM kill, swipe from recents) before reaching a
 * terminal state.
 *
 * The catalog takes already-resolved strings: mapping a node id to its
 * display label is a presentation-layer concern.
 *
 * @property nodeLabel display label of the graph node the run stopped at
 *   (e.g. `"Summarise"`, `"LITE_RT"`), already resolved by the host. The
 *   card renders it inside the "interrupted at node …" body line.
 * @property resumable whether the run can still be resumed from its
 *   checkpoint. When `false` (the resume window elapsed, or the record
 *   predates checkpoint support) the card hides the Resume CTA, shows the
 *   expired note, and offers Discard only.
 */
data class InterruptedRunCardModel(val nodeLabel: String, val resumable: Boolean = true)
