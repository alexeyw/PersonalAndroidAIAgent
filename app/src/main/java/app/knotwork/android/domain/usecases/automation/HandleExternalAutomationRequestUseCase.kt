package app.knotwork.android.domain.usecases.automation

import app.knotwork.android.domain.constants.ExternalAutomationContract
import app.knotwork.android.domain.models.ExternalAutomationBinding
import app.knotwork.android.domain.models.ExternalAutomationInvocation
import app.knotwork.android.domain.models.ExternalAutomationJournalEntry
import app.knotwork.android.domain.models.ExternalAutomationRejectionReason
import app.knotwork.android.domain.models.ExternalAutomationRequest
import app.knotwork.android.domain.models.ExternalAutomationStatus
import app.knotwork.android.domain.models.ExternalAutomationTarget
import app.knotwork.android.domain.models.RunOrigin
import app.knotwork.android.domain.repositories.ExternalAutomationJournalRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.services.ScheduledTaskConstraints
import app.knotwork.android.domain.services.TaskScheduler
import app.knotwork.android.domain.usecases.RunRateCeiling
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Decides what happens to one inbound external-automation call, and — when it is
 * admitted — starts the run.
 *
 * This is the **single write seam** of the external-request journal: every path
 * out of [invoke] leaves exactly one journal record, which is what makes the
 * no-silent-skips invariant of `DESCRIPTION.md §12.3` hold at this entry point.
 * A refusal because the contract is switched off is journalled just as loudly as
 * an admission — without that, "the user never turned it on" and "the broadcast
 * never arrived" are indistinguishable from inside the app, and those are the two
 * things a caller debugging a silent profile most needs to tell apart.
 *
 * **No leniency against the app's own background runs.** Admission ends at
 * [TaskScheduler.scheduleOneTime] with [RunOrigin.EXTERNAL], the same entry the
 * scheduler and the trigger runtime use. There is no second execution path, so
 * human-in-the-loop gates, per-tool risk overrides and the destructive-tool block
 * apply by construction rather than by remembering to re-apply them: an external
 * call is a request to run something, never a form of approval for what it does.
 *
 * **Order of checks is the security model, read top to bottom:** the contract must
 * be on, the surface must be bound, the request must name that exact pipeline, and
 * only then does the rate ceiling get consulted. The three policy checks are a pure
 * function ([AuthorizeExternalAutomationRequestUseCase]); only the ceiling needs
 * storage, because only it is a statement about the moment rather than the request.
 *
 * @property parseRequest Pure syntax validation of the raw call.
 * @property authorizeRequest Pure policy decision over a parsed request.
 * @property settingsRepository Source of the master switch and the surface binding.
 * @property pipelineRepository Resolves the bound pipeline, whose name completes
 *   the binding a by-name request is matched against.
 * @property journal The request journal — also the ledger the rate ceiling counts.
 * @property taskScheduler The one execution path an admitted request may take.
 */
