package ai.agent.android.domain.repositories

import ai.agent.android.domain.models.McpServerConfig
import ai.agent.android.domain.models.TestProbeResult
import ai.agent.android.domain.models.ToolApprovalPolicy
import ai.agent.android.domain.models.ToolRisk
import ai.agent.android.domain.models.UpdateMcpServerResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing application-wide settings and user
 * preferences. Provides abstraction over the underlying persistence
 * mechanism (DataStore + EncryptedSharedPreferences for the secret
 * payloads).
 *
 * The interface is intentionally large; per-feature splits (Sampling /
 * Identity / Memory) are planned post-v0.1. The detekt suppression is
 * file-scoped because every method here serves a single coherent
 * "user-tunable preference" concern.
 */
@Suppress("TooManyFunctions")
interface SettingsRepository {

    /**
     * A [Flow] representing the current state of the first launch flag.
     * Emits `true` if it's the user's first time launching the app, `false` otherwise.
     *
     * Semantics (intentionally narrow): this flag gates one-shot seeding
     * inside `InitializeAppUseCase` (default prompts, the seeded
     * `Default System Pipeline`). It is cleared as part of that
     * initialization, so callers cannot rely on it to decide whether the
     * user has seen onboarding — use [hasCompletedOnboarding] for that.
     */
    val isFirstLaunch: Flow<Boolean>

    /**
     * Updates the first launch flag.
     *
     * @param isFirstLaunch The new value to set.
     */
    suspend fun setFirstLaunch(isFirstLaunch: Boolean)

    /**
     * A [Flow] indicating whether the user has finished (or skipped) the
     * onboarding flow at least once. Emits `false` until [setHasCompletedOnboarding]
     * is called.
     *
     * This is intentionally a separate flag from [isFirstLaunch]: cold-start
     * initialization (`InitializeAppUseCase`) clears `isFirstLaunch` before
     * the splash hands control to the nav-graph, so the onboarding gate
     * cannot key off it. The two flags evolve independently — seeding the
     * default pipeline runs exactly once per fresh install, while
     * onboarding can be re-shown later via Settings → Reset onboarding
     * (Phase 21 / Task 10) without re-seeding.
     */
    val hasCompletedOnboarding: Flow<Boolean>

    /**
     * Updates the onboarding-completion flag.
     *
     * @param completed `true` after the user finishes or skips onboarding;
     *        `false` resets onboarding so it is shown again on the next
     *        launch (consumed by the Settings → Reset onboarding action).
     */
    suspend fun setHasCompletedOnboarding(completed: Boolean)

    /**
     * A [Flow] representing the saved HuggingFace authorization token.
     */
    val huggingFaceAuthToken: Flow<String?>

    /**
     * Updates the HuggingFace authorization token.
     *
     * @param token The new token to save, or null to clear it.
     */
    suspend fun setHuggingFaceAuthToken(token: String?)

    /**
     * A [Flow] representing the maximum allowed context length (e.g., in characters or tokens).
     */
    val maxContextLength: Flow<Int>

    /**
     * Updates the maximum allowed context length.
     *
     * @param length The new maximum length to set.
     */
    suspend fun setMaxContextLength(length: Int)

    /**
     * A [Flow] representing the sampling temperature for generation.
     */
    val temperature: Flow<Float>

    /**
     * Updates the sampling temperature.
     */
    suspend fun setTemperature(temperature: Float)

    /**
     * A [Flow] representing the top-k sampling parameter for generation.
     */
    val topK: Flow<Int>

    /**
     * Updates the top-k sampling parameter.
     */
    suspend fun setTopK(topK: Int)

    /**
     * A [Flow] representing the top-p sampling parameter for generation.
     */
    val topP: Flow<Float>

    /**
     * Updates the top-p sampling parameter.
     */
    suspend fun setTopP(topP: Float)

