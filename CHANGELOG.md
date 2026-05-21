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

- **Tools — full MCP server configuration** (Phase 22 / Task 10/17) —
  Each MCP server now carries a full [McpServerConfig]: optional
  display name, transport selection (SSE / Streamable HTTP), and
  arbitrary request headers (the typical case being
  `Authorization: Bearer …` for auth-required servers). Adding and
  editing happen on a dedicated full-screen `McpServerConfigScreen`
  (route `tools/mcp-config?originalUrl={url}`) — the pencil
  affordance on each row opens the screen pre-filled, the `+ Add
  MCP` link opens it blank, and Save / Cancel pop back to the
  list. KoogMcpClient now configures the Ktor `defaultRequest`
  block with the user-supplied headers so they reach both the SSE
  handshake and every subsequent JSON-RPC call. Persistence
  switched from a `stringSet` of URLs to a JSON-encoded list of
  configs in the new `mcp_servers_json` key — the manager one-shot
  migrates the legacy key on the first read, and writes the new
  shape on the next mutation.
- **Tools — MCP per-tool detail and tool-list fetcher** (Phase 22 /
  Task 10/17) — Tools surface now drives a real
  `tools/list` MCP round-trip through the new
  `McpServerRepository` (data impl: `McpServerRepositoryImpl`).
  Per-server snapshots in `ToolsUiState` carry the live
  `McpConnectionStatus` (`Connecting` / `Connected` /
  `Error(reason)`) and the discovered `McpTool` list — both
  rendered in the catalog under the expanded server row. Tool
  list responses are cached for 5 minutes; the trailing refresh
  icon on every server row force-bypasses the cache. Per-MCP-tool
  `ToolDetailScreen` now resolves a real
  `McpTool.inputSchemaJson` instead of the placeholder, and
  local AppFunction tools render their actual
  `AgentTool.parameters` (no more cosmetic `{ "...": ... }`
  stub). New `disabledMcpTools` set in `SettingsRepository`
  (keyed by `mcp:<sha8(serverUrl)>:<toolName>`) tracks the
  per-MCP-tool enabled state independently of
  `disabledAppFunctions`. Standalone `AddMcpServerScreen` route
  + file deleted — the inline add-form on `ToolsScreen` is the
  single entry point.

- **Settings — redesign + full backend wiring** (Phase 22 / Task 9/17) —
  Settings was rewritten end-to-end to match the new mockup. New surface
  hosts nine cards: identity (device-id + Keystore probe), system
  instructions (with variable chip row and char/token counter),
  restrictions (segmented `Approve tool calls` + Block destructive /
  Block network from local model toggles + Cap autonomous steps),
  LLM parameters (Temperature / Top-K / Top-P / Repetition penalty /
  Max context / Max steps + "Reset to defaults"), local model
  (metadata card + Inference backend dropdown + Test backend with
  persisted `TestProbeResult`), external providers (collapsed nav-rows
  with key fingerprint + Add provider sheet → `ProviderDetailScreen`),
  memory (CHUNKS / SIZE / THREADS / AVG SCORE stat grid +
  Auto-summarize threshold slider + Embedding model row +
  Export / Re-embed / Clear actions), notifications (Long-running
  tasks toggle), and privacy (Crash reporting + Reset all settings).
  The legacy boolean `requiresUserConfirmation` flag is migrated
  one-shot into the new `ToolApprovalPolicy` enum
  (`true` → `SensitiveOrDestructive`, `false` → `NeverPrompt`). New
  domain: `IdentityRepository`, `MemoryRepository.observeStats()` +
  `deleteAllMemories()`, `LocalModelRepository.observeActiveModelMeta()`,
  `EmbeddingModelMetaProvider` (static), `LongRunningTaskNotifier`,
  `ToolApprovalPolicy`, `TestProbeResult`, `MemoryStats`,
  `ActiveModelMeta`, `ProviderSummary`, `Identity`, four new
  `$VARIABLE` providers (`$LANG`, `$LOCATION`, `$USER`, `$DEVICE`),
  and seven new use cases (`ResetSamplingDefaults`, `ClearAllMemory`,
  `ExportMemoryBase` over SAF, `ReembedAllMemories` with progress flow,
  `TestBackend` returning typed probe metrics,
  `GetSystemPromptVariableCatalog`). `ToolNodeExecutor` now gates by
  the new policy enum and hard-denies destructive tools when the
  `Block destructive tools` toggle is on; `KoogClientFactory` returns
  `null` for every cloud provider (Ollama still reachable) when the
  `Block network from local model` toggle is on. Restart-required
  banner detects backend / Ollama URL changes and reboots via
  `ProcessPhoenix.triggerRebirth`. Destructive actions
  (Clear memory / Reset settings) use a typed-confirm dialog (`yes`
  keyword, matching the HITL Destructive pattern). New routes:
  `settings/provider/{providerId}` (detail editor) +
  `settings/provider/add` (picker). About screen expanded with
  version / commit / license / acknowledgments / privacy policy
  sections.
- **Settings — port provider/sampling forms in the Knotwork style**
  (Phase 22 / Task 8/17) — the Settings screen now drives the catalog
  `SettingsContent` as the single source of truth for chrome (TopAppBar,
  sections, scroll, visual states). Provider configuration moved to the
  catalog: `KnotworkProviderRow` renders a collapsible card with masked
  API-key input, model dropdown, and (for Ollama) base-URL +
  context-window fields with inline validation. Sampling sliders
  (temperature, top-K, top-P, max context, pipeline max steps, memory
  summary default limit) render through `KnotworkParamSlider`. The
  system-prompt prefix uses the new `KnotworkMonoTextArea`. A new
  `memorySummaryDefaultLimit` slider exposes the `$MEMORY_SUMMARY`
  limit (1–50) on the Memory section. About surfaces `app version`,
  short `git SHA` (via `BuildConfig.GIT_SHA`), and a license link
  routed to `AboutScreen`. MCP section navigates to the Tools screen
  for server management. `SettingsContent` now accepts an optional
  `rowContent` override so the app can replace the default
  title-subtitle-trailing row with the richer Knotwork variants without
  forking the catalog scaffolding, and an optional `onBack` callback so
  the embedded TopAppBar can render a navigation icon. Restart-required
  / destructive-confirm wiring stays deferred to Task 9 (audit pass).
