package app.knotwork.android.presentation.ui.common

import androidx.annotation.StringRes
import app.knotwork.android.R
import app.knotwork.android.domain.models.RunCeilingAxis
import app.knotwork.android.domain.models.RunNoticeCause
import app.knotwork.android.domain.models.RunTerminationKind
import app.knotwork.android.domain.models.RunTerminationReason

/**
 * How loudly a stopped run should speak, and with which glyph.
 *
 * Status is never carried by colour alone in this app, so every tone pairs a
 * colour with a word and an icon. The three tones are a deliberate ranking:
 *
 *  - [LIMIT] — a guard the user configured did exactly what it was configured
 *    to do. Not a fault, and not styled as one.
 *  - [STUCK] — the run misbehaved and was ended. A defect-shaped event, but the
 *    app caught it.
 *  - [INFO] — housekeeping. The run ended for a reason that implies nothing
 *    about the pipeline's quality: the app was killed, the user discarded it,
 *    an approval window closed.
 *
 * An ordinary node failure has no tone here at all — it is not a typed
 * termination, and it keeps the destructive-toned error tile it always had.
 *
 * @property labelRes The word shown beside the glyph.
 */
enum class RunTerminationTone(@StringRes val labelRes: Int) {
    LIMIT(R.string.run_termination_tone_limit),
    STUCK(R.string.run_termination_tone_stuck),
    INFO(R.string.run_termination_tone_info),
}

/**
 * The one action offered on a stopped run.
 *
 * **Retry is deliberately absent from this list.** Every typed termination is a
 * decision the app made about *this* run, and re-running the identical turn
 * walks into the identical wall — offering Retry teaches the user the app is
 * broken when it is merely bounded. Retry survives only on the untyped error
 * tile, where a transient fault genuinely may not recur.
 *
 * @property labelRes The button label.
 */
enum class RunTerminationAction(@StringRes val labelRes: Int) {
    /** Opens the run-limits screen. Offered when a ceiling bound the run. */
    ADJUST_LIMITS(R.string.run_termination_action_adjust_limits),

    /** Opens the run console, where the repetition is visible. */
    OPEN_CONSOLE(R.string.run_termination_action_open_console),

    /** Starts the same request again — offered only where a fresh run can succeed. */
    RUN_AGAIN(R.string.run_termination_action_run_again),
}

/**
 * Everything the chat surface needs to explain one stopped run.
 *
 * @property tone Ranking and glyph, see [RunTerminationTone].
 * @property title One line naming what happened.
 * @property body Two sentences: what happened and what to do about it.
 * @property meter The numbers behind the decision, or `null` when the reason
 *   has none. Rendered in tabular figures on its own line — deliberately not
 *   interpolated into [body], so the same sentence works whether or not the
 *   numbers are available.
 * @property banner A single clause for the composer banner. **Not** the same
 *   string as [title] + [body]: the banner is clamped to two lines, and sharing
 *   one sentence between the two is what cut the copy in half at large font
 *   scales.
 * @property action The offered action, or `null` when there is nothing useful
 *   to offer (a run the user discarded on purpose).
 */
data class RunTerminationCopy(
    val tone: RunTerminationTone,
    val title: UiText,
    val body: UiText,
    val meter: UiText?,
    val banner: UiText,
    val action: RunTerminationAction?,
)

/**
 * The advisory shown above the composer while a run is still going.
 *
 * @property tone Which glyph and colour the notice carries.
 * @property text The whole sentence — a notice is one line, not a card.
 */
data class RunNoticeCopy(val tone: RunTerminationTone, val text: UiText)

/**
 * Title and body for the notification posted when a background run stops.
 *
 * @property title What happened, in the same words the chat and the trigger
 *   journal use.
 * @property body One sentence naming the run and, for a ceiling, what it spent.
 *   Never the allowance: the persisted record does not carry the limit, and a
 *   token stop routinely lands past it — see [RunTerminationCopyMapper.notificationCopy].
 */
data class RunTerminationNotificationCopy(val title: UiText, val body: UiText)

/**
 * The single place a typed run outcome becomes words.
 *
 * Every surface that has to explain why a run stopped — the chat tile, the
 * composer banner, the foreground-service notification, the background-run
 * notification — resolves its text here. That is the whole point of the class:
 * before it, one event had accumulated four different wordings across the
 * engine, the settings search and the documentation, **two of which described a
 * pause the engine has never performed**. A vocabulary with one owner cannot
 * drift from itself.
 *
 * Everything except the numbers depends only on [RunTerminationKind], so a
 * surface holding a run read back from storage gets the same sentence as a
 * surface watching it live; only the numbers line differs, because only a live
 * run knows the limit that bound it.
 */
object RunTerminationCopyMapper {