    /**
     * Global "ask before every tool call" override for the Human-in-the-loop gate.
     *
     * Semantics (canonical implementation in `ToolNodeExecutor`):
     *  - `SENSITIVE` and `DESTRUCTIVE` tools **always** prompt; this flag has no effect.
     *  - `READ_ONLY` tools prompt **only** when this flag is `true`. Default `false`:
     *    read-only invocations run silently so the agent feels fluid.
     *
     * Renamed semantically from "requires confirmation for critical actions" — critical
     * actions are now classified per-tool by `ToolRepository.getRisk`. This flag exists
     * solely to let cautious users opt into a prompt on every single tool invocation.
     */
    val requiresUserConfirmation: Flow<Boolean>

    /**
     * Updates the requirement for user confirmation.
     */
    suspend fun setRequiresUserConfirmation(required: Boolean)

    /**
     * A [Flow] representing the system prompt prefix.
     */
    val systemPromptPrefix: Flow<String>

    /**
     * Updates the system prompt prefix.
     *
     * @param prompt The new prompt to set.
     */
    suspend fun setSystemPromptPrefix(prompt: String)

    /**
     * A [Flow] representing the tool usage instruction prompt.
     */
    val toolUsageInstruction: Flow<String>

    /**
     * Updates the tool usage instruction prompt.
     *
     * @param instruction The new instruction to set.
     */
    suspend fun setToolUsageInstruction(instruction: String)

    /**
     * Configured MCP servers, ordered by user insertion order.
     *
     * Each entry carries the URL plus optional display name, transport
     * choice, and arbitrary request headers (typically `Authorization`).
     * Implementations migrate legacy URL-only persistence one-shot to
     * default [McpServerConfig]s on first read.
     */
    val mcpServers: Flow<List<McpServerConfig>>

    /**
     * Persists [config]. If a server with the same URL already exists,
     * it is replaced in place (preserving order).
     */
    suspend fun addMcpServer(config: McpServerConfig)

    /**
     * Replaces the server identified by [originalUrl] with [updated].
     * If [originalUrl] is not present, behaves the same as [addMcpServer].
     * The URL inside [updated] may differ from [originalUrl] (edit
     * scenario); persistence keeps the row at its old position.
     *
     * Refuses to persist with [UpdateMcpServerResult.UrlCollision] when
     * `updated.url` matches the URL of a different existing row. Without
     * this guard, replacing by index would silently produce a `[B, B]`
     * list and lose the original server's auth / headers / display name.
     */
    suspend fun updateMcpServer(originalUrl: String, updated: McpServerConfig): UpdateMcpServerResult

    /** Removes the server identified by [url]. No-op when missing. */
    suspend fun removeMcpServer(url: String)

    /**
     * A [Flow] representing the set of disabled local app function names.
     */
    val disabledAppFunctions: Flow<Set<String>>

    /**
     * Updates the set of disabled local app functions.
     */
    suspend fun setDisabledAppFunctions(functions: Set<String>)

    /**
     * A [Flow] of disabled MCP tool ids. Entries match the
     * `mcp:<sha8(serverUrl)>:<toolName>` ids produced by
     * `McpServerRepositoryImpl.mcpToolId`. Kept separate from
     * [disabledAppFunctions] because the two namespaces share no collision
     * guarantees — disabling `search_tool` (local) and an MCP tool named
     * `search_tool` are independent decisions.
     */
    val disabledMcpTools: Flow<Set<String>>

    /**
     * Updates the set of disabled MCP tools.
     */
    suspend fun setDisabledMcpTools(toolIds: Set<String>)

    /**
     * A [Flow] of per-AppFunction risk overrides, keyed by the AppFunction's
     * tool name. The map is the user's authoritative voice on what risk class a
     * discovered AppFunction should be treated as; when an entry is present it
     * takes precedence over the conservative `SENSITIVE` default applied by
     * `LocalAppFunctionManager`.
     *
     * The map applies only to discovered AppFunctions. Built-in tools carry
     * hard-coded risk constants (see `ToolRepositoryImpl.getBuiltinTools`) and
     * are not affected by entries in this map. MCP tools are governed by a
     * blanket policy until a per-server scheme is introduced.
     *
     * Missing entries (or an empty map) mean "no override, use the source
     * default" — the canonical resolution lives in `ToolRepository.getRisk`.
     */
    val appFunctionRiskOverrides: Flow<Map<String, ToolRisk>>