- **Memory — design audit & alignment** (Phase 22 / Task 7/17) — full 7-state
  Roborazzi baseline (Empty / Populated / Searching / LoadingMore /
  EntryExpanded / Editing / Error) in both themes plus a populated-pinned
  snapshot covering the Task 6 glyph. Detail-sheet tag chips switched from
  `ChipStyle.Outline` to `ChipStyle.Tonal` per `screens/README.md §C6`. New
  `MemoryAccessibilityTest` enumerates the TalkBack-reachable surfaces on
  happy path #5 (Search → expand → delete) and asserts the pinned-row
  glyph publishes a non-blank `contentDescription` so colour is never the
  only signal. Audit findings (closed / deferred) appended to
  `project_docs/ui-audit-phase22.md`.
- **Memory — edit + pin persistence** (Phase 22 / Task 6/17) — the
  detail-sheet Edit and Pin affordances on the Memory screen now drive
  real persistence instead of "coming soon" snackbars.
  `MemoryRepository` gains `updateMemory(id, text, embedding)` and
  `setMemoryPinned(id, pinned)`; the editor regenerates the vector
  embedding for the new text through `TextEmbeddingEngine` so semantic
  search stays coherent with the visible body. The catalog
  `MemoryRow` / `MemoryEntryDetail` / `MemoryEntryRow` carry a new
  `isPinned` field — pinned rows render a leading star glyph and float
  to the top of the list ahead of the active sort partition, and the
  sheet pin button toggles between "Pin to top" and "Unpin" depending
  on the current state. Room migrates `v22 → v23` adding
  `memory_chunks.isPinned INTEGER NOT NULL DEFAULT 0`.
- **Chat home — design audit & alignment** (Phase 22 / Task 5/17) — token
  sweep over the production chat scope, drawer slide-in motion gated
  through `respectReducedMotionTransitions`, HitlConfirm snapshot matrix
  expanded to the 3 risk variants (`Readonly` / `Sensitive` /
  `Destructive`), and 2 new font-scale 2.0× snapshots covering the worst
  cases. New `ChatHomeAccessibilityTest` asserts that the TopAppBar,
  drawer, HITL card, and console pane each publish the minimum number of
  TalkBack-reachable nodes. Audit findings (closed / deferred) live in
  `project_docs/ui-audit-phase22.md`.
- **Chat home — secondary affordances** (Phase 22 / Task 4/17) — the
  drawer, top app bar, and composer-overflow callbacks that were left
  stubbed in Tasks 1–3 now drive real backend operations. Every entry
  point in `compose/screens/README.md §C1` is wired:
  - **New chat** opens a `ModalBottomSheet` pipeline picker pre-selected
    to the user's current binding; confirming persists a fresh
    `ChatSession` and switches to it.
  - **Rename thread** opens a rename sheet (`OutlinedTextField` +
    Save / Cancel) driving the new
    `ChatRepository.renameSession(sessionId, newName)`. The input is
    trimmed and a blank Save is a no-op.
  - **Favorite chat** persists a session-level `isStarred` flag via
    `ChatRepository.setSessionFavorite`. Favorited chats sort to the top
    of the drawer thread list and render a small leading star glyph next
    to the title (new `ChatHomeThreadRow.starred` field in the catalog).
  - **Import chat** launches `ActivityResultContracts.OpenDocument()`
    with mime `application/json`, reads the file on `Dispatchers.IO`,
    and forwards the payload to `ChatRepository.importChat(json)` (port
    of the legacy JSON parser; accepts both export-shaped objects and
    bare message arrays).
  - **Open Settings / Models** — `ChatHomeScreen` now takes
    `onOpenSettings` / `onOpenModels` constructor parameters wired by
    `AppNavGraph` to the existing `NavRoutes.SETTINGS` and
    `NavRoutes.MODELS` routes.
  - **Model picker** opens a `ModalBottomSheet` listing the locally
    installed LiteRT models (live from
    `LocalModelRepository.getAllModels()`). Picking a model calls
    `setActiveModel` + `LoadModelUseCase` to swap the inference handle;
    the empty case surfaces an "Open Models" pill deep-linking to the
    Models tab.
  - **Overflow menu** (anchored `DropdownMenu` on the TopAppBar `⋮`
    icon) drives Export chat, Delete chat (destructive `AlertDialog`),
    and Clear console. Export emits a `ChatExportPayload` via
    `viewModel.exportEvents`; the screen handles the
    `Intent.ACTION_SEND` share-sheet dispatch. Delete cascades into
    auto-selecting the next available thread, or creating a fresh
    unbound chat when none remains.
- **Room migration `v21 → v22`** — adds the `isStarred INTEGER NOT NULL
  DEFAULT 0` column to `chat_sessions`. Backfilled to `0` for every
  pre-existing row. Distinct from `MIGRATION_19_20` which introduced the
  message-level `isStarred` on `chat_messages`.
- **`ChatExportPayload`** relocated from `chat/legacy/` to `chat/home/`
  so the legacy package can be deleted in Phase 22 / Task 17 without a
  dangling import.
- **Catalog: `ChatHomeThreadRow.starred: Boolean`** + leading star glyph
  in `ChatHomeDrawerThreadRow`.

### Changed

- `ChatHomeViewModel` constructor now injects `LocalModelRepository` and
  `LoadModelUseCase`. Both are used exclusively by the new model-picker
  sheet — every other flow is untouched.