    /**
     * Chat copy for a run that has just stopped.
     *
     * @param reason The typed cause carried by the terminal orchestrator state.
     * @return Title, body, numbers, banner clause and action.
     */
    fun terminationCopy(reason: RunTerminationReason): RunTerminationCopy = RunTerminationCopy(
        tone = toneFor(reason.kind),
        title = titleFor(reason.kind),
        body = bodyFor(reason.kind),
        meter = meterFor(reason),
        banner = bannerFor(reason),
        action = actionFor(reason.kind),
    )

    /**
     * Copy for the advisory shown while a run is still in flight.
     *
     * @param cause What the notice is about.
     * @return Tone and sentence.
     */
    fun noticeCopy(cause: RunNoticeCause): RunNoticeCopy = when (cause) {
        is RunNoticeCause.ApproachingCeiling -> RunNoticeCopy(
            tone = RunTerminationTone.LIMIT,
            text = UiText.Resource(
                id = when (cause.axis) {
                    RunCeilingAxis.STEPS -> R.string.run_notice_soft_steps
                    // The money axis is not measured, so it cannot be
                    // approached; a notice for it is unreachable rather than
                    // merely unlikely, and the token wording is the honest
                    // fallback if that ever changes.
                    RunCeilingAxis.TOKENS, RunCeilingAxis.MONEY -> R.string.run_notice_soft_tokens
                },
                args = listOf(cause.spent, cause.hardLimit),
            ),
        )
        // One sentence for both signals. The signal is the console's business;
        // to the person watching, "this keeps repeating" is the whole story,
        // and splitting it in two would make the reader work out which of two
        // near-identical wordings they were shown.
        is RunNoticeCause.LooksStuck -> RunNoticeCopy(
            tone = RunTerminationTone.STUCK,
            text = UiText.Resource(R.string.run_notice_looks_stuck),
        )
    }

    /**
     * Notification copy for a background run that stopped.
     *
     * Reads from the persisted record, so the ceiling that bound the run is not
     * available. The body therefore reports the **spend**, never the allowance
     * — and the distinction is not pedantry. Steps are charged one at a time
     * and checked before charging, so a step stop really does land on its
     * ceiling; tokens are charged a whole node's usage at once and only then
     * compared, so a token stop routinely lands *past* it. Wording either as
     * "used all N it was allowed" would state the overshoot as the configured
     * limit, and contradict the chat, which shows both numbers.
     *
     * @param kind The persisted cause.
     * @param runLabel Human name of the run's session, quoted in the body.
     * @param stepsSpent Node executions charged across the run tree.
     * @param tokensSpent Tokens accounted across the run tree.
     * @return The title and body to post, in the same words every other surface
     *   uses for the same event.
     */
    fun notificationCopy(
        kind: RunTerminationKind,
        runLabel: String,
        stepsSpent: Int,
        tokensSpent: Int,
    ): RunTerminationNotificationCopy = RunTerminationNotificationCopy(
        title = titleFor(kind),
        body = when (kind) {
            RunTerminationKind.STEP_CEILING -> UiText.Resource(
                R.string.run_termination_notification_body_step_ceiling,
                listOf(runLabel, stepsSpent),
            )
            RunTerminationKind.TOKEN_CEILING -> UiText.Resource(
                R.string.run_termination_notification_body_token_ceiling,
                listOf(runLabel, tokensSpent),
            )
            // The six housekeeping reasons say the same thing here as in the
            // chat. Inventing a second, notification-only sentence for them is
            // exactly how the wording fragmented the first time.
            // Neither body promises a destination: the notification's only tap
            // action deep-links to the chat, which shows nothing about a run
            // that already finished, so "tap to review the limits" would have
            // been a promise the tap does not keep.
            else -> bodyFor(kind)
        },
    )

    /**
     * How loudly this cause should be announced.
     *
     * Public because surfaces that render no sentence still have to pick a
     * glyph — the background notification does exactly that — and deriving the
     * tone a second time is how one event ends up looking like two.
     *
     * @param kind The persisted cause.
     * @return Its tone.
     */
    fun toneFor(kind: RunTerminationKind): RunTerminationTone = when (kind) {
        RunTerminationKind.STEP_CEILING, RunTerminationKind.TOKEN_CEILING -> RunTerminationTone.LIMIT
        RunTerminationKind.NO_PROGRESS, RunTerminationKind.RUN_STALLED -> RunTerminationTone.STUCK
        RunTerminationKind.HITL_WINDOW_EXPIRED,
        RunTerminationKind.GRAPH_CHANGED,
        RunTerminationKind.PROCESS_DIED,
        RunTerminationKind.DISCARDED_BY_USER,
        RunTerminationKind.NOT_RESUMABLE,
        -> RunTerminationTone.INFO
    }

