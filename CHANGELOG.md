# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Until version `1.0.0`, this project is considered a pre-release: breaking
changes to the Kotlin public surface, pipeline JSON schema, settings layout
and on-device storage formats may ship in minor releases without a
migration path. See the *Pre-release notice* in [README.md](README.md) for
details.

## [Unreleased]

### Added

- Bundled brand fonts in `:app/src/main/res/font/` (Phase 21 / Task 3/11):
  Inter Regular / Medium / SemiBold / Bold and JetBrains Mono Regular /
  Medium. Sources are the SIL OFL 1.1 upstreams (Inter v4.0,
  JetBrains Mono v2.304) subset to Latin-1 plus a handful of typographic
  punctuation glyphs. Total APK delta ≈ 152 KB (well under the 350 KB
  ceiling). `App.onCreate()` calls
  `KnotworkFontsBootstrap.install()` so `KnotworkTextStyles` resolves
  against the bundled families on the first frame instead of system
  fallbacks. License notices ship in `app/src/main/assets/THIRD_PARTY_LICENSES.txt`.
- 17 custom `ImageVector` icons hand-ported from
  `project_docs/design/icons-src/` into
  `:catalog/.../icons/imagevector/` — brand mark + wordmark glyph, the
  Pipelines-tab `Flow`, editor `AutoLayout`, memory `Brain`, and the 12
  pipeline-node glyphs (`NodeInput`, `NodeIntentRouter`, `NodeBranch`,
  `NodeClarify`, `NodeLite`, `NodeCloud`, `NodeTool`, `NodeDecompose`,
  `NodeQueue`, `NodeEval`, `NodeSummary`, `NodeOutput`). The `AppIcons`
  facade now resolves to real vectors instead of the prior `error(...)`
  stubs; a JVM unit test (`AppIconsTest`) guards 24×24-dp/viewport and
  non-empty path invariants for each entry.
- `IconCatalogPage` composable in `:catalog` rendering every custom icon
  plus a curated set of Material Icons Extended at 24/32/48 dp on light
  and dark swatches. Light + dark Roborazzi baselines committed under
  `:catalog/src/test/snapshots/icon_catalog_{light,dark}.png` so future
  icon edits surface in code review as a snapshot diff.
- Knotwork adaptive launcher icon in
  `:app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml`,
  derived from `project_docs/design/compose/brand/ic_launcher_*.svg`.
  Background = `knotwork_accent_500`, foreground = the three-vertex
  Knotwork mark in `knotwork_surface_0`, monochrome = the mark in
  pure black for the Android 13+ themed-icon mode. Adaptive XMLs in
  `mipmap-anydpi-v26/` wire all three layers; no `mipmap-anydpi-v33/`
  folder is created (dead path under `minSdk 36`, see `decisions.md §15`).
- Platform splash window via `androidx.core.splashscreen.installSplashScreen()`.
  `Theme.App.Splash` (parent `Theme.SplashScreen`) pins
  `windowSplashScreenBackground = @color/knotwork_accent_500` and
  `windowSplashScreenAnimatedIcon = @drawable/ic_launcher_foreground`;
  `postSplashScreenTheme` swaps back to the regular app theme when the
  platform splash dismisses on the first Compose frame, handing off to
  the existing in-app `SplashScreen` composable without a blank-frame
  flash.
- Knotwork design tokens ported into `:catalog` under
  `app.knotwork.design.tokens` (Phase 21 / Task 2/11). Six token files —
  `Color.kt`, `ExtendedColors.kt`, `Type.kt`, `Spacing.kt`, `Shape.kt`,
  `Elevation.kt`, `Motion.kt` — establish the canonical Compose-side
  source of truth for colour, typography, spacing, shape, elevation and
  motion. Every token is exposed both via the upstream `KnotworkPalette`
  / `KnotworkLight` / `KnotworkDark` objects and through a Material3
  mapping (`knotworkLightColorScheme()` / `knotworkDarkColorScheme()` /
  `knotworkTypography()` / `MaterialKnotworkShapes`).
