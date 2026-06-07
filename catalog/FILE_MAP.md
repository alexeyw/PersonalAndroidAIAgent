# Directory Map: `:catalog` (Knotwork design system)

This file maps the contents of the `:catalog` Android library module.

The module hosts the Knotwork design system: tokens, atomic components,
and screen-level catalog pages. It depends on nothing else in this
project — `:app` consumes it as an `implementation` dependency.

- `build.gradle.kts` — Android library build script (Compose, ktlint,
  detekt, Roborazzi).
- `consumer-rules.pro` — ProGuard rules contributed to `:app`; empty.
- `src/main/AndroidManifest.xml` — minimal library manifest (no
  `<application>` element).
- `src/main/java/app/knotwork/design/a11y/` — accessibility scaffolding
  (`decisions.md §14`).
  - `KnotworkA11y.kt` — `KnotworkA11y` interface, `DefaultKnotworkA11y`
    implementation backed by `Settings.Global`, `FixedKnotworkA11y` test
    double, and the `LocalKnotworkA11y` composition local.
  - `RespectReducedMotionTransitions.kt` — `respectReducedMotionTransitions`
    helper that swaps caller-supplied enter/exit transitions for an
    80 ms alpha-only crossfade when reduced motion is on.
- `src/main/java/app/knotwork/design/tokens/` — design tokens.
  - `Color.kt` — `KnotworkPalette`, `KnotworkLight`, `KnotworkDark`,
    plus `knotworkLightColorScheme()` / `knotworkDarkColorScheme()`
    Material3 mappings.
  - `ExtendedColors.kt` — `KnotworkExtendedColors` data class (chat
    surfaces, console, risk pills, 12 node hues) and the
    `LocalKnotworkExtendedColors` composition local provider.
  - `Type.kt` — `KnotworkFonts.install(...)` font-family registry,
    `KnotworkTextStyles` raw scale and `knotworkTypography()` Material3
    mapping.
  - `Spacing.kt` — `KnotworkSpacing` 4 dp grid + `LocalKnotworkSpacing`.
  - `Shape.kt` — `KnotworkShapes` corner radii + `MaterialKnotworkShapes`
    M3 mapping + `LocalKnotworkShapes`.
  - `Elevation.kt` — `KnotworkElevation` levels + `LocalKnotworkElevation`.
  - `Motion.kt` — `KnotworkMotion` durations / easings +
    `LocalKnotworkMotion`.
  - `KnotworkIconSizes.kt` — icon-size tokens (AppBar / Nav / Inline /
    Fab / Micro) for consistent glyph render targets across surfaces.
- `src/main/java/app/knotwork/design/theme/` — root theme.
  - `KnotworkTheme.kt` — `@Composable fun KnotworkTheme(...)` wires
    Knotwork tokens into `MaterialTheme` and installs the extended /
    spacing / shape / elevation / motion / a11y composition locals;
    sibling `object KnotworkTheme` exposes them via
    `KnotworkTheme.extended` / `.spacing` / `.shapes` / `.elevation` /
    `.motion` / `.a11y`.
- `src/main/java/app/knotwork/design/icons/` — brand + node icon
  catalogue.
  - `AppIcons.kt` — facade exposing every custom Knotwork
    `ImageVector` (brand mark, 12 node glyphs, auto-layout, brain).
  - `IconCatalogPage.kt` — scrollable preview of the full icon set
    (used for design review + Roborazzi baseline).
  - `IconCatalogPagePreview.kt` — Android Studio `@Preview` for the
    page in both themes.
  - `imagevector/` — one file per custom `ImageVector` (brand mark,
    wordmark, flow, 12 node glyphs, auto-layout, brain).