    /**
     * Sets (or replaces) the risk override for a single AppFunction. To remove
     * an override, callers should pass the source-default value or wait for the
     * removal API to land in a follow-up task; this initial cut intentionally
     * keeps the surface minimal because there is no UI surface yet.
     *
     * @param toolName Name of the AppFunction to override.
     * @param risk Effective risk class to associate with [toolName].
     */
    suspend fun setAppFunctionRiskOverride(toolName: String, risk: ToolRisk)

    /**
     * A [Flow] representing the current active chat session ID.
     */
    val currentChatSessionId: Flow<String?>

    /**
     * Updates the current active chat session ID.
     */
    suspend fun setCurrentChatSessionId(sessionId: String?)

    /**
     * A [Flow] emitting the user's last-selected console tab on the chat
     * home pane. Stored as a raw string (the enum name from
     * `app.knotwork.design.components.console.ConsoleTab`) so the domain
     * layer stays free of `:catalog` imports. Defaults to `"Logs"` for a
     * fresh install.
     */
    val consolePreferredConsoleTabName: Flow<String>

    /**
     * Persists the user's chosen console tab so it survives process death.
     *
     * @param name Enum name of the chosen tab (`Logs` / `Vars` / `Traces`).
     */
    suspend fun setConsolePreferredConsoleTabName(name: String)

    /**
     * A [Flow] representing the maximum number of memory chunks to load for similarity search.
     */
    val maxMemoryChunksForSearch: Flow<Int>

    /**
     * Updates the maximum number of memory chunks for similarity search.
     *
     * @param limit The new limit.
     */
    suspend fun setMaxMemoryChunksForSearch(limit: Int)

    /**
     * A [Flow] emitting the wire key of the selected local-model backend
     * ([ai.agent.android.domain.models.LocalBackend.key]). Stored as a raw string for
     * backward compatibility with DataStore values written before the typed enum existed.
     */
    val localModelBackend: Flow<String>

    /**
     * Updates the selected backend for the local model.
     *
     * @param backend The new backend wire key; use
     *        [ai.agent.android.domain.models.LocalBackend.key] to obtain it.
     */
    suspend fun setLocalModelBackend(backend: String)

    /**
     * Sentinel emitted by [setLastInitBackendAttempt] right before a
     * non-CPU LiteRT backend init is attempted. Cleared on successful init.
     *
     * Used as a crash-recovery breadcrumb: if a cold-start observes this
     * key still set to a non-CPU backend, the previous LiteRT init
     * crashed mid-flight (typically a missing GPU/NPU dispatch library
     * killing the process via SIGABRT before Kotlin try/catch can fire).
     * `LiteRTLlmEngine.initialize` then forces CPU and clears the
     * persisted backend so subsequent restarts are stable.
     */
    val lastInitBackendAttempt: Flow<String?>

    /**
     * Updates the crash-recovery breadcrumb. Pass `null` to clear it on
     * successful init.
     */
    suspend fun setLastInitBackendAttempt(backendKey: String?)

    /**
     * A [Flow] representing the timeout in milliseconds for tool approval requests.
     * After this duration without a user response, the approval is considered timed out.
     */
    val toolCallTimeoutMs: Flow<Long>

    /**
     * Updates the tool call approval timeout.
     *
     * @param timeoutMs The new timeout in milliseconds.
     */
    suspend fun setToolCallTimeoutMs(timeoutMs: Long)

    /**
     * A [Flow] representing the maximum number of pipeline execution steps.
     * Prevents infinite loops in pipeline graphs. Valid range: 5–100.
     */
    val pipelineMaxSteps: Flow<Int>

