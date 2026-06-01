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

- **CI on GitHub Actions** (Phase 26 / Task 2/10): the `.github/workflows/check.yml`
  gate is now tracked in the repository (previously kept locally and gitignored
  because the remote PAT lacked the `workflow` scope). The job runs
  `./gradlew check` — detekt, ktlintCheck, lintDebug, testDebugUnitTest,
  koverVerifyDebug, verifyBrowserEditorConstants and `checkNoInternalFqn` — on
  every `pull_request → main`, every `push` to `main`, and on manual
  `workflow_dispatch`. It sets up JDK 21 (temurin) and the Android SDK
  (the API 37 platform for `compileSdk 37` is fetched automatically by AGP),
  caches Gradle, and uploads detekt / ktlint / lint / unit-test / Kover /
  Roborazzi reports as artifacts on failure. A live build badge was added to
  `README.md`.
- **JetBrains Mono SemiBold (600) + Bold (700)** (Phase 26 / Task 3/10): the
  brand monospace family now ships its load-bearing heavier cuts as real
  Latin-subset font files (SIL OFL 1.1) instead of relying on synthetic bold —
  source / status tags, node-kind labels, badges and stat numbers render in
  Mono 700 at 9.5–11 px without smearing.
- **Custom `I.*` icon family** (Phase 26 / Task 3/10): the spec §0.7
  single-stroke icon set (73 glyphs — `menu`, `back`, `search`, `add`, `edit`,
  `trash`, `send`, `play`, `refresh`, `eye`, `terminal`, `undo`/`redo`, …, plus
  `pin`/`pin-on`) ships as custom `AppIcons.*` vectors built from the designer's
  SVG sources (stroke 1.6 default; solid for `more` / `play` / `pause` / `dot` /
  `pin-on` / `stop`; mixed for `theme`). **All Material Design icon call sites
  (240) migrated to the brand family** — the app UI now renders entirely on one
  stroke family (system Material components such as `RadioButton` keep their
  built-in glyph). The selected bottom-nav tab renders at the active 2.0 stroke.
  Stroke-weight and render-size icon tokens (`IconStroke`, `KnotworkIconSizes`)
  back the family.

### Changed

- **Design-token reconciliation against the Controls & Components spec**
  (Phase 26 / Task 3/10): aligned the Knotwork tokens and shared controls to the
  canonical `tokens.css` source of truth.
  - **Colour.** `primary` moved from accent-500 to **accent-600** (light) /
    **accent-300** (dark) so white `on-primary` clears the 3:1 UI-contrast floor
    on filled buttons. Added chat-bubble pairs (`chat-user/agent/tool` × bg/fg,
    with a distinct quieter tool bubble), the `console-tag` accent, and the
    memory source-tag provenance palette (`mem-auto/manual/compact` × bg/fg/rail
    — AUTO blue 220, MANUAL brand amber, COMPACT violet 285). All values derived
    deterministically from the `oklch` token source.
  - **Iconography.** Default stroke 1.5 → **1.6**; added named stroke
    (default / active / contextual) and render-size (22 / 20 / 18 / 26 / 14)
    tokens per spec §0.7.
  - **Controls.** Button labels now Inter 600 / 14 sp / +0.1; `RiskPill`
    JetBrains Mono 500 / +0.2; node-kind and `CURRENT` pills moved to
    JetBrains Mono 700 (with a real 5 px dot on `CURRENT`); icon-button glyph
    22 dp and badge 14 dp / Inter 700 / primary fill; bottom-nav on `surface-2`
    with a hairline top divider and Inter 11 sp labels.

## [0.3.0] - 2026-05-30

Rolls up the post-`0.2.0` work that landed on `main`: the complete long-term
**memory lifecycle** (Phase 25 — extraction, embedding-provider abstraction,
retrieval + re-rank, background compaction, export / import, memory-screen
redesign and tuning controls), **pipeline & prompt presets** end-to-end
(Phase 24 — bundled catalogues, in-app pickers, browser-editor preset support
and gradle-driven constant-sync automation), and the **test / coverage
hardening** that raised the enforced Kover gate to 75 % (Phase 23 —
executor / DAO / Robolectric / Compose `androidTest` suites).

### Added

- **Long-term memory documentation + end-to-end test** (Phase 25 / Task 10/10):
  - `DESCRIPTION.md` §6 now documents the full memory subsystem (extraction,
    embedding providers, storage, retrieval/re-rank, context injection,
    compaction, import/export, and the message-to-retrieval lifecycle).
  - `docs/architecture.md` gains a §2.2 Mermaid *memory lifecycle* diagram;
    `docs/extending.md` gains an *Add a new `EmbeddingProvider`* recipe;
    `docs/user-guide.md` gains a *memory search isn't finding an entry*
    troubleshooting section.
  - New instrumented `MemoryLifecycleIntegrationTest` wires the real domain
    components (Room, `MemoryRepositoryImpl`, `MemoryReranker`,
    `NodeContextBuilder`, `KMeansClusterer`, extraction/retrieval/compaction
    use cases) over an in-memory database: a fact extracted in one session is
    retrieved into the next session's `--- Long-Term Memory ---` block and a
    pinned chunk survives a compaction pass.
- **Memory tuning controls** (Phase 25 / Task 9/10) — *Settings → Memory*
  now exposes the long-term-memory parameters that previously only had code
  defaults:
  - **Sliders** for retrieval *Search results (top-K)* (1–20), *Similarity
    threshold* (0.30–0.90), *Recency half-life* (7–180 days), *Compaction age*
    (7–90 days), and *Max stored chunks* (1 000–20 000).
  - A **Background compaction** toggle and an **Embedding model** dropdown that
    lists every registered provider (on-device USE, OpenAI, Ollama) and persists
    the active selection.
  - Out-of-range or unknown-provider edits are rejected at the ViewModel layer
    with an inline validation message and are never persisted.
- **Memory export / import** (Phase 25 / Task 8/10) — move an agent's
  long-term memory between devices:
  - **Export** — *Settings → Memory → Export* writes the table to a
    `schemaVersion: 1` JSON file via the Storage Access Framework, stamped with
    the active embedding provider id and an export timestamp (new
    `domain/memoryio/MemoryJsonSerializer`; the existing `ExportMemoryBaseUseCase`
    now emits the richer document, including per-chunk provenance and tags).
  - **Import** — *Settings → Memory → Import* parses a file and offers a
    **Merge** (keep existing, skip duplicate ids) or **Replace all** (wipe then
    load) strategy (new `MemoryImportUseCase`), preserving each chunk's id,
    provenance, pin state, and tags.
  - **Provider-mismatch handling** — when the file was exported under a
    different embedding provider, imported chunks are flagged `needsReembedding`
    and re-computed with the active provider by a background WorkManager job
    (new `MemoryReembedWorker` + `RecomputePendingEmbeddingsUseCase`, scheduled
    at import time), so transferred memories become findable off the hot path
    without stalling retrieval or needing a manual re-embed. The manual
    *Settings → Memory → Re-embed* action now also clears the flag.
