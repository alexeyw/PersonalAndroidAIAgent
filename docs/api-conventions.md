# API & Integration Conventions

This document collects the cross-cutting conventions that govern how the
codebase talks to its external dependencies — LiteRT-LM, AppFunctions
Jetpack, the Model Context Protocol (MCP), cloud LLM APIs, Room, and
DataStore — plus the error-handling and JSON-parsing patterns shared
across all of them.

For end-to-end recipes (adding a new tool, cloud provider, node type, or
prompt variable), see [`extending.md`](extending.md). For the broader
layering rationale, see [`architecture.md`](architecture.md).

---

## LiteRT-LM (on-device inference)

- Always load the model in a coroutine on `Dispatchers.IO`.
- Expose inference as a `Flow<String>` (token streaming) from the
  repository.
- Implement a `ModelSession` wrapper that holds the native handle and
  exposes `suspend fun generate(prompt: String): Flow<String>`.
- Call `session.close()` from the `ViewModel.onCleared()` and from the
  foreground service's `onDestroy()` to prevent OOM.
- Gate every inference call with a `Mutex` to prevent concurrent session
  access.
- Log memory usage before and after model load with `Timber.d`.

```kotlin
// Canonical pattern
interface LiteRtRepository {
    suspend fun loadModel(modelPath: String): Result<Unit>
    fun generate(prompt: String): Flow<String>
    suspend fun unloadModel()
    val isModelLoaded: StateFlow<Boolean>
}
```

---

## AppFunctions (tool calling)

- **Caller-side** discovery and dispatch lives in
  `data/tools/local/LocalAppFunctionManager`. AppFunctions are keyed by
  their qualified name (`"${packageName}/${id}"`) so identical ids
  from different packages can coexist. `ToolRepositoryImpl` merges the
  discovered set into the visible tool catalogue.
- **Callee-side** wrappers live in `data/tools/local/appfunctions/`
  annotated with `androidx.appfunctions.service.AppFunction`. The
  auto-merged `androidx.appfunctions.service.PlatformAppFunctionService`
  (from `appfunctions-service`) dispatches incoming requests through
  KSP-generated invokers. Do **not** subclass `AppFunctionService` or
  write a manual router; the recipe for a new wrapper lives in
  [`extending.md`](extending.md) §2.5.
- **`AppFunctionDataCodec` is the single point of serialization.** Every
  conversion between the LLM-emitted JSON argument string and the typed
  `AppFunctionData` consumed by `AppFunctionManager.executeAppFunction(...)`
  goes through
  [`AppFunctionDataCodec`](../app/src/main/java/ai/agent/android/data/tools/local/AppFunctionDataCodec.kt).
  Likewise for the response: `ExecuteAppFunctionResponse` → flat JSON
  for the agent's observation log. Do not hand-roll `JSONObject`
  walking in callers — the codec is the source of truth for type
  coercion rules and `IllegalArgumentException` boundaries.
- **`ToolRepository.getRisk(name)` is the single source of truth for
  HITL.** The gate in `ToolNodeExecutor` consults it once per
  invocation, never the legacy `SettingsRepository.requiresUserConfirmation`
  flag in isolation. The risk resolves through three layers:
  built-in defaults (`search_tool` → `READ_ONLY`,
  `schedule_task` / `delegate_task` → `SENSITIVE`), per-tool overrides
  for discovered AppFunctions
  (`SettingsRepository.setAppFunctionRiskOverride`), then `SENSITIVE` as
  the conservative fallback for anything else (MCP tools included).
  `requiresUserConfirmation` is now an opt-in "ask on every single call"
  override and never silences `SENSITIVE` / `DESTRUCTIVE`.

```kotlin
enum class ToolRisk { READ_ONLY, SENSITIVE, DESTRUCTIVE }

interface Tool {
    val id: String
    val risk: ToolRisk
    val description: String
    suspend fun execute(args: Map<String, String>): ToolResult
}
```

---

## Model Context Protocol (MCP)

- The MCP client lives in the `data/mcp` package.
- Each MCP server connection is held in a
  `ConcurrentHashMap<String, McpClient>` inside `ToolRepositoryImpl`
  (thread-safe).
- Connections are lazy: they open on first use and close when the agent
  session ends.
- Wrap every MCP call in `runCatching` and map errors to
  `ToolResult.Error`.

---

## Cloud LLM APIs (OpenAI / Anthropic / Google / DeepSeek / Ollama)

- All cloud providers implement the domain interface
  `CloudLlmClientFactory` (data-layer impl: `KoogClientFactory`).
- API keys are stored in **`EncryptedSharedPreferences` only** — never in
  plain DataStore, log files, exported pipelines, or anything committed to
  the repository.
- Requests include a `timeout` of 60 seconds (OkHttp).
- Use the unified `CLOUD` pipeline node with a `provider` parameter — do
  not add per-provider node types to the pipeline graph.

---

## Room database

- Every DAO method that returns a `Flow` must be annotated with `@Query` —
  no magic.
- Use `@Transaction` for operations that touch multiple tables.
- Database migrations must be explicit
  (`Migration(oldVersion, newVersion) { ... }`). Auto-migrations are
  allowed for additive changes only.
- Always inject a `CoroutineDispatcher` into `DataSource` classes so they
  remain testable.

---

## DataStore (settings)

- Define a single `PreferencesDataStore` instance per feature module, not
  per class.
- All preference keys are `object`s in a `PreferenceKeys` companion
  object.
- Wrap `DataStore.data` collection in a `catch` operator to handle
  `IOException`.

---

## JSON parsing

- Use `org.json.JSONObject` or `kotlinx.serialization` — **never** manual
  string parsing.
- Always handle `JSONException` and map it to a typed error result.
- When parsing tool arguments from LLM output, use the canonical parser in
  `domain/parser/ToolArgumentParser.kt`.

---

## Error handling

- All repository methods return `Result<T>` (`kotlin.Result`) or emit a
  `sealed class` state hierarchy.
- **Never** propagate raw exceptions to the presentation layer.
- Log every error with `Timber.e(throwable, "Context message")`.