- `src/main/java/app/knotwork/design/components/` — atomic components
 .
  - `ComponentsCatalogPage.kt` — single-scroll page composing every
    component category (Buttons, Chips & pills, List rows, Misc, Chat,
    Console).
  - `ComponentsCatalogPagePreview.kt` — Android Studio `@Preview`
    wrappers for the page in both themes.
  - `MarkdownTheme.kt` — typography + colour bindings for the
    multiplatform-markdown-renderer used in content surfaces.
  - `brand/` — brand-mark composables.
    - `KnotworkLogo.kt` — renders the canonical two-node brand glyph at
      configurable sizes plus the plated app-icon tile variant.
  - `buttons/` — `KnotworkPrimaryButton` / `KnotworkSecondaryButton` /
    `KnotworkTextButton` / `KnotworkIconButton` + previews.
  - `chips/` — chip family per `inputs-and-chips.md` §6.
    - `KnotworkChipDefaults.kt` — `KnotworkChipSize` enum + shared size /
      padding / motion constants.
    - `KnotworkFilterChip.kt` — toggle / segmented chip (selected ↔
      unselected, 180 ms cross-fade, optional trailing count).
    - `KnotworkSuggestionChip.kt` — action-only chip (quick-reply,
      onboarding suggestions; outline + surface1 so it reads on chat
      bubbles).
    - `KnotworkInputChip.kt` — removable chip with trailing `×`.
    - `KnotworkChipsInput.kt` — composite for list-of-strings entry:
      [`FlowRow`] of input chips + inline `BasicTextField` that commits
      on Enter / `,`, honours optional `maxItems` cap.
    - `KnotworkVariableChip.kt` — mono accent-coloured `$VAR` insert chip.
    - `KnotworkDateChip.kt` — non-interactive section-divider pill for
      chat (Today / Yesterday / locale date).
    - `RiskPill.kt` — transparent + 1 dp risk-coloured border + 6 dp dot
      + Mono13 label (Read only / Sensitive / Destructive).
    - `StatusPill.kt` — same geometry as `RiskPill`, status-driven
      colour family (`Queued`, `Idle`, `Running`, `Success`, `Warning`,
      `Error`, `Cancelled`); pulses the dot for `Running` (respects
      `KnotworkTheme.a11y.reducedMotion()`).
    - `KnotworkChip.kt` — general-purpose pill chip with `Default /
      Tonal / Outline` styles, optional leading/trailing icons and a
      decorative (no-`onClick`) variant for tag rows; complements the
      intent-specific split family above.
    - `KnotworkChipsPreview.kt` — `@Preview` wrappers.
  - `controls/` — text-input + slider atoms per `inputs-and-chips.md`
    §1–§5.
    - `KnotworkFieldDefaults.kt` — `KnotworkFieldSize` enum +
      heights / paddings / borders / icon gaps shared by every text
      input.
    - `KnotworkField.kt` — caps-label + helper / error wrapper
      (M3 floating label intentionally off across the design system).
    - `KnotworkTextField.kt` — single-line `BasicTextField` with full
      state table (default / hovered / focused / filled / disabled /
      readOnly / error), mono flag, search-bar variant.
    - `KnotworkTextArea.kt` — multi-line counterpart; live
      `\$[A-Z_]+` highlight, optional `insertChips` strip.
    - `KnotworkPasswordField.kt` — masked text + eye-toggle on top of
      `KnotworkTextField`.
    - `KnotworkCompactSlider.kt` — 4×18 dp pill thumb + 4 dp track.
    - `KnotworkSegmentedControl.kt` — segmented row of filter chips.
  - `lists/` — `PipelineListRow` / `ToolListRow` / `MemoryEntryRow` /
    `KnotworkNavListRow` (leading-icon + title + chevron routing row) +
    previews.
  - `misc/` — `KnotworkLoader` / `KnotworkSnackbar` / `EmptyState` /
    `StripedPlaceholder` / `KnotworkSectionAction` (right-aligned action
    link) / `KnotworkStatCell` (counter grid cell) + previews.
  - `chat/` — chat-surface components.
    - `ChatRole.kt` — `User | Assistant | System | Tool` enum.
    - `ChatMetadata.kt` — `ChatMetadata` data class + `ChatMessageStatus`
      enum driving the trailing footer glyph.
    - `ChatContent.kt` — sealed `ChatContent` body (`Text`, `Markdown`,
      `Confirmation`, `Clarification`, `Error`, `ToolCall`) +
      `ToolCallStatus` enum.
    - `ChatBubbleShapes.kt` — asymmetric `User` / `Assistant` bubble
      shapes (16/16/4/16 mirrored).
    - `ChatMessage.kt` — root chat-message renderer with long-press
      context menu and per-content dispatch.
    - `ChatContextAction.kt` — `Copy | Rerun | Rate` enum surfaced by
      the long-press menu.
    - `HitlConfirmationModel.kt` — immutable payload for
      `ChatContent.Confirmation`.
    - `HitlConfirmationCard.kt` — full HITL card (risk pill, tool name,
      summary, JSON args block, destructive type-confirm, action row).
    - `HitlConfirmationState.kt` — pure-Kotlin gating helpers
      (Allow-enabled, Always-allow visibility, type-confirm word).
    - `ClarificationCardModel.kt` — immutable payload for
      `ChatContent.Clarification`.
    - `ClarificationCard.kt` — clarification UI with quick-reply chips
      and free-form field; collapses to a one-line summary when answered.
    - `ChatComposer.kt` — multiline composer with `Idle | Generating |
      Error` state machine and send ↔ stop morph.
    - `ChatCatalogContent.kt` — single-column harness covering every
      chat variant + theme previews (used by the Roborazzi baseline).
  - `console/` — agent console components.
    - `ConsoleModels.kt` — `ConsoleSnap`, `ConsoleTab`, `ConsoleSource`,
      `ConsoleLevel`, `ConsoleFilter`, `ConsoleLine`, `ConsoleVarRow`,
      `ConsoleTraceSpan`, `SpanStatus`.
    - `ConsolePane.kt` — bottom-sheet container with sticky header,
      three tabs, source-filter chips, and per-tab body.
    - `ConsoleCatalogContent.kt` — single-column harness covering each
      snap × tab combination + theme previews.
  - `pipelineeditor/` — pipeline-editor base components.
    - `NodeType.kt` — enum of the 12 editor node types (Input, Output,
      LiteRT, Cloud, IntentRouter, IfCondition, Clarification, Tool,
      Decomposition, QueueProcessor, Evaluation, Summary).
    - `NodeTypeColors.kt` — composables mapping `NodeType` to header
      tint, luminance-banded foreground, and display label.
    - `NodeIcons.kt` — `NodeType` → header glyph extension over
      `AppIcons`.
    - `NodePorts.kt` — `OutboundPort` sealed class + `NodePorts`
      descriptor of inbound/outbound port topology per node type.
    - `NodeError.kt` — sealed `Validation` / `Runtime` error states
      surfaced on `NodeCard` borders and bodies.
    - `NodeCard.kt` — unified node card covering idle / selected /
      multi-selected / error / running states with dynamic port dots and
      header tint.
    - `EdgeLabel.kt` — floating edge-label chip (branch conditions
      True/False/Item/Done/Pass/Retry/Fail or intent class names).
    - `NodeConfig.kt` — sealed `NodeConfig` interface + 12 per-type
      payload data classes (`InputConfig`, `OutputConfig`, …).
    - `NodeConfigValidation.kt` — pure-Kotlin validator for `NodeConfig`
      rules (title uniqueness, range bounds, JSON parsing) → field-keyed
      error map.
    - `NodeConfigForms.kt` — per-type form bodies (12 variants) with
      shared helpers (`FieldLabel`, `InlineError`, `VariableChipsRow`).
    - `NodeConfigSheet.kt` — modal bottom sheet hosting the per-type
      forms with sticky Cancel/Save row and optional app-provided
      sections.
    - `EditorToolbar.kt` — editor top toolbar (back, inline-editable
      name, subtitle, primary Run/Re-run action).
    - `RunStatusBanner.kt` — run-status strip (status badge + metrics +
      Pause/Stop/Resume/Trace actions per `RunStatus`).
    - `PipelineEditorCatalogContent.kt` — scrollable harness exercising
      every pipeline-editor base component + theme previews.
  - `topbar/` — top-app-bar chrome.
    - `KnotworkTopAppBarShell.kt` — wraps a `TopAppBar` in a column with
      an attached hairline divider so it never bleeds into scrolled
      content.
