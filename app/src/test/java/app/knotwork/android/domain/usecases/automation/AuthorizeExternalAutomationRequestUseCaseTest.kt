package app.knotwork.android.domain.usecases.automation

import app.knotwork.android.domain.constants.ExternalAutomationContract
import app.knotwork.android.domain.models.ExternalAutomationBinding
import app.knotwork.android.domain.models.ExternalAutomationRejectionReason
import app.knotwork.android.domain.models.ExternalAutomationRequest
import app.knotwork.android.domain.models.ExternalAutomationStatus
import app.knotwork.android.domain.models.ExternalAutomationTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [AuthorizeExternalAutomationRequestUseCase] — the security
 * model of the external entry point.
 *
 * The allowlist reading of the binding is asserted directly here rather than
 * through the receiver, because it is the decision, not its plumbing, that a
 * regression would compromise: a fallback reading would still pass every
 * end-to-end happy path while silently opening the whole library to callers.
 */
class AuthorizeExternalAutomationRequestUseCaseTest {

    private val useCase = AuthorizeExternalAutomationRequestUseCase()

    private val binding = ExternalAutomationBinding(pipelineId = "pipe-1", pipelineName = "Evening journal")

    private fun request(target: ExternalAutomationTarget) = ExternalAutomationRequest(
        target = target,
        prompt = "go",
        requestId = "req-1",
        returnAction = ExternalAutomationContract.ACTION_RUN_RESULT,
    )

    @Test
    fun `given the contract is off when authorizing then it is rejected as disabled`() {
        val result =
            useCase(request(ExternalAutomationTarget.ById("pipe-1")), contractEnabled = false, binding = binding)

        assertEquals(ExternalAutomationStatus.Rejected(ExternalAutomationRejectionReason.CONTRACT_DISABLED), result)
    }

    @Test
    fun `given the contract is off and nothing is bound when authorizing then disabled outranks unbound`() {
        // The switched-off contract must not reveal whether a binding exists.
        val result = useCase(request(ExternalAutomationTarget.ById("pipe-1")), contractEnabled = false, binding = null)

        assertEquals(ExternalAutomationStatus.Rejected(ExternalAutomationRejectionReason.CONTRACT_DISABLED), result)
    }

    @Test
    fun `given no binding when authorizing then it is rejected as unbound`() {
        val result = useCase(request(ExternalAutomationTarget.ById("pipe-1")), contractEnabled = true, binding = null)

        assertEquals(ExternalAutomationStatus.Rejected(ExternalAutomationRejectionReason.SURFACE_NOT_BOUND), result)
    }

    @Test
    fun `given a target matching the binding by id when authorizing then it is accepted`() {
        val result =
            useCase(request(ExternalAutomationTarget.ById("pipe-1")), contractEnabled = true, binding = binding)

        assertEquals(ExternalAutomationStatus.Accepted, result)
    }

    @Test
    fun `given a target matching the binding by name when authorizing then it is accepted`() {
        val result = useCase(
            request(ExternalAutomationTarget.ByName("Evening journal")),
            contractEnabled = true,
            binding = binding,
        )

        assertEquals(ExternalAutomationStatus.Accepted, result)
    }

    @Test
    fun `given another pipeline by id when authorizing then it is rejected instead of running the bound one`() {
        val result =
            useCase(request(ExternalAutomationTarget.ById("pipe-2")), contractEnabled = true, binding = binding)

        assertEquals(ExternalAutomationStatus.Rejected(ExternalAutomationRejectionReason.TARGET_NOT_ALLOWED), result)
    }

    @Test
    fun `given another pipeline by name when authorizing then it is rejected instead of running the bound one`() {
        val result = useCase(
            request(ExternalAutomationTarget.ByName("Delete everything")),
            contractEnabled = true,
            binding = binding,
        )

        assertEquals(ExternalAutomationStatus.Rejected(ExternalAutomationRejectionReason.TARGET_NOT_ALLOWED), result)
    }

    @Test
    fun `given a name differing only in case when authorizing then it is rejected rather than matched loosely`() {
        val result = useCase(
            request(ExternalAutomationTarget.ByName("evening journal")),
            contractEnabled = true,
            binding = binding,
        )

        assertEquals(ExternalAutomationStatus.Rejected(ExternalAutomationRejectionReason.TARGET_NOT_ALLOWED), result)
    }

    @Test
    fun `given a name matching the bound id when authorizing then the target form is not interchangeable`() {
        // Naming a pipeline "pipe-1" must not let a by-name request through on
        // the strength of the bound pipeline's id.
        val result = useCase(
            request(ExternalAutomationTarget.ByName("pipe-1")),
            contractEnabled = true,
            binding = binding,
        )

        assertEquals(ExternalAutomationStatus.Rejected(ExternalAutomationRejectionReason.TARGET_NOT_ALLOWED), result)
    }

    @Test
    fun `given a binding when asked directly then it allows only its own pipeline`() {
        assertEquals(true, binding.allows(ExternalAutomationTarget.ById("pipe-1")))
        assertEquals(true, binding.allows(ExternalAutomationTarget.ByName("Evening journal")))
        assertEquals(false, binding.allows(ExternalAutomationTarget.ById("pipe-2")))
        assertEquals(false, binding.allows(ExternalAutomationTarget.ByName("Something else")))
    }
}