    /**
     * Updates the maximum number of pipeline execution steps.
     *
     * @param steps The new limit. Will be coerced to the range 5–100.
     */
    suspend fun setPipelineMaxSteps(steps: Int)

    /**
     * A [Flow] representing the id of the pipeline the user has marked as
     * default. `null` means no explicit choice — callers should fall back
     * to the first pipeline returned by `PipelineRepository.getAllPipelines()`
     * (the same convention used before this setting was introduced).
     *
     * Set on first launch by `InitializeAppUseCase` to the seeded
     * `Default System Pipeline` so the default is unambiguous from the
     * start. Cleared automatically when the marked pipeline is deleted.
     */
    val defaultPipelineId: Flow<String?>

    /**
     * Updates the user-marked default pipeline id. Pass `null` to clear
     * the marker (the resolution then falls back to the first pipeline
     * in the library).
     *
     * @param pipelineId Pipeline id to mark as default, or `null` to clear.
     */
    suspend fun setDefaultPipelineId(pipelineId: String?)

    /**
     * A [Flow] indicating whether the user has opted in to anonymous crash
     * reporting via Firebase Crashlytics. Defaults to `false` — the project's
     * on-device privacy positioning forbids any automatic data egress.
     *
     * When `false`, the implementation must short-circuit every
     * `CrashReportingRepository` call to a no-op so no payload ever leaves
     * the device. When `true`, Firebase Crashlytics + Analytics collection
     * is enabled and exceptions / custom keys are forwarded.
     */
    val crashReportingEnabled: Flow<Boolean>

    /**
     * Updates the user's opt-in for anonymous crash reporting.
     *
     * @param enabled `true` to enable Crashlytics + Analytics collection,
     *                `false` to disable and force all reporting calls into a no-op.
     */
    suspend fun setCrashReportingEnabled(enabled: Boolean)

    /**
     * A [Flow] representing the default number of recent memory chunks rendered
     * by the `$MEMORY_SUMMARY` prompt variable. Defaults to 5.
     */
    val memorySummaryDefaultLimit: Flow<Int>

    /**
     * Updates the default number of recent memory chunks shown by `$MEMORY_SUMMARY`.
     *
     * @param limit The new chunk count. Values `<= 0` are valid and disable the
     * variable (it resolves to an empty string).
     */
    suspend fun setMemorySummaryDefaultLimit(limit: Int)

    /**
     * Policy that drives the Human-in-the-Loop approval gate in
     * `ToolNodeExecutor`. Supersedes the legacy boolean
     * [requiresUserConfirmation] flag.
     *
     * The first read of this flow performs a one-shot migration from the
     * legacy boolean key: `true` → [ToolApprovalPolicy.SensitiveOrDestructive],
     * `false` → [ToolApprovalPolicy.NeverPrompt]. Migration only fires when
     * the new key is absent.
     */
    val toolApprovalPolicy: Flow<ToolApprovalPolicy>

    /**
     * Persists the user's tool-approval policy choice.
     *
     * @param policy The new policy.
     */
    suspend fun setToolApprovalPolicy(policy: ToolApprovalPolicy)

    /**
     * `true` when the user has opted to hard-block every `DESTRUCTIVE`
     * tool — `ToolNodeExecutor` refuses to call the tool and returns a
     * structured "blocked by policy" observation. Defaults to `false`.
     */
    val blockDestructiveTools: Flow<Boolean>

    /**
     * Updates the hard-deny destructive-tool flag.
     *
     * @param blocked `true` to refuse destructive tools outright.
     */
    suspend fun setBlockDestructiveTools(blocked: Boolean)

    /**
     * Local-only mode flag. When `true`, `KoogClientFactory` returns `null`
     * for every cloud provider (OpenAI / Anthropic / Google / DeepSeek);
     * only the on-device LiteRT engine and LAN-local Ollama remain
     * reachable. Defaults to `false`.
     */
    val blockNetworkFromLocalModel: Flow<Boolean>

