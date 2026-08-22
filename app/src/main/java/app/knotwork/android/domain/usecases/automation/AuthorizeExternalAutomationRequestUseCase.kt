package app.knotwork.android.domain.usecases.automation

import app.knotwork.android.domain.models.ExternalAutomationBinding
import app.knotwork.android.domain.models.ExternalAutomationRejectionReason
import app.knotwork.android.domain.models.ExternalAutomationRequest
import app.knotwork.android.domain.models.ExternalAutomationStatus
import javax.inject.Inject

/**
 * Decides whether a well-formed [ExternalAutomationRequest] may run at all.
 *
 * This use case *is* the security model of the external entry point, written as
 * a pure function so the model itself is under test rather than only its
 * plumbing. Everything it needs — whether the user switched the contract on, and
 * what they pointed it at — is passed in; it reads nothing and starts nothing.
 *
 * **The binding is an allowlist, not a default.** A request must name the
 * pipeline it wants, and a request naming anything else is refused rather than
 * redirected to the bound one. The alternative — treating the binding as a
 * fallback — would let any app that can reach the entry point run any pipeline
 * in the library by name, which is not what a user consents to when they pick
 * one pipeline in the settings surface.
 *
 * Two refusals that look similar are deliberately distinct: [ExternalAutomationRejectionReason.SURFACE_NOT_BOUND]
 * (the user has allowed nothing) and [ExternalAutomationRejectionReason.TARGET_NOT_ALLOWED]
 * (the user has allowed something else). They call for different actions from
 * the user, so the journal and the callback must be able to tell them apart.
 */
class AuthorizeExternalAutomationRequestUseCase @Inject constructor() {

    /**
     * Authorizes [request] against the current configuration.
     *
     * [ExternalAutomationStatus.Accepted] means "permitted", not "enqueued": the
     * rate ceiling is applied after this decision, at the moment of acceptance,
     * because it is a statement about the moment rather than about the request.
     *
     * @param request The parsed request.
     * @param contractEnabled Whether the user switched the external-automation
     *   contract on. `false` is its default state, and it is checked first: a
     *   switched-off contract must not even reveal whether a pipeline exists.
     * @param binding The pipeline the user allowed external automation to run,
     *   or `null` when the surface is unbound.
     * @return [ExternalAutomationStatus.Accepted] when the request may proceed,
     *   otherwise [ExternalAutomationStatus.Rejected] with the reason.
     */
    operator fun invoke(
        request: ExternalAutomationRequest,
        contractEnabled: Boolean,
        binding: ExternalAutomationBinding?,
    ): ExternalAutomationStatus = when {
        !contractEnabled -> rejected(ExternalAutomationRejectionReason.CONTRACT_DISABLED)
        binding == null -> rejected(ExternalAutomationRejectionReason.SURFACE_NOT_BOUND)
        !binding.allows(request.target) -> rejected(ExternalAutomationRejectionReason.TARGET_NOT_ALLOWED)
        else -> ExternalAutomationStatus.Accepted
    }

    /**
     * Builds a refusal status.
     *
     * @param reason Why the request was refused.
     * @return The rejected status carrying [reason].
     */
    private fun rejected(reason: ExternalAutomationRejectionReason): ExternalAutomationStatus =
        ExternalAutomationStatus.Rejected(reason)
}