- **Memory screen redesign** (Phase 25 / Task 7/10) — a full rework of the
  long-term-memory surface:
  - **Save to memory from chat** — the message long-press menu gains a
    *Save to memory* action that embeds the message text with the active
    `EmbeddingProvider` and stores it as a `Manual` chunk (new
    `SaveMessageToMemoryUseCase`), confirming with a *Saved to memory* snackbar.
  - **Stats header** — total count, on-disk size, "compacted N ago", and a
    provenance breakdown bar (Auto / Compaction / Manual) with a one-tap
    **Compact** action gated behind a confirm dialog that previews an estimate
    (≈ removed / freed / runtime) via the new `EstimateCompactionUseCase`; the
    manual Compact now runs the real consolidation pass.
  - **Category chips + dropdowns** — single-select category chips (All / Pinned
    / Auto / Manual / Compaction) with live counts, plus Sort and date-range
    dropdowns.
  - **Semantic search** — the search field now embeds the query and ranks
    results by relevance, showing a per-row score.
  - **Time-grouped list** — entries grouped into Pinned / Today / This week /
    Earlier, each row carrying a provenance accent + badge and its tags.
  - **Rich detail sheet** — token estimate, source, "Learned from" chat,
    captured time, "Used in N replies", inline body + tag editing, and
    pin / delete / save actions.
  - **Add memory** — a FAB + dialog to store a memory by hand.
  - **Tags & usage tracking** — chunks now carry tags (auto-extraction persists
    each fact's type) and a retrieval use-count / last-used time recorded by the
    pipeline engine. Backed by an additive Room migration (26 → 27).

### Changed

- `MemoryRepository` gains `setMemoryTags` / `recordUsage` and a `tags`
  parameter on `saveMemory`; `ExportMemoryBaseUseCase` accepts an optional id
  subset; `SettingsRepository` records the last compaction time.

### Fixed

- **Long-term memory now embeds every read and write with the active provider**
  (Phase 25 / Task 3/10). Retrieval embedded the search query with the fixed
  on-device Universal Sentence Encoder (512-d) while auto-extraction (Task 2)
  stored chunks via the user-selected `EmbeddingProvider` — so with a non-`use`
  provider active (OpenAI 1536-d, Ollama 768-d) query and stored vectors lived
  in different dimensions, cosine similarity collapsed to `0`, and the
  `longTermMemory` node flag surfaced nothing. All memory paths now resolve the
  same active provider via `EmbeddingProviderResolver`:
  - `RetrieveRelevantMemoryUseCase` (read) — so enabling the flag actually
    injects relevant memories regardless of the chosen backend.
  - `DelegateTaskTool` (delegated-result write), `MemoryViewModel.editVectorMemory`
    (inline edit re-embed) and `ReembedAllMemoriesUseCase` (Settings → Memory →
    Re-embed) — previously these persisted 512-d USE vectors that a non-`use`
    query could never match. Re-embed remains the canonical way to migrate the
    whole corpus into a newly selected provider's space.
- **Memory retrieval is skipped when no executed node requests it**
  (Phase 25 / Task 3/10). `GraphExecutionEngine` previously embedded the user
  prompt at run start unconditionally. It now resolves long-term memory lazily —
  at most once, the first time an executed node actually opts into the
  `longTermMemory` context block — so graphs with memory disabled never embed
  the prompt, sparing avoidable cloud-embedding latency/cost and not shipping
  the prompt to an embedding backend the user did not enable memory for.

### Added

- **Memory observability in the agent console** (Phase 25 / Task 6/10). Every
  long-term-memory retrieval now surfaces in the chat console as a dedicated
  `MEMORY` source line (previously collapsed into the generic `RUNTIME`
  source), so the new `MEMORY` filter chip isolates memory activity from node
  and tool output. Each retrieval line echoes the truncated query, the hit
  count, and the per-hit similarity scores
  (`Memory: query='…' → 2 hits (0.83, 0.40)`) instead of the old bare count.
  A new opt-in **Settings → Privacy → Verbose memory logging** toggle (default
  off, `SettingsRepository.verboseMemoryLoggingEnabled`) expands each retrieval
  line with a per-hit snippet + score, and makes `MemoryCompactionUseCase` log
  the cluster membership (merged chunk ids) of every consolidation to logcat.
- **Background memory compaction** (Phase 25 / Task 5/10). A daily
  `MemoryCompactionWorker` (WorkManager, constrained to charging + device-idle)
  consolidates stale, redundant long-term memory so the `memory_chunks` table
  does not balloon with near-duplicate facts over weeks of use. The pass loads
  non-pinned chunks older than `memoryCompactionAgeDays` (default 30), clusters
  them by embedding similarity with a new deterministic `KMeansClusterer`
  (`k = max(1, floor(sqrt(N) / 2))`), and for every cluster of ≥ 3 chunks runs a
  single local-model consolidation prompt
  (`DefaultPrompts.MemoryCompaction`), embeds the summary with the active
  provider, saves it tagged `MemorySource.Compaction` (carrying the merged ids),
  and deletes the originals. Pinned chunks are never touched; a blank model
  reply or an embedding error skips only that cluster (its originals are kept).
  An out-of-schedule watch (`MemoryCompactionScheduler.startHardLimitWatch`)
  triggers an immediate, relaxed-constraint pass when the table grows past
  `maxMemoryChunks` (default 5000). New settings `memoryCompactionEnabled`
  (default on), `memoryCompactionAgeDays` and `maxMemoryChunks` back the feature
  (the Settings → Memory UI for them lands in Task 9).
- **Memory retrieval re-ranking** (Phase 25 / Task 4/10). Raw cosine
  similarity is no longer the final word on what reaches the prompt. A new
  pure-domain `MemoryReranker` re-scores the full scored search pool before
  the top-K cut, applying four deterministic rules:
  - **Recency weighting** — a non-pinned chunk's score decays with age
    (`final = similarity * (1 - 0.5 * daysSince / halfLife)`, floored at 0),
    so a stale chunk no longer crowds out a fresher one. The half-life is
    configurable via the new `SettingsRepository.memoryRecencyHalfLifeDays`
    (default 30 days; Settings UI lands with the later Settings/tuning task).
  - **Pinned boost** — pinned chunks skip decay, gain a flat `+0.2`, sort
    ahead of every non-pinned chunk, and are exempt from the threshold filter,
    so a deliberately curated fact is always surfaced.
  - **Deduplication** — chunks sharing their first 80 characters collapse to
    the newest survivor, sparing the limited context budget.
  - **Threshold filter** — applied to the *final* (post-rerank) score.
  Re-ranking lives in `RetrieveRelevantMemoryUseCase` (the retrieval-only
  path), leaving `MemoryRepository.findSimilarMemories` raw for
  `MemoryExtractionUseCase`'s near-duplicate detection. The use case now pulls
  the full scored pool so a pinned or fresh chunk just outside the raw-cosine
  top-K can still be promoted.
- **Configurable memory retrieval tuning** (Phase 25 / Task 3/10). The
  retrieval top-K and relevance threshold are no longer hard-coded:
  `SettingsRepository.memorySearchTopK` (default 5) and
  `memorySearchThreshold` (default 0.55) back them via DataStore.
  `RetrieveRelevantMemoryUseCase` reads these by default (callers may still
  override per-call). The Settings UI for these controls lands with the later
  Settings/tuning task of this phase.
- **Automatic memory extraction** (Phase 25 / Task 2/10). After a pipeline run
  completes, the agent now mines the conversation for durable facts and writes
  the novel ones into long-term memory — making the "remembers past chats"
  capability actually populate memory instead of relying on manual saves.
  - A new `MemoryExtractionUseCase` (domain) runs the local model once with a
    conservative, no-hallucination prompt
    (`DefaultPrompts.MemoryExtraction.SYSTEM_FALLBACK`, `$DATE`-grounded) that
    returns a JSON array of `{type, text}` facts; all facts are embedded in a
    single batch `EmbeddingProvider.embed(List)` call (one cloud round-trip
    instead of N) and each is saved only if it is not a near-duplicate
    (cosine ≥ 0.92) of an existing chunk or another fact from the same pass.
  - A `MemoryAutoExtractionCoordinator` (domain, app-scoped) triggers the pass
    on pipeline completion with a 30 s per-session debounce, short-circuiting
    when the toggle is off and deferring while another pipeline is still
    generating.
  - The on-device inference engine (`LiteRTLlmEngine`) now serialises every
    generation behind a `Mutex`, since LiteRT-LM allows only one active
    conversation — this prevents the background extraction pass and a
    foreground response from concurrently tearing down each other's session
    (which could crash the native layer).
  - Memory chunks now carry a typed `MemorySource`
    (`ChatSession` / `Manual` / `Compaction` / `Unknown`), persisted via a new
    `memory_chunks.source` column (Room migration 25 → 26, legacy rows backfilled
    to `Unknown`).
  - New `Settings → Memory → Auto-extract from conversations` toggle
    (`SettingsRepository.autoExtractEnabled`, default on) describing exactly what
    is collected.
- **Embedding provider abstraction** (Phase 25 / Task 1/10). Long-term memory
  no longer hard-codes the on-device Universal Sentence Encoder. A new
  `EmbeddingProvider` domain abstraction (`embed` / batch `embed` /
  `dimension` / `id` / `displayName`) is implemented by three backends, Hilt-
  multibound into a `Map<String, EmbeddingProvider>` and selected at call time
  by `EmbeddingProviderResolver`:
  - `use` — on-device MediaPipe Universal Sentence Encoder (512-d), the
    default; needs no network or API key.
  - `openai_3_small` — OpenAI `text-embedding-3-small` (1536-d) via the
    existing Koog OpenAI client.
  - `ollama` — local-network Ollama `nomic-embed-text` (768-d) via the Koog
    Ollama client.

  The active backend is persisted in the new
  `SettingsRepository.activeEmbeddingProviderId` setting (default `"use"`).
  Each provider reports `isAvailable()` (cloud providers require a configured
  key / base URL); when the selected provider is unavailable the resolver
  substitutes the on-device default — keeping each provider's declared
  `dimension` honest rather than silently returning mis-dimensioned vectors.
  Backend failures surface as a typed `EmbeddingException`, and coroutine
  cancellation is propagated unwrapped. Existing embedding consumers are
  unchanged in this task; migrating them onto the resolver lands with the
  later memory tasks of this phase.
- **Browser-editor constant sync automation** (Phase 24 / Task 8/9). The
  `:app:generateBrowserEditorConstants` Gradle task regenerates the
  `NODE_TYPES`, `PROMPT_VARIABLES`, `AVAILABLE_TOOLS` and
  `DEFAULT_SYSTEM_PROMPTS` blocks of `pipeline-editor.html` straight from the
  Android domain sources (`NodeType.kt`, `DefaultPrompts.kt`,
  `PromptTemplateModule.kt`, `LocalToolsModule.kt`), injecting them between
  `AUTO-GEN` markers. `:app:verifyBrowserEditorConstants` — wired into
  `./gradlew check` — fails the build if the committed HTML has drifted,
  replacing the previous review-only "KEEP IN SYNC" rule that had let those
  mirrors diverge (Task 6). The pure generation logic lives in `buildSrc`
  (`BrowserEditorConstantsGenerator`) with its own JUnit suite; editor-only
  metadata (palette order, colours, icons, tool labels) is cross-checked
  against the domain set so adding a `NodeType`/tool without metadata fails
  generation.
- **Pipeline presets — browser editor** (Phase 24 / Task 7/9). The
  standalone `pipeline-editor.html` gains a `📚 Presets` top-bar button
  opening a modal with **Bundled** and **Mine** tabs:
  - **Bundled** mirrors the 6 starter presets shipped in the APK under
    `assets/presets/pipelines/*.json`, inlined as the
    `BUILTIN_PIPELINE_PRESETS` JS constant (Task 8 will replace the
    hand-maintained block with a gradle-generated one).
  - **Mine** lists per-browser presets persisted in `localStorage`
    (degrades to an empty in-memory list in private-mode browsers).
  - Each card offers **Load** (materialises the preset onto the canvas
    via the existing `importFromJson` path, with a freshly-minted
    pipeline id) and **Export** (downloads `<id>.preset.json`); Mine
    cards also offer **Delete**. The modal footer adds **Import
    preset…** (parses a `.preset.json`, runs a schema-version mismatch
    confirm when needed, and stores it under Mine without touching the
    canvas) and **Save canvas as preset…** (a name / category / tags /
    description form, 60-char name cap matching
    `SavePipelineAsPresetUseCase`).
  - The `.preset.json` format is a strict superset of the pipeline JSON
    — same `schemaVersion` / `id` / `name` / `updatedAt` / `nodes` /
    `connections` plus `category` / `tags` / `description` — matching
    `PipelinePresetJsonSerializer.kt`, so files round-trip between the
    browser editor and the Android app.

- **Prompt presets — UI** (Phase 24 / Task 5/9). Wires the bundled and
  user-saved prompt-preset catalogue (Task 4) into the two production
  surfaces:
  - The pipeline editor's `NodeConfigSheet` gets a 📚 / 💾 pair on
    every prompt-bearing field. 📚 opens a new Knotwork-styled
    `PromptPresetPickerSheet` (modal bottom sheet) filtered by the
    active node's `NodeType`, with **Bundled** / **Mine** tabs, a
    leading `All N` chip + per-tag filter chips, 200 ms debounced
    search by name, radio-style row selection, a per-row magnifier
    preview, a `● CURRENT` pill on the row whose prompt matches the
    field's current value, and a sticky bottom `Cancel / ✓ Use prompt`
    bar. The Preview action surfaces the existing
    `PromptPreviewBottomSheet` with full `$VARIABLE` substitution. 💾
    captures the current draft as a user preset via a
    name / description / tags dialog routed through
    `SavePromptAsPresetUseCase`.
  - The standalone **Prompt library** screen (More → Library) now
    surfaces the same `PromptPreset` catalogue (bundled + user) in
    place of the legacy `PromptTemplate` source. Bundled rows are
    read-only (delete is silently a no-op); user rows can be edited
    via the existing bottom-sheet editor — saves go through
    `SavePromptAsPresetUseCase` with `existingId` (new upsert path)
    so an edit replaces the same preset in place. Duplicate writes a
    new user preset with a `(copy)` suffix; the source preset can be
    bundled, which is the canonical path for "start customising a
    bundled template".
  Replaces the legacy `PromptLibraryDialog` (Knotwork-styled
  `AlertDialog`) that was backed by the older `PromptTemplate` model.

- **Prompt presets — domain model & bundled catalogue** (Phase 24 /
  Task 4/9). New first-class entity for reusable system-prompt templates,
  attached to a single LLM-driven `NodeType` (LITE_RT, CLOUD, OUTPUT,
  SUMMARY, INTENT_ROUTER, DECOMPOSITION, EVALUATION, CLARIFICATION).
  Bundled presets ship inside the APK at `assets/presets/prompts/` (18
  starter prompts — 2–3 per LLM-driven type, including
  `litert_concise_assistant`, `output_markdown_with_sections`,
  `router_keyword_classifier`, `decomposition_json_subtasks`,
  `clarification_multiple_choice`, …) and are loaded read-only via
  `LocalPromptPresetRepositoryImpl`. User-saved presets land in a new
  `prompt_presets` Room table (schema v25), created and observed
  through `PromptPresetDao` / `PromptPresetRepository`. The new
  `SavePromptAsPresetUseCase` is the single entry point for the
  user-save flow — it validates name (1..60 chars), `systemPrompt`
  (≤ 8000 chars, `PromptPresetConstants.MAX_SYSTEM_PROMPT_LENGTH`), and
  the target `NodeType`. The catalogue ships behind a
  `PromptPresetCatalogValidationTest` (JVM unit) that pins the filename
  set, asserts every `nodeType` is LLM-driven, every `$VARIABLE`
  resolves against the registered provider whitelist, every
  `systemPrompt` fits within the soft limit, and every LLM-driven type
  has at least one bundled preset. The Prompt-Library UI rewiring that
  actually surfaces this catalogue lives in Task 5/9.

- **Pipeline presets — UI** (Phase 24 / Task 3/9). Surfaces the Phase 24
  preset catalogue end-to-end through three user-facing entry points:
  - **Speed-dial FAB** on the pipeline library — replaces the single
    "+ New pipeline" FAB with a two-action speed-dial (`+ New pipeline` /
    `+ From preset`). The "+ From preset" action opens a new modal
    `PresetPickerSheet` with Bundled / Mine tabs, `PresetCategory`
    filter chips, and a one-line `INPUT → LITE_RT → OUTPUT` graph
    preview on every card. Tapping "Use this preset" materialises a
    fresh pipeline via `LoadPipelineFromPresetUseCase` and routes the
    user straight into the editor.
  - **Save as preset** action — exposed in both the pipeline-library
    row overflow menu and the editor overflow. Opens a dialog
    capturing name / description / category / tags, then persists via
    `SavePipelineAsPresetUseCase`.
  - **More → Library** — new `PipelinePresetsManagerScreen` reachable
    from the More tab. Bundled presets render as read-only rows;
    user presets expose Rename / Export-JSON (via SAF) / Delete with
    a destructive-confirm dialog.

- **Bundled pipeline-preset catalogue** (Phase 24 / Task 2/9). Ships six
  curated starter presets under `assets/presets/pipelines/` covering the
  typical entry-point scenarios: `local_only_qa` (offline INPUT → LITE_RT →
  OUTPUT), `cloud_assist` (cloud with chat history + long-term memory),
  `tool_using_react` (reasoner → tool → summariser chain),
  `multi_step_research` (DECOMPOSITION → QUEUE_PROCESSOR with an explicit
  `Item` / `Done` fan-out plus a back-edge that loops each subtask through
  a cloud researcher), `clarify_then_act` (CLARIFICATION gate before a
  local reply) and `routed_local_cloud` (INTENT_ROUTER sending simple
  requests on-device and complex ones to the cloud). A new
  `PipelinePresetCatalogValidationTest` (JVM unit) parses every bundled
  file, runs `PipelineGraph.validate()` over each embedded graph, and
  verifies that every `$VARIABLE` token resolves against the registered
  `PromptVariableProvider` set in `di/PromptTemplateModule.kt` — so adding
  a broken preset, an unknown variable, or accidentally deleting one of
  the six fails the build.

- **Pipeline preset — domain model and storage** (Phase 24 / Task 1/9).
  Introduces `PipelinePreset` as a reusable pre-built pipeline template
  with two persistence tiers:
  - **Bundled** presets ship inside the APK under
    `assets/presets/pipelines/*.json` (catalogue files filled in by
    Task 2/9; Task 1 wires the loader and lays the empty directory).
  - **User** presets are persisted in a new `pipeline_presets` Room
    table (schema **v23 → v24** via `MIGRATION_23_24`).
  `LoadPipelineFromPresetUseCase` materialises a preset into a concrete
  pipeline with fresh ids (regenerated for the pipeline, every node, and
  every connection — orphan connections are dropped, mirroring
  `DuplicatePipelineUseCase`). `SavePipelineAsPresetUseCase` packages the
  current graph into a user preset after validating the name (1..60 chars)
  and running `PipelineGraph.validate()`. The new
  `PipelinePresetJsonSerializer` delegates the embedded graph half to
  `PipelineJsonSerializer`, keeping the preset and pipeline formats
  forever in sync.

### Changed

- **`MemoryAccess` console events carry query + scores** (Phase 25 / Task 6/10).
  The format moved from `Memory: N chunk(s) retrieved` to a richer line built
  by the new pure `MemoryAccessLogFormatter`. `RetrieveRelevantMemoryUseCase`
  gains a score-preserving `retrieveScored(...)` entry point (the score-free
  `invoke(...)` now delegates to it) so the engine can render scores without a
  second retrieval.
- **Browser pipeline editor — full sync sweep** (Phase 24 / Task 6/9).
  Re-synced `pipeline-editor.html` with the Android source of truth after
  the Phase 20–24 drift:
  - `BUILTIN_PROMPT_TEMPLATES` (the 📚 Prompts popover) now mirrors the
    21-entry bundled prompt-preset catalogue from
    `assets/presets/prompts/` (Task 4) verbatim — `systemPrompt`,
    name, and description copied byte-for-byte — replacing the 7 legacy
    generic per-type entries. The popover filters built-ins by the node's
    `NodeType`, matching the Android Prompt Library; each row carries the
    preset description as a hover tooltip.
  - `IF_CONDITION` presets are routed to the **condition-prompt** field, not
    `systemPrompt`: the IF picker lives on the "Classification prompt" field
    (which maps to `NodeModel.conditionPrompt` — the only field
    `EvaluateIfConditionUseCase` reads for branching), and the IF node no
    longer renders a `systemPrompt` field at all, mirroring
    `NodeConfigForms.IfConditionFormBody`. Previously an IF preset populated
    an ignored `systemPrompt` and left `conditionPrompt` empty, so the
    imported node fell through to `false`.
  - `AVAILABLE_TOOLS` (TOOL-node config) re-synced with the
    `LocalToolExecutor` registry: `web_search` → `search_tool`, plus the
    previously-missing `schedule_task`. The ids now equal the executors'
    `TOOL_NAME` constants so TOOL nodes built in the browser resolve
    on-device.
  - `CLOUD_PROVIDERS` gains `ollama`, matching the `CloudProvider` enum.
  - Verified in-sync (no change needed): `PROMPT_VARIABLES` (9 variables),
    `DEFAULT_SYSTEM_PROMPTS`, `NODE_TYPES`, `NODE_TYPE_TOOLTIPS`,
    `defaultContextConfig`, and the `schemaVersion: 1` JSON node contract.
- **Dependency currency**: bumped `firebase-bom` 34.13.0 → 34.14.0 to clear
  the `GradleDependency` lint error gating `./gradlew check`.

### Fixed

- **Preset pickers no longer apply a hidden selection after a filter
  change** (Phase 24). In `PresetPickerSheet` (pipeline presets) and
  `PromptPresetPickerDialog` (prompt presets), selecting a preset and then
  switching the tab / category / tag chip left the previous selection
  active: the footer CTA stayed enabled on the non-null id and applied the
  now-hidden preset instead of the row visible to the user. Both pickers now
  require the selected id to still be present in the filtered list before
  enabling and applying the action (`selectionVisible` /
  `visibleSelectedRowId`), so the CTA disables when the pick scrolls out of
  view and only ever instantiates a currently-visible preset.

### Build / coverage

- **Coverage gate raised 70 % → 75 % LINE aggregate** (Phase 23 / Task 9/9).
  `koverVerifyDebug` (run via `./gradlew check`) now fails the build if
  aggregate line coverage over the unit-testable surface drops below 75 %.
  Today's measurement after the new exclusions sits at ~77.6 %, leaving
  ~2.6 pp of headroom against silent regression. Per-package targets are
  documented as informational guidance in
  [`docs/coverage-baseline.md`](docs/coverage-baseline.md); they will be
  promoted to enforced rules once Kover 0.10 ships rule-level filters
  (0.9.8 is the latest available on the Gradle Plugin Portal). Several
  Compose-surface and Android-runtime-glue packages introduced earlier in
  phase/23 were also added to the Kover exclusion list to align with the
  existing `presentation.ui.*Screen*` convention:
  `presentation.ui.navigation.*`,
  `presentation.ui.about.{AboutScreen,AboutAcknowledgments}*`,
  `presentation.ui.more.MoreScreen*`,
  `presentation.ui.settings.provider.{ProviderPickerScreen,ProviderDetailScreen}*`,
  and `data.tools.local.appfunctions.*`. Without these exclusions the
  aggregate would have been 73.8 % — the previously-claimed "~80 %" in
  the baseline doc was stale because the new screens never made it into
  the filter when they shipped. Touched files:
  [`app/build.gradle.kts`](app/build.gradle.kts),
  [`docs/coverage-baseline.md`](docs/coverage-baseline.md),
  [`docs/static-analysis.md`](docs/static-analysis.md),
  [`docs/testing.md`](docs/testing.md).

### Documentation

- **Preset feature documented end-to-end** (Phase 24 / Task 9/9).
  `docs/extending.md` §5 is restructured into "Add a bundled preset"
  with a new pipeline-preset recipe (asset schema, `PresetCategory`
  keys, `validate()` / variable-whitelist rules, the
  `PipelinePresetCatalogValidationTest` registration step, and the
  hand-maintained `BUILTIN_PIPELINE_PRESETS` mirror in
  `pipeline-editor.html`) alongside the existing prompt-preset recipe,
  plus two new rows in the synchronization table. `docs/user-guide.md`
  gains a "Pipeline presets" section (bundled vs. Mine, cross-linked to
  the browser editor). `DESCRIPTION.md` adds §8.3 (preset domain model,
  repositories, bundled-vs-user lifecycle, Room migrations v23→v24 /
  v24→v25, build-time guarantees) and §10.6 (preset flow in the
  Library).

### Tests

- **Preset end-to-end integration tests** (Phase 24 / Task 9/9). Two
  pure-JVM suites that wire the real production classes together rather
  than the shipped artefacts alone (which the catalogue tests already
  pin): `PipelinePresetIntegrationTest` reads each bundled
  `assets/presets/pipelines/*.json`, materialises it through the real
  `LoadPipelineFromPresetUseCase` (asserting fresh ids + preserved
  connections + zero validation errors), then runs the `local_only_qa`
  preset through a real `GraphExecutionEngine` (only the
  `LlmInferenceEngine` token stream stubbed) and asserts it reaches
  `AgentOrchestratorState.Completed`. `PromptPresetIntegrationTest`
  parses every bundled `assets/presets/prompts/*.json`, applies the
  body to a `NodeModel.systemPrompt`, renders it through the real
  `PromptTemplateEngine`, and asserts every registered `$VARIABLE` is
  substituted (plus the escape / unknown-token contract).

- **Compose `androidTest` coverage for the remaining `presentation.ui`
  surfaces — Memory / Settings / Tools / Onboarding / Prompt Library**
  (Phase 23 / Task 8). Each screen gets a shared
  `mock<Screen>ViewModel` factory (relaxed MockK VM + mutable state-flow
  handles so tests drive transitions without re-stubbing) and 2–6 test
  classes under
  `app/src/androidTest/java/ai/agent/android/presentation/ui/<screen>/`.
  Coverage: `MemoryScreenSearchTest` (200 ms debounce gated by
  `mainClock.advanceTimeBy`), `MemoryScreenInteractionsTest` (row pin /
  edit-commit which exercises the re-embedding call site / delete /
  pinned-glyph rendering / empty-state CTA);
  `SettingsScreenRestartRequiredTest` (banner test-tag visibility),
  `SettingsScreenDestructiveConfirmTest` (typed-confirm dialog,
  Confirm button gated on the resource keyword),
  `SettingsScreenTogglesTest` (LazyColumn scroll-to + Notifications
  toggle wiring); `ToolsScreenLocalToolsTest` (risk-pill rendering for
  all three `ToolRisk` levels, Switch tap invokes `toggleLocalTool`,
  row tap routes to `onOpenToolDetail`), `ToolsScreenMcpServersTest`
  (Connecting → Connected subtitle flip, expand-chevron toggle,
  overflow Refresh action); `OnboardingScreenPagerTest` (Welcome
  headline render, Continue CTA forwards to `next`, top-bar Skip
  invokes `skipOnboarding` + `onCompleted`, Ready step suppresses Skip
  and finishes via the primary CTA), `OnboardingScreenDownloadGateTest`
  (LiteRtModel CTA matrix across no-install / in-flight / installed,
  Ready step CTA gated on `isModelWarmed`); `PromptLibraryScreenListTest`
  (category-filtered list, tab-tap forwards to `selectCategory`, FAB
  opens a new draft, per-card Edit / Delete / Duplicate icons fire
  their VM hooks), `PromptLibraryScreenEditorTest` (bottom-sheet
  visibility on a non-null draft, prefilled fields, Save / Cancel
  dispatch). All factories follow the Phase 23 / Task 6 + Task 7
  pattern (`createComposeRule()` + relaxed MockK VM); no production
  code changes were required. Note: `SettingsUiState` does not expose
  the "pending change" or "ValidationError" surfaces mentioned in the
  task brief, so those branches are deliberately out-of-scope.

- **Compose `androidTest` coverage for `presentation.ui.pipeline.editor`**
  (Phase 23 / Task 7). Twelve test files mirroring the Phase 23 / Task 6
  chat-home pattern: `createComposeRule()` + a shared
  `mockOrchestratorViewModel` factory (exposes mutable `StateFlow` /
  `SharedFlow` handles for `uiState`, `runState`, `focusNodeRequest`
  so tests drive state transitions without re-stubbing) plus a
  `PipelineEditorTestFixtures` graph palette. Most tests render
  internal canvas / bar / sheet composables directly with a real
  `EditorState`; a small minority drive the full `PipelineEditorScreen`
  to exercise overflow / sheet / dialog wiring. Coverage:
  `PipelineEditorContentRenderTest` (empty hero, populated cards,
  multi-select toolbar swap, clean-validation copy),
  `PipelineEditorOverflowMenuTest` (Save / Undo / Redo / Rename / Delete
  / Auto-layout / Mini-map / Grid / Find / Paste menu items render and
  dispatch; Save tap → `vm.saveCurrentPipeline`; Find opens FilterBar;
  Grid label flips Show ↔ Hide; Paste on empty clipboard does not
  invoke `vm.addNode`), `PipelineEditorMultiSelectTest` (toolbar count
  pluralisation, Cancel / Copy / Delete callbacks),
  `PipelineEditorRadialMenuTest` (all twelve `NodeType` labels render;
  tile tap dispatches the domain `NodeType`; close icon dispatches
  `onDismiss`), `PipelineEditorValidationBarTest` (clean-state copy;
  header banner plural; per-row labels; Auto-fix CTA invokes callback
  when at least one error is auto-fixable; `Go ↗` dispatches
  `onFocusNode` with the resolved node id),
  `PipelineEditorNodeConfigSheetTest` (per-type forms — Input / Output
  / LiteRt / Cloud — render their characteristic fields; editing the
  Input variable-name field fires `onChange` with the mutated
  `InputConfig`; Save / Cancel dispatch), `PipelineEditorSearchTest`
  (FilterBar placeholder, query change forwarding, match-count pill,
  Close button), `PipelineEditorMiniMapAndGridTest` (MiniMap renders
  OVERVIEW header + `formatScalePercent`; close button dispatches),
  `PipelineEditorCopyPasteTest` (multi-select Copy populates
  `editor.clipboard` from the live graph; overflow Paste on empty
  clipboard does not dispatch `vm.addNode` / `vm.updateNodeFromEditor`),
  `PipelineEditorRunStateTest` (Idle hides the run banner; flipping
  `runState` to Running shows the RUNNING badge and hides the toolbar
  Run button), `PipelineEditorGestureTest` (real `performTouchInput` —
  long-press on a node card enters multi-select; drag on a node card
  commits `vm.moveNode` with a non-zero delta). Pinch / two-finger zoom
  is deliberately out of scope — pure-Kotlin math is exhaustively
  covered by `CanvasTransformTest`, and `ZoomRail` exposes the same
  code path through deterministic buttons.
- **Compose `androidTest` coverage for `presentation.ui.chat.home`**
  (Phase 23 / Task 6). Extended the existing
  `createComposeRule()` + mocked `ChatHomeViewModel` pattern with a shared
  `mockChatHomeViewModel` factory (exposes mutable `StateFlow` handles so
  tests drive `Idle → Generating → Idle`, HITL approve, Clarification
  clear, etc. without re-stubbing). Added `ChatHomeSendFlowTest`
  (composer Send dispatches `sendMessage()`; Generating renders the
  loader bubble + Stop affordance; flip back to Idle restores Send;
  Error renders the retry tile and routes to `retryAfterError()`),
  `ChatHomeHitlScreenFlowTest` (Sensitive Allow / Reject → `approveTool`
  / `rejectTool`; Destructive typed-confirm gate stays disabled until
  the magic word lands; live `pendingTool` tool name renders end-to-end),
  `ChatHomeClarificationFlowTest` (live `pendingClarification` question
  + quick-reply chips render; chip tap dispatches
  `submitClarificationReply(label)`; clearing `pendingClarification` +
  flipping back to Idle removes the card), `ChatHomeConsolePaneTest`
  (Logs/Vars/Traces tab switching, source-filter chip toggle,
  search-bar input forwarding, header Search / Clear icons routing into
  the VM, Clear-confirm AlertDialog Confirm/Cancel, long-press
  "Copy line" round-trip into a fake `LocalClipboardManager`), and
  `ChatHomeDrawerTest` (DrawerOpen renders threads + footer rows,
  thread tap dispatches `selectThread` + `closeDrawer`, New chat opens
  the pipeline-picker `ModalBottomSheet`, picking + Create dispatches
  `createNewSessionWithPipeline(id)`, composer Send affordance remains
  present in HITL / Clarification / ConsoleExpanded states as a
  structural IME-overlap stand-in). Caveats spelled out in test KDoc:
  the 5 s Clarification timeout watchdog runs via `delay` inside the
  VM and is exercised by JVM unit tests; real soft-keyboard IME overlap
  needs screenshot coverage and is out of scope. Refactored the
  existing `ChatHomeOverflowMenuTest` onto the shared factory.
- **Robolectric coverage for `presentation.notifications` + `presentation.receivers`**
  (Phase 23 / Task 5). Added `ApprovalNotificationManagerTest` (risk-based
  channel routing: `AGENT_APPROVAL_DESTRUCTIVE` vs `AGENT_APPROVAL` with
  `READ_ONLY` sharing the `SENSITIVE` channel, `IMPORTANCE_HIGH` per channel,
  idempotent channel registration across repeat sends, active-session
  suppression vs. cross-session post, Approve / Deny `PendingIntent` extras
  + `FLAG_IMMUTABLE`, stable-vs-distinct notification ids on the
  `NOTIFICATION_ID + hash % NOTIFICATION_ID_RANGE` partition, `BigTextStyle`
  + auto-cancel + `PRIORITY_HIGH`) and `AgentApprovalReceiverTest`
  (`APPROVE` → `resumeWithApproval(true)`, `DENY` → `resumeWithApproval(false)`,
  unknown / null action skip the orchestrator, missing `sessionId` extra
  short-circuits before cancelling, notification cancellation at the
  partitioned slot, dedup-free repeat delivery). Used the same Hilt-bypass
  reflection trick as `AgentForegroundServiceTest` so `@AndroidEntryPoint`
  doesn't require a `HiltTestApplication` runner. The previous
  `ai.agent.android.presentation.notifications.*` and
  `ai.agent.android.presentation.receivers.*` Kover exclusions are gone —
  both packages now show **100 % LINE** in `koverHtmlReportDebug`.
- **Robolectric coverage for `data.services`** (Phase 23 / Task 4). Added
  `AgentForegroundServiceTest` (channel registration with `IMPORTANCE_LOW`,
  `startForeground` notification with `FLAG_ONGOING_EVENT`, wake-lock
  acquire/release across `Thinking` / `ExecutingTool` / `Idle` / `Error`
  states, `onDestroy` engine close-on-`isInitialized`, `onStartCommand`
  `START_STICKY` idempotency, `onBind` null contract), rewrote
  `AgentWorkerTest` on top of `TestListenableWorkerBuilder` + a manual
  `WorkerFactory` (covers null/blank input, happy path, stream-completes-
  with-`Error`-is-still-success, retry-on-exception, stage-only emissions,
  public key contract), extended `AgentIdleManagerTest` (no-`startObserving`
  silence, `Loading` no-op, `Error` triggers, 5-minute default timeout),
  extended `AgentPowerManagerTest` (low-battery → charging transition
  fires once, duplicate-`StateFlow`-emission dedup, never-initialized engine
  short-circuit), and added `LongRunningTaskNotifierImplTest`
  (`LONG_RUNNING_TASKS` channel registration, opt-out gate, `POST_NOTIFICATIONS`
  permission gate, channel-id and stable per-pipeline notification-id
  contract, empty-flow defaults-to-disabled). The previous
  `ai.agent.android.data.services.*` Kover exclusion is gone — package
  coverage rose from **44.0 %** to **99.4 % LINE** (171 / 172). Pinned
  Robolectric runtime SDK to 36 via `app/src/test/resources/robolectric.properties`
  and enabled `unitTests.isIncludeAndroidResources = true` so `getString`
  on notifier strings resolves against the real merged resources.
- **Room DAO + migration regression suite** (Phase 23 / Task 3). Brought the
  in-memory `Room.inMemoryDatabaseBuilder` coverage on `data.local.dao` up to
  every public method on `ChatDao` (including `Upsert`, `renameSession`,
  `setSessionStarred`, single-column `UPDATE`s, `getDisplayMessagesBySessionId`
  `isFinal` filter, `getStarredMessages`, `getRecentMessagesByRole` ordering +
  limit), `LocalModelDao` (`observeActiveModel` Flow, `updateModel`,
  `deleteModelById`, `countByName`, `findByName` happy / miss /
  duplicate-name determinism), and `MemoryDao` (`insertMemory` rowId,
  `getRecentMemories` / `getRecentMemorySummaries` projection, `deleteMemoryById`,
  `deleteAllMemories` ignoring pin state, `observeChunkCount` /
  `observeTotalBytes` Flows). Added `PipelineDaoTest` (`@Transaction`-backed
  `getAllPipelines` / `getPipelineById`, `savePipelineTransaction` atomicity,
  scoped `deleteNodesForPipeline` / `deleteConnectionsForPipeline`,
  FK cascade on `deletePipelineById`, `NodeContextConfig` TypeConverter +
  nullable `config_json` round-trip), `PromptTemplateDaoTest` (`category, name
  ASC` ordering, `REPLACE` conflict, scoped delete, count), and
  `TraceStepDaoTest` (per-session `timestamp ASC` order, `durationMs` /
  `tokenCount` round-trip, scoped delete, FK cascade from `chat_sessions`).
  Added `AppDatabaseMigrationTest` — direct invocation of every
  `MIGRATION_17_18 … MIGRATION_22_23` against a real on-disk SQLite file via
  `FrameworkSQLiteOpenHelperFactory`, plus a chained `migrateAll_17_to_23`
  end-to-end run. `@Database(exportSchema = true)` + `room.schemaLocation` ksp
  arg + `app/schemas/` committed wire the v23 schema snapshot in for Room's
  runtime validation; future schema bumps must commit the new `N.json`
  alongside the migration. Added `androidx.room:room-testing` to
  `androidTestImplementation`.
- **`LocalAppFunctionManager` unit-test suite + extra codec edge cases**
  (Phase 23 / Task 2). 29 JVM tests for `LocalAppFunctionManager` cover the
  pure JSON-Schema generator (all supported and unsupported parameter types,
  description handling, required-array emission), discovery via
  `mockkObject(AppFunctionManager.Companion)` (empty manager → empty list +
  cache clear, `SENSITIVE` risk tagging, JSON-schema population per tool,
  description propagation), qualified-name dedup across packages (`pkg.a/foo`
  + `pkg.b/foo`), cache + re-discovery semantics for `isDiscovered` /
  `getParametersMetadata` / `getTargetPackageName`, `executeFunction`
  unavailable-manager error path, and the extracted `wrapInvocationError`
  helper (`AppFunctionException` → `IllegalStateException` with cause;
  others pass through). Two `AppFunctionDataCodecTest` gap-fillers also
  added: extra-field warning recurses through `SetObject` payloads, and a
  missing required field inside a `SetObjectList` item raises the same
  `IllegalArgumentException` as a top-level miss. Coverage of
  `data.tools.local` rises from **27.7 %** to **74.5 %**; the remaining gap
  is the `invokeByName` happy path that constructs `AppFunctionData`
  (Android-stub `Bundle` reference makes it unreachable from JVM tests —
  covered by `AppFunctionsEndToEndTest`).
- **`data.tools.local.executors` unit-test suite** (Phase 23 / Task 1).
  Added GWT-style JUnit + MockK coverage for `DelegateTaskExecutor`,
  `ScheduleTaskExecutor`, and `SearchToolExecutor` — happy path, JSON
  default branches (`targetModel` → `anthropic`, `intervalHours` /
  `delayMinutes` → `0`, `lang` → `en`), blank-query short-circuit,
  malformed / missing-field JSON, and downstream-exception propagation.
  The package was excluded from Kover reporting up to now; the
  exclusion was lifted in `app/build.gradle.kts` so the new tests
  surface as `100% line coverage` (21/21) and any future regression in
  the local-tool argument-parsing contract is caught by the build gate.

## [0.2.0] - 2026-05-25

Ships Phase 22 end-to-end: every Knotwork-redesigned screen wired to its
real backend, MCP routing hardened against config-edit / disabled /
flaky-provider regressions, R8-minified release variant, and a fresh set
of README hero shots regenerated from new Roborazzi baselines.

### Fixed

- **MCP routing — three regressions in `ToolRepositoryImpl`** (Phase 22 /
  Task 17 follow-up).
  - `syncMcpClients` now keys the pool by the full `McpServerConfig`
    (not just the URL) and tears the connection down + reconnects when
    any of auth / transport / headers / display name change for an
    already-connected server. Previously a credentials edit in
    **Settings → External providers** for an existing endpoint was a
    silent no-op, and every subsequent `getAvailableTools` /
    `executeTool` kept using the stale auth until the process restarted.
  - `executeMcpTool` no longer throws on the first advertising provider
    whose per-server `mcpId` is in `disabledMcpTools`. Since the gate is
    scoped per server (id = `mcp:<sha8(serverUrl)>:<toolName>`), two
    servers can advertise the same tool name and only one's id can be
    disabled — the loop now flags `sawDisabled = true` and keeps
    probing, so the enabled sibling still gets a chance to execute.
  - `executeMcpTool` no longer `break`s out of the loop when an
    advertising provider's `executeTool` throws. The failure is
    remembered as `lastExecutionError` and the loop falls through to
    the remaining providers; if every advertising provider also fails,
    the **most recent failure is re-thrown** (preserves the actual
    network / 5xx / parse error instead of the generic "not found").
    Restores multi-provider resilience — one flaky server can no longer
    eclipse a healthy sibling.
  - Plus: the MCP dispatch loop in `executeMcpTool` /
    `getAvailableTools` / `getRisk` now walks the persisted
    **`settingsRepository.mcpServers`** order instead of the
    non-deterministic `ConcurrentHashMap.entries`, so multi-provider
    probe order is both predictable and matches the priority the user
    actually configured in Settings. The new helper
    `distinctMcpConfigs()` deduplicates the list by URL (keep-first)
    before iteration — defensive measure that pairs with the
    persistence-side fix below.
- **`SettingsManager.updateMcpServer` no longer silently produces
  `[B, B]` when the user edits server A's URL to match an existing
  server B's URL.** The method now reads the persisted list
  pre-write, delegates the check to the pure
  `McpServerCollisionCheck.detectCollision` helper, and returns a typed
  `UpdateMcpServerResult.UrlCollision(collidingUrl,
  collidingDisplayName)` instead of writing. `SettingsRepository`'s
  signature changes from `Unit` to `UpdateMcpServerResult` and
  `McpServerConfigViewModel.onSubmit` surfaces the collision as an
  inline `urlError` on the URL field ("A server with this URL already
  exists: \"<name>\"") — the form stays open with `submitting = false`
  so the user can fix the value and resubmit. Add-mode (`addMcpServer`)
  is unchanged. Integration tests in `SettingsManagerTest` use a real
  file-backed `PreferenceDataStore` (over a `TemporaryFolder`) to
  prove the `dataStore.edit { … }` write actually does not happen on
  collision and that the no-op rename (`newUrl == originalUrl`) still
  persists cleanly.

### Added

- **R8 minification + resource shrinking on the release build** (Phase 22 /
  Task 17). `buildTypes.release` now sets `isMinifyEnabled = true` /
  `isShrinkResources = true`, and `app/proguard-rules.pro` carries keep
  rules for every reflection-driven subsystem (kotlinx.serialization, Gson,
  MediaPipe / LiteRT JNI, SQLCipher, Koog + Ktor, AppFunctions KSP-generated
  inventories / invokers, Hilt, Room) plus `-dontwarn` blocks for the
  OpenTelemetry incubator + AutoValue symbols Koog pulls in transitively.
  Each section is documented inline with a one-line rationale.
- **`docs/release.md`** — single-page release playbook covering variant
  matrix, signing posture (current debug-keystore vs. the production block
  to land before Play Store submission), R8 keep-rules rationale,
  bundle / size measurement instructions, and the v0.2.0 APK-size
  breakdown.
- **README hero + screenshot grid regenerated at 1080 × 2400 from new
  Roborazzi baselines** (one `HeroSnapshotTest` per surface). Five
  surfaces × light + dark = 10 PNGs total, all wired through `<picture>`
  blocks that honour `prefers-color-scheme`:
  - `hero-chat-home{,-dark}.png` — README hero (Idle chat).
  - `hero-pipeline-editor{,-dark}.png` — vertical stack of NodeCards
    per node type, clipped from `PipelineEditorCatalogContent`.
  - `hero-pipeline-library{,-dark}.png` — populated pipeline library
    (`PipelineLibraryPreview.populated()`).
  - `hero-tools{,-dark}.png` — built-in AppFunctions + an expanded
    MCP server (`ToolsPreview.defaultExpanded()`).
  - `hero-settings{,-dark}.png` — populated Settings stack
    (`SettingsPreview.default()`).

### Changed

- **Public documentation actualised against the post-legacy code state**
  (Phase 22 / Task 17 follow-up). Removed references to deleted legacy
  classes (`ChatScreen` / `ChatViewModel` / `ChatUiState`), retired the
  "Saving and filtering messages" feature description, rewrote the
  **Console** section to describe the agent-status pill + independent
  `ModalBottomSheet` console pane that replaced the legacy mini-console /
  full-log split, dropped the orphaned `screenshots/TODO.png` placeholders
  in `docs/user-guide.md`, fixed the chat life-of-a-message sequence
  diagram in `docs/architecture.md` to use the `ChatHome*` names, updated
  the **Settings** section of `docs/extending.md` to list all nine cards
  (was: six), refreshed the `PROMPT_VARIABLES` example in
  `docs/extending.md` + `pipeline-editor.html` to include the four new
  variables (`LANG / LOCATION / USER / DEVICE`) that landed in Phase 22 /
  Task 9, corrected the SDK install requirement in `CONTRIBUTING.md`
  (compileSdk is API 37, not API 36), wired `docs/release.md` into
  `README.md`, `CONTRIBUTING.md` (Further reading + Build & test), and
  `docs/architecture.md` (Further reading), and removed the stale
  duplicate `more/` block in `FILE_MAP.md`.

- **Jansi non-Android native binaries excluded from the release APK**
  (Phase 22 / Task 17). `android.packaging.resources` drops
  `org/fusesource/jansi/internal/native/{Windows,Mac,Linux,FreeBSD}/**` and
  `META-INF/native-image/jansi/**` — Jansi ships through Koog's logger and
  only its ANSI-escape rendering runs on JVM hosts. Saves ~430 KB on the
  release artefact.

### Removed

- **Legacy chat surface** (`presentation/ui/chat/legacy/`) — 13 production
  files + paired tests (2 unit tests, 4 instrumented tests). The
  redesigned `chat/home/` package has been the production surface since
  Phase 21 / Task 8 and absorbed every behaviour that mattered
  (orchestrator core, HITL, Clarification, console pane, chat export);
  the legacy package was kept around as a reference while wiring landed,
  and is now removed so future grep / FILE_MAP / file-tree audits stay
  honest. Stale docstring + comment references in `ChatHomeViewModel`,
  `ChatExportPayload`, `AppNavGraph`, `HitlIntegrationTest`, and
  `ClarificationIntegrationTest` are cleaned up alongside the deletion.

- **Global UI audit on Knotwork-converted screens** (Phase 22 / Task 16).
  Swept `app/src/main/` for design-system violations across hex colours,
  shape / typography / dp / motion tokens, theme single-source, and
  component reuse. One Block-grade and eight Major findings were
  closed in this PR; the full triage and the deferred TalkBack + dynamic-
  type release-smoke checklists landed in `project_docs/ui-audit-phase22.md`
  under the new `## Task 16` section.
  - `NodeContextConfigSection.kt` swaps the hint `Surface` shape from
    `RoundedCornerShape(8.dp)` to `KnotworkTheme.shapes.sm` and rebinds
    paddings to spacing tokens.
  - `PromptPreviewBottomSheet.kt`, `VariableChipsRow.kt`,
    `PromptLibraryDialog.kt`, `PipelineLibraryScreen.kt`, and
    `ChatHomeDebugStatePicker.kt` rebind every on-scale dp literal
    (`4 / 8 / 12 / 16 / 24`) to the matching `KnotworkTheme.spacing.*`
    token. Off-scale intentional values (`120 dp` loading container,
    `2 dp` checkbox-row vertical rhythm) are now private vals with a
    KDoc documenting the intent.
  - `AppShellScaffold.kt` ties the bottom-nav slide-in to
    `KnotworkTheme.motion.dur3` + `easeStd` and collapses to
    `EnterTransition.None / ExitTransition.None` under
    `KnotworkTheme.a11y.reducedMotion()`.
  - `ValidationBar.kt` Auto-fix header action migrated from raw M3
    `TextButton` to `KnotworkTextButton` for catalog typography +
    accent. The per-row `Go ↗` action stays on raw `TextButton`
    pending a `trailingIcon` slot on the catalog button (filed as
    Minor in the audit doc).

- **Phase 22 / Task 16 post-merge fixes (F1–F10).** Ten review
  findings landed in the same PR by user request. Roborazzi
  baselines for `chat_home_*`, `settings_*`, and
  `prompts_editor_*` were re-recorded.
  - **F1** — `ChatHomeContent` TopAppBar subtitle drops the
    `Pipeline · ` prefix (`knotwork_chat_home_topbar_pipeline`) so
    the token counter fits on a single line at the default font
    scale.
  - **F2** — Markdown rendering restored. Catalog `ChatMessage` /
    `ChatHomeContent` gained an optional
    `markdownRenderer: @Composable (String) -> Unit` slot;
    `ChatHomeScreen` wires the `com.mikepenz.markdown.m3.Markdown`
    renderer. `ChatHomeViewModel.chatMessageToRow` now emits
    `ChatContent.Markdown` for agent / tool rows and keeps
    `ChatContent.Text` for user rows. Plain-text fallback is the
    default when no renderer is supplied. The new catalog
    `MarkdownTheme.kt` exposes Knotwork-themed
    `knotworkMarkdownTypography()` / `knotworkMarkdownColor()`
    factories that flow `KnotworkTextStyles` headings (collapsed
    from the M3 display tier into `TitleXl → BodySm`), `MonoBase`
    code, and `extended.surface{1,2,3}` / `divider` backgrounds
    through the m3 renderer. `:catalog` now declares `markdown-m3`
    as an `api` dependency.
  - **F3** — Drawer selected-thread contrast in dark theme. The
    selected row's background switched from the static
    `KnotworkPalette.Accent50` to `MaterialTheme.colorScheme.primaryContainer`
    paired with `onPrimaryContainer` text, mirroring the
    onboarding Step-2 fix; light theme retains WCAG-AA contrast.
  - **F4** — Eliminated the empty-chat hero flash on cold start.
    Added `ChatHomeUiState.Loading` / `ChatHomeVisualState.Loading`,
    the VM starts in `Loading`, and the catalog renders a centred
    `CircularProgressIndicator` until the first chat snapshot
    arrives. `rebalanceRestingState` settles `Loading → Empty / Idle`
    on the first emission.
  - **F5** — Settings secondary text (non-SemiBold subtitles on
    `onSurfaceMuted`) switched from `BodySm` / `LabelSm` to
    `MonoSm` to match the cloud-provider rows. Touches the
    Identity card, params helper, system-instructions counter,
    local-model backend label, Test backend probe line, and
    memory re-embedding progress.
  - **F6** — Test backend `Run` action shrunk to
    `KnotworkButtonSize.Sm`, matching the Memory section's Export /
    Clear / Reset buttons.
  - **F7** — Prompt library editor `EditorTextField` renders the
    multi-line prompt body in `KnotworkTextStyles.MonoSm`, matching
    the Settings → System instructions field. The Name field keeps
    the proportional `BodyBase` face.
  - **F8** — LITE_RT node "Active model" sentinel. Blank
    `LiteRtConfig.modelId` is now the explicit "use the currently-
    active model at execute time" choice — surfaced as a new
    `Active model` dropdown item, the codec persists `modelPath =
    null` on the domain row, and `LoadModelUseCase` centralises
    the blank-coerce so every executor (LITE_RT / Output / Summary
    / Clarification / Tool / System) honours the sentinel. The
    validator no longer flags blank `modelId` as REQUIRED. The
    earlier `LaunchedEffect` that eagerly froze the active id into
    the field on open was removed.
  - **F9** — Streaming token counter in the agent status pill. New
    `ChatHomeViewModel.streamingTokens` flow derives an approximate
    count from `AgentOrchestratorState.Thinking` / `Answering`
    `partialText.length`. The catalog status string composes as
    `generating · N tok` while non-zero, resets on each new send /
    Completed / Error.
  - **F10** — Restored `systemPrompt` field on the OUTPUT node form.
    Catalog `OutputConfig` gained the field; `OutputFormBody`
    renders the same `TextField` + variable chips pattern as
    LITE_RT / CLOUD; the codec encodes / decodes the field and
    syncs it onto `NodeModel.systemPrompt` (legacy rows surface
    their persisted prompt instead of silently clearing).

### Added

- **Knotwork conversion of the remaining screens** (Phase 22 / Task 15).
  Splash, Models, Prompt library, Task monitor, Live metrics, More, and
  About now share the catalog-driven Compose surface used by the §C1–C8
  screens. Each app-side screen is a slim mapper that folds its
  `UiState` into a catalog `*ViewState` and renders the matching
  `*Content` composable; design changes from now on live in the
  `:catalog` module. Specifically:
  - **Splash** — new `SplashContent` with `KnotworkLogo(Lg)` brand
    hero, `LinearProgressIndicator` on `KnotworkTheme.extended.surface2`
    track, status label and `KnotworkPrimaryButton` Retry on error.
  - **Models** — inline-section layout that matches the design mockup:
    accent-tinted Active card → HuggingFace token field with `+ Paste`
    clipboard action → Custom URL field with `Get` button → preset list
    with three per-row variants (Idle / Downloading / OnDisk).
    `ModelsViewModel` gained `cancelDownload()` (cancels the in-flight
    job) and `deleteModel(id)`; `ModelsUiState` adds the
    `activeDownloadFileName` field so the progress row binds to the
    matching preset.
  - **Prompt library** — `ScrollableTabRow` of category tabs +
    accent-stripe cards with inline `$VAR` highlighting + footer
    `used by N pipelines · Duplicate` action. Editor moved into a
    `ModalBottomSheet` (`PromptEditorSheetBody`) with Name / Category /
    Prompt text / `INSERT` chip row. New VM methods:
    `selectCategory`, `openEditor(id?)`, `closeEditor`, per-field
    setters, `saveEditor`, `duplicatePrompt`.
  - **Task monitor** — `KnotworkFilterChip` row + per-task rows with
    leading icon tile, `StatusPill` trailing, optional cancel button,
    and a `TaskMonitorDetailSheetBody` `ModalBottomSheet` opened on
    row tap. `TaskMonitorViewModel` adds `openDetails(id)` /
    `closeDetails()`.
  - **Live metrics** — `MonitoringContent` renders the power-saving
    banner, a 3-cell `KnotworkStatCell` grid (Inference time / tokens
    per second / total tokens), total execution line, per-node-type
    breakdown, and recent-logs lazy column. The original
    `MonitoringViewModel` is unchanged; the screen consumes its
    existing state through a pure-Kotlin mapper.
  - **More** — landing tab is now stateful: `MoreViewModel` aggregates
    live counters from `MemoryRepository.observeStats`,
    `LocalModelRepository.getAllModels`, `PromptRepository.getAllPrompts`,
    `TaskQueueManager.activeSessionsState`, and the new
    `NetworkActivityTracker.lastOutboundAt` flow. Renders seven rows on
    `KnotworkNavListRow` with subtitle counters and an active-tasks
    badge, plus a footer privacy pill (`on-device · no network calls
    in last N m`).
  - **About** — full body: `KnotworkLogo(Lg)` hero, version / build /
    commit card, license card with an `Open license text` CTA, hand-
    maintained acknowledgments list (15 key dependencies), and a
    privacy summary card with a `Read privacy policy` CTA.
- **`NetworkActivityTracker`** (`domain/repositories/` + `data/repositories/`).
  Singleton timestamp of the most recent outbound LLM / MCP call.
  Recorded by `CloudLlmNodeExecutor` immediately before each
  `executeStreaming` call and by `KoogMcpClient.connect` /
  `executeTool`. Drives the More tab privacy pill — distinct from
  `NetworkStateRepository`, which only reflects connectivity. The
  value resets on process recreation, which matches the indicator's
  "since you opened the app" semantics.
- **`KnotworkLogo`** (`components/brand/`) — purely vector brand mark
  (rounded square frame + inner diamond) in three sizes (`Sm` 32 dp,
  `Md` 64 dp, `Lg` 128 dp). Stroke width is a fixed fraction of the
  canvas side so all sizes look visually identical.
- **`KnotworkNavListRow`** (`components/lists/`) — 72 dp tall
  navigation row with a 48 dp leading icon tile, title (`TitleMd`),
  optional mono subtitle, optional trailing slot (badge / status
  pill), and a permanent chevron-right glyph. Backbone of the More
  tab and a candidate for the secondary surfaces that still use
  Material 3 `ListItem`.

- **Inputs & chips — design-system alignment** (Phase 22 / Task 14
  follow-up). Brings every text input and chip on screen onto the
  canonical Knotwork atom family laid out in
  `inputs-and-chips.md`. Before this change the catalog had a single
  `KnotworkChip` (pill-shaped, three styles squeezed into one atom)
  plus a thin `KnotworkMonoTextArea`; everything else fell back to
  raw Material 3 `OutlinedTextField` / `FilterChip` / `AssistChip`
  scattered across 13 production call sites with no shared geometry,
  caps-label, or focus-state policy. Now:
  - New catalog atoms under `components/controls/`:
    - `KnotworkFieldDefaults` + `KnotworkFieldSize { Sm, Md, Lg,
      Composer }` — single source of truth for heights, paddings,
      border weights, icon sizes, label / helper / field gaps.
    - `KnotworkField` — caps-label + optional mono inline hint +
      helper / error row wrapper. M3 floating label stays off
      everywhere by design (dense rows + brand-signal cap label).
    - `KnotworkTextField` — single-line `BasicTextField` with the
      full 7-state visual table (default / hovered / focused /
      filled / disabled / readOnly / error), `monospace` flag for
      tokens / URLs / expressions, and a search-bar variant
      (pill shape, `surface2`, no border).
    - `KnotworkTextArea` — multi-line counterpart with
      length-preserving `VisualTransformation` that recolours
      `\$[A-Z_]+` tokens accent + underline inline, optional
      `insertChips` strip that splices the matching `$NAME` at the
      active cursor position, and `minLines` / `maxLines` controls.
    - `KnotworkPasswordField` — masks the value behind a `•`
      transformation by default and offers an eye-toggle trailing
      icon; flips to mono typography when revealed (long API keys
      read better in monospace).
  - New catalog atoms under `components/chips/`:
    - `KnotworkChipDefaults` + `KnotworkChipSize { Xs, Sm, Md }`.
    - `KnotworkFilterChip` — selected ↔ unselected toggle on the
      8 dp `sm` shape (Knotwork deliberately diverges from M3's
      pill-shaped filter chip), 180 ms cross-fade, optional
      `trailingCount` and `role` overrides for `Role.Tab` /
      `Role.Checkbox`.
    - `KnotworkSuggestionChip` — action-only chip with
      `surface1` + 1 dp outline so it reads on chat bubbles.
    - `KnotworkInputChip` + `KnotworkChipsInput` — removable
      chips inside a `FlowRow` plus an inline `BasicTextField`
      that commits on Enter / `,` and respects an optional
      `maxItems` cap (replaces the implicit "Max N items"
      behaviour that previously had to be reimplemented per
      caller).
    - `KnotworkVariableChip` — mono accent-coloured `$VAR`
      insert chip. Replaces the raw `AssistChip` row that used
      to back `VariableChipsRow`; the wrapper now forwards to
      this atom so prompt-variable affordances finally share the
      brand mono / accent / hollow-border treatment with the
      `KnotworkTextArea` highlight pass.
    - `KnotworkDateChip` — non-interactive section-divider pill
      for the chat stream (Today / Yesterday / locale date).
  - `RiskPill` and `StatusPill` brought onto the new spec: both
    drop the filled container for a transparent fill + 1 dp
    coloured border + leading 6 dp dot + `Mono13` label so they
    read on every surface (light card, console, chat bubble) and
    stop creating the contrast-collapse on inverted surfaces that
    the filled pills hit on the dark theme. `StatusPill` adds
    `Queued` and `Cancelled` states and pulses the dot on
    `Running` via an `infiniteRepeatable` tween that collapses to
    a constant alpha when
    `KnotworkTheme.a11y.reducedMotion()` is `true`. `Risk.Readonly`
    label switches from `"Read-only"` to `"Read only"` per spec.
  - Catalog `ChatComposer` trailing button now models the full
    4-state matrix from the spec: mic (Idle + empty + `onMic`
    callback) → send (Idle + non-empty) → stop (Generating) →
    retry (Error). Container and content colours swap per state
    (mic uses muted `surface3`, retry uses `riskDestructive`);
    morph stays at 200 ms and honours reduced motion.
  - 13 production usage sites migrated to the new family:
    `ChatHomeScreen` rename sheet, `ChatScreen` rename dialog,
    `ChatScreen` chat input bar (now wraps the catalog
    `ChatComposer`), `ClarificationCard` free-form input and the
    quick-reply chips (which were raw `OutlinedButton`s
    pretending to be chips), `ModelsScreen` HuggingFace token
    (now `KnotworkPasswordField` with mask + eye-toggle) and
    custom-URL field, `PipelineEditorScreen` rename dialog,
    `PipelineLibraryScreen` pipeline-name dialog,
    `PromptLibraryScreen` name + text fields (the text field
    folds in the `VariableChipsRow` companion via the new
    `insertChips` parameter), `FilterBar` search bar (`Md`
    pill, leading search icon, `Search` IME action),
    `ConsoleFullLogSheet` log filter row, `TaskMonitorScreen`
    task filter row, `VariableChipsRow` (`AssistChip` →
    `KnotworkVariableChip`), and the catalog
    `ChatHomeContent` sample-prompt row (`KnotworkChip` →
    `KnotworkSuggestionChip`).
  - The legacy `KnotworkChip` becomes `@Deprecated` (pointing at
    `KnotworkFilterChip`) and is scheduled for removal one
    iteration later — kept for now so any consumer outside this
    audit's reach does not break in the middle of the migration.

- **Koog 1.0.0 migration follow-up** — the prior dependency bump
  (`build: update koog, json, and roborazzi versions`) pointed every
  `koog-*` library at version `1.0.0`, but three Koog modules
  (`agents-mcp-server`, `prompt-executor-google-client`,
  `prompt-executor-deepseek-client`) have not promoted past
  `1.0.0-beta-preview7` on Maven Central yet — so the build failed
  to resolve. Plus the major bump renamed two DeepSeek model
  constants and the MCP client / prompt types moved between
  packages, breaking the source set. Fixes:
  - `koog-preview = "1.0.0-beta-preview7"` introduced as a separate
    version ref so the three unpromoted artifacts can pin to the
    latest beta without holding back the rest of the Koog stack
    (re-collapse onto `koog` once they ship 1.0.0 stable).
  - `koog-mcp` library entry switched from `agents-mcp-server`
    (server-only since 1.0.0; the misnaming used to work in 0.8.0
    via a transitive client dep) to `agents-mcp`, which is the
    actual client artifact carrying `McpToolRegistryProvider` /
    `mcpStreamableHttpTransport`.
  - `DeepSeekModels.DeepSeekChat` → `DeepSeekV4Flash`,
    `DeepSeekModels.DeepSeekReasoner` → `DeepSeekV4Pro` —
    `KoogModelMapper`, `KoogCloudLlmModelResolver`, and
    `DelegateTaskTool` updated to the new identifiers. Persisted
    user model ids fall back to `DeepSeekV4Flash` via
    `getDeepSeekModel`'s `else` branch.
  - Test source set migrated to the new Koog 1.0 prompt API:
    `ai.koog.prompt.dsl.Prompt` → `ai.koog.prompt.Prompt`,
    `Message.content` → `Message.textContent()` (the base
    `Message` interface now exposes `parts: List<MessagePart>` and
    a default `textContent()` extractor; `Message.Response` was
    renamed to `Message.Assistant`). `DelegateTaskToolTest`,
    `GraphExecutionEngineTest`, `CloudLlmNodeExecutorTest` were
    updated; the dead `Message.Response` mock that
    `DelegateTaskToolTest` carried was deleted (response flows
    through `StreamFrame.TextDelta` anyway).
  - Runtime `KoogHttpClient.Factory` lookup fixed. Koog 1.0.0
    declares the HTTP client as a JVM `ServiceLoader` SPI, but the
    `http-client-ktor-android-1.0.0` AAR published to Maven Central
    omits the `META-INF/services/ai.koog.http.client.KoogHttpClient$Factory`
    registration file (the `KtorKoogHttpClient$Factory` class itself
    is present, the SPI descriptor is not). The default `LLMClient`
    secondary constructors therefore threw
    `IllegalStateException: No KoogHttpClient.Factory provider
    found on the runtime classpath` on the first cloud call,
    crashing the app at startup. `KoogClientFactory` now constructs
    a single `KtorKoogHttpClient.Factory()` instance and passes it
    explicitly to every cloud / Ollama `LLMClient` constructor
    (`OpenAILLMClient`, `AnthropicLLMClient`, `GoogleLLMClient`,
    `DeepSeekLLMClient`, `OllamaClient`), bypassing the broken SPI
    lookup. `app/build.gradle.kts` already pulls in
    `libs.koog.http.client.ktor` so the factory class is on the
    runtime classpath; remove the explicit-factory plumbing once
    Koog ships an AAR with the services descriptor restored.
  - Lint baseline absorbs a Koog-version false-positive
    (`NewerVersionAvailable` claims `1.0.0-beta` is newer than
    `1.0.0-beta-preview7` for `agents-mcp`; Maven Central only
    publishes the `…-previewN` series and `-preview7` is the
    latest). Re-baseline together with the next genuine bump.

- **Pipeline editor — review follow-up** (Phase 22 / Task 14/17,
  second pass) — eight issues caught on a real device after the
  initial alignment landed: the `RunStatusBanner` now uses a single
  Material `Surface` with a `border` parameter so the inner border
  and the outer background tint always paint the same rectangle (the
  prior two-Surface layering left the border framing a smaller box
  than the background); `Stop` on the banner calls a new
  `OrchestratorViewModel.stopRunAndReset()` that wipes both
  `isRunning` and `activeNodeId`, and a screen-level `DisposableEffect`
  fires the same reset on screen leave so the banner can no longer
  follow the user back to the library or into another pipeline; the
  `ZoomRail` tiles dropped `IconButton` for `Surface + clickable +
  Icon` so the 40 dp tile size survives Material3's 48 dp
  minimum-interactive-component enforcement (tiles no longer overlap);
  `AutoLayout` post-translates the freshly-computed bbox so its
  centroid lands on the centroid of the previously occupied area —
  the re-laid graph stays where the user was looking instead of
  landing in the upper-left corner; a `FloatingActionButton` with `+`
  now sits in the bottom-right corner of the canvas (hidden behind
  the mini-map) and opens the radial quick-add menu at the viewport
  centre, honouring the empty-state's "Tap + to drop your first node"
  promise; the toolbar primary `Run` / `Re-run` button shrunk to the
  `Sm` size variant and dropped its leading icon so the title +
  subtitle stack has horizontal room to breathe (no more
  `Running · ste…` truncation); the empty-state CTAs wrap via
  `FlowRow` and use the `Sm` button size so `From template` no longer
  truncates to `Fro…`; the overflow `DropdownMenu` gained an
  explicit `Save pipeline` item that calls
  `OrchestratorViewModel.saveCurrentPipeline` and surfaces a snackbar
  on completion, so the user has a reliable "write to disk" lever
  instead of guessing whether each edit autosaved. Catalog snapshot
  baselines regenerated to reflect the new toolbar + banner layout.

- **Clarification node — default prompt + library seeds**
  (Phase 22 / Task 14/17, review fix) — two regressions caught on a
  fresh Clarification node:
  - `NodeConfigCodec.defaultFor(CLARIFICATION)` now seeds
    `questionTemplate` from `DefaultPrompts.getDefaultPromptForNodeType(CLARIFICATION)`.
    The previous `ClarificationConfig(title = title)` left the
    prompt blank on every new Clarification node — the user had to
    type from scratch instead of seeing the registered default.
  - `GetPromptTemplatesUseCase.seedMissingDefaults` extends the
    seed set to every prompt-bearing node type
    (`CLARIFICATION / EVALUATION / LITE_RT / CLOUD / OUTPUT`
    on top of the existing `INTENT_ROUTER / DECOMPOSITION /
    SUMMARY / TOOL`). Seed is now **additive** — it inserts only
    the categories that aren't already in the repository, so
    existing users who installed before a new category was
    registered pick up the new entry on the next library open
    instead of waiting for a DB wipe. Updated `GetPromptTemplatesUseCaseTest`
    to pin both the "all-present → no-op" and the
    "Clarification-missing → inserted" paths.

- **App-wide slider — single `KnotworkCompactSlider` atom**
  (Phase 22 / Task 14/17, review polish) — extracted the
  inline `CompactSlider` that the Settings screen had been rolling
  privately into a new catalog atom at
  `components/controls/KnotworkCompactSlider.kt`. Every slider in
  the app now goes through it: `KnotworkParamSlider` (Settings
  numeric params), `SettingsContent` (memory auto-summarize),
  `NodeConfigForms` (LITE_RT / CLOUD / Decomposition /
  Evaluation / Summary numeric fields). Visual contract — 4 dp
  pill-shaped thumb (4 × 18 dp) + 4 dp track, primary thumb /
  active track, `extended.surface3` inactive, no tick marks
  regardless of `steps`. Result: every slider reads identically
  regardless of which surface it sits in, instead of the previous
  mix of Material defaults vs the inline Settings variant vs the
  per-sheet `Modifier.height(28.dp)` hack. The dropped per-callsite
  customisation (settings-parity color helper, `COMPACT_SLIDER_*`
  constants, private Settings `CompactSlider`) is now `dead-code`
  removed.

- **Pipeline editor — polish round 3** (Phase 22 / Task 14/17,
  second real-device review pass) — six fixes on `NodeConfigSheet`:
  - Every field on the sheet now uses `KnotworkTextStyles.MonoBase`
    (Title + plain text fields + identifier fields). The mixed
    BodyBase / MonoBase stack read as accidentally inconsistent on
    the same dialog. The `monospace` parameter on `TextField` was
    removed (every call became `MonoBase`).
  - `VariableChipsRow` converts to a `LazyRow` with horizontal scroll
    so chips never wrap to a second row. The full prompt-variable
    set is now exposed: `$DATE / $TIME / $LANG / $LOCATION / $USER /
    $DEVICE / $MODEL / $TOOLS / $MEMORY_SUMMARY` (matches every
    registered `PromptVariableProvider` in the `:app` module).
  - `CloudFormBody` drops the per-node Model id field — cloud-model
    ids live once per provider in Settings → External providers and
    are shared across every Cloud node. `CloudConfig.model` stays on
    the data class for persisted-JSON backward-compat (executor falls
    back to the provider's configured model when the field is blank).
    `NodeConfigValidation.validateCloud` correspondingly stops
    flagging a blank `model` — the previous rule would have locked
    Save out forever now that the user can't reach the field.
  - Sheet sliders shrink to a compact 28 dp height — the M3 default
    48 dp interactive area was visibly inflating every slider row.
    Same primary thumb / surface3 inactive palette as
    `KnotworkParamSlider`; trade-off accepted: the form is a config
    surface, not a discoverability one.
  - `NodeContextConfigSection` ("Input Data" checkboxes — Original
    task / Chat history / Long-term memory / Tool results) is back
    on every NodeConfigSheet. The catalog `NodeConfigSheet` grew an
    `extraSection: @Composable (() -> Unit)?` slot (and the
    production `NodeConfigSheetHost` forwards it) so the
    domain-coupled section can render between the form body and the
    Save row without dragging `NodeContextConfig` into the catalog.
    `EditorState` gains a `workingContextConfig` mirror; Save now
    stitches it into the persisted `NodeModel.contextConfig`.
  - Default system prompts restored on every prompt-bearing node
    type. `NodeConfigCodec.deriveFromLegacy` falls back to
    `DefaultPrompts.getDefaultPromptForNodeType(node.type)` when
    `node.systemPrompt` is null/blank — older pipelines that
    persisted before defaults were wired into `NodeModel`
    construction now see the registered defaults on first open.
  - Catalog `pipeline_editor_*` snapshot baselines regenerated.

- **Pipeline editor — polish round 2** (Phase 22 / Task 14/17,
  real-device review pass) — eight more issues:
  - `NodeConfigSheet` Title field now uses `KnotworkTextStyles.BodyBase`
    instead of Material3's default `bodyLarge` so it no longer reads
    larger / different from every other field on the sheet.
  - `NodeConfigSheet` IntentRouter `Add class` auto-names the new
    class to a unique `class_N` placeholder so Save doesn't disable
    while the user is mid-rename (blank names used to immediately
    trigger the `REQUIRED` validator and lock Save out).
  - `NodeConfigSheet` prompt-library button moved back into a row
    above the field (sibling of the label, 32 dp tap-target). The
    earlier `trailingIcon` placement crowded the prompt and was hard
    to associate with the `VariableChipsRow` underneath.
  - `VariableChipsRow` extended to every prompt-bearing form
    (IntentRouter, Clarification, Decomposition, Evaluation, Summary
    custom-prompt) — previously only LITE_RT and CLOUD surfaced the
    `$DATE` / `$TIME` / `$TOOLS` / `$MODEL` / `$MEMORY_SUMMARY`
    chips.
  - `NodeConfigForms` `FloatSliderField` / `IntSliderField` now apply
    the same `SliderDefaults.colors` as `KnotworkParamSlider` on the
    Settings screen (primary thumb + active track, `surface3`
    inactive). Sheet sliders no longer fall back to M3's tonal
    palette.
  - Pipeline editor `BackHandler` learns the `searchOpen` state —
    system back closes the Find-node bar (and clears its query)
    instead of falling through to `onBack` and exiting the editor.
  - `Run` primary action now surfaces a `Preview only` snackbar
    explaining that the banner is the UI scaffold and that the real
    `GraphExecutionEngine` wiring lands in a follow-up. Avoids the
    "I tapped Run, nothing happened" confusion.
  - Catalog `pipeline_editor_*` snapshot baselines regenerated.

- **NodeConfigSheet — density tightening** (Phase 22 / Task 14/17,
  same review pass) — every per-field label now rides on
  `OutlinedTextField`'s floating-label slot instead of a separate row
  above (saves ~24 dp per field across 12 forms). The optional
  prompt-library button moves into the field's `trailingIcon` slot at
  a compact 32 dp tap target so the row no longer inflates to fit a
  48 dp `IconButton`. Sheet body padding drops `sp4 → sp3` horizontal
  + `sp3 → sp2` vertical; inter-field gap drops `sp3 → sp2`. The Save
  primary CTA shrinks to `KnotworkButtonSize.Sm` so the sticky action
  row matches the toolbar's `Run` button density. The catalog
  `pipeline_editor_*` snapshot baselines were regenerated to reflect
  the tighter sheet.

- **Pipeline editor — design alignment & feature backfill**
  (Phase 22 / Task 14/17) — closes every divergence the diff document
  caught between the spec, the production code, and the new designer
  mockups. The catalog `EditorToolbar` is reshaped to
  `[← back] [title + subtitle] [primary action] [overflow]`
  (`EditorPrimaryAction.Run` / `Rerun` / `None` for the primary slot);
  Undo / Redo / Delete / Auto-layout move into the production-side
  overflow `DropdownMenu`. The new top-of-canvas `RunStatusBanner`
  surfaces `Running` (amber + Pause / Stop) and `Done` (green + Trace)
  variants — replaces the prior bottom `RunTraceBar`. An
  always-visible right-edge `ZoomRail` (`+ / − / ⤡`) anchors zoom +
  fit-to-view; a `MiniMap` overlay (270 × 290 dp, `OVERVIEW · 0.42×`
  header, per-type-hue node bricks + accent viewport rect) drops in
  from the overflow. A 24 dp `DotGridBackground` lights up under the
  canvas (toggle from overflow). The empty state becomes a
  full-hero `EmptyPipelineState` (brand-mark tile + `Start with
  INPUT` / `From template` CTAs + info pill). The `ValidationBar`
  gains a header `Auto-fix` action (six recipes registered in
  `ValidationAutoFix`) + per-row `Go ↗` jumps + severity glyphs.
  Non-active nodes dim to α 0.40 during a run; running edges adopt
  the source node's header hue. Copy / Paste node (multi-select
  Copy button + overflow Paste). Find-node bar (`FilterBar`, opened
  from overflow). Inline rename via the overflow `Rename node…`
  dialog. The toolbar subtitle now renders `Editing · nodes N ·
  edges M` / `N issues · can't run` / `Running · <label>` /
  `Overview · 0.42× · nodes N`. Two TODO candidates are explicitly
  deferred to follow-ups: pipeline-as-PNG export (requires
  FileProvider plumbing) and the sidebar drag-from-palette (overlaps
  with the radial quick-add menu — best landed alongside broader
  wide-screen layouts).

- **Onboarding — review follow-up** (Phase 22 / Task 13/17, second
  pass) — three issues caught on a real device after the initial
  audit landed: the Step-2 selected `LiteRtModelRow` background
  switched from the static `Accent50` palette to
  `MaterialTheme.colorScheme.primaryContainer` so the model name stays
  readable in dark theme; the per-step `StepHeadline` / `StepBody`
  dropped from `Display2xl` / `BodyLg` to `TitleXl` / `BodyBase` so
  the title + description block no longer eats half the viewport on
  small phones; Step 3's Configure tap now navigates to the same
  per-provider API-key editor that Settings uses (via the new
  `OnboardingScreen.onConfigureProvider` callback wired through
  `AppNavGraph`), and the `configuredCloudProviders` projection is
  computed reactively from `ApiKeyRepository` so the "Configured" pill
  flips on whatever is actually persisted — not on which row the user
  tapped.

- **Onboarding — design audit & alignment** (Phase 22 / Task 13/17) —
  the four-step pager now honours every chrome rule called out in the
  task brief: the `StepHeadline` composable clamps the user's
  `fontScale` to 1.6× per `decisions.md §14` (via an overridden
  `LocalDensity` scoped to the headline subtree only, leaving every
  other text on the screen at the unclamped system value); the
  step-2 download progress indicator now branches on
  `KnotworkTheme.a11y.reducedMotion()` and collapses to a static
  primary-filled bar when the download reaches `≥ 0.99f`, matching
  the brief "под reduced-motion — статичный full bar"; the system
  predictive-back gesture is wired through a new
  `PredictiveBackHandler` in `OnboardingScreen` and rewinds the pager
  one step on steps 2–4, or raises a typed-confirm "Quit setup?"
  dialog on step 1; the activity-level `SnackbarHost` (which catches
  the skip-flow hint after onboarding is popped off the back-stack)
  now renders every message through `KnotworkSnackbar(variant =
  Default)` so the skip-hint sits on `extended.surface3` instead of
  the raw Material3 chrome. Roborazzi baseline grew from 8 → 16 PNGs
  with new fixtures for step-2 `Downloading`, step-2 `DownloadError`,
  step-2 `CustomUrlInput`, and step-4 `ModelReady`. Findings logged
  in `project_docs/ui-audit-phase22.md` (`## Task 13 — Onboarding`);
  `screens/README.md §C5` rewritten to match the second-pass JSX
  artboard family that `OnboardingViewState` was designed against,
  replacing the stale first-pass spec.

- **Onboarding — LiteRT model download wiring** (Phase 22 / Task 12/17)
  — Step 2 of the onboarding pager now actually downloads the picked
  LiteRT model. The CTA stays disabled until the model is on disk or a
  download is in flight, so the user can no longer advance into chat
  with no active model (which previously produced a "LiteRT handle
  released by system" error on the first send). Re-entering onboarding
  after a previous install detects the existing file through the new
  `LocalModelRepository.isInstalled(fileName)` query and surfaces an
  "Installed" pill on the matching row. Picking *Custom URL…* reveals
  an inline text field, with on-the-fly filename derivation from the
  trailing path segment. As soon as a model becomes available the
  ViewModel runs `LoadModelUseCase` to warm the inference handle, so
  step 4's "Open chat" CTA becomes enabled the moment the model
  finishes loading. Skipping onboarding emits a snackbar — "You can
  install a model from Settings → Models" — so the user knows where to
  recover the flow later. The bundled preset URLs live in the new
  `domain/constants/OnboardingModelCatalog.kt` and are shared between
  the onboarding catalog and the data-layer downloader.

- **Tools — full MCP server configuration** (Phase 22 / Task 10/17) —
  Each MCP server now carries a full [McpServerConfig]: optional
  display name, transport selection (SSE via Koog's
  `defaultSseTransport`; Streamable HTTP via the upstream MCP
  Kotlin SDK's `HttpClient.mcpStreamableHttpTransport` extension —
  both end-to-end wired against real servers), a typed
  authentication selector (None / Bearer / Basic / API Key) with
  per-scheme fields, and arbitrary request headers for advanced
  overrides. Adding and editing happen on a
  dedicated full-screen `McpServerConfigScreen` (route
  `tools/mcp-config?originalUrl={url}`) — the row's overflow ⋮
  menu (Refresh / Edit / Remove) opens it pre-filled, the
  `+ Add MCP` link opens it blank, and Save / Cancel pop back to
  the list. KoogMcpClient now configures the Ktor `defaultRequest`
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

[Unreleased]: https://github.com/alexeyw/PersonalAndroidAIAgent/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/alexeyw/PersonalAndroidAIAgent/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/alexeyw/PersonalAndroidAIAgent/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/alexeyw/PersonalAndroidAIAgent/releases/tag/v0.1.0