- `ChatHomeStateMapping.toViewState` accepts a `threads:
  List<ChatHomeThreadRow>` parameter so the screen can pass the live VM
  projection. The fixtures fallback is preserved for the debug picker
  when the drawer is forced open before any session has been persisted.
- `ChatHomeViewModel._modelName` is now derived from the active
  `LocalModel.name` instead of the static `"Local model"` placeholder.

- **Chat home — Console pane real-time wiring** (Phase 22 / Task 3/17) —
  replaces the `sampleConsoleLines()` / `sampleConsoleVars()` /
  `sampleConsoleTraces()` fixtures with live data streamed off the agent
  orchestrator. The console pane now reflects the real pipeline run on
  every step and survives Clear / Copy / tab-change interactions across
  process death.
  - New domain state `AgentOrchestratorState.NodeIO(nodeId, nodeType,
    input, output)`, emitted by `GraphExecutionEngine` after every
    non-`INPUT` / non-`OUTPUT` node completes (right after the existing
    `PipelineTrace` emission). Powers the Vars tab of the console pane —
    the VM aggregates emissions into a `LinkedHashMap<nodeId, NodeIO>`
    so repeated invocations of the same node id overwrite (rather than
    duplicate) Vars rows.
  - New pure-Kotlin `ChatHomeConsoleMapping.kt`: `ConsoleEvent →
    ConsoleLine` (timestamp `HH:mm:ss.SSS`, source resolution and severity
    mapping covering every `ConsoleEventType`), `TraceStep →
    ConsoleTraceSpan`, `NodeIO → List<ConsoleVarRow>` with
    `JSONObject.quote`-escaped values.
  - `ChatHomeViewModel` extensions: `consoleLines`, `consoleVars`,
    `consoleTraces`, `consoleTab`, `consoleClearConfirmRequested`,
    `consoleSnackbarEvents` flows plus public methods
    `onConsoleTabChange`, `requestConsoleClear`,
    `confirmConsoleClear`, `dismissConsoleClear`,
    `signalConsoleLineCopied`, `signalConsoleAllCopied`,
    `buildConsoleLineCopyPayload`, `buildConsoleAllCopyPayload`. The
    `consoleClearBaseline` logic from the legacy `ChatViewModel` is
    preserved verbatim so a mid-run `Clear` survives the next cumulative
    engine snapshot.
  - `SettingsRepository` gains
    `consolePreferredConsoleTabName: Flow<String>` +
    `setConsolePreferredConsoleTabName(name: String)`. The VM hydrates
    the active tab from this flow at init and writes through on
    `onConsoleTabChange`, so the user's chosen tab survives process
    death. Domain stays free of `:catalog` imports — the enum name is
    stored as a raw string and decoded at the presentation boundary.
  - `ChatHomeScreen` wires the four previously-stubbed callbacks:
    `onConsoleCopyLine` writes the plain-text payload via
    `LocalClipboardManager` and raises a Snackbar; `onConsoleCopyAll`
    pre-filters the buffer through the active `ConsoleFilter` +
    search-query so the clipboard mirrors exactly what the user sees;
    `onConsoleClear` opens a destructive `AlertDialog` and only advances
    the baseline once the user confirms; `onConsoleTabChange` persists
    through the VM. The Clear and Line-copied snackbars use new
    `chat_console_clear_dialog_confirm` / `chat_console_clear_dialog_cancel`
    / `chat_snackbar_console_line_copied` strings.
  - `ChatHomeStateMapping.toViewState` now accepts `consoleLogs`,
    `consoleVars`, `consoleTraces`, `consoleTab` and forwards them to
    `ChatHomeConsoleState`. The old `sampleConsoleLines()` /
    `sampleConsoleVars()` / `sampleConsoleTraces()` fixtures are
    deleted.
  - **Console pane is now an independent overlay** —
    `ChatHomeConsoleState.snap` becomes nullable (`null` = closed);
    catalog `ChatHomeContent` renders the overlay whenever
    `state.console.snap != null` instead of gating on
    `visualState == ConsoleExpanded`. `ChatHomeUiState.ConsoleExpanded`
    is removed from the sealed hierarchy. The VM exposes a dedicated
    `consoleSnap: StateFlow<ConsoleSnap?>` that is orthogonal to the
    chat state machine, so the pane survives `Generating →
    HitlConfirm → Clarification → Completed / Error` transitions
    instead of being closed by every terminal emission. The debug
    state picker now routes its `CONSOLE_*` entries through
    `debugConsoleSnapForId` + `openConsole(snap)`.
  - Console `Close` icon in the Partial / Full header now actually
    dismisses the overlay (catalog `ConsolePane` gains a dedicated
    `onCloseConsole` parameter); previously it only snapped the pane
    down to `Peek`.
  - `FullTabStrip` columns widened from 72 dp → 88 dp + `maxLines = 1`
    so the longest tab label (`TRACES`) no longer wraps onto two lines.
  - Pill-tap affordance: tapping the agent-status pill above the
    composer opens the console pane at the Partial snap (catalog
    `ChatHomeCallbacks.onAgentStatusClick`, screen routes to
    `viewModel.openConsole()`).
  - **Console hosted in M3 `ModalBottomSheet`** — the hand-rolled
    overlay (custom scrim + `Modifier.height(snap.height)` +
    self-implemented `detectVerticalDragGestures`) is replaced with
    Material 3's anchored-draggable bottom sheet. We get smooth
    snap-transition animations, fling-to-snap physics, drag-from-body
    (not just the handle), tap-outside-to-dismiss, swipe-down-to-close,
    and a `BottomSheetDefaults.DragHandle` that announces correctly
    via TalkBack — all for free. The console keeps its "always dark"
    identity via overridden `containerColor` / `contentColor`. The
    sheet's `SheetState` is bidirectionally synced with the host's
    `consoleSnap` flow: programmatic `partialExpand()` /
    `expand()` calls follow VM-driven changes, and user-driven snap
    moves are mirrored back through `onConsoleSnapChange`.
  - **Engine emits no longer conflated.** The per-session orchestrator
    flow in `TaskQueueManagerImpl` switched from `MutableStateFlow` to
    `MutableSharedFlow(replay = 1, extraBufferCapacity = 256)`. The
    `StateFlow` was conflated — when `GraphExecutionEngine` emitted
    `PipelineTrace` immediately followed by `NodeIO` (two `emit`
    calls back-to-back), the second `.value =` overwrote the first
    before the chat-home collector resumed on the main dispatcher, so
    the Traces tab stayed empty even when the Vars tab populated
    correctly. `SharedFlow` with `replay = 1` preserves the legacy
    "subscriber sees latest state on attach" behaviour while the 256-
    event buffer guarantees no engine emit is silently dropped. The
    `enqueueTask` / `processTask` / `updateActiveSessionsState` /
    `evictOldestTerminalSession` paths are migrated to
    `emit(...)` + `replayCache.lastOrNull()`.
  - **`ConsoleSnap.Peek` retired.** The 44 dp ticker strip duplicated
    the agent-status pill above the composer and proved a dead-end UX
    in user testing. The enum is now `Partial` ↔ `PartiallyExpanded` +
    `Full` ↔ `Expanded`, matching M3's native `SheetValue`. The
    debug-picker `CONSOLE_PEEK` entry, the `PeekHeader` /
    `PeekTabStrip` / `PeekTickerRow` / `DragHandleStrip` composables,
    and the custom `resolveDragOutcome` snap-cycle helper are deleted.

