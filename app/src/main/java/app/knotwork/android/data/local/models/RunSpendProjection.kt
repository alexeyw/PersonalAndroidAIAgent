package app.knotwork.android.data.local.models

/**
 * Two-column projection of a run row's accumulated spend.
 *
 * Exists so the engine can seed its budget ledger at the top of a run — and,
 * on a resume, continue where the previous attempt stopped — without loading
 * and mapping a whole [PipelineRunEntity] to read two integers out of it.
 *
 * @property stepsSpent Node executions already charged to the run tree.
 * @property tokensSpent Tokens already charged to the run tree.
 */
data class RunSpendProjection(val stepsSpent: Int, val tokensSpent: Int)
