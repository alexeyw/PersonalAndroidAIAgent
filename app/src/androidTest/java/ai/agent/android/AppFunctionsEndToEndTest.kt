package ai.agent.android

import ai.agent.android.data.testing.AppFunctionsE2ETestEntryPoint
import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.engine.executors.ToolNodeExecutor
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeOutput
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.ToolRisk
import ai.agent.android.domain.services.ApprovalNotifier
import ai.agent.android.domain.usecases.LoadModelUseCase
import android.os.Build
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import ai.agent.android.domain.models.Result as DomainResult

/**
 * End-to-end test harness for Phase 20's AppFunctions surface.
 *
 * The four scenarios on this class exercise the agent's caller-side, HITL gate,
 * callee-side and risk-override paths against a second app installed alongside the test
 * APK (`:tools-probe`, applicationId `ai.agent.android.toolsprobe`, deployed via
 * AGP's `androidTestUtil` configuration). The probe declares a single `@AppFunction
 * echo(message)` whose response is the input verbatim — making it a deterministic
 * remote target that does not depend on network access or any non-trivial business
 * logic on the probe side.
 *
 * Why instrumented and not Robolectric: the Android 16 `AppFunctionManager` is a
 * system-process service. The test class therefore requires API 36+ and a real device
 * or emulator; on lower API levels every test is skipped via [assumeTrue] so the
 * file is harmless in JVM-only check builds.
 *
 * Why not `@HiltAndroidTest`: the agent's production application class is already
 * `@HiltAndroidApp`, so the SingletonComponent is bootstrapped automatically when the
 * instrumentation Application is created. Pulling dependencies from the production
 * graph through [AppFunctionsE2ETestEntryPoint] is cheaper than spinning up a parallel
 * Hilt test component and avoids the custom `AndroidJUnitRunner` / `HiltTestApplication`
 * scaffolding that would otherwise leak into the rest of the test suite.
 */
@RunWith(AndroidJUnit4::class)
class AppFunctionsEndToEndTest {

    private lateinit var entryPoint: AppFunctionsE2ETestEntryPoint
    private lateinit var qualifiedEchoName: String

    @Before
    fun setUp() = runBlocking {
        // The platform AppFunctions framework is only available on Android 16+. CI runners
        // and developer JVM-only check loops will reach this guard before any of the test
        // logic, so the file is a no-op there rather than a hard failure.
        assumeTrue("Requires Android 16+ for AppFunctionManager", Build.VERSION.SDK_INT >= 36)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppFunctionsE2ETestEntryPoint::class.java,
        )

        // The platform indexer needs a moment after install before it surfaces a freshly
        // deployed AppFunction. `androidTestUtil` installs the probe immediately before the
        // test class is loaded, so the very first discovery call from a cold device may
        // race the indexer; poll briefly until the qualified name appears. The total
        // budget is intentionally short — we want a regression in discovery (no echo
        // metadata at all) to fail loudly, not stretch the test to a minute-long timeout.
        qualifiedEchoName = waitForProbeEchoDiscovery()
            ?: error(
                "Probe AppFunction `echo` was not discovered within ${PROBE_DISCOVERY_TIMEOUT_MS}ms. " +
                    "Check that `:tools-probe` was installed via androidTestUtil and the agent " +
                    "package can see ${PROBE_PACKAGE} via <queries>.",
            )

