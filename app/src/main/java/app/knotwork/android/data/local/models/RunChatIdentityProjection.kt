package app.knotwork.android.data.local.models

/**
 * The two columns needed to decide whether a settled run should leave a line in
 * a chat, and in which one.
 *
 * A projection rather than a whole [app.knotwork.android.data.local.entities.PipelineRunEntity]
 * for the reason `RunSpendProjection` gives: this is read on the terminal
 * transition of every run, and mapping a full row to look at two columns puts
 * that cost on the hot completion path.
 *
 * @property sessionId Chat session the run belongs to — where the line goes.
 * @property parentRunId Parent run when this is a nested sub-pipeline child,
 *   `null` for a root run. A child's failure reaches the user as the root's, so
 *   only roots are announced.
 */
data class RunChatIdentityProjection(val sessionId: String, val parentRunId: String?)
