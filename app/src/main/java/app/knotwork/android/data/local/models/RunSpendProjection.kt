package app.knotwork.android.data.local.models

/**
 * Narrow projection of a run row's accumulated spend and its granted ceiling
 * extensions.
 *
 * Exists so the engine can seed its budget ledger at the top of a run — and,
 * on a resume, continue where the previous attempt stopped — without loading
 * and mapping a whole [PipelineRunEntity] to read four integers out of it.
 *
 * The extensions are projected alongside the spend rather than read separately
 * because a ledger seeded with one and not the other is wrong in the worst
 * direction: a run resumed after the user raised its ceiling would breach again
 * immediately and re-ask the question that was just answered.
 *
 * @property stepsSpent Node executions already charged to the run tree.
 * @property tokensSpent Tokens already charged to the run tree.
 * @property stepCeilingExtensions Extra portions of the step ceiling granted to
 *   this run tree.
 * @property tokenCeilingExtensions Extra portions of the token ceiling granted
 *   to this run tree.
 */
data class RunSpendProjection(
    val stepsSpent: Int,
    val tokensSpent: Int,
    val stepCeilingExtensions: Int,
    val tokenCeilingExtensions: Int,
)