- `src/main/java/app/knotwork/design/foundations/` — catalog pages.
  - `FoundationsCatalogPage.kt` — palette + type scale + spacing
    surface, used for design review and the snapshot baseline.
  - `FoundationsCatalogPagePreview.kt` — Android Studio `@Preview` for
    the page in both themes.
- `src/main/java/app/knotwork/design/screens/` — screen-level catalog
  surfaces (`*Content` composables + `*ViewState` render contracts)
  consumed by `:app`'s slim mapper screens.
  - `about/`
    - `AboutContent.kt` — hero brand mark + version / license /
      acknowledgments / privacy cards in a scrollable `LazyColumn`.
    - `AboutViewState.kt` — app name / version / build / commit SHA /
      license / acknowledgments / privacy render contract.
  - `chat/`
    - `ChatHomeContent.kt` — chat surface: message history, composer,
      console pane, and HITL / clarification / error overlays.
    - `ChatHomeViewState.kt` — visual-state enum (Loading / Empty / Idle
      / Generating / HitlConfirm / Clarification / Error / DrawerOpen /
      ConsoleExpanded) + message / thread row models.
    - `ChatHomePreviewData.kt` — deterministic preview fixtures for the
      chat surface (thread, model, message rows, HITL / clarification
      cards).
    - `ChatHomeContentPreview.kt` — Android Studio `@Preview` group for
      the chat variants in both themes.
  - `memory/`
    - `MemoryContent.kt` — Memory Manager surface (stats header,
      category chips, semantic search, provenance breakdown, entry
      cards).
    - `MemoryViewState.kt` — visual-state enum + sort/filter enums and
      segment / stat / category / row models.
    - `MemoryType.kt` — per-element typography overrides transcribed
      from the Memory Manager design spec.
  - `models/`
    - `ModelsContent.kt` — Models surface (active model card, Hugging
      Face auth, preset rows with download progress).
    - `ModelsViewState.kt` — visual-state enum + active-card / preset-row
      models with download status.
  - `monitoring/`
    - `MonitoringContent.kt` — Monitoring surface (metrics grid,
      per-node-type breakdown, system log lines).
    - `MonitoringViewState.kt` — visual-state enum + stats / breakdown /
      log-line models.
  - `more/`
    - `MoreContent.kt` — navigation-row list (via `KnotworkNavListRow`)
      with optional badges and a footer network-status pill.
    - `MoreViewState.kt` — ordered navigation rows + network-status
      render contract.
  - `onboarding/`
    - `OnboardingContent.kt` — four-step pager (Welcome / LiteRtModel /
      CloudKeys / Ready) with progress bar and per-step CTAs.
    - `OnboardingViewState.kt` — step enum + model options
      (Gemma4E2B / Gemma4E4B / CustomUrl).
  - `pipelines/`
    - `PipelineLibraryContent.kt` — pipeline list with sort/filter,
      per-row overflow, default/active badges, swipe-reveal.
    - `PipelineLibraryViewState.kt` — visual-state enum + filter enum +
      pipeline-row model.
    - `PipelineLibraryPreview.kt` — deterministic preview fixtures
      (4 sample pipelines) for snapshots.
  - `prompts/`
    - `PromptLibraryContent.kt` — prompt library (tabbed categories, card
      list, FAB, optional edit-sheet overlay).
    - `PromptLibraryViewState.kt` — visual-state enum + prompt-row /
      editor state + category / variable tracking.
    - `PromptPresetPickerSheet.kt` — modal preset picker by `NodeType`
      (Bundled / Mine tabs, searchable rows, tag filter).
  - `settings/`
    - `SettingsContent.kt` — full settings stack (identity / system
      instructions / restrictions / LLM params / local model / providers
      / memory / privacy cards).
    - `SettingsViewState.kt` — visual-state enum + per-card data slices.
    - `KnotworkMonoTextArea.kt` — multi-line monospace textarea (brand
      outline) for system instructions.
    - `KnotworkParamSlider.kt` — labelled numeric-parameter slider with
      value label and optional validation error.
    - `KnotworkProviderRow.kt` — cloud-provider row with optional
      Ollama-specific fields (base URL, model) and validation.
  - `splash/`
    - `SplashContent.kt` — splash surface (brand logo, app name,
      determinate progress or error + Retry CTA).
    - `SplashViewState.kt` — sealed Initializing / Loading / Error
      cold-start state.
  - `taskmonitor/`
    - `TaskMonitorContent.kt` — task list (filter row, task cards,
      expandable detail sheet with logs + actions).
    - `TaskMonitorViewState.kt` — visual-state enum + filter enum + task
      row / detail / status-lifecycle models.
  - `tools/`
    - `ToolsContent.kt` — Tools surface (built-in AppFunctions section +
      MCP servers with expandable tool lists and connection states).
    - `ToolsViewState.kt` — visual-state enum + risk-tier / MCP
      connection enums + tool / server row models.