    /**
     * Updates the local-only mode flag.
     *
     * @param blocked `true` to gate every cloud provider.
     */
    suspend fun setBlockNetworkFromLocalModel(blocked: Boolean)

    /**
     * Repetition-penalty parameter applied to local generation. Range
     * `1.0..2.0`. `1.0f` is the neutral identity; higher values penalise
     * recent tokens.
     */
    val repetitionPenalty: Flow<Float>

    /**
     * Updates the repetition-penalty value. The implementation coerces the
     * argument into the documented `1.0..2.0` range so a misbehaving
     * slider cannot persist out-of-bounds values.
     *
     * @param value New penalty.
     */
    suspend fun setRepetitionPenalty(value: Float)

    /**
     * Fraction of the memory context budget at which automatic
     * summarisation triggers. Range `0f..1f`. Default
     * [ai.agent.android.domain.constants.SettingsDefaults.AUTO_SUMMARIZE_THRESHOLD_DEFAULT].
     */
    val autoSummarizeThreshold: Flow<Float>

    /**
     * Updates the auto-summarize threshold. Coerced into `0f..1f`.
     *
     * @param threshold Fraction triggering summarisation.
     */
    suspend fun setAutoSummarizeThreshold(threshold: Float)

    /**
     * `true` when the user has opted into long-running task notifications.
     * Drives the "Long-running tasks" toggle in the Notifications card —
     * gates the runtime watcher that fires a system notification when a
     * pipeline run exceeds the wall-clock threshold while the app is in the
     * background. Defaults to `true`.
     */
    val longRunningTaskNotificationsEnabled: Flow<Boolean>

    /**
     * Persists the long-running task notifications toggle.
     *
     * @param enabled `true` to allow background notifications.
     */
    suspend fun setLongRunningTaskNotificationsEnabled(enabled: Boolean)

    /**
     * Last persisted result of a `Test backend` run inside Settings.
     * Emits `null` until the user has run the probe at least once.
     */
    val lastTestProbeResult: Flow<TestProbeResult?>

    /**
     * Persists the latest test-backend probe result so the row's
     * subtitle survives navigation.
     *
     * @param result Probe outcome to persist; pass `null` to clear.
     */
    suspend fun setLastTestProbeResult(result: TestProbeResult?)

    /**
     * Id of the currently active embedding provider, matching one of the
     * `EmbeddingProvider.ID_*` constants (e.g. `"use"`, `"openai_3_small"`,
     * `"ollama"`). Drives which backend the long-term memory subsystem uses to
     * turn text into vectors. Defaults to
     * [SettingsDefaults.ACTIVE_EMBEDDING_PROVIDER_ID_DEFAULT] (on-device USE).
     *
     * If the persisted value no longer matches a registered provider,
     * `EmbeddingProviderResolver` falls back to the on-device default at
     * resolution time — the stored value is left untouched.
     */
    val activeEmbeddingProviderId: Flow<String>

    /**
     * Persists the active embedding provider id.
     *
     * @param id One of the `EmbeddingProvider.ID_*` constants.
     */
    suspend fun setActiveEmbeddingProviderId(id: String)

    /**
     * Resets the local-generation sampling parameters back to the
     * documented defaults ([SettingsDefaults.TEMPERATURE_DEFAULT],
     * [SettingsDefaults.TOP_K_DEFAULT], [SettingsDefaults.TOP_P_DEFAULT],
     * [SettingsDefaults.REPETITION_PENALTY_DEFAULT],
     * [SettingsDefaults.MAX_CONTEXT_LENGTH_DEFAULT],
     * [SettingsDefaults.PIPELINE_MAX_STEPS_DEFAULT]). Used by the
     * "Reset to defaults" action in Settings → LLM parameters.
     */
    suspend fun resetSamplingDefaults()
}
