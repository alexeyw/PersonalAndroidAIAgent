package ai.agent.android.data.testing

import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt [EntryPoint] resolved from the application context by the
 * instrumented test (`AppFunctionsEndToEndTest`).
 *
 * The instrumented test runs in the agent app's own process — `@HiltAndroidApp` on
 * `ai.agent.android.App` has already initialised the SingletonComponent by the time the
 * test class is constructed — but the test itself is not Hilt-injected. This entry point
 * is the cheapest way to reach the production singletons the test exercises:
 *
 *  - [toolRepository] — caller-side dispatch (`getAvailableTools`, `executeTool`) plus the
 *    canonical risk classification consumed by the HITL gate (`getRisk`).
 *  - [settingsRepository] — exercised through `setAppFunctionRiskOverride` to assert the
 *    SENSITIVE → READ_ONLY downgrade flow short-circuits the gate.
 *  - [chatRepository] — passed to the manually-constructed `ToolNodeExecutor` so the
 *    executor's tool-observation persistence side effect hits the real database (the
 *    test then has no need to verify the message body, only the executor's emitted
 *    flow).
 *
 * Kept deliberately minimal: only the dependencies the e2e test actually consumes are
 * exposed. Adding more accessors here means accepting a wider test-only surface in
 * production code — prefer extending the test's own scaffolding before adding entries.
 *
 * Lives under `data/testing/` rather than the test source set because the Hilt code
 * generator needs to see the interface during the main compilation pass to wire it into
 * the `SingletonComponent`. The kspAndroidTest configuration is intentionally avoided to
 * keep the test-only build classpath untouched.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppFunctionsE2ETestEntryPoint {
    fun toolRepository(): ToolRepository
    fun settingsRepository(): SettingsRepository
    fun chatRepository(): ChatRepository
}
