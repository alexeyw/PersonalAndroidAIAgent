package app.knotwork.android.domain.models

/**
 * Typed result of the live in-process waiting phase of a clarification
 * request (see
 * [app.knotwork.android.domain.repositories.ClarificationRepository.requestAnswer]).
 *
 * Distinguishing a timeout from an answer at the type level moves the
 * timeout policy to the caller: the clarification node executor parks a
 * persisted run on its pending-interaction record instead of fabricating a
 * default answer, while non-persisted runs (editor test runs) keep the
 * legacy default-answer fallback.
 */
sealed class ClarificationOutcome {

    /**
     * The user answered within the live waiting window.
     *
     * @property answer The user's reply text (option label or free-form).
     */
    data class Answered(val answer: String) : ClarificationOutcome()

    /** The live waiting window elapsed without an answer. */
    data object TimedOut : ClarificationOutcome()
}