- `KnotworkTheme` composable in `:catalog` now wires the tokens into a
  real `MaterialTheme` and installs `KnotworkExtendedColors`,
  `KnotworkSpacing`, `KnotworkShapes`, `KnotworkElevation` and
  `KnotworkMotion` into composition locals. A sibling `object KnotworkTheme`
  exposes them through `KnotworkTheme.extended` / `.spacing` / `.shapes`
  / `.elevation` / `.motion` accessors, mirroring the shape of
  `MaterialTheme.colorScheme`. Material You / dynamic colour stays
  intentionally unexposed.
- `FoundationsCatalogPage` composable plus light + dark `@Preview`s
  rendering the palette, type scale and spacing tokens as a single
  scrollable surface for design review and snapshot baselines.
- `knotwork_*` colour and `knotwork_sp_*` dimen mirrors in
  `:app/src/main/res/values/colors.xml`,
  `:app/src/main/res/values-night/colors.xml` and
  `:app/src/main/res/values/dimens.xml` so non-Compose surfaces
  (notifications, app widgets, splash window theme) can reach the
  Knotwork tokens through the standard Android resource pipeline.
  Compose code keeps reading from `:catalog` `KnotworkTheme.*`. Resources
  are pre-published — `tools:ignore="UnusedResources"` is applied at the
  file level until the consuming surfaces land in later Phase 21 tasks.
- Snapshot-testing infrastructure: Roborazzi `1.60.0` + Robolectric
  `4.16.1` wired into `:catalog`, with the first baseline (light + dark
  PNGs of `FoundationsCatalogPage`) committed under
  `:catalog/src/test/snapshots/`. `./gradlew :catalog:recordRoborazziDebug`
  refreshes the baselines; `./gradlew :catalog:verifyRoborazziDebug`
  is the CI gate. Aggregated `./gradlew check` already triggers
  `verifyRoborazziDebug` via the `testDebugUnitTest` chain.
- `:catalog` Android library module hosting the Knotwork design system.
  Phase 21 / Task 1/11 shipped the project scaffold: namespace
  `app.knotwork.design`, `minSdk 36` / `compileSdk 37`, Compose BOM,
  ktlint and detekt mirrored from `:app`. Task 2/11 replaced the
  pass-through `KnotworkTheme` with the real token-wired implementation
  (see entries above).
- `androidx.core.splashscreen 1.0.1` dependency wired into `:app`. The
  platform-side `installSplashScreen(...)` call lands in Task 3/11 once
  the brand mark and accent ramp are available; declaring the artefact
  here unblocks downstream tasks without touching the existing Compose
  `SplashScreen` route.
- `android:enableOnBackInvokedCallback="true"` declared on the agent
  `<application>` element to opt the app in to Android's predictive-back
  gesture stack. Surface-level `PredictiveBackHandler` wiring on modal
  sheets follows in Task 4/11.

### Changed

- `MainActivity.enableEdgeToEdge(...)` now passes explicit transparent
  `SystemBarStyle.auto(...)` parameters for both status and navigation
  bars so the design system can paint to the device edges deterministically
  in both light and dark themes. Visible behaviour is unchanged on the
  current screens.
- `CONTRIBUTING.md` now lists JDK 21 as the required toolchain for
  running unit tests (Phase 21 / Task 2/11). Roborazzi's Robolectric
  backend requires JDK 21 to render against `minSdk 36`. Production
  code still compiles to `JavaVersion.VERSION_17` / `JvmTarget.JVM_17`.
  Note for repo owner: the Action runner in `.github/workflows/check.yml`
  (gitignored — see `.gitignore`) must be bumped from `java-version: '17'`
  to `'21'` for the gate to keep passing once this branch lands; the
  bump cannot be made from this PR because the Git PAT lacks the
  `workflow` scope.

### Changed