- **Chat home — HITL and Clarification real-time wiring** (Phase 22 /
  Task 2/17) — replaces the `forceState(...)` stubs in
  `ChatHomeScreen.onHitlAllowOnce` / `onHitlReject` / `onClarificationReply`
  with live orchestrator round-trips.
  - `ChatHomeViewModel` now injects `ClarificationRepository` and exposes
    two new `StateFlow`s: `pendingTool: StateFlow<HitlPending?>`
    (snapshot of the tool the orchestrator is paused on, with risk +
    raw arguments) and `pendingClarification: StateFlow<ClarificationRequest?>`.
  - `handleOrchestratorState` maps `WaitingForApproval(name, args, risk)`
    onto `ChatHomeUiState.HitlConfirm(Risk)` (via `ToolRisk.toCatalogRisk`)
    and `AwaitingClarification(request)` onto `ChatHomeUiState.Clarification`.
  - New VM callbacks: `approveTool()` resumes the pipeline with `true`
    (refused defensively for `Destructive` tools until the typed-confirm
    matches `"yes"`); `rejectTool()` resumes with `false` and persists a
    `SYSTEM` chat row recording the denial; `submitClarificationReply(text)`
    forwards the reply through `ClarificationRepository.submitClarification`
    and flips back to `Generating`.
  - Clarification watchdog: when `AwaitingClarification` arrives with a
    positive `timeoutMs`, the VM arms a `delay(timeoutMs)` job that — on
    elapse — submits the default answer (first option, or empty for
    free-form), appends a `SYSTEM` "clarification timed out" chat row,
    and settles back on `Idle` / `Empty`. The repository's own
    `withTimeout` remains the authoritative gate; the watchdog is a
    UI safety-net.
  - `ChatHomeStateMapping.toViewState` now accepts `pendingTool` /
    `pendingClarification` and renders the trailing HITL /
    Clarification rows from live data — tool name, risk, JSON-decoded
    `Map<String, String>` of argument fragments, question, and
    quick-reply options — falling back to the existing fixtures only
    when no real pending snapshot is available (preserves the debug
    state picker).

- **Chat home — orchestrator core wiring** (Phase 22 / Task 1/17) —
  replaces the Phase 21 stub `ChatHomeViewModel` (canned delay + reply)
  with a fully wired Hilt ViewModel that runs the agent orchestrator on
  the redesigned chat surface.
  - `ChatHomeViewModel` now injects `AgentOrchestratorUseCase`,
    `ChatRepository`, `PipelineRepository`, `SettingsRepository`,
    `LlmInferenceEngine`, and `GetContextWindowUseCase`. `sendMessage()`
    drives the `Idle → Generating → Idle / Error` cycle through
    `agentOrchestratorUseCase(sessionId, prompt, pipelineId).collect`,
    forwarding the active session's `pipelineId`; intermediate states
    keep `Generating` until HITL / Clarification / Console handlers land
    in tasks 2/17 and 3/17.
  - **Pipeline binding** wired end-to-end: TopAppBar subtitle resolves
    to the bound pipeline name (or the user-marked default, or the
    first available pipeline), and the deleted-pipeline fallback
    silently rebinds the session to the default and surfaces a
    `KnotworkSnackbar` via a new `pipelineFallbackEvents` one-shot
    `SharedFlow`.
  - **Session lifecycle**: initial session is restored from
    `SettingsRepository.currentChatSessionId` (or freshly generated
    when none exists) and persisted via `ChatRepository.saveSession`;
    `selectThread` re-subscribes the message stream and rebalances the
    resting state.
  - **Token counter**: the TopAppBar shows a rough
    `getContextWindowUseCase(sessionId).length / 4` estimate against
    `SettingsRepository.maxContextLength`; a precise tokenizer is
    queued as a separate follow-up.
  - Display-message flow now goes through
    `ChatRepository.getDisplayMessagesForSession`; the legacy in-VM
    `MutableStateFlow` of pretend messages and the canned reply
    constants are gone.
  - Auto-renames a new chat to the first user message (truncated to 20
    characters) on send, matching legacy parity.