- `src/test/java/app/knotwork/design/tokens/KnotworkTokensTest.kt` —
  pure-JVM sanity tests for the token data classes (no Compose runtime).
- `src/test/java/app/knotwork/design/theme/KnotworkThemeTest.kt` —
  Robolectric + Compose-rule tests verifying `KnotworkTheme` wires
  tokens into `MaterialTheme.colorScheme` and the `KnotworkTheme.*`
  accessors in both light and dark.
- `src/test/java/app/knotwork/design/foundations/FoundationsCatalogPageSnapshotTest.kt`
  — Roborazzi snapshot baseline for `FoundationsCatalogPage` in light
  and dark.
- `src/test/java/app/knotwork/design/a11y/KnotworkA11yTest.kt` —
  Robolectric tests verifying `DefaultKnotworkA11y` reads system
  scales and `FixedKnotworkA11y` honours its constructor args.
- `src/test/java/app/knotwork/design/components/ComponentsCatalogPageSnapshotTest.kt`
  — Roborazzi baseline for the full components catalog page.
- `src/test/java/app/knotwork/design/components/buttons/KnotworkButtonsSnapshotTest.kt`
- `src/test/java/app/knotwork/design/components/chips/KnotworkChipsSnapshotTest.kt`
- `src/test/java/app/knotwork/design/components/chips/KnotworkChipTest.kt`
- `src/test/java/app/knotwork/design/components/chips/RiskPillTest.kt`
- `src/test/java/app/knotwork/design/components/chips/StatusPillTest.kt`
- `src/test/java/app/knotwork/design/components/lists/KnotworkListsSnapshotTest.kt`
- `src/test/java/app/knotwork/design/components/lists/PipelineListRowTest.kt`
- `src/test/java/app/knotwork/design/components/lists/ConnectionStatusTest.kt`
- `src/test/java/app/knotwork/design/components/misc/KnotworkMiscSnapshotTest.kt`
- `src/test/java/app/knotwork/design/components/chat/ChatCatalogPageSnapshotTest.kt`
  — Roborazzi baseline for `ChatCatalogContent` (light / dark /
  reduced-motion).