- Callee-side AppFunctions surface now relies on the auto-merged
  `androidx.appfunctions.service.PlatformAppFunctionService` (from
  `appfunctions-service`) for dispatch. `SearchAppFunction.invoke` is annotated with
  `@AppFunction`, so its KSP-generated entry in `app_functions_v2.xml` advertises the
  function to external callers. **The wire id contains literal backticks around the
  `data` package segment** —
  `` ai.agent.android.`data`.tools.local.appfunctions.SearchAppFunction#invoke `` —
  because the AppFunctions compiler bakes Kotlin source-level escaping for soft
  keywords into the id string. External callers must include the backticks verbatim,
  exactly as `AppFunctionsEndToEndTest.SEARCH_TOOL_ID` and the `:tools-probe`
  `MainActivity` constant do. The Hilt-managed instance is supplied to the
  AppFunctions runtime through a new `AppFunctionConfiguration.Provider`
  implementation on `App`.

### Removed

- Hand-rolled callee dispatch: `AgentAppFunctionService`, `AppFunctionRouter`,
  `AppFunctionDispatchEntryPoint`, the matching manifest `<service>` entry, and
  `AppFunctionRouterTest`. The merged platform service plus KSP-generated invokers cover
  the same surface end-to-end and remove the parallel routing path that previously had to
  be kept in sync with `SearchAppFunction`.

### Added

- `:tools-probe` debug-only Android module shipping a single
  `@AppFunction echo(message)` so the Phase 20 end-to-end instrumented test
  (`AppFunctionsEndToEndTest` in `:app/src/androidTest`) has a deterministic
  remote target. The probe APK is installed on the device before the
  agent's instrumented tests via a Gradle task hook that adds
  `:tools-probe:installDebug` as a prerequisite of
  `installDebugAndroidTest` — works for both CLI
  (`./gradlew :app:connectedDebugAndroidTest`) and Android Studio's Run
  Test flows. Its `MainActivity` doubles as a one-tap manual smoke for the
  agent's callee-side surface (`search_tool` query "Knotwork") on the
  Phase 20 reference device.
- `AppFunctionsEndToEndTest` covering four end-to-end scenarios on Android
  16+: caller-side `ToolRepository.executeTool` round-trip, HITL gate
  emission of `WaitingForApproval` for SENSITIVE-by-default AppFunctions
  followed by `ToolNodeExecutor.resumeWithApproval`, callee-side invocation
  of `search_tool` through the system `AppFunctionManager`, and risk
  override resolution via `SettingsRepository.setAppFunctionRiskOverride`.
  Note: on stock Android 16 builds the `EXECUTE_APP_FUNCTIONS` permission
  is declared at signature / module / preinstalled protection level —
  verified on both the Pixel 9 Pro emulator
  (`system-images;android-36;default;x86_64`) and a Samsung Galaxy S25
  Ultra (Android 16). `pm grant` rejects the permission with "not a
  changeable permission type" and `appops set` reports "Unknown operation".
  Third-party agents can therefore neither observe nor invoke cross-package
  AppFunctions on those builds, regardless of whether the host is an
  emulator or real device. The test detects that platform state from the
  captured grant-attempt stderr and skips itself via `Assume.assumeTrue`
  with a detailed transcript. The probe's manual MainActivity button hits
  the same platform restriction (plus a second pre-requisite: the agent's
  `search_tool` will only show up in `app_functions_v2.xml` once it gains
  an `@AppFunction` annotation in Phase 20-7). Both gates re-open
  automatically on future Android builds that relax the permission's
  protection level for debuggable apps.
- `AppFunctionsE2ETestEntryPoint` (Hilt `EntryPoint`) under
  `data/testing/` exposing the singletons the new instrumented test
  consumes (`ToolRepository`, `SettingsRepository`, `ChatRepository`),
  avoiding a parallel Hilt test component.