- **Accessibility + release-candidate gate** (Phase 21 / Task 11/11) —
  finalises the v0.1 surface against `decisions.md §14`:
  - Localised the only hard-coded English `contentDescription` left in
    the catalog (`PipelineListRow` overflow icon now resolves through
    `knotwork_library_row_overflow_cd`) and dropped the duplicated
    semantics override on `QuickAddRadialMenu`'s close button so
    TalkBack reads the localised resource instead of `"close"`.
  - `QuickAddTile` now merges its glyph + label into a single
    `mergeDescendants` semantics node with a 48 dp `minimumInteractiveComponentSize`
    floor, so the radial menu tiles are TalkBack-reachable in one focus
    stop instead of two.
  - Memory FLIP rank-shuffle (`animateItem(placementSpec)`) now gates
    on `KnotworkTheme.a11y.reducedMotion()` — the 320 ms slide
    collapses to an 80 ms crossfade when the user has the system
    "Remove animations" toggle on.
  - **`A11yMatrixSnapshotTest`** — new `:catalog` Roborazzi suite
    locking baselines at `fontScale = 2.0` ("Largest" preset) for Chat
    home / Pipeline library / Tools / Memory, plus a generating-state
    snapshot under reduced-motion. Provides visual proof that no
    dynamic-type layout breaks at 200 %.
  - **`WcagContrastTest`** — pure-JVM ratifies WCAG 2.1 AA contrast for
    the console foreground/background pair and the risk-destructive
    label on `surface1` in both themes. Codifies the §14 contrast
    contract so a future token regression fails CI.
  - **`TalkBackHappyPathsTest`** — Compose-test scaffolds covering the
    five happy paths from `decisions.md §14`. Asserts each entry
    surface (chat home, pipeline library, tools, settings, memory)
    publishes at least one TalkBack-reachable node (non-blank
    `contentDescription`, `text`, or `OnClick` action). The live
    TalkBack walkthrough on physical hardware is owned by QA via the
    v0.1 release checklist.
  - **Roborazzi baseline-lock** — every new screen's full state matrix
    is committed under `:catalog/src/test/snapshots/` so
    `:catalog:verifyRoborazziDebug` runs green without `*-actual.png`
    leftovers.
  - **`REVIEW_RETRO_phase21.md`** — internal retrospective walk through
    the [`REVIEW.md`](project_docs/design/compose/components/REVIEW.md)
    checklist for every Phase 21 sub-task, with the gaps closed in
    Task 11 explicitly cross-linked.
  - **Release APK build** — `:app:assembleRelease` now produces an
    installable APK signed with the debug keystore (until a release
    keystore is provisioned), targeting `arm64-v8a` only (every
    `minSdk = 36` device is 64-bit). v0.1 APK weighs in well above the
    initial 30 MB target — the on-device LiteRT-LM runtime, the
    bundled cloud-provider SDKs (OpenAI / Anthropic / Google /
    DeepSeek / Ollama via Koog), and the un-shrunk DEX classes
    dominate. Switching to Android App Bundle + R8 minification is
    tracked as the v0.2 size-reduction work; the APK size is documented
    here so reviewers can plan accordingly.
- **Hero screenshot in `README.md`** — pipeline editor (light theme)
  rendered into `docs/images/hero-pipeline-editor.png` from the
  catalog Roborazzi baseline. Replaces the missing visual entry the
  README has carried since the rewrite started.

- Redesigned **remaining screens C3 – C8** (Phase 21 / Task 10/11) — six
  user-facing surfaces now driven by stateless catalog content
  composables, each backed by a sealed `*ViewState` matrix mirroring
  `compose/screens/README.md`:
  - **C3 Pipeline library** (`PipelineLibraryContent`) — search field,
    `All / Recent / Shared / Mine` filter chips (Shared rendered
    disabled until the sync backend lands), swipe Duplicate / Archive /
    Delete reusing the catalog `PipelineListRow`, FAB → "+ New
    pipeline" dialog, multi-select chrome, Empty / Loading / Filtering
    (no-matches) / SwipeOpen / MultiSelect / Error states. App-side
    `PipelineLibraryScreen` rewritten as a thin VM ↔ catalog adapter.
  - **C4 Tools / MCP** (`ToolsContent`, `ToolDetailContent`,
    `AddMcpServerContent`) — collapsible sections per MCP server with
    `StatusPill`s; per-tool enable toggle; URL validation in
    `AddMcpServerScreen`; new `ToolDetailScreen` shows the JSON-Schema
    preview + enable/disable toggle. Routes `TOOL_DETAIL` and
    `ADD_MCP_SERVER` now resolve to real screens.
  - **C5 Onboarding pager** (`OnboardingContent`) — replaces the
    single-screen Task 4 stub with a 4-step flow (Welcome → Model
    source → Permissions → Sample pipelines) plus a custom
    `OnboardingScaffold` (progress dots, skip, back / next, finish).
    `OnboardingViewModel` records the user's model-source pick, API-key
    field with inline format validation, permission grants, and
    sample-pipeline selections.
  - **C6 Memory** (`MemoryContent`) — single recall list driven by the
    catalog `MemoryEntryRow`; 200 ms-debounced search; sort chips
    (Recent / Relevance / A→Z); detail bottom sheet with Edit / Pin /
    Delete; FLIP rank-shuffle animation on every `LazyColumn` row
    (`animateItem` with `tween(320 ms, emphasized)`). The legacy
    "Chat history" tab is dropped.
  - **C7 Settings** (`SettingsContent`) — sectioned LazyColumn
    (Appearance / Models / Privacy / Memory / MCP / About) with the
    documented state matrix (Loading / Default / PendingChange /
    ValidationError / RestartRequired / DestructiveAction / Error). The
    legacy app-side `SettingsScreen.kt` keeps its rich provider /
    sampling forms until a follow-up ports them row by row.
  - **C8 Console pane** (extension of `ConsolePane`) — inline
    search-by-text field above the Logs body, long-press menu on every
    log row (`Copy line` / `Only show this source`), localised "no
    matches" placeholder. Wired through `ChatHomeViewModel` so the
    affordance survives state transitions.
- **Roborazzi snapshot suites** for every new catalog screen (light +
  dark across the documented state matrix): pipeline library,
  onboarding, memory, settings, tools / tool detail / add-MCP-server.