class HandleExternalAutomationRequestUseCase @Inject constructor(
    private val parseRequest: ParseExternalAutomationRequestUseCase,
    private val authorizeRequest: AuthorizeExternalAutomationRequestUseCase,
    private val settingsRepository: SettingsRepository,
    private val pipelineRepository: PipelineRepository,
    private val journal: ExternalAutomationJournalRepository,
    private val taskScheduler: TaskScheduler,
) {

    /**
     * Handles one inbound call end to end.
     *
     * @param invocation The raw call as received at the entry point.
     * @param attestedSenderPackage The sending package if the system supplied one.
     *   Ordinarily `null`: a broadcast carries the sender's identity only when the
     *   sender opted in, which automation apps and `adb` do not. Recorded for
     *   diagnostics and used to catch a caller naming someone else's package for
     *   the callback; never used as authorisation, because a value that is almost
     *   always absent cannot carry a security decision.
     * @param nowMillis Current wall-clock time, epoch-millis (injectable for tests).
     * @return The status to answer the caller with, and to journal.
     */
    suspend operator fun invoke(
        invocation: ExternalAutomationInvocation,
        attestedSenderPackage: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): ExternalAutomationStatus {
        val parsed = when (val result = parseRequest(invocation)) {
            is ExternalAutomationParseResult.Invalid ->
                return refuse(invocation, null, attestedSenderPackage, nowMillis, rejected(result.reason))
            is ExternalAutomationParseResult.Parsed -> result.request
        }

        // The caller may not aim the callback at a third party. Only checkable on
        // the rare call that shared its identity, so it is a bonus guard rather
        // than the trust model — but when the system does tell us who called, a
        // mismatch is a caller trying to make this app broadcast at someone else.
        if (attestedSenderPackage != null &&
            parsed.returnPackage != null &&
            parsed.returnPackage != attestedSenderPackage
        ) {
            val status = rejected(ExternalAutomationRejectionReason.TARGET_NOT_ALLOWED)
            return refuse(invocation, parsed, attestedSenderPackage, nowMillis, status)
        }

        val decision = authorizeRequest(
            request = parsed,
            contractEnabled = settingsRepository.externalAutomationEnabled.first(),
            binding = resolveBinding(),
        )
        if (decision is ExternalAutomationStatus.Rejected) {
            return refuse(invocation, parsed, attestedSenderPackage, nowMillis, decision)
        }

        return admit(invocation, parsed, attestedSenderPackage, nowMillis)
    }

    /**
     * Reads the surface binding and completes it with the bound pipeline's name.
     *
     * A binding pointing at a pipeline that no longer exists resolves to `null` —
     * i.e. an unbound surface — rather than to a binding nothing can satisfy: the
     * user's intent ended when the pipeline did, and `SURFACE_NOT_BOUND` describes
     * the situation the user must act on.
     *
     * @return The binding, or `null` when the surface is unbound or its pipeline
     *   has been deleted.
     */
    private suspend fun resolveBinding(): ExternalAutomationBinding? {
        val pipelineId = settingsRepository.externalAutomationPipelineId.first() ?: return null
        val pipeline = pipelineRepository.getPipelineById(pipelineId) ?: return null
        return ExternalAutomationBinding(pipelineId = pipeline.id, pipelineName = pipeline.name)
    }

    /**
     * Journals a refusal and hands the status back to the caller.
     *
     * @param invocation The raw call, the only source of detail when parsing failed.
     * @param request The parsed request when there is one.
     * @param attestedSenderPackage System-supplied sender package, if any.
     * @param nowMillis Timestamp of the refusal.
     * @param status The refusal to record.
     * @return [status], unchanged.
     */
    private suspend fun refuse(
        invocation: ExternalAutomationInvocation,
        request: ExternalAutomationRequest?,
        attestedSenderPackage: String?,
        nowMillis: Long,
        status: ExternalAutomationStatus,
    ): ExternalAutomationStatus {
        journal.recordRefusal(
            entry(invocation, request, attestedSenderPackage, nowMillis, status, runId = null),
        )
        return status
    }

    /**
     * Admits a request if the rate ceiling allows, then starts its run.
     *
     * The journal row is written **before** the enqueue, not after, and that
     * ordering is the ceiling: the row *is* the ledger the ceiling counts, and a
     * receiver must answer immediately, long before any `pipeline_runs` row
     * exists. Writing it afterwards would let a burst of broadcasts each count the
     * same zero and each be admitted.
     *
     * @param invocation The raw call.
     * @param request The parsed, authorised request.
     * @param attestedSenderPackage System-supplied sender package, if any.
     * @param nowMillis Timestamp of the admission.
     * @return `Accepted` once the run is enqueued, `Blocked(RATE_LIMITED)` when the
     *   ceiling refused it, or `Failed` when the enqueue itself failed.
     */
    private suspend fun admit(
        invocation: ExternalAutomationInvocation,
        request: ExternalAutomationRequest,
        attestedSenderPackage: String?,
        nowMillis: Long,
    ): ExternalAutomationStatus {
        val runId = UUID.randomUUID().toString()
        val ceiling = RunRateCeiling.EXTERNAL
        val admitted = journal.admitAcceptedWithinCeiling(
            entry = entry(
                invocation = invocation,
                request = request,
                attestedSenderPackage = attestedSenderPackage,
                nowMillis = nowMillis,
                status = ExternalAutomationStatus.Accepted,
                runId = runId,
            ),
            windowStartEpochMs = ceiling.windowStart(nowMillis),
            limitPerWindow = ceiling.limitPerWindow,
        )
        if (!admitted) {
            val blocked = ExternalAutomationStatus.Blocked(ExternalAutomationRejectionReason.RATE_LIMITED)
            return refuse(invocation, request, attestedSenderPackage, nowMillis, blocked)
        }

        return try {
            taskScheduler.scheduleOneTime(
                prompt = request.prompt,
                delayMinutes = 0,
                sessionId = EXTERNAL_AUTOMATION_SESSION_ID,
                constraints = ScheduledTaskConstraints(requiresBatteryNotLow = true),
                pipelineId = targetPipelineId(request),
                origin = RunOrigin.EXTERNAL,
                runId = runId,
            )
            ExternalAutomationStatus.Accepted
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The admission is already on the ledger, so it cannot be un-counted —
            // deliberately the conservative direction. What must not survive is the
            // row claiming an accepted run that never started: settle it as failed
            // so the callback and the journal agree with reality.
            Timber.tag(TAG).e(e, "External-automation enqueue failed for request %s", request.requestId)
            journal.recordOutcome(runId, ExternalAutomationStatus.Failed)
            ExternalAutomationStatus.Failed
        }
    }

    /**
     * The pipeline an authorised request runs.
     *
     * Authorisation has already established that the request names the bound
     * pipeline, so a by-id request carries the id directly; a by-name request is
     * resolved through the binding, which is the only name the authorizer accepts.
     *
     * @param request The authorised request.
     * @return The pipeline id to run, or `null` if the binding vanished between
     *   authorisation and here — in which case the run falls back to the app's
     *   default routing, exactly as any other pipeline-less run does.
     */
    private suspend fun targetPipelineId(request: ExternalAutomationRequest): String? =
        when (val target = request.target) {
            is ExternalAutomationTarget.ById -> target.pipelineId
            is ExternalAutomationTarget.ByName -> settingsRepository.externalAutomationPipelineId.first()
        }

    /**
     * Builds the journal record for one decision.
     *
     * Detail is taken from the parsed request when there is one and from the raw
     * invocation otherwise, so a call refused for being unparseable still records
     * what it actually said — which is the only way its author can find the typo.
     *
     * @param invocation The raw call.
     * @param request The parsed request, when parsing succeeded.
     * @param attestedSenderPackage System-supplied sender package, if any.
     * @param nowMillis Timestamp of the decision.
     * @param status The decision.
     * @param runId Pre-minted run id for an admission, `null` for a refusal.
     * @return The record to persist.
     */
    private fun entry(
        invocation: ExternalAutomationInvocation,
        request: ExternalAutomationRequest?,
        attestedSenderPackage: String?,
        nowMillis: Long,
        status: ExternalAutomationStatus,
        runId: String?,
    ): ExternalAutomationJournalEntry = ExternalAutomationJournalEntry(
        id = UUID.randomUUID().toString(),
        requestId = request?.requestId ?: rawRequestId(invocation),
        receivedAt = nowMillis,
        action = invocation.action,
        target = request?.target ?: rawTarget(invocation),
        declaredReturnPackage = request?.returnPackage
            ?: invocation.value(ExternalAutomationContract.EXTRA_RETURN_PACKAGE),
        returnAction = request?.returnAction ?: rawReturnAction(invocation),
        attestedSenderPackage = attestedSenderPackage,
        status = status,
        runId = runId,
    )

    /**
     * The correlation id an unparseable call carried, if any.
     *
     * @param invocation The raw call.
     * @return The caller's request id, or [ABSENT_REQUEST_ID] when it sent none.
     */
    private fun rawRequestId(invocation: ExternalAutomationInvocation): String =
        invocation.value(ExternalAutomationContract.EXTRA_REQUEST_ID) ?: ABSENT_REQUEST_ID

    /**
     * The target an unparseable call named, if any.
     *
     * A call that named its target both ways is recorded by id, so the row still
     * shows something the author can recognise; the reason column already says the
     * target was ambiguous, so nothing is lost by picking one to display.
     *
     * @param invocation The raw call.
     * @return The named target, or `null` when the call named none.
     */
    private fun rawTarget(invocation: ExternalAutomationInvocation): ExternalAutomationTarget? {
        val id = invocation.value(ExternalAutomationContract.EXTRA_PIPELINE_ID)
        if (id != null) return ExternalAutomationTarget.ById(id)
        val name = invocation.value(ExternalAutomationContract.EXTRA_PIPELINE_NAME)
        return name?.let { ExternalAutomationTarget.ByName(it) }
    }

    /**
     * The callback action an unparseable call asked for.
     *
     * @param invocation The raw call.
     * @return The requested action, or the contract's default.
     */
    private fun rawReturnAction(invocation: ExternalAutomationInvocation): String =
        invocation.value(ExternalAutomationContract.EXTRA_RETURN_ACTION)
            ?: ExternalAutomationContract.ACTION_RUN_RESULT

    /**
     * Wraps a reason as a refusal status.
     *
     * @param reason Why the request was refused.
     * @return The rejection.
     */
    private fun rejected(reason: ExternalAutomationRejectionReason): ExternalAutomationStatus.Rejected =
        ExternalAutomationStatus.Rejected(reason)

    /** Constants of the entry point that collaborators outside it depend on. */
    companion object {
        /** Timber tag for the entry point's own diagnostics. */
        private const val TAG = "ExternalAutomation"

        /**
         * The chat every external run lands in.
         *
         * A fixed id rather than `null`, which would mint a fresh chat per request:
         * at the external ceiling of twelve runs an hour that is up to 288 new chats
         * a day, and the results of an automation belong together anyway. The worker
         * that resolves this creates the session on first use with exactly this id
         * and reuses it afterwards, so one accumulating conversation costs no new
         * storage and no new setting — the same shape the share surface reaches with
         * its "keep shares in one chat" default.
         */
        const val EXTERNAL_AUTOMATION_SESSION_ID = "external-automation"

        /**
         * Placeholder recorded when a call carried no usable correlation id.
         *
         * A refusal still needs a row, and an empty cell reads as data loss where
         * this reads as what it is: the caller sent none.
         */
        private const val ABSENT_REQUEST_ID = "(absent)"
    }
}