- Callee-side AppFunctions surface: `AgentAppFunctionService` now routes
  incoming `ExecuteAppFunctionRequest`s through a pure-Kotlin
  `AppFunctionRouter` that resolves Hilt-managed wrappers via an
  application-scoped `AppFunctionDispatchEntryPoint`. The first wrapper,
  `SearchAppFunction`, exposes the read-only `search_tool` built-in to
  external callers using the same Wikipedia code path the agent invokes
  internally. Side-effect built-ins (`schedule_task`, `delegate_task`)
  remain intentionally excluded from the callee surface. Cancellation,
  invalid-argument, function-not-found and unexpected-error paths are
  translated into the matching `AppFunctionException` codes.
- `ToolRisk` domain model (`READ_ONLY` / `SENSITIVE` / `DESTRUCTIVE`) with
  per-tool defaults: `search_tool` → `READ_ONLY`, `schedule_task` /
  `delegate_task` → `SENSITIVE`, discovered AppFunctions → `SENSITIVE` (with
  per-tool override via `SettingsRepository.appFunctionRiskOverrides`),
  MCP tools → blanket `SENSITIVE`. Resolved through the new
  `ToolRepository.getRisk(name)` seam. HITL gate consumption lands in a
  follow-up task; this change ships the data model only.
- `CONTRIBUTING.md` at the repository root covering dev setup, build & test
  commands, branch model, Conventional Commits, the pull-request checklist,
  and the English-only language policy.
- `CODE_OF_CONDUCT.md` at the repository root adopting Contributor
  Covenant 2.1; reporting routes through the same private GitHub Security
  Advisories channel as `SECURITY.md`.
- Public contributor-facing documentation under `docs/`:
  `docs/code-style.md`, `docs/testing.md`, `docs/api-conventions.md`.
- Caller-side end-to-end execution of discovered AppFunctions. The local
  tool catalogue now includes AppFunctions surfaced by
  `LocalAppFunctionManager` alongside built-ins (built-ins still win on
  name collisions, with a warning log). AppFunctions are addressed by
  their qualified name (`"${packageName}/${id}"`) so identical ids exposed
  by different packages can coexist without overwriting each other in the
  caller-side cache. `ToolRepository.executeTool` routes AppFunction calls
  through `LocalAppFunctionManager.invokeByName`, which encodes arguments
  via `AppFunctionDataCodec`, dispatches through the system
  `AppFunctionManager`, and renders the response back to a flat JSON
  string. Disabling an AppFunction via
  `SettingsRepository.disabledAppFunctions` now also gates execution.

### Changed

- Coverage policy wording in `docs/testing.md` aligned with the actual
  enforcement: target 100% logic coverage for new code in `domain` /
  `data`, build gate at 70% LINE aggregate (per-package decomposition in
  `docs/coverage-baseline.md`, full policy in `docs/static-analysis.md`).
- Prominent inline approval prompt (`ApprovalBanner`) rendered directly
  above the chat input whenever the orchestrator is in `WaitingForApproval`.
  Replaces the easy-to-miss 16dp console-line affordance as the primary
  surface for the HITL decision; the console line still appears as a
  short status echo. Full-width Approve / Deny buttons meet the 48dp tap
  target and the banner is unaffected by the compact-console layout.
- Risk-based Human-in-the-Loop (HITL) gate. `ToolNodeExecutor` now consults
  `ToolRepository.getRisk(name)` instead of a global flag: `SENSITIVE` and
  `DESTRUCTIVE` tools always prompt; `READ_ONLY` tools run silently unless
  the user has globally opted into "ask on every tool call" via
  `SettingsRepository.requiresUserConfirmation` (which is now an override,
  not the primary trigger). `AgentOrchestratorState.WaitingForApproval`
  carries the resolved `risk` and the inline approval row in the chat
  console renders a coloured risk chip (`READ` / `SENS` / `DEST`) next to
  the tool name. `DESTRUCTIVE` approvals route through a dedicated
  `IMPORTANCE_HIGH` notification channel with a warning glyph; `SENSITIVE`
  / opt-in `READ_ONLY` continue on the existing approval channel.

### Deprecated

### Removed

### Fixed

