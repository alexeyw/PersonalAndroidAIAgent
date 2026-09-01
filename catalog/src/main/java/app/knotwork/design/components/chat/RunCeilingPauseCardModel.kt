package app.knotwork.design.components.chat

/**
 * Immutable payload of a [RunCeilingPauseCard] — the status card surfaced in the
 * chat stream when a run has spent one of the limits the user set for it and is
 * waiting to be told whether it may carry on.
 *
 * Every field is an already-resolved string, following the
 * [InterruptedRunCardModel] precedent: which limit bound, what the numbers mean
 * and how large the next portion is are all decisions the host makes from the
 * typed run model, and the catalog renders what it is given.
 *
 * The two CTA labels are supplied rather than fixed here for one specific
 * reason: the affirmative one names the *size* of the grant. A button reading
 * only "Continue" would suggest the limit is being lifted, when what the tap
 * actually buys is one more portion of the same allowance.
 *
 * @property title One line naming what happened. Says paused, not stopped —
 *   the run is still there.
 * @property body What continuing means, in the units of the limit that bound.
 * @property meter The numbers behind the pause, rendered on their own muted
 *   line so the sentence above reads the same whatever they are.
 * @property continueLabel Affirmative CTA label, including the size of the
 *   portion the tap grants.
 * @property stopLabel Negative CTA label.
 */
data class RunCeilingPauseCardModel(
    val title: String,
    val body: String,
    val meter: String,
    val continueLabel: String,
    val stopLabel: String,
)
