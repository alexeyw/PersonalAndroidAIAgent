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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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
 * Threading: the platform calls [onExecuteFunction] on its binder pool thread. The
 * underlying suspend wrappers may perform network I/O (`search_tool` hits Wikipedia), so
 * dispatch happens inside a [runBlocking] block tied to a [SupervisorJob] on
 * [Dispatchers.IO]. The platform's [CancellationSignal] is bridged to that job so cancels
 * propagate cleanly to the wrapper.
 *
 * Hilt integration: the service is constructed by the platform before Hilt can intercept
 * it, so dependencies are resolved on demand via [EntryPointAccessors] against the
 * application context. The accessor returns the same singletons the agent itself uses,
 * guaranteeing caller and callee paths share state (e.g. rate limits, caches).
 */
class AgentAppFunctionService : AppFunctionService() {

    override fun onExecuteFunction(
        request: ExecuteAppFunctionRequest,
        callingPackage: String,
        callingPackageSignature: SigningInfo,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>,
    ) {
        val job = SupervisorJob()
        cancellationSignal.setOnCancelListener {
            Timber.d("AppFunction '%s' cancelled by system", request.functionIdentifier)
            job.cancel()
        }
        try {
            val outcome = runBlocking(job + Dispatchers.IO) { dispatch(request) }
            when (outcome) {
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            // The system either tripped `cancellationSignal` or the dispatching coroutine
            // was otherwise cancelled. The platform exposes a dedicated error code for
            // this case so callers can distinguish "you asked us to stop" from a generic
            // app crash; surface it here instead of falling through to ERROR_APP_UNKNOWN_ERROR.
            Timber.d("AppFunction '%s' cancelled", request.functionIdentifier)
            callback.onError(
                AppFunctionException(
                    AppFunctionException.ERROR_CANCELLED,
                    e.message ?: "AppFunction execution cancelled",
                ),
            )
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