- `ApprovalBannerTest` and `ChatScreenTest` no longer fail to compile on the
  current Compose / `UiText` surfaces. The fixes (a stale `assertDoesNotExist`
  import and a raw `String` passed where a `UiText?` is expected) had drifted
  silently because CI runs JVM-only `./gradlew check`; the new Phase 20-6
  instrumented-test work made the breakage observable.

### Security

## [0.1.0] - 2026-05-11

First public pre-release. This entry retrospectively summarises the work
that produced the initial 0.1.0 snapshot.

### Added

- On-device LLM inference engine built on **LiteRT-LM** (Google Edge AI,
  formerly TensorFlow Lite) with optional NPU/GPU acceleration and a
  streaming token API.
- **AppFunctions Jetpack** integration for on-device tool calling.
- **Model Context Protocol (MCP)** client for connecting external tool
  servers.
- Optional cloud LLM providers — OpenAI, Anthropic, Google (Gemini),
  DeepSeek and Ollama — all opt-in and bring-your-own-key.
- Visual pipeline orchestrator inside the app for editing typed
  node graphs (input, local LLM, cloud LLM, tool, routing,
  decomposition, evaluation, clarification, output).
- Standalone browser-based `pipeline-editor.html` for authoring and
  exporting pipelines without launching the app.
- Long-term memory with semantic retrieval (RAG) over past conversations.
- Multi-session chats backed by a priority task queue.
- Per-chat pipeline binding with rename / duplicate / delete operations
  on the pipeline library.
- Prompt variables substituted fresh on every render: `$DATE`, `$TIME`,
  `$TOOLS`, `$MODEL`, `$MEMORY_SUMMARY`.
- Agent-initiated clarifications — a pipeline node can ask the user a
  question mid-execution and suspend until a reply arrives
  (human-in-the-loop).
- Live mini-console and an expanded execution-log view inside the app
  for observing pipeline runs.
- Opt-in Firebase Crashlytics integration for anonymous crash reporting,
  disabled by default and gated by an explicit in-app consent dialog.

### Changed

- Decomposed `GraphExecutionEngine` into per-node `NodeExecutor`
  strategies so node-specific execution logic lives next to the node
  type instead of in a single monolithic engine.
- Unified the cloud-provider pipeline nodes into a single `CLOUD` node
  with a `provider` parameter, replacing the earlier per-provider node
  types.
- Extracted all hardcoded user-visible strings, prompt templates and
  magic numbers into Android resources and Kotlin constants.
- Promoted detekt, ktlint and Android lint to strict mode in the build:
  new warnings fail the local and CI quality gate.

### Fixed

- Restored prompt-variable interpolation in `ToolRepositoryImpl` — tool
  arguments were previously forwarded literally instead of being
  rendered through the prompt template engine.
- Implemented `AgentAppFunctionService.onExecuteFunction`, which was
  previously a no-op stub and silently dropped invocations.
- Surfaced JSON Schema metadata from `KoogMcpClient.getTools` so MCP
  tools correctly advertise their argument schema to the agent.
- Made `AgentIdleManager` and `TaskQueueManagerImpl` thread-safe by
  replacing ad-hoc state mutations with synchronised primitives.
- Eliminated the N+1 query pattern in `ChatRepositoryImpl.saveMessage`
  by batching session updates alongside the message insert.

### Security

- **At-rest encryption**: the Room database (`agent_database.db`) is
  encrypted with SQLCipher via `net.zetetic:sqlcipher-android`.
- **API keys**: cloud provider API keys are stored in
  `EncryptedSharedPreferences` and never written to plain
  `DataStore`.
- **SQLCipher passphrase**: a 32-byte random passphrase is generated on
  first launch and stored in `EncryptedSharedPreferences`.
- **Master key**: `EncryptedSharedPreferences` is rooted in the Android
  Keystore, so the master key is hardware-backed where available.

[Unreleased]: https://github.com/alexeyw/PersonalAndroidAIAgent/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/alexeyw/PersonalAndroidAIAgent/releases/tag/v0.1.0
