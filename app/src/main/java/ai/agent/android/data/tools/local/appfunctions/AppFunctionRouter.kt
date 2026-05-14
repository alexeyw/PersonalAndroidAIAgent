package ai.agent.android.data.tools.local.appfunctions

/**
 * Pure-Kotlin router that maps an incoming AppFunctions `functionIdentifier` plus a
 * key-value view of its arguments onto the appropriate wrapper invocation.
 *
 * The class is deliberately decoupled from the platform `android.app.appfunctions.*`
 * types so it can be unit-tested on the JVM source set without needing Robolectric or
 * an emulator. The thin platform-aware shim lives in
 * [ai.agent.android.data.tools.local.AgentAppFunctionService] and is responsible for:
 *  1. Extracting argument strings from the platform `GenericDocument` and passing them
 *     through this router via the [getArg] closure;
 *  2. Translating [DispatchOutcome] back into the platform `ExecuteAppFunctionResponse`
 *     and `AppFunctionException`.
 *
 * Routing is intentionally case-sensitive and exhaustive: an unknown identifier always
 * collapses to [DispatchOutcome.FunctionNotFound], never to a silent success.
 */
internal class AppFunctionRouter(private val searchAppFunction: SearchAppFunction) {

    /**
     * Routes the request named [identifier].
     *
     * @param identifier The `functionIdentifier` field from `ExecuteAppFunctionRequest`.
     *   Currently the router recognises [SEARCH_TOOL_ID] only; future built-ins that are
     *   safe to expose callee-side should add their own branch here.
     * @param getArg Closure returning the raw string value of a named argument, or
     *   `null` when the argument is absent from the request parameters. Decoupling the
     *   shape from a concrete map keeps the router cheap on the production path
     *   (`GenericDocument.getPropertyString(name)` is called lazily) and trivially
     *   substitutable in tests.
     * @return A typed [DispatchOutcome]. The shim translates [DispatchOutcome.Success]
     *   into a populated `ExecuteAppFunctionResponse` and every error variant into a
     *   matching `AppFunctionException` so the system reports a meaningful error code.
     */
    suspend fun route(identifier: String, getArg: (String) -> String?): DispatchOutcome = when (identifier) {
        SEARCH_TOOL_ID -> routeSearchTool(getArg)
        else -> DispatchOutcome.FunctionNotFound(identifier)
    }

    private suspend fun routeSearchTool(getArg: (String) -> String?): DispatchOutcome {
        val query = getArg(ARG_QUERY)
        if (query.isNullOrBlank()) {
            return DispatchOutcome.InvalidArgument(
                "search_tool requires a non-blank '$ARG_QUERY' argument",
            )
        }
        val lang = getArg(ARG_LANG)?.takeIf { it.isNotBlank() }.orEmpty()
        return try {
            DispatchOutcome.Success(searchAppFunction.invoke(query, lang))
        } catch (e: IllegalArgumentException) {
            DispatchOutcome.InvalidArgument(e.message ?: "Invalid argument")
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            DispatchOutcome.InternalError(e)
        }
    }

    companion object {
        /**
         * Canonical wire identifier of the read-only `search_tool` built-in. Kept in sync
         * with `SearchTool.TOOL_NAME` — the same string the agent itself uses when
         * dispatching the tool internally.
         */
        const val SEARCH_TOOL_ID: String = "search_tool"

        /** Name of the required `query` parameter inside the AppFunctions argument document. */
        const val ARG_QUERY: String = "query"

        /** Name of the optional `lang` parameter inside the AppFunctions argument document. */
        const val ARG_LANG: String = "lang"
    }
}

/**
 * Typed result of [AppFunctionRouter.route]. The platform-aware shim in
 * [ai.agent.android.data.tools.local.AgentAppFunctionService] is the single point that
 * maps these variants onto `android.app.appfunctions.ExecuteAppFunctionResponse` /
 * `AppFunctionException`, so the router itself never needs to depend on platform types.
 */
internal sealed interface DispatchOutcome {

    /** The wrapper returned [result]; the shim packages it into a `GenericDocument`. */
    data class Success(val result: String) : DispatchOutcome

    /** No wrapper is registered for [identifier]; maps to `ERROR_FUNCTION_NOT_FOUND`. */
    data class FunctionNotFound(val identifier: String) : DispatchOutcome

    /** Supplied arguments failed validation; maps to `ERROR_INVALID_ARGUMENT`. */
    data class InvalidArgument(val message: String) : DispatchOutcome

    /** Unhandled error while executing the wrapper; maps to `ERROR_APP_UNKNOWN_ERROR`. */
    data class InternalError(val cause: Throwable) : DispatchOutcome
}
