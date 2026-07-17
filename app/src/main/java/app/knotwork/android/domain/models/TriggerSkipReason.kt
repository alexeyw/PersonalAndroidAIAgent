package app.knotwork.android.domain.models

/**
 * Why a trigger evaluation did **not** fire the bound pipeline.
 *
 * Shared domain vocabulary between two consumers, so it lives in `domain.models`
 * rather than being local to either:
 * - the pure decision core
 *   ([app.knotwork.android.domain.usecases.EvaluateTriggerFiringUseCase]), which
 *   returns it inside its `Skip` decision, and
 * - the persistent trigger-evaluation journal ([TriggerEvaluation]), which
 *   records it verbatim so every non-fire is explainable after the fact.
 *
 * The names are **persisted** (stored on the journal row as the skip-reason
 * discriminator), so they must never be renamed — doing so would make historical
 * journal rows undecodable.
 */
enum class TriggerSkipReason {
    /** The trigger is disabled. */
    DISABLED,

    /** The trigger has no bound pipeline (inert until bound). */
    UNBOUND,

    /** The condition is not currently satisfied (not charging, no network, or before the daily time). */
    CONDITION_NOT_MET,

    /** The condition is satisfied but the trigger already fired for it (interval window not elapsed, or still disarmed). */
    ALREADY_FIRED,
}