- Redesigned **Pipeline editor screen** (Phase 21 / Task 9/11) —
  `PipelineEditorScreen` under `presentation/ui/pipeline/editor/*`
  becomes the production canvas surface, replacing the legacy
  `VisualOrchestratorScreen`. Composes the catalog atoms from Task 7
  (`NodeCard`, `EditorToolbar`, `NodeConfigSheet` + all 12 per-type
  `NodeConfigForms`) into a working editor:
  - **Infinite canvas** with pan + 2-finger pinch zoom clamped to
    `0.4×–2.0×`; node positions in canvas-space px, projected through a
    pure-Kotlin `CanvasTransform` so the gesture stream never mutates
    `OrchestratorViewModel`.
  - **Drag-and-drop** of nodes with the spec'd pickup
    (1.0 → 1.04 over 100 ms `easeOut`) and release-settle
    (`spring(.7f, 15f)`) animations, snapping to a 24 dp canvas grid on
    commit.
  - **Connection mode** — drag from an output port renders a preview
    Bezier; release on a target's inbound hit-zone routes through
    `OrchestratorViewModel.addConnection` (which keeps the DAG invariants).
  - **Long-press radial quick-add menu** with one tile per node type;
    selecting a tile spawns the node and opens its `NodeConfigSheet`
    pre-filled with sensible defaults from `NodeConfigCodec.defaultFor`.
  - **Multi-select** via long-press on a node card; the editor toolbar
    swaps for `MultiSelectToolbar` with bulk Cancel / Delete actions.
  - **Sugiyama-style auto-layout** — longest-path layering + median
    crossing reduction + grid-aligned coordinates (`AutoLayout.compute`)
    triggered from `EditorToolbar.onAutoLayout`.
  - **Validation bar** lists every `PipelineValidationError` returned by
    `PipelineGraph.validate()`; tapping a row drives `requestFocusNode`
    on the VM, which the screen consumes via a `SharedFlow` to centre
    the canvas on the offending node.
  - **Run-trace bar** + per-node run pulse (catalog `NodeCard.running`)
    + traveling-dot accent on active edges (arc-length-derived cycle so
    motion stays at the spec's 40 dp/s).
  - **Undo / redo** stack (`EditorUndoRedo`, capacity 50) wired to every
    graph mutation; clears the redo branch on a new push.
  - Full per-type **`NodeConfigSheet`** integration — all 12 forms from
    `node-specs.md` (Input / Output / LiteRt / Cloud / IntentRouter /
    IfCondition / Clarification / Tool / Decomposition / QueueProcessor /
    Evaluation / Summary) shipped by the catalog and now editable from
    the production editor; configs persisted as a JSON blob via
    `NodeConfigCodec` into the new nullable `pipeline_nodes.config_json`
    column (Room migration `MIGRATION_20_21`, DB version 21).
- Phase-21 hooks on `OrchestratorViewModel`: `updateNodeFromEditor`,
  `replaceCurrentPipeline`, `requestFocusNode` /
  `focusNodeRequest: SharedFlow<String>`, `setRunning`,
  `setActiveRunningNode`, `runState: StateFlow<PipelineRunState>`,
  `labelFor(error)` — exposed so the editor screen can drive validation
  focus, undo / redo replays, and the run-trace bar without
  re-implementing the wording or the VM state map.
- Pure-Kotlin editor core (JVM-testable, no Compose dependency):
  `CanvasTransform` (pan / zoom / grid snap), `BezierEdge`
  (control-point math + hit-test + arc length), `AutoLayout`
  (Sugiyama-style), `EditorUndoRedo` (bounded snapshot stack),
  `EditorState` (`@Stable` selection / drafts / sheet holder).
- Unit-test additions for the editor core
  (`CanvasTransformTest`, `BezierEdgeTest`, `AutoLayoutTest`,
  `EditorUndoRedoTest`, `NodeConfigCodecTest`, `NodeTypeMapperTest`) and
  five `OrchestratorViewModelTest` cases covering the Phase-21 hook
  surface.

### Changed

- The legacy `VisualOrchestratorScreen` and its `DraggableNode` helper
  are deleted; `AppNavGraph` routes `NavRoutes.PIPELINE_EDITOR` and
  `PIPELINE_EDIT_WITH_ID` to the new `PipelineEditorScreen` instead.
  The parameterised alias now loads the requested pipeline through
  `OrchestratorViewModel.loadPipeline(id)` instead of being a stub.
- `NodeModel` / `NodeEntity` gain a nullable `configJson: String?` field
  (Room DB v20 → v21, `MIGRATION_20_21`) — additive, defaulted to
  `NULL`, derived from the legacy flat fields on first edit so
  pre-Phase-21 rows open in the new editor without a data migration.

- Redesigned **Chat home screen** (Phase 21 / Task 8/11) — the new primary
  surface of the app, built on the Knotwork design system. `ChatHomeScreen`
  covers the eight documented visual states from `compose/screens/README.md
  §C1` (Empty, Idle, Generating, HITL Confirm, Clarification, Error, Drawer
  open, Console expanded) plus the cross-cutting dark theme. Highlights:
  - Stateless `ChatHomeContent` lives in `:catalog` under
    `app.knotwork.design.screens.chat.*` and is exercised by Roborazzi
    snapshots (8 states × 2 themes = 16 PNG baselines).
  - Stub `ChatHomeViewModel` in `:app` exposes a deterministic
    `StateFlow<ChatHomeUiState>`; the real orchestrator wiring (legacy
    `chat.legacy.ChatViewModel`) will reattach in a follow-up task after
    v0.1 — until then `sendMessage` simulates a 1.2 s
    `Idle → Generating → Idle` round-trip so the composer morph is
    testable.
  - **Debug state picker** (`ChatHomeDebugStatePicker`) reachable by
    triple-tapping the TopAppBar title in debug builds — flips through
    all 12 picker rows (8 visual states + 3 HITL risk tiers).
  - Reduced-motion + a11y rules go through `KnotworkTheme.a11y` per
    `decisions.md §14`; loader dots collapse to a steady `"•••"` glyph.

### Changed

- The legacy chat surface (`ChatScreen`, `ChatViewModel`,
  `ConsolePanelCollapsed`, `ConsoleFullLogSheet`, `ApprovalBanner`,
  `ClarificationCard`, `PipelineSummary`, `PipelineTraceCard`, and the
  paired tests) moved to `presentation.ui.chat.legacy/*` and is no longer
  wired into the navigation graph. It remains in the source tree as the
  reference implementation for the post-v0.1 orchestrator integration
  task that will reattach the real backend to `ChatHomeScreen`.

- Knotwork pipeline-editor base components (Phase 21 / Task 7/11), shipped
  under `:catalog/app.knotwork.design.components.pipelineeditor.*`:
  - **NodeCard** — 168 dp × 64..96 dp card covering idle / selected /
    multi-selected / running / runtime-error / validation-error states in
    one stateless composable. Header strip tinted from
    `KnotworkTheme.extended.node*` (12 hues), foreground tone resolved by
    a luminance-band rule (white / `onPrimary` / `onSurface`), and a 1.2 s
    running pulse gated through `KnotworkTheme.a11y.reducedMotion()`.
    Ports rendered with 12 dp visual / 24 dp hit-target dots; the inbound
    dot is pulled 6 dp into the header strip per spec.
  - **NodePorts.forType** — single source for the canonical port layout
    per node type (`IfCondition` → `True/False`, `QueueProcessor` →
    `Item/Done`, `Evaluation` → `Pass/(Retry)/Fail`, `IntentRouter` → one
    `Custom` per class, others → 1 in / 1 unlabelled out).
  - **EdgeLabel** — floating chip on `surface1` with 1 dp `outline`
    border, `LabelSm` text, plus an overload that derives the label from
    an `OutboundPort`.
  - **EditorToolbar** — inline-editable pipeline name on the left,
    Undo / Redo / Delete / Auto-layout cluster in the centre,
    `Run` (`KnotworkPrimaryButton`) + overflow on the right.
  - **NodeConfigSheet** + **NodeConfigForms** — `ModalBottomSheet` shell
    with a fixed type-pill header and sticky Cancel / Save row, plus a
    per-type form body for each of the 12 `NodeConfig` variants
    (`Input`, `Output`, `LiteRt`, `Cloud`, `IntentRouter`, `IfCondition`,
    `Clarification`, `Tool`, `Decomposition`, `QueueProcessor`,
    `Evaluation`, `Summary`). Save is gated on
    `NodeConfigValidation.validate(...)`, which enumerates every rule
    from `node-specs.md` §Validation rules.
  - **PipelineEditorCatalogContent** — scrollable harness rendering all
    12 NodeCard hues + the state matrix (selected / runtime-error /
    validation-error) + every EdgeLabel kind + the EditorToolbar + an
    idle LiteRT NodeConfigSheet body + an invalid IntentRouter body
    (surfacing inline `TITLE_DUPLICATE` / `INTENT_CLASS_COUNT` /
    `REQUIRED` errors). Wired into `ComponentsCatalogPage` under
    "Pipeline editor" and snapshot-tested via Roborazzi in both themes
    with `FixedKnotworkA11y(reducedMotion = true)` for determinism.
- Knotwork chat-surface components (Phase 21 / Task 6/11), shipped under
  `:catalog/app.knotwork.design.components.chat.*` and
  `:catalog/app.knotwork.design.components.console.*`:
  - **ChatMessage** with sealed `ChatContent` (`Text`, `Markdown`,
    `Confirmation`, `Clarification`, `Error`, `ToolCall`) — dispatches
    per content type, applies asymmetric bubble shapes (`ChatBubbleShapes`,
    16/16/4/16 mirrored for user vs assistant), and surfaces a long-press
    context menu (Copy / Rerun / Rate) with haptic feedback and a 60 ms
    scale-to-0.98 press animation. Reduced motion (per `decisions.md §14`)
    skips the scale.
  - **HitlConfirmationCard** — full HITL prompt with risk pill, tool
    name, summary, collapsible JSON args block, destructive "type yes"
    typed-confirm row, and a `Reject / Always allow / Allow once` action
    row gated by `HitlConfirmationState.isAllowOnceEnabled(...)`
    (pure-Kotlin helper, JVM-tested).
  - **ClarificationCard** — quick-reply chips (`KnotworkChip(Tonal)`) +
    free-form `OutlinedTextField` with `ImeAction.Send`; collapses to a
    `Replied: …` summary when the model carries a `replied` value.
  - **ChatComposer** — multiline 1..6 line composer with leading
    attach / voice icons, trailing send button that morphs to a stop
    button via `AnimatedContent` (200 ms crossfade; reduced motion
    swaps instantly), and an inline `signalError` banner for the
    `Error(message)` state.
  - **ConsolePane** — bottom-sheet container with three snap heights
    (`Peek` 44 dp / `Partial` 360 dp / `Full` 720 dp) and three tabs
    (`Logs` / `Vars` / `Traces`). Always-dark surface
    (`extended.consoleBg` / `consoleFg`) regardless of system theme.
    Source-filter chips drive the `ConsoleFilter.matches` predicate
    (JVM-tested); traces tab renders a thin per-row duration bar
    relative to the longest span.
  - **Catalog surface** — `ChatCatalogContent` and
    `ConsoleCatalogContent` harnesses, wired into `ComponentsCatalogPage`
    under new `Chat` and `Console` sections.
  - **Roborazzi snapshot baselines** — five new PNGs under
    `:catalog/src/test/snapshots/`: `chat_light.png`, `chat_dark.png`,
    `chat_reduced_motion.png` (pinned through `FixedKnotworkA11y`),
    `console_light.png`, `console_dark.png`. Behavioural coverage:
    `HitlConfirmationStateTest`, `ChatBubbleShapesTest`,
    `ConsoleFilterTest`, `ConsoleSnapTest`.
- Knotwork base component library (Phase 21 / Task 5/11), shipped under
  `:catalog/app.knotwork.design.components.*`:
  - **Buttons** — `KnotworkPrimaryButton`, `KnotworkSecondaryButton`
    (with `destructive` flag for HITL `Reject`), `KnotworkTextButton`,
    `KnotworkIconButton` (optional `badge: Int?`, `9+` overflow). All
    four follow the `KnotworkTheme.shapes.md` shape, expose a 48 dp
    minimum touch target, and honour the spec'd `loading` /
    `disabled` palettes (label fades to alpha 0.3, container shifts
    to `extended.surface3` / `onSurfaceDim`).
  - **Chips & pills** — `KnotworkChip` with `Default / Tonal /
    Outline` styles + `selected` palette, decorative no-`onClick`
    variant for tag rows; `RiskPill` (`Read-only / Sensitive /
    Destructive`) paired with `Visibility / WarningAmber / GppMaybe`
    glyphs from `icons/icon-mapping.md`; `StatusPill` (`Idle /
    Running / Success / Warning / Error`). All three pills expose
    `Risk level: …` / `Status: …` `contentDescription`s so colour is
    never the only signal.
  - **List rows** — `PipelineListRow` (72 dp, swipe-from-right reveals
    `Duplicate / Archive / Delete` via `Modifier.draggable` +
    `Animatable<Float>`; `revealed: Boolean?` parameter drives
    deterministic snapshot rendering); `ToolListRow` (64 dp, trailing
    connection-status pill via a `ConnectionStatus → Status`
    mapping); `MemoryEntryRow` (variable height, 3-line `BodyBase`
    clamp, tag chips + relevance score footer).
  - **Misc** — `EmptyState` (centred illustration slot defaulting to
    `StripedPlaceholder`, title + subtitle + optional CTA);
    `KnotworkSnackbar` (`Default / Error / Success` variants);
    `KnotworkLoader` (three pulsing dots `Accent300 → Accent400 →
    Accent500`, 1.2 s loop, 200 ms stagger; collapses to a static
    `•••` glyph under reduced motion); `StripedPlaceholder` (40 %
    diagonal stripes, optional mono caption).
  - **Catalog surface** — `ComponentsCatalogPage` mirrors
    `FoundationsCatalogPage` / `IconCatalogPage`, surfacing every
    base component under one scrollable column.
  - **Roborazzi snapshot baselines** — eight new PNGs under
    `:catalog/src/test/snapshots/`: `buttons_*.png`, `chips_*.png`,
    `lists_*.png`, `misc_*.png`, `components_*.png` (one light + one
    dark per category, plus the aggregated catalog page). Behavioural
    coverage: `KnotworkA11yTest`, `RiskPillTest`, `StatusPillTest`,
    `KnotworkChipTest`, `PipelineListRowTest`, `ConnectionStatusTest`.
- Accessibility primitives in `:catalog/app.knotwork.design.a11y` —
  `KnotworkA11y` interface (`reducedMotion()` backed by
  `Settings.Global.TRANSITION_ANIMATION_SCALE` /
  `ANIMATOR_DURATION_SCALE` with a `ContentObserver`-driven recompose;
  `fontScale()` mirroring `LocalConfiguration`), the
  `DefaultKnotworkA11y` production singleton, the test-friendly
  `FixedKnotworkA11y(reducedMotion, fontScale)`, the
  `LocalKnotworkA11y` composition local, and the
  `respectReducedMotionTransitions(enter, exit)` helper that swaps any
  enter/exit transitions for an 80 ms alpha-only crossfade when
  reduced motion is enabled. Wired into `KnotworkTheme` via the new
  `KnotworkTheme.a11y` accessor — components MUST consume the gate
  through this accessor instead of touching `LocalConfiguration` /
  `Settings.Global` directly.
- App shell with bottom navigation, single unified nav-graph, and a
  first-launch onboarding gate (Phase 21 / Task 4/11):
  - Four-tab `NavigationBar` — **Chat** (start) / **Pipelines** /
    **Tools** / **More** — replaces the legacy hub-style `HomeScreen`.
    Tab state (back-stack, scroll position) is preserved across
    switches and rotations via the canonical
    `popUpTo(startDestination) { saveState = true } + restoreState = true`
    pattern. While on a tab's start destination, the system Back
    gesture finishes the activity (Back on root tabs exits the app,
    not switches tab).
  - `AppNavGraph` consolidates every route into one `NavHost`: splash,
    onboarding, all four tabs, the pipelines nested-graph (library +
    editor with the parameterised `pipeline/{id}/edit` alias), tools
    detail + add-MCP placeholders, and the secondary screens under
    More (Memory / Models / Prompts / Task monitor / Live metrics /
    Settings / About). Modal bottom-sheet routes
    (`sheet/node-config`, `sheet/console`) are registered as
    placeholders backed by a shared `KnotworkModalRoute` wrapper
    (Material3 `ModalBottomSheet` + `PredictiveBackHandler`); the
    sheet bodies arrive in Tasks 6/7/10.
  - Chat deep-link: `knotwork://chat/{threadId}` resolves to the Chat
    tab and forwards the thread id to `ChatViewModel.switchSession`.
  - Onboarding stub: a single-screen welcome with a Get-started CTA.
    Gating is keyed off a new dedicated
    `SettingsRepository.hasCompletedOnboarding` flag (separate from
    `isFirstLaunch`, which `InitializeAppUseCase` clears during
    cold-start seeding and therefore cannot drive the UI gate). The
    full 4-step `HorizontalPager` (Welcome → Models → Permissions →
    Sample pipelines) is Task 10's deliverable.
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
