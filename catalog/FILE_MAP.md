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
- `src/main/java/app/knotwork/design/components/` — atomic components.
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
    - `KnotworkButtonDefaults.kt` — `KnotworkButtonSize` tier enum
      (`Sm` / `Md` / `Lg`) and the height / padding / text-style lookup
      behind it: the single source of truth every button composable in
      `:catalog` resolves its geometry through.
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
    - `HealthBadge.kt` — trigger list-row health badge on `StatusPill`
      geometry but a **glyph** (not the dot) + tinted fill, so state reads
      by icon + label (`Healthy` / `Overdue` / `Last run failed`), never
      colour alone; collapses to icon-only at font-scale ≥ 2.0 (label kept
      for TalkBack).
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
    - `LabeledSwitchRow.kt` — settings-style toggle row (label + muted
      description, trailing `Switch` scaled down to sit beside the 14 sp
      title). The whole row is the tap target — the switch itself is
      non-interactive — so the 48 dp floor holds wherever it is reused.
  - `dialogs/` — dialog components.
    - `TypedConfirmDialog.kt` — canonical destructive typed-confirm dialog
      (`TypedConfirmDialogState` payload + `typedConfirmMatches` keyword
      rule). Shared by the Settings destructive actions and the splash
      data-recovery wipe so the confirmation contract cannot drift.
  - `lists/` — `PipelineListRow` / `ToolListRow` / `MemoryEntryRow` /
    `KnotworkNavListRow` (leading-icon + title + chevron routing row) +
    previews.
    - `SwipeRevealRow.kt` — wraps a list row in a **single** 64 dp
      swipe-revealed action (`SwipeRevealAction`: glyph + spoken label +
      tint). `PipelineListRow`'s mechanics — `Animatable` offset driven by
      `Modifier.draggable`, clamped and snapped at half the reveal, partial
      reveal never a dismiss — with one action instead of three, so the
      strip claims 20 % of a 320 dp drawer instead of 60 %. Shared by the
      chat drawer row (Archive) and the archive row (Restore).
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
    - `InterruptedRunCardModel.kt` — immutable payload of the
      interrupted-run status card (already-resolved node label — mapping a
      node id to a display name is a presentation concern).
    - `InterruptedRunCard.kt` — inline status card shown in the chat
      stream when the session's last run died with the process (Doze, OOM
      kill, swipe from recents) instead of finishing: `surface1` tile with
      a muted outline, so it reads as system status rather than an agent
      message, a question, or a permission gate.
    - `ImageThumbnail.kt` — Coil-backed attachment image (thumbnail or
      full-bleed) with its own **loading** and **missing-file** fallbacks —
      the latter is the state an attachment reaches once retention has
      swept the stored file.
    - `ImageViewer.kt` — full-screen viewer opened from a thumbnail:
      full-bleed image on an always-dark scrim (photo viewing reads the
      same in both themes), file name + dimensions in the top bar, an
      optional share action; no pinch-zoom.
    - `SourceChooserSheet.kt` — two-option image-source chooser
      (photo library / camera) as a `ModalBottomSheet`, plus the bare
      `SourceChooserSheetContent` rows so the body can be snapshot-tested
      without sheet chrome. Stateless: the caller owns visibility.
    - `AudioSourceChooserSheet.kt` — the voice-input counterpart (record /
      pick an audio file), same 56 dp row vocabulary, with the recording
      limit rendered as `m:ss` in the record subtitle.
    - `ImageAttachmentCatalogContent.kt` — harness exercising the
      attachment components together (thumbnail states, image bubble with
      and without caption, composer attachment affordance, chooser rows)
      with deterministic images for the Roborazzi baseline.
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
  - `automation/`
    - `ExternalAutomationJournalContent.kt` — external-automation
      request journal: posture banner (off / on-but-unbound /
      accepting), the collapsible wire-contract block, and a per-day
      timeline of inbound requests with a status tile, the refusal
      reason as a sentence, the claimed-versus-attested sender line and
      a repeat-count badge.
    - `ExternalAutomationConsentDialog.kt` — `ExternalAutomationConsentContent`,
      the stateless card body of the consent moment raised when the
      master switch is turned on (the host wraps it in a `Dialog`), plus
      its `ExternalAutomationConsentStrings` copy contract. The body
      scrolls under a height ceiling so the two buttons stay reachable
      at font-scale 200 %.
    - `ExternalAutomationJournalViewState.kt` — render contract: the
      catalog mirrors of the domain status (5) and rejection-reason (12)
      dictionaries, the sender-attestation kind, the per-day groups, the
      wire-key rows, and the whole localisable copy surface.
    - `ExternalAutomationPreview.kt` — deterministic fixtures for the
      journal states, including the caller looping against a
      switched-off contract and the request that tried to redirect its
      answer at a third package.
  - `chat/`
    - `ChatHomeContent.kt` — chat surface: message history, composer,
      console pane, and HITL / clarification / error overlays.
    - `ChatHomeViewState.kt` — visual-state enum (Loading / Empty / Idle
      / Generating / HitlConfirm / Clarification / Error / DrawerOpen /
      ConsoleExpanded) + message / thread row models.
    - `ChatHomePreviewData.kt` — deterministic preview fixtures for the
      chat surface (thread, model, message rows, HITL / clarification
      cards).
    - `ChatHomeDrawer.kt` — the alternate-nav drawer overlay extracted from
      `ChatHomeContent.kt`: sessions list, `+ New chat` pill, the thread row
      (`⋮` menu = Rename / Archive / — / Delete chat, plus a one-action
      Archive swipe and TalkBack custom actions; drops its status dot at
      font-scale 200 %) and the footer rows (`Archived chats` only when the
      count is positive, `Import chat`, `Settings`).
    - `ChatHomeContentPreview.kt` — Android Studio `@Preview` group for
      the chat variants in both themes.
  - `chatarchive/` — archived-chats surface (the chats a user took out of
    the drawer without deleting them).
    - `ChatArchiveContent.kt` — list of archived chats, most-recently-
      archived first: leading archive tile, relative archived-at label, an
      optional "run finished after archiving" note, inline Restore pill, a
      one-action Restore swipe and a row overflow (Restore / Export chat /
      ─ / Delete forever behind a plain confirmation). Teaching empty state
      with no CTA, skeleton and error branches. At font-scale 200 % the
      tile is dropped and Restore collapses to an icon-only 48 dp button
      with the word kept for TalkBack.
    - `ChatArchiveViewState.kt` — visual-state enum (Loading / Default /
      Empty / Error), `ChatArchiveRowUi`, the localised `ChatArchiveStrings`
      bundle and the callbacks. `revealedRowId` is a preview/snapshot
      control only — production leaves each row's swipe under the finger.
    - `ChatArchivePreview.kt` — deterministic fixtures (long title,
      starred row, a chat whose run settled after archiving).
    - `ChatArchiveContentPreview.kt` — Android Studio `@Preview` group.
  - `discover/` — model discovery over the curated Hugging Face
    organisation (list → detail → install).
    - `DiscoverContent.kt` — list surface: sticky search field
      (submits on the IME action), pull-to-refresh repository list with a
      host-formatted stats line, optional "N files" hint and a gated lock
      badge, plus the loading / empty / error branches.
    - `DiscoverViewState.kt` — `DiscoverVisualState` (Loading /
      Populated / Empty / Error) + `DiscoverModelRow`. Stats arrive
      pre-formatted and the file count stays a raw `Int`, so the catalog
      neither formats numbers nor resolves plurals.
    - `DiscoverDetailContent.kt` — detail surface for one repository:
      header, access-gated notice with an inline token field, the
      installable `.litertlm` file list, a "View on Hugging Face" link,
      and the license-confirmation dialog that precedes any download.
    - `DiscoverDetailViewState.kt` — detail visual-state enum, sealed
      `DiscoverFileStatus` per-file state, `DiscoverFileRow`, and the
      detail callback bundle.
    - `DiscoverPreview.kt` — deterministic fixtures shared by both
      surfaces' `@Preview`s and the Roborazzi matrix; `internal`, so
      `:app` cannot reach them.
  - `files/` — the workspace-sandbox browser.
    - `FilesContent.kt` — Files surface: quota header, file list,
      multi-select bar, detail bottom sheet, and the empty / error
      states. Deliberately reuses the `MemoryContent` vocabulary rather
      than introducing new components; all I/O and SAF launching stay in
      the host.
    - `FilesDialogs.kt` — the screen's two confirmations, split out to
      keep the composable count per file manageable: the destructive
      delete (single or bulk) and the import name-collision chooser
      (keep both / replace / cancel).
    - `FilesViewState.kt` — `FilesVisualState` body state plus the
      separate overlay/selection fields (preview sheet, dialogs), file
      kind / quota tone enums, and the row / quota / preview / dialog
      models, so opening a preview never changes the body branch.
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
    - `PerformanceCard.kt` — the Performance card below the active-model
      card: rolling-average TTFT / decode speed / peak memory for the
      active model, and the controlled benchmark with its segmented
      progress track.
    - `PerformanceCardState.kt` — sealed card state (no active model /
      idle with figures / running) + `BenchmarkPhase`, strings and
      callbacks. Every number arrives **pre-formatted** (`"420 ms"`,
      `"12.4 tok/s"`, `"1.8 GB"`) — units and localisation live in the
      host.
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
  - `settings/` — the settings hub plus one content composable per
    category sub-screen (the flat single-screen `SettingsContent` was
    split into this stack).
    - `SettingsHubContent.kt` — category hub: search field with live
      result rows, category cards, and the entry points into every
      sub-screen below.
    - `GenerationSettingsContent.kt` — system instructions, restrictions
      and the LLM generation parameters.
    - `ModelsSettingsContent.kt` — local model + inference backend and
      the cloud-provider list.
    - `MemorySettingsContent.kt` — long-term-memory controls
      (extraction, retrieval thresholds, re-embed).
    - `PipelinesSettingsContent.kt` — pipeline / structured-output
      controls. Basic tier is the **Run limits** entry row, carrying the
      current step and token limits as its subtitle.
    - `RunLimitsContent.kt` — the run-limits screen: four ceilings plus the
      spend statement. Carries two components of its own. `LimitSliderRow`
      is a slider with a description and an optional state qualifier, laid
      out in a `FlowRow` rather than a `Row` — at a 200 % font scale a
      title and a trailing chip cannot share one line, and a `Row` resolves
      that by clipping the chip off the screen. `StatementRow` is an axis
      the product *states* rather than controls, deliberately not a
      disabled slider: a disabled control implies something could enable
      it, and nothing will.
    - `RunLimitsViewState.kt` — `RunLimitsViewState`,
      `LimitSliderRowState`, `StatementRowState` and `RunLimitsCallbacks`.
      Every axis has a *move* and a *commit*, because writing a background
      limit is what stops it following the interactive one.
    - `ToolsSettingsContent.kt` — tool-calling and approval controls.
    - `BackgroundSettingsContent.kt` — background work, triggers,
      notifications and entry-surface bindings.
    - `PrivacySettingsContent.kt` — privacy, retention and telemetry
      controls (links out to the usage-statistics surface).
    - `AboutSettingsContent.kt` — identity card, build info, licenses
      and the reset actions.
    - `UsageTelemetryContent.kt` — on-device usage-statistics surface
      (recording toggle, run / pipeline / trigger / active-day stat
      sections, the **Setup** install-to-first-value section, export and
      reset actions) plus its own `UsageTelemetryViewState` /
      `UsageTelemetryCallbacks` / `UsageStatRow` models and empty state.
    - `SettingsViewStates.kt` — per-screen view states (hub, generation,
      models, memory, pipelines, tools, …).
    - `SettingsModels.kt` — shared settings models and enums (category
      ids, hub search rows, slider rows, identity / system-instruction
      card states).
    - `SettingsCallbacks.kt` — the single typed callback bundle shared by
      the hub and every sub-screen.
    - `SettingsRowAnchors.kt` — stable deep-link anchor constants
      mirroring the app-side `SettingsRegistry` (app-side test asserts the
      subset relation, so a registry rename can't orphan a highlight).
    - `SettingsCommon.kt` — the shared building blocks of the stack
      (category scaffold, section labels, advanced disclosure, toggle /
      nav / provider rows).
    - `KnotworkMonoTextArea.kt` — multi-line monospace textarea (brand
      outline) for system instructions.
    - `KnotworkParamSlider.kt` — labelled numeric-parameter slider with
      value label and optional validation error.
    - `KnotworkProviderRow.kt` — cloud-provider row with optional
      Ollama-specific fields (base URL, model) and validation.
  - `skills/` — reusable-skill surfaces (a skill = instruction + tool
    allowlist + context flags).
    - `SkillLibraryContent.kt` — library list: Bundled / Mine tabs,
      per-row overflow actions and a "New skill" FAB, over the
      loader / list / empty / error branches.
    - `SkillEditorContent.kt` — full-screen create-or-edit surface for a
      skill's name, description, instruction, tri-state tool allowlist
      and context flags.
    - `SkillDeleteDialog.kt` — delete-confirmation body (the host
      supplies the dialog container) with three branches keyed off the
      dependent pipelines: none, one, or an N-dependent "will break"
      list.
    - `SkillLibraryViewState.kt` — visual-state and tab enums, the
      tool-indicator / tool-mode enums behind the tri-state allowlist,
      and the row / tool-option / context-flag / editor / delete models.
    - `SkillLibraryPreview.kt` — canonical sample states shared by the
      `@Preview`s and the Roborazzi baselines, so the two cannot drift.
  - `splash/`
    - `SplashContent.kt` — splash surface (brand logo, app name,
      determinate progress or error + Retry CTA).
    - `SplashViewState.kt` — sealed Initializing / Loading / Error
      cold-start state.
  - `taskmonitor/`
    - `TaskMonitorContent.kt` — task list (filter row, task cards,
      expandable detail sheet with logs + actions), plus the top-bar
      "stop all scheduled tasks" action and its confirmation. The
      action appears only when something is actually stoppable; the
      confirm is a plain dialog rather than the typed-keyword one, since
      this is recoverable and is itself a recovery action.
    - `TaskMonitorViewState.kt` — visual-state enum + filter enum + task
      row / detail / status-lifecycle models, plus the scheduled-task
      count and bulk-cancel confirmation flag.
  - `tools/`
    - `ToolsContent.kt` — Tools surface (built-in AppFunctions section +
      MCP servers with expandable tool lists and connection states).
    - `ToolsViewState.kt` — visual-state enum + risk-tier / MCP
      connection enums + tool / server row models.
    - `AllowedDomainsContent.kt` — pushed editor for the `http_request`
      host allowlist — the gesture that opts the device into outbound
      HTTP. Empty state (globe hero, the tool-is-off explanation, an
      amber risk note) and populated state (explainer + host list with
      per-row removal), mirroring the MCP-server editor's structure.
    - `AllowedDomainsViewState.kt` — screen state plus the sealed
      `AddHostState` feedback for the add-a-host field (`Idle` /
      `NormalizedPreview` / `Duplicate` / `Invalid`); only
      `NormalizedPreview` enables **Add**. Normalisation itself is the
      host's (`HttpRequestPolicy`) job.
  - `triggers/` — automation-trigger surfaces (`TriggersContent` list with
    inline enable switch + health badge, `TriggerEditorContent`
    full-screen editor, `TriggerDeleteDialogContent`, and the detail below).
    - `TriggerDetailContent.kt` — trigger-detail surface: identity header
      (When / Runs / State + enable switch), Edit / Delete, an optional
      overdue stale banner, and the **evaluation journal** timeline (per-day
      groups; each entry = verdict tile + source + timestamp, plus a settled
      outcome line for fired rows or a human skip / re-arm sentence, and a
      third HITL line when the run stopped to ask the user — state plus a
      "from the notification" qualifier when it had to park);
      loading / empty / populated states, reduced-motion-aware pending dot.
    - `TriggerDetailViewState.kt` — detail view-state + the journal
      vocabulary mirrors (`TriggerHealthUi`, source / verdict / skip-reason /
      outcome / `TriggerJournalHitlUi` enums, entry / day-group models,
      `TriggerJournalVisualState`),
      `TriggerDetailStrings` (final English copy defaults), callbacks.
    - `TriggersViewState.kt` — list view-state (`TriggerRowUi` incl. the
      optional `health` badge, editor / delete models, strings, callbacks).
    - `TriggerDeleteDialog.kt` — `TriggerDeleteDialogContent`, the
      destructive delete-confirmation body (the host owns the dialog
      container).
    - `TriggersPreview.kt` — deterministic fixtures behind the triggers
      `@Preview`s and the Roborazzi baselines (list, editor, detail).
- `src/test/java/app/knotwork/design/store/StoreScreenshotTest.kt` —
  Roborazzi baselines for the app-store listing, rendered at
  `w360dp-h720dp-xxhdpi` = 1080 × 2160 because Play rejects a screenshot
  whose longer side exceeds twice the shorter one (the 1080 × 2400 hero
  baselines do). Copied by hand into `fastlane/metadata/android/en-US/
  images/phoneScreenshots/`.
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
  per-screen `HeroSnapshotTest` README heroes, covering about /
  automation / chat / memory / models / monitoring / more / onboarding /
  pipelines / prompts / settings / splash / taskmonitor / tools.
- `src/test/snapshots/` — committed Roborazzi baselines: one `*.png`
  per catalog page / component group / screen state, each in light and
  dark (plus reduced-motion and font-scale variants where exercised by
  the matching snapshot test above).
