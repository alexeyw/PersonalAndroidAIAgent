package ai.agent.android.data.tools.local

import ai.agent.android.data.tools.local.appfunctions.AppFunctionDispatchEntryPoint
import ai.agent.android.data.tools.local.appfunctions.AppFunctionRouter
import ai.agent.android.data.tools.local.appfunctions.DispatchOutcome
import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionService
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appsearch.GenericDocument
import android.content.pm.SigningInfo
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Platform-bound `AppFunctionService` that exposes a curated set of agent built-ins to
 * external callers via Android 16's `AppFunctionManager`.
 *
 * Callee-side surface:
 *  - [AppFunctionRouter.SEARCH_TOOL_ID] (`search_tool`) — read-only Wikipedia search.
 *
 * Intentionally excluded from the callee surface:
 *  - `schedule_task` — schedules a WorkManager task on behalf of the user; running
 *    arbitrary background jobs at an external caller's request would violate the user's
 *    expectation of agency.
 *  - `delegate_task` — delegates the prompt to a cloud LLM provider using the user's
 *    API key; never exposed to third-party callers.
 *  - Discovered AppFunctions from other packages — these are inherently caller-side
 *    only (the agent calls them, not the other way round).
 *
 * Threading: [onExecuteFunction] is invoked on the platform's binder pool thread, which
 * is a finite resource shared across every system service binding. The underlying
 * wrappers may perform network I/O (`search_tool` hits Wikipedia), so we never block the
 * caller thread — dispatch is `launch`-ed on a service-scoped [CoroutineScope] backed by
 * a [SupervisorJob] on [Dispatchers.IO], and results are delivered through the supplied
 * asynchronous [OutcomeReceiver]. The per-request child [Job] is bridged to the
 * platform's [CancellationSignal] so cancels propagate cleanly to the suspending wrapper
 * without blocking the binder thread for the duration of the call.
 *
 * Hilt integration: the service is constructed by the platform before Hilt can intercept
 * it, so dependencies are resolved on demand via [EntryPointAccessors] against the
 * application context. The accessor returns the same singletons the agent itself uses,
 * guaranteeing caller and callee paths share state (e.g. rate limits, caches).
 */
class AgentAppFunctionService : AppFunctionService() {

    /**
     * Service-lifetime supervisor. A [SupervisorJob] isolates per-request child jobs from
     * each other: a failure or cancellation of one in-flight request never tears down
     * the scope or sibling requests. Cancelled in [onDestroy] so any still-running
     * dispatch coroutines are torn down with the service.
     */
    private val supervisor = SupervisorJob()

    /**
     * Scope on which every [onExecuteFunction] dispatch coroutine runs. Pinned to
     * [Dispatchers.IO] because the in-tree wrappers issue blocking network I/O; Hilt
     * resolution and `EntryPointAccessors` are cheap reflection calls that also fit on
     * the IO pool.
     */
    private val serviceScope = CoroutineScope(supervisor + Dispatchers.IO)

    override fun onDestroy() {
        serviceScope.cancel("AgentAppFunctionService destroyed")
        super.onDestroy()
    }

    override fun onExecuteFunction(
        request: ExecuteAppFunctionRequest,
        callingPackage: String,
        callingPackageSignature: SigningInfo,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>,
    ) {
        // Allocate the per-request Job up-front so the cancellation listener can be wired
        // before the coroutine starts. Setting the listener after `launch` returns leaves
        // a small window where the system could trip the signal before we observe it; by
        // creating the Job first we close that window.
        val requestJob = Job(supervisor)
        cancellationSignal.setOnCancelListener {
            Timber.d("AppFunction '%s' cancelled by system", request.functionIdentifier)
            requestJob.cancel()
        }
        serviceScope.launch(requestJob) { handleRequest(request, callback) }
    }

    /**
     * Body of the dispatch coroutine. Lives on the service scope, so the platform's binder
     * thread returns from [onExecuteFunction] immediately and the result is delivered
     * asynchronously through [callback].
     *
     * Exception handling is intentionally fanned out inside the coroutine (not around
     * `launch`) so that suspending failures, cancellations and synchronous setup crashes
     * all funnel into the same translation table to a platform [AppFunctionException].
     */
    private suspend fun handleRequest(
        request: ExecuteAppFunctionRequest,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>,
    ) {
        try {
            when (val outcome = dispatch(request)) {
                is DispatchOutcome.Success -> callback.onResult(buildResponse(outcome.result))
                is DispatchOutcome.FunctionNotFound -> callback.onError(
                    AppFunctionException(
                        AppFunctionException.ERROR_FUNCTION_NOT_FOUND,
                        "Unknown functionIdentifier '${outcome.identifier}'",
                    ),
                )
                is DispatchOutcome.InvalidArgument -> callback.onError(
                    AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT, outcome.message),
                )
                is DispatchOutcome.InternalError -> {
                    Timber.e(outcome.cause, "AppFunction '%s' execution failed", request.functionIdentifier)
                    callback.onError(
                        AppFunctionException(
                            AppFunctionException.ERROR_APP_UNKNOWN_ERROR,
                            outcome.cause.message ?: "AppFunction execution failed",
                        ),
                    )
                }
            }
        } catch (e: AppFunctionException) {
            callback.onError(e)
        } catch (e: CancellationException) {
            // The platform tripped `cancellationSignal` (or the parent scope was torn
            // down). Report the platform-defined cancellation code so callers can
            // distinguish "you asked us to stop" from a generic app crash, then rethrow
            // to preserve structured-concurrency semantics for the parent supervisor.
            Timber.d("AppFunction '%s' cancelled", request.functionIdentifier)
            callback.onError(
                AppFunctionException(
                    AppFunctionException.ERROR_CANCELLED,
                    e.message ?: "AppFunction execution cancelled",
                ),
            )
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Timber.e(e, "AppFunction '%s' dispatch crashed", request.functionIdentifier)
            callback.onError(
                AppFunctionException(
                    AppFunctionException.ERROR_APP_UNKNOWN_ERROR,
                    e.message ?: "AppFunction dispatch crashed",
                ),
            )
        }
    }

    /**
     * Routes [request] through the application-scoped [AppFunctionRouter]. The platform
     * `GenericDocument` is exposed to the router as a string-keyed lookup so the router
     * stays Android-free and JVM-testable.
     */
    private suspend fun dispatch(request: ExecuteAppFunctionRequest): DispatchOutcome {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            AppFunctionDispatchEntryPoint::class.java,
        )
        val router = AppFunctionRouter(entryPoint.searchAppFunction())
        val parameters = request.parameters
        return router.route(request.functionIdentifier) { name -> readString(parameters, name) }
    }

    /**
     * Reads a string property from a platform [GenericDocument], returning `null` when the
     * property is absent or stored under a non-string type. The platform getter raises an
     * `IllegalArgumentException` for missing keys, so we contain it here to keep the
     * router contract clean (`null` ⇒ "not supplied").
     */
    private fun readString(document: GenericDocument, name: String): String? = try {
        document.getPropertyString(name)
    } catch (
        @Suppress("SwallowedException") _: IllegalArgumentException,
    ) {
        null
    }

    /**
     * Builds an [ExecuteAppFunctionResponse] whose result document carries [value] under
     * the canonical [ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE] key. The
     * `namespace` / `id` / `schemaType` fields of the document are left empty because the
     * platform identifies the return value by its property name, not by the document
     * envelope.
     */
    private fun buildResponse(value: String): ExecuteAppFunctionResponse {
        val document = GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
            .setPropertyString(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE, value)
            .build()
        return ExecuteAppFunctionResponse(document)
    }
}
