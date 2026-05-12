# Extending the Agent

This guide is for contributors who want to add new functionality to the
**On-Device AI Agent for Android** — a new node type for the pipeline
engine, a new tool the agent can call, a new cloud LLM provider, or a
new prompt variable. Each section is a step-by-step recipe with the
exact files to touch and the order in which to touch them.

It assumes you have already read [`docs/architecture.md`](architecture.md)
(layers, pipeline engine, integrations). For end-user documentation,
see [`docs/user-guide.md`](user-guide.md).

---

## Table of contents

1. [Add a new `NodeType`](#1-add-a-new-nodetype)
2. [Add a new `Tool`](#2-add-a-new-tool)
3. [Add a new cloud provider](#3-add-a-new-cloud-provider)
4. [Add a new prompt variable](#4-add-a-new-prompt-variable)
5. [Synchronization table — "if you change X, also touch Y"](#5-synchronization-table)
6. [Quality gate](#6-quality-gate)

---

## 1. Add a new `NodeType`

A `NodeType` is one entry in the pipeline graph's vocabulary. Adding
one means: defining what the enum value means, writing a strategy that
executes it, wiring it into the dispatch factory, and mirroring the new
type into the browser-based editor so users can place it on the canvas.

### 1.1. Extend the `NodeType` enum

Add a new constant to
[`domain/models/NodeType.kt`](../app/src/main/java/ai/agent/android/domain/models/NodeType.kt).
Keep the name SCREAMING_SNAKE_CASE and group it logically with similar
existing types (e.g. control-flow next to `IF_CONDITION`,
LLM-driven next to `LITE_RT` / `CLOUD`).

### 1.2. Implement `NodeExecutor`

Create a new class under
`app/src/main/java/ai/agent/android/domain/engine/executors/` that
implements
[`NodeExecutor`](../app/src/main/java/ai/agent/android/domain/engine/executors/NodeExecutor.kt):

```kotlin
class MyNewNodeExecutor @Inject constructor(
    // dependencies (repositories, the prompt engine, …) go here
) : NodeExecutor {
    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
    ): Flow<NodeOutput> = flow {
        // 1. emit NodeOutput.State events for progress
        // 2. emit exactly one terminal NodeOutput.Result at the end
    }
}
```

Contract reminders:

- Long-running work goes on `Dispatchers.IO` (or `Dispatchers.Default`
  for pure compute). Never block the main thread.
- The flow must terminate with **exactly one** `NodeOutput.Result`
  carrying a `NodeExecutionResult`. `NodeOutput.State` events in
  between are forwarded to the inline mini-console.
- If your executor calls into the LLM, run the assembled prompt
  through `PromptTemplateEngine.render(...)` first so `$DATE`,
  `$TOOLS` and friends get substituted.
- If your executor calls into the LLM, wrap reads of `node.systemPrompt`
  with the matching `DefaultPrompts.<Node>.SYSTEM_FALLBACK` so an
  unset prompt does not produce an empty system message:
  `val sp = node.systemPrompt ?: DefaultPrompts.MyNode.SYSTEM_FALLBACK`.

### 1.3. Register the executor in the factory

[`NodeExecutorFactory`](../app/src/main/java/ai/agent/android/domain/engine/executors/NodeExecutorFactory.kt)
is the single dispatch point used by `GraphExecutionEngine`. Inject
the new executor and add a branch to the `when`:

```kotlin
class NodeExecutorFactory @Inject constructor(
    // existing executors …
    private val myNewNodeExecutor: MyNewNodeExecutor,
) {
    fun getExecutor(type: NodeType): NodeExecutor = when (type) {
        // existing branches …
        NodeType.MY_NEW_TYPE -> myNewNodeExecutor
    }
}
```

The `when` is exhaustive, so the Kotlin compiler will refuse to build
until every `NodeType` is routed. Several types may share the same
executor — `INTENT_ROUTER`, `DECOMPOSITION`, and `EVALUATION` all
delegate to `SystemNodeExecutor` because they have the same
"LLM-with-system-prompt, no streaming side effects" shape. Reuse an
existing executor when the new type fits that shape; otherwise write a
fresh class.

### 1.4. Provide default context flags

`NodeContextConfig.defaultForType(type)` in
[`domain/models/NodeContextConfig.kt`](../app/src/main/java/ai/agent/android/domain/models/NodeContextConfig.kt)
returns the recommended starting context for a freshly-created node of
each type. Add a `when` branch for your new type that selects the
minimum set of blocks the executor actually needs:

```kotlin
NodeType.MY_NEW_TYPE -> NodeContextConfig(
    chatHistory = false,
    originalTask = true,
    nodeInput = true,
    longTermMemory = false,
    toolResults = false,
)
```

Defaults are not validation. Users can still toggle any flag in the
visual editor. The goal is "right out of the box" — a small LiteRT
model should not default to receiving the entire chat history, and a
tool node should not default to seeing long-term memory.

### 1.5. Update graph validation if needed

[`PipelineGraph.validate()`](../app/src/main/java/ai/agent/android/domain/models/PipelineGraph.kt)
already enforces the universal invariants (exactly one `INPUT`, at
least one `OUTPUT`, DAG, no dangling connections, non-empty
`contextConfig` for executor-driven types). You only need to touch
`validate()` if the new node type has special structural rules — for
example, "must be a singleton in the graph" or "must always feed into
an `OUTPUT`".

### 1.6. Mirror the new type into the browser editor

[`pipeline-editor.html`](../pipeline-editor.html) is a standalone
single-file HTML app that mirrors the Android pipeline schema. It is
the most common place where changes drift out of sync — every change
to the `NodeType` set must be reflected here in **three** places:

1. `NODE_TYPES` array (around line 827) — add a row with `id`,
   `label`, `color`, `icon`, `inputs`, `outputs`. Match the
   inputs/outputs to your executor's connector shape (most types are
   `1/1`; `INPUT` is `0/1`; `OUTPUT` is `1/0`; branching types like
   `IF_CONDITION` and `QUEUE_PROCESSOR` are `1/2`).
2. `defaultContextConfig(typeId)` (around line 972) — add a `case`
   that returns the same flags as your Kotlin `defaultForType` branch.
3. `NODE_TYPE_TOOLTIPS` (around line 1059) — add a one-line tooltip
   for the palette item.

If your node has a custom default system prompt, also add it to
`DEFAULT_SYSTEM_PROMPTS` (around line 855) so the editor seeds new
nodes with the same baseline as the Android app.

### 1.7. Tests

- A unit test for the executor that covers the happy path, at least
  one error branch, and (if relevant) the empty-input case.
- A new case in
  [`GraphExecutionEngineTest`](../app/src/test/java/ai/agent/android/domain/engine/GraphExecutionEngineTest.kt)
  that walks a minimal graph including the new node type.
- If `defaultForType` or `validate()` changed, add a unit test in the
  matching test file.

---

## 2. Add a new `Tool`

The agent calls tools through a Hilt multibinding keyed by tool name.
Adding a built-in tool is therefore a small, mechanical change: one
class plus one `@Binds` line.

### 2.1. Implement `LocalToolExecutor`

Create a class under
`app/src/main/java/ai/agent/android/data/tools/local/executors/` that
implements
[`LocalToolExecutor`](../app/src/main/java/ai/agent/android/domain/repositories/LocalToolExecutor.kt):

```kotlin
class MyToolExecutor @Inject constructor(
    // dependencies you need
) : LocalToolExecutor {

    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String): String {
        // 1. parse `arguments` as JSON (kotlinx.serialization or JSONObject —
        //    never manual string splitting)
        // 2. perform the action
        // 3. return a short textual result for the agent observation log
    }

    companion object {
        const val TOOL_NAME = "my_tool"
    }
}
```

Existing implementations to crib from:

- [`SearchToolExecutor`](../app/src/main/java/ai/agent/android/data/tools/local/executors/SearchToolExecutor.kt)
  — calls a public HTTP API.
- [`DelegateTaskExecutor`](../app/src/main/java/ai/agent/android/data/tools/local/executors/DelegateTaskExecutor.kt)
  — delegates to a different LLM provider via the cloud client factory.
- [`ScheduleTaskExecutor`](../app/src/main/java/ai/agent/android/data/tools/local/executors/ScheduleTaskExecutor.kt)
  — bridges to a domain use case (`ScheduleTaskUseCase`).

### 2.2. Register the executor

Add one `@Binds @IntoMap @StringKey(...)` entry to
[`di/LocalToolsModule.kt`](../app/src/main/java/ai/agent/android/di/LocalToolsModule.kt):

```kotlin
@Binds
@IntoMap
@StringKey(MyToolExecutor.TOOL_NAME)
abstract fun bindMyToolExecutor(executor: MyToolExecutor): LocalToolExecutor
```

That is the only DI touch-point.
[`ToolRepositoryImpl`](../app/src/main/java/ai/agent/android/data/repositories/ToolRepositoryImpl.kt)
already consumes the multibinding map and dispatches by name; you do
not edit it.

### 2.3. Declare risk and handle the human-in-the-loop gate

Every tool has a `ToolRisk`:

| Risk          | Behaviour                                                                 |
|---------------|---------------------------------------------------------------------------|
| `READ_ONLY`   | Runs immediately. No confirmation prompt.                                 |
| `SENSITIVE`   | The orchestrator emits `PendingConfirmation` and suspends until approval. |
| `DESTRUCTIVE` | Same as `SENSITIVE`. Use whenever data can be irreversibly modified.      |

If your tool sends an email, deletes a file, makes a purchase, or
mutates any system state the user would care to undo, set the risk to
`DESTRUCTIVE`. If it reads private data (location, contacts, calendar)
without modifying anything, set it to `SENSITIVE`. The
human-in-the-loop gate in `ToolNodeExecutor` and the chat UI is
non-optional — there is no code path that bypasses it.

### 2.4. Tests

- Unit test the executor with mocked dependencies. Cover the happy
  path, an invalid-arguments branch, and the failure branch
  (`runCatching` mapped to `ToolResult.Error`).
- If the tool surfaces new UI (e.g. a custom confirmation dialog), add
  an instrumented Compose test under `androidTest/`.

---

## 3. Add a new cloud provider

Cloud providers are dispatched by the single unified `CLOUD` node. You
do **not** create a new node type for a new provider — you teach the
existing factory and resolver about it. This keeps `pipeline-editor.html`
and the engine untouched.

### 3.1. Extend the `CloudProvider` enum

Add a constant to
[`domain/models/CloudProvider.kt`](../app/src/main/java/ai/agent/android/domain/models/CloudProvider.kt)
with a stable wire-id (the lowercase string used in pipeline JSON,
e.g. `"mistral"`). Existing values are
`OPENAI`, `ANTHROPIC`, `GOOGLE`, `DEEPSEEK`, `OLLAMA`.

### 3.2. Implement client construction

Add a branch in
[`KoogClientFactory.createClient(...)`](../app/src/main/java/ai/agent/android/data/engine/KoogClientFactory.kt)
that constructs the Koog executor for the new provider. If the
provider's SDK needs extra configuration (base URL, organization id),
keep all of that inside the helper method — `CloudLlmNodeExecutor`
should remain provider-agnostic.

### 3.3. Teach the model resolver

[`KoogCloudLlmModelResolver`](../app/src/main/java/ai/agent/android/data/engine/KoogCloudLlmModelResolver.kt)
owns the per-provider default model id and (for Ollama-shaped
providers) the context-window lookup. Add an entry so the resolver
can map a free-text model id to a concrete Koog `LLModel`.

### 3.4. Store the API key securely

API keys live in `EncryptedSharedPreferences` only — never in
DataStore, never in `local.properties`, never committed to git.

- Add a new key constant in
  [`ApiKeyManager`](../app/src/main/java/ai/agent/android/data/local/ApiKeyManager.kt)
  (e.g. `MISTRAL_KEY`).
- Add reader/writer methods for the new key (or extend the generic
  ones if your provider follows the standard shape).

### 3.5. Add a Settings section

Add a UI block to
[`SettingsScreen`](../app/src/main/java/ai/agent/android/presentation/ui/settings/SettingsScreen.kt)
that lets the user paste the API key and select a default model. Reuse
the existing `ProviderSettingsSection` shape so the new entry is
visually consistent with OpenAI / Anthropic / etc.

### 3.6. Tests

- Unit test the new branch in `KoogClientFactory` with a fake key.
- Unit test the resolver branch — both the "known model id" and the
  "fallback to default model" paths.
- If the Settings UI gained a new field, add a Compose test that
  verifies the field round-trips through the ViewModel.

---

## 4. Add a new prompt variable

A prompt variable is a `$KEY` placeholder substituted by
`PromptTemplateEngine` right before a system prompt is sent to an LLM.
The five built-in variables (`$DATE`, `$TIME`, `$TOOLS`, `$MODEL`,
`$MEMORY_SUMMARY`) all follow the same pattern.

### 4.1. Implement `PromptVariableProvider`

Create a class under
`app/src/main/java/ai/agent/android/data/prompt/` that implements
[`PromptVariableProvider`](../app/src/main/java/ai/agent/android/domain/prompt/PromptVariableProvider.kt):

```kotlin
class WeatherVariableProvider @Inject constructor(
    private val weatherRepository: WeatherRepository,
) : PromptVariableProvider {

    override fun key(): String = "WEATHER"

    override suspend fun resolve(): String =
        weatherRepository.currentSummary().orEmpty()
}
```

Rules:

- The key must match `[A-Z_][A-Z0-9_]*`. Lowercase keys and `$50`-style
  sequences are not recognised by the renderer.
- `resolve()` is allowed to suspend and perform I/O. If it throws,
  `PromptTemplateEngine` catches the exception, logs a warning, and
  substitutes an empty string — a broken provider can never break the
  whole render.
- Two providers must not share the same key. Resolution order is
  unspecified in that case.

### 4.2. Register the provider via Hilt

Add a `@Binds @IntoSet` method to
[`di/PromptTemplateModule.kt`](../app/src/main/java/ai/agent/android/di/PromptTemplateModule.kt):

```kotlin
@Binds
@IntoSet
abstract fun bindWeatherVariableProvider(
    impl: WeatherVariableProvider,
): PromptVariableProvider
```

`PromptTemplateEngine` consumes the resulting `Set<PromptVariableProvider>`
directly — no further wiring is needed.

### 4.3. Mirror the variable into the browser editor

Add the key to the `PROMPT_VARIABLES` array in
[`pipeline-editor.html`](../pipeline-editor.html) (around line 901):

```js
const PROMPT_VARIABLES = ['DATE', 'TIME', 'TOOLS', 'MODEL', 'MEMORY_SUMMARY', 'WEATHER'];
```

This drives the clickable chips above the `systemPrompt` textarea. If
you skip this step the runtime still resolves the variable (so prompts
work), but users will not see it in the autocomplete chips.

### 4.4. Document the variable

Add a row to the "Variables in system prompts" table in
[`docs/user-guide.md`](user-guide.md) (around line 247) so end users
can discover the new placeholder.

### 4.5. Tests

- Unit test the provider's `resolve()` with mocked dependencies.
  Include the failure path — verify that throwing inside `resolve()`
  results in an empty substitution after `PromptTemplateEngine` runs.
- Add a `PromptTemplateEngine` round-trip test that renders a template
  containing `$YOUR_KEY` and asserts on the substituted output.

---

## 5. Synchronization table

The same change can require updates in multiple places. The table
below lists the four extension points and every file that must move
together. **`pipeline-editor.html` is the most frequent drift point —
double-check it for every recipe in this guide.**

| You changed …                | Files you must also update                                                                                                                                                                                                                                          |
|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| A new `NodeType`             | `domain/models/NodeType.kt` · a new `NodeExecutor` implementation · `domain/engine/executors/NodeExecutorFactory.kt` · `domain/models/NodeContextConfig.kt` (`defaultForType`) · `domain/models/PipelineGraph.kt` (`validate`, if special invariants) · **`pipeline-editor.html`** (`NODE_TYPES`, `defaultContextConfig`, `NODE_TYPE_TOOLTIPS`, optional `DEFAULT_SYSTEM_PROMPTS`) · executor unit test · `GraphExecutionEngineTest` |
| A new `Tool`                 | a new `LocalToolExecutor` implementation · `di/LocalToolsModule.kt` (`@Binds @IntoMap @StringKey`) · declare `ToolRisk` correctly · executor unit test · optional Compose test if new UI                                                                            |
| A new cloud provider         | `domain/models/CloudProvider.kt` · `data/engine/KoogClientFactory.kt` · `data/engine/KoogCloudLlmModelResolver.kt` · `data/local/ApiKeyManager.kt` · `presentation/ui/settings/SettingsScreen.kt` · factory / resolver unit tests                                    |
| A new prompt variable        | a new `PromptVariableProvider` implementation · `di/PromptTemplateModule.kt` (`@Binds @IntoSet`) · **`pipeline-editor.html`** (`PROMPT_VARIABLES`) · `docs/user-guide.md` (variables table) · provider unit test · `PromptTemplateEngine` round-trip test           |

When in doubt, search the repository for the exact identifier you
changed (`grep -R 'MY_NEW_TYPE'`) — anything that already mentions one
of the existing constants is a candidate for the same edit.

---

## 6. Quality gate

Before pushing any change from the recipes above, run the full quality
gate locally:

```bash
./gradlew check
```

The aggregated `check` task runs:

- `detekt` — static analysis (style and complexity).
- `ktlintCheck` — Kotlin formatting.
- `lintDebug` — Android Lint.
- `testDebugUnitTest` — JVM unit tests.
- `koverVerifyDebug` — line-coverage verification.

The same task gates every pull request in CI, so running it locally
just trades local feedback for slower CI feedback. The coverage
baseline, per-package thresholds, and the rationale behind every
exclusion are documented in
[`docs/coverage-baseline.md`](coverage-baseline.md); the policy itself
lives in [`docs/static-analysis.md`](static-analysis.md).

---

## Further reading

- [`docs/architecture.md`](architecture.md) — the layered model, the
  pipeline engine, and the integration surface this guide extends.
- [`docs/user-guide.md`](user-guide.md) — how the features you ship
  appear to end users.
- [`docs/coverage-baseline.md`](coverage-baseline.md) — current
  coverage numbers and what is excluded from measurement.
- [`docs/static-analysis.md`](static-analysis.md) — detekt / ktlint /
  Android Lint policy.
- [`SECURITY.md`](../SECURITY.md) — threat model and how to report a
  vulnerability before shipping a risky tool or provider.