        // Each test owns its session id so HITL deferred state cannot leak between
        // scenarios. The pre-test cleanup also drops any override the probe id might still
        // carry from a previous run that crashed before the @After cleanup ran.
        val settings = entryPoint.settingsRepository()
        val existing = settings.appFunctionRiskOverrides.first()
        if (qualifiedEchoName in existing) {
            settings.setAppFunctionRiskOverride(qualifiedEchoName, ToolRisk.SENSITIVE)
        }
    }

    /**
     * Scenario 1 — Caller-side end-to-end.
     *
     * Asserts that the agent app sees the probe's `echo` AppFunction in the merged tool
     * catalogue and that dispatching through [ToolRepository.executeTool] returns the
     * argument verbatim. This is the cheapest possible round-trip — it bypasses the
     * LLM-driven argument generation by hand-crafting the JSON arguments, so a failure
     * here points squarely at the caller-side wiring (discovery, codec encode, IPC,
     * codec decode) rather than at the executor's prompt construction.
     */
    @Test
    fun caller_side_executeTool_returns_echo_message() = runBlocking {
        val toolRepo = entryPoint.toolRepository()

        val tools = toolRepo.getAvailableTools()
        assertTrue(
            "Probe echo `$qualifiedEchoName` should be present in the available tools catalogue",
            tools.any { it.name == qualifiedEchoName },
        )

        val response = toolRepo.executeTool(qualifiedEchoName, """{"message":"hi"}""")
        val parsed = JSONObject(response)
        assertEquals(
            "Echo round-trip should reflect the `message` argument verbatim",
            "hi",
            parsed.optString("result"),
        )
    }

    /**
     * Scenario 2 — HITL gate at the SENSITIVE default.
     *
     * Constructs a `ToolNodeExecutor` with mock `LlmInferenceEngine` / `LoadModelUseCase`
     * (the LLM is irrelevant to the gate behaviour and unloading a real model on every
     * instrumented test run is prohibitively expensive) and real `ToolRepository` /
     * `SettingsRepository`. Drives `execute(...)` against a TOOL node configured to
     * call the probe's qualified echo, observes the [AgentOrchestratorState.WaitingForApproval]
     * emission, and then calls `resumeWithApproval(sessionId, true)` to release the
     * suspended invocation. The final [NodeOutput.Result] must carry the echoed message.
     */
    @Test
    fun hitl_executor_emits_waiting_then_resumes_on_approval() = runBlocking {
        val executor = newExecutor(
            llmResponse = """{"arguments":{"message":"hi"}}""",
        )

        val sessionId = "test-${UUID.randomUUID()}"
        val node = newToolNode(toolName = qualifiedEchoName)

        val outputs = mutableListOf<NodeOutput>()
        val sawWaiting = java.util.concurrent.atomic.AtomicBoolean(false)
        val collectorScope = CoroutineScope(Dispatchers.Default)

        val collection = collectorScope.async {
            withTimeout(EXECUTOR_TIMEOUT_MS) {
                executor.execute(node, inputText = "echo hi", sessionId = sessionId, originalPrompt = "echo hi")
                    .onEach { output ->
                        outputs.add(output)
                        val state = (output as? NodeOutput.State)?.state
                        if (state is AgentOrchestratorState.WaitingForApproval &&
                            sawWaiting.compareAndSet(false, true)
                        ) {
                            // The executor registers its CompletableDeferred *after* this emit
                            // resumes its producer (see `ToolNodeExecutor.execute`). The short
                            // delay before resuming gives the producer time to record the
                            // deferred — otherwise `resumeWithApproval` may run against an empty
                            // map and be silently dropped, leaving the gate to time out.
                            launch {
                                delay(RESUME_GRACE_PERIOD_MS)
                                executor.resumeWithApproval(sessionId, true)
                            }
                        }
                    }
                    .toList()
            }
        }
        collection.await()

        val waitingStates = outputs.filterIsInstance<NodeOutput.State>()
            .map { it.state }
            .filterIsInstance<AgentOrchestratorState.WaitingForApproval>()
        assertTrue(
            "Executor must emit WaitingForApproval for the SENSITIVE-by-default AppFunction",
            waitingStates.isNotEmpty(),
        )
        assertEquals(
            "WaitingForApproval risk should reflect the default AppFunction risk classification",
            ToolRisk.SENSITIVE,
            waitingStates.first().risk,
        )

        val finalResult = outputs.filterIsInstance<NodeOutput.Result>().first()
        val parsed =
            JSONObject(checkNotNull(finalResult.result.outputText) { "Executor must return a non-null outputText" })
        assertEquals(
            "After approval, the executor must surface the echo payload from the probe",
            "hi",
            parsed.optString("result"),
        )
    }

    /**
     * Scenario 3 — Callee-side: the agent exposes `search_tool` to outside callers.
     *
     * Builds an [ExecuteAppFunctionRequest] for `ai.agent.android/search_tool` and
     * dispatches it through the system [AppFunctionManager], exercising the same code
     * path the probe's MainActivity uses for manual smoke checks. The response is parsed
     * back through the platform [GenericDocument]; the test asserts only that the call
     * succeeded and the return value is non-blank — content correctness depends on the
     * Wikipedia network round-trip and is not what this scenario covers.
     */
    @Test
    fun callee_side_search_tool_is_invocable_via_appFunctionManager() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = AppFunctionManager.getInstance(context)
            ?: error("AppFunctionManager not available — Android 16+ required.")

        val packages = withTimeoutOrNull(PROBE_DISCOVERY_TIMEOUT_MS) {
            manager.observeAppFunctions(AppFunctionSearchSpec(packageNames = setOf(AGENT_PACKAGE))).first()
        } ?: error("AppFunctionManager.observeAppFunctions timed out for agent package")
        val metadata = packages
            .flatMap { it.appFunctions }
            .firstOrNull { it.id == SEARCH_TOOL_ID }
            ?: error("Agent app does not expose `$SEARCH_TOOL_ID` — check AgentAppFunctionService manifest entry.")

        val parameters = AppFunctionData.Builder(metadata.parameters, metadata.components)
            .setString("query", "Knotwork")
            .setString("lang", "en")
            .build()
        val request = ExecuteAppFunctionRequest(
            targetPackageName = AGENT_PACKAGE,
            functionIdentifier = SEARCH_TOOL_ID,
            functionParameters = parameters,
        )

        val response = manager.executeAppFunction(request)
        when (response) {
            is ExecuteAppFunctionResponse.Success -> {
                val payload = response.returnValue
                    .getString(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)
                    .orEmpty()
                assertTrue(
                    "search_tool response must be non-blank; got an empty payload",
                    payload.isNotBlank(),
                )
            }
            is ExecuteAppFunctionResponse.Error -> error(
                "search_tool returned an Error response: ${response.error.message ?: response.error::class.java.simpleName}",
            )
        }
    }

    /**
     * Scenario 4 — Risk override flow.
     *
     * Persists a [ToolRisk.READ_ONLY] override for the probe's echo through
     * [SettingsRepository.setAppFunctionRiskOverride] and verifies that
     * [ToolRepository.getRisk] resolves the same name to READ_ONLY on the next call.
     * The HITL gate then short-circuits to a direct execution because READ_ONLY tools
     * skip the approval flow (see `ToolNodeExecutor.execute`'s `needsApproval` branch).
     */
    @Test
    fun risk_override_downgrades_echo_to_read_only_and_skips_hitl() = runBlocking {
        val toolRepo = entryPoint.toolRepository()
        val settings = entryPoint.settingsRepository()

        assertEquals(
            "Echo must default to SENSITIVE before any override is set",
            ToolRisk.SENSITIVE,
            toolRepo.getRisk(qualifiedEchoName),
        )

        settings.setAppFunctionRiskOverride(qualifiedEchoName, ToolRisk.READ_ONLY)
        try {
            assertEquals(
                "Persisted override must be reflected by ToolRepository.getRisk",
                ToolRisk.READ_ONLY,
                toolRepo.getRisk(qualifiedEchoName),
            )

            val executor = newExecutor(llmResponse = """{"arguments":{"message":"hi"}}""")
            val sessionId = "test-readonly-${UUID.randomUUID()}"
            val node = newToolNode(toolName = qualifiedEchoName)

            val outputs = withTimeout(EXECUTOR_TIMEOUT_MS) {
                executor.execute(
                    node,
                    inputText = "echo hi",
                    sessionId = sessionId,
                    originalPrompt = "echo hi",
                ).toList()
            }
            val waitingStates = outputs.filterIsInstance<NodeOutput.State>()
                .map { it.state }
                .filterIsInstance<AgentOrchestratorState.WaitingForApproval>()
            assertTrue(
                "READ_ONLY override must short-circuit the HITL gate — no WaitingForApproval expected",
                waitingStates.isEmpty(),
            )

            val finalResult = outputs.filterIsInstance<NodeOutput.Result>().first()
            assertNotNull("Executor must complete and return a result", finalResult.result.outputText)
        } finally {
            // Restore the SENSITIVE default so a subsequent run of the same test class
            // starts from the production-default state regardless of prior failure
            // boundary.
            settings.setAppFunctionRiskOverride(qualifiedEchoName, ToolRisk.SENSITIVE)
        }
    }

    /**
     * Polls [LocalAppFunctionManager.getAvailableFunctions] (transitively via
     * [ToolRepository.getAvailableTools]) until a tool whose qualified name starts with
     * the probe's package shows up, or the poll budget expires. Returns the qualified
     * name on success and `null` on timeout — the latter is escalated to a hard failure
     * by [setUp].
     */
    private suspend fun waitForProbeEchoDiscovery(): String? {
        val deadline = System.currentTimeMillis() + PROBE_DISCOVERY_TIMEOUT_MS
        val toolRepo = entryPoint.toolRepository()
        while (System.currentTimeMillis() < deadline) {
            val tools = toolRepo.getAvailableTools()
            val match = tools.firstOrNull { tool ->
                tool.name.startsWith("$PROBE_PACKAGE/") && tool.name.endsWith("echo")
            }
            if (match != null) return match.name
            delay(PROBE_POLL_INTERVAL_MS)
        }
        return null
    }

    /**
     * Builds a fresh [ToolNodeExecutor] with real `ToolRepository`, `SettingsRepository`
     * and `ChatRepository` (so the AppFunctions IPC path and the persisted approval
     * settings are exercised end-to-end) and mocked `LlmInferenceEngine` /
     * `LoadModelUseCase` (the LLM has no role in the HITL gate verification and loading
     * a real LiteRT model on every test would dwarf the test runtime).
     */
    private fun newExecutor(llmResponse: String): ToolNodeExecutor {
        val fakeEngine = mockk<LlmInferenceEngine>()
        every { fakeEngine.generateResponseStream(any()) } returns flowOf(llmResponse)

        val fakeLoadModel = mockk<LoadModelUseCase>()
        coEvery { fakeLoadModel.invoke(any()) } returns DomainResult.Success(Unit)
        coEvery { fakeLoadModel.invoke() } returns DomainResult.Success(Unit)

        val silentNotifier = object : ApprovalNotifier {
            override fun sendApprovalRequest(sessionId: String, toolName: String, arguments: String, risk: ToolRisk) {
                // Notification side effects are not part of this test's contract; the gate
                // suspension is observed through the WaitingForApproval state emission, not
                // via a system notification assertion. Swallowing here also keeps the test
                // independent of the POST_NOTIFICATIONS runtime permission.
            }
        }

        return ToolNodeExecutor(
            llmEngine = fakeEngine,
            loadModelUseCase = fakeLoadModel,
            toolRepository = entryPoint.toolRepository(),
            settingsRepository = entryPoint.settingsRepository(),
            approvalNotifier = silentNotifier,
            chatRepository = entryPoint.chatRepository(),
        )
    }

    private fun newToolNode(toolName: String): NodeModel = NodeModel(
        id = "test-tool-node",
        type = NodeType.TOOL,
        x = 0f,
        y = 0f,
        label = "TOOL",
        toolName = toolName,
        modelPath = "test-fake-model.bin",
        contextConfig = NodeContextConfig.ALL_ENABLED,
    )

    private companion object {
        const val AGENT_PACKAGE = "ai.agent.android"
        const val PROBE_PACKAGE = "ai.agent.android.toolsprobe"
        const val SEARCH_TOOL_ID = "search_tool"
        const val PROBE_DISCOVERY_TIMEOUT_MS = 15_000L
        const val PROBE_POLL_INTERVAL_MS = 500L
        const val EXECUTOR_TIMEOUT_MS = 30_000L
        const val RESUME_GRACE_PERIOD_MS = 200L
    }
}