    private fun titleFor(kind: RunTerminationKind): UiText = UiText.Resource(
        when (kind) {
            // Both ceilings share one title on purpose: from the user's side it
            // is one event — a limit they set stopped the run — and the body
            // says which one. The wording is the trigger journal's, verbatim.
            RunTerminationKind.STEP_CEILING,
            RunTerminationKind.TOKEN_CEILING,
            -> R.string.run_termination_title_ceiling
            RunTerminationKind.NO_PROGRESS -> R.string.run_termination_title_no_progress
            RunTerminationKind.RUN_STALLED -> R.string.run_termination_title_run_stalled
            RunTerminationKind.HITL_WINDOW_EXPIRED -> R.string.run_termination_title_hitl_expired
            RunTerminationKind.GRAPH_CHANGED -> R.string.run_termination_title_graph_changed
            RunTerminationKind.PROCESS_DIED -> R.string.run_termination_title_process_died
            RunTerminationKind.DISCARDED_BY_USER -> R.string.run_termination_title_discarded
            RunTerminationKind.NOT_RESUMABLE -> R.string.run_termination_title_not_resumable
        },
    )

    private fun bodyFor(kind: RunTerminationKind): UiText = UiText.Resource(
        when (kind) {
            RunTerminationKind.STEP_CEILING -> R.string.run_termination_body_step_ceiling
            RunTerminationKind.TOKEN_CEILING -> R.string.run_termination_body_token_ceiling
            RunTerminationKind.NO_PROGRESS -> R.string.run_termination_body_no_progress
            RunTerminationKind.RUN_STALLED -> R.string.run_termination_body_run_stalled
            RunTerminationKind.HITL_WINDOW_EXPIRED -> R.string.run_termination_body_hitl_expired
            RunTerminationKind.GRAPH_CHANGED -> R.string.run_termination_body_graph_changed
            RunTerminationKind.PROCESS_DIED -> R.string.run_termination_body_process_died
            RunTerminationKind.DISCARDED_BY_USER -> R.string.run_termination_body_discarded
            RunTerminationKind.NOT_RESUMABLE -> R.string.run_termination_body_not_resumable
        },
    )

    private fun actionFor(kind: RunTerminationKind): RunTerminationAction? = when (kind) {
        RunTerminationKind.STEP_CEILING,
        RunTerminationKind.TOKEN_CEILING,
        -> RunTerminationAction.ADJUST_LIMITS
        RunTerminationKind.NO_PROGRESS -> RunTerminationAction.OPEN_CONSOLE
        // Not OPEN_CONSOLE: a run that went silent left nothing in the console
        // to look at, which is exactly what distinguishes it from a loop. The
        // step it hung on may well answer next time, so running it again is the
        // one action that can help.
        RunTerminationKind.RUN_STALLED,
        RunTerminationKind.HITL_WINDOW_EXPIRED,
        RunTerminationKind.GRAPH_CHANGED,
        RunTerminationKind.PROCESS_DIED,
        RunTerminationKind.NOT_RESUMABLE,
        -> RunTerminationAction.RUN_AGAIN
        // The user ended this run on purpose. Offering to start it again would
        // be the app arguing with a decision it was just given.
        RunTerminationKind.DISCARDED_BY_USER -> null
    }

    private fun meterFor(reason: RunTerminationReason): UiText? = when (reason) {
        is RunTerminationReason.StepCeiling -> UiText.Resource(
            R.string.run_termination_meter_steps_live,
            listOf(reason.spent, reason.limit),
        )
        is RunTerminationReason.TokenCeiling -> UiText.Resource(
            R.string.run_termination_meter_tokens_live,
            listOf(reason.spent, reason.limit),
        )
        RunTerminationReason.HitlWindowExpired,
        RunTerminationReason.NoProgress,
        RunTerminationReason.RunStalled,
        RunTerminationReason.GraphChanged,
        RunTerminationReason.ProcessDied,
        RunTerminationReason.DiscardedByUser,
        RunTerminationReason.NotResumable,
        -> null
    }

    private fun bannerFor(reason: RunTerminationReason): UiText = when (reason) {
        is RunTerminationReason.StepCeiling -> UiText.Resource(
            R.string.run_termination_banner_step_ceiling,
            listOf(reason.spent, reason.limit),
        )
        is RunTerminationReason.TokenCeiling -> UiText.Resource(
            R.string.run_termination_banner_token_ceiling,
            listOf(reason.spent, reason.limit),
        )
        RunTerminationReason.NoProgress -> UiText.Resource(R.string.run_termination_banner_no_progress)
        RunTerminationReason.RunStalled -> UiText.Resource(R.string.run_termination_banner_run_stalled)
        RunTerminationReason.HitlWindowExpired -> UiText.Resource(R.string.run_termination_banner_hitl_expired)
        RunTerminationReason.GraphChanged -> UiText.Resource(R.string.run_termination_banner_graph_changed)
        RunTerminationReason.ProcessDied -> UiText.Resource(R.string.run_termination_banner_process_died)
        RunTerminationReason.DiscardedByUser -> UiText.Resource(R.string.run_termination_banner_discarded)
        RunTerminationReason.NotResumable -> UiText.Resource(R.string.run_termination_banner_not_resumable)
    }
}