- `src/test/java/app/knotwork/design/components/chat/ChatBubbleShapesTest.kt`
  — pure-JVM check that `ChatBubbleShapes.User` / `.Assistant` carry
  the documented asymmetric radii.
- `src/test/java/app/knotwork/design/components/chat/HitlConfirmationStateTest.kt`
  — pure-JVM coverage of the Allow / Always-Allow / typed-confirm rules.
- `src/test/java/app/knotwork/design/components/console/ConsoleCatalogPageSnapshotTest.kt`
  — Roborazzi baseline for `ConsoleCatalogContent` (light / dark).
- `src/test/java/app/knotwork/design/components/console/ConsoleFilterTest.kt`
  — pure-JVM coverage of `ConsoleFilter.matches` + `allOn`.
- `src/test/java/app/knotwork/design/components/console/ConsoleSnapTest.kt`
  — pure-JVM lock-down of the three snap-point heights.
- `src/test/java/app/knotwork/design/icons/AppIconsTest.kt`
- `src/test/java/app/knotwork/design/icons/IconCatalogPageSnapshotTest.kt`
- `src/test/java/app/knotwork/design/components/lists/ConnectionStatusTest.kt`
  — pure-JVM coverage of the connection-status → pill mapping.
- `src/test/java/app/knotwork/design/components/pipelineeditor/NodePortsTest.kt`
  — pure-JVM check of the per-type port topology.
- `src/test/java/app/knotwork/design/components/pipelineeditor/NodeConfigValidationTest.kt`
  — pure-JVM coverage of the `NodeConfig` validation rules.
- `src/test/java/app/knotwork/design/components/pipelineeditor/PipelineEditorCatalogPageSnapshotTest.kt`
  — Roborazzi baseline for the pipeline-editor catalog page.
- `src/test/java/app/knotwork/design/components/pipelineeditor/HeroSnapshotTest.kt`
  — Roborazzi hero baseline for the pipeline editor.
- `src/test/java/app/knotwork/design/tokens/WcagContrastTest.kt`
  — pure-JVM WCAG contrast-ratio checks over the palette.
- `src/test/java/app/knotwork/design/a11y/A11yMatrixSnapshotTest.kt`
  — Roborazzi font-scale × theme accessibility matrix.
- `src/test/java/app/knotwork/design/a11y/TalkBackHappyPathsTest.kt`
  — Robolectric TalkBack semantics happy-path coverage.
- `src/test/java/app/knotwork/design/screens/` — per-screen Roborazzi
  snapshot baselines (`*ContentSnapshotTest`, light / dark / a11y
  font-scale variants) plus `*AccessibilityTest` semantics checks and
  per-screen `HeroSnapshotTest` README heroes, covering about / chat /
  memory / models / monitoring / more / onboarding / pipelines /
  prompts / settings / splash / taskmonitor / tools.
- `src/test/snapshots/` — committed Roborazzi baselines: one `*.png`
  per catalog page / component group / screen state, each in light and
  dark (plus reduced-motion and font-scale variants where exercised by
  the matching snapshot test above).
