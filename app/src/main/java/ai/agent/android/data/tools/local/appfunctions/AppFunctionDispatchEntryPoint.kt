package ai.agent.android.data.tools.local.appfunctions

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point that exposes the per-AppFunction wrapper classes consumed by the
 * callee-side router.
 *
 * Why an [EntryPoint] and not constructor injection: [android.app.appfunctions.AppFunctionService]
 * is instantiated by the platform AppFunctions framework before any Hilt machinery can
 * intercept it. Subclassing `AppFunctionService` with `@AndroidEntryPoint` would bind the
 * service to a Hilt component, but the framework's documented contract is to keep the
 * service free of injected fields; we therefore resolve dependencies on demand via
 * [dagger.hilt.android.EntryPointAccessors] from the application context.
 *
 * The entry point is intentionally minimal: every method returns a per-wrapper singleton
 * (e.g. [SearchAppFunction]) so that callee-side execution observes the same instances —
 * and therefore the same caches and rate limits — as the in-agent caller path.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppFunctionDispatchEntryPoint {

    /**
     * @return The application-scoped [SearchAppFunction] wrapper used by callee-side
     *   `search_tool` invocations.
     */
    fun searchAppFunction(): SearchAppFunction
}
