# Directory Map: `:catalog` (Knotwork design system)

This file maps the `:catalog` Android library module — the Knotwork design
system: tokens, atomic components, and screen-level catalog pages. It depends
on nothing else in this project; `:app` consumes it as an `implementation`
dependency.

Three files here are not Kotlin and so are not generated below:
`build.gradle.kts` (Android library build script — Compose, ktlint, detekt,
Roborazzi), `consumer-rules.pro` (ProGuard rules contributed to `:app`; empty)
and `src/main/AndroidManifest.xml` (minimal library manifest, no
`<application>` element).

**Generated.** The tree below is rebuilt by `./gradlew :app:generateFileMap`
from the source tree itself, and `:app:verifyFileMap` fails `./gradlew check`
when the committed file no longer matches it. A *description* is yours to edit —
it is carried across regenerations by path. Entries themselves are not: adding,
removing or reordering one by hand is undone by the next run.

Only Kotlin files appear inside the generated blocks.

Paths below are relative to `src/main/java/app/knotwork/design/`.

<!-- AUTO-GEN:FILE_MAP -->
- `a11y/` - accessibility scaffolding (`decisions.md §14`).
  - `KnotworkA11y.kt` - `KnotworkA11y` interface, `DefaultKnotworkA11y` implementation backed by `Settings.Global`, `FixedKnotworkA11y` test double, and the `LocalKnotworkA11y` composition local.
  - `RespectReducedMotionTransitions.kt` - `respectReducedMotionTransitions` helper that swaps caller-supplied enter/exit transitions for an 80 ms alpha-only crossfade when reduced motion is on.
- `components/` - atomic components.
  - `brand/` - brand-mark composables.
    - `KnotworkLogo.kt` - renders the canonical two-node brand glyph at configurable sizes plus the plated app-icon tile variant.
  - `buttons/` - `KnotworkPrimaryButton` / `KnotworkSecondaryButton` / `KnotworkTextButton` / `KnotworkIconButton` + previews.
    - `KnotworkButtonDefaults.kt` - `KnotworkButtonSize` tier enum (`Sm` / `Md` / `Lg`) and the height / padding / text-style lookup behind it: the single source of truth every button composable in `:catalog` resolves its geometry through.
    - `KnotworkButtonsPreview.kt` - Harness rendering every button variant in one column, shared by the Android Studio preview pane and the Roborazzi baseline, so a regression in any state surfaces in the same diff.
    - `KnotworkIconButton.kt` - Knotwork icon button — square 40 dp visual with an optional badge.
    - `KnotworkPrimaryButton.kt` - Zero-elevation constant used by every state of the primary button.
    - `KnotworkSecondaryButton.kt` - Knotwork brand **secondary** button — transparent container with a 1 dp outline.
    - `KnotworkTextButton.kt` - Knotwork **text** button — chrome-less, label-only.
  - `chat/` - chat-surface components.
    - `AudioSourceChooserSheet.kt` - the voice-input counterpart (record / pick an audio file), same 56 dp row vocabulary, with the recording limit rendered as `m:ss` in the record subtitle.
    - `ChatBubbleShapes.kt` - asymmetric `User` / `Assistant` bubble shapes (16/16/4/16 mirrored).
    - `ChatCatalogContent.kt` - single-column harness covering every chat variant + theme previews (used by the Roborazzi baseline).
    - `ChatComposer.kt` - multiline composer with `Idle | Generating | Error` state machine and send ↔ stop morph.
    - `ChatContent.kt` - sealed `ChatContent` body (`Text`, `Markdown`, `Confirmation`, `Clarification`, `Error`, `ToolCall`) + `ToolCallStatus` enum.
    - `ChatContextAction.kt` - `Copy | Rerun | Rate` enum surfaced by the long-press menu.
    - `ChatMessage.kt` - root chat-message renderer with long-press context menu and per-content dispatch.
    - `ChatMetadata.kt` - `ChatMetadata` data class + `ChatMessageStatus` enum driving the trailing footer glyph.
    - `ChatRole.kt` - `User | Assistant | System | Tool` enum.
    - `ClarificationCard.kt` - clarification UI with quick-reply chips and free-form field; collapses to a one-line summary when answered.
    - `ClarificationCardModel.kt` - immutable payload for `ChatContent.Clarification`.
    - `HitlConfirmationCard.kt` - full HITL card (risk pill, tool name, summary, JSON args block, destructive type-confirm, action row).
    - `HitlConfirmationModel.kt` - immutable payload for `ChatContent.Confirmation`.
    - `HitlConfirmationState.kt` - pure-Kotlin gating helpers (Allow-enabled, Always-allow visibility, type-confirm word).
    - `ImageAttachmentCatalogContent.kt` - harness exercising the attachment components together (thumbnail states, image bubble with and without caption, composer attachment affordance, chooser rows) with deterministic images for the Roborazzi baseline.
    - `ImageThumbnail.kt` - Coil-backed attachment image (thumbnail or full-bleed) with its own **loading** and **missing-file** fallbacks — the latter is the state an attachment reaches once retention has swept the stored file.
    - `ImageViewer.kt` - full-screen viewer opened from a thumbnail: full-bleed image on an always-dark scrim (photo viewing reads the same in both themes), file name + dimensions in the top bar, an optional share action; no pinch-zoom.
    - `InterruptedRunCard.kt` - inline status card shown in the chat stream when the session's last run died with the process (Doze, OOM kill, swipe from recents) instead of finishing: `surface1` tile with a muted outline, so it reads as system status rather than an agent message, a question, or a permission gate.
    - `InterruptedRunCardModel.kt` - immutable payload of the interrupted-run status card (already-resolved node label — mapping a node id to a display name is a presentation concern).
    - `SourceChooserSheet.kt` - two-option image-source chooser (photo library / camera) as a `ModalBottomSheet`, plus the bare `SourceChooserSheetContent` rows so the body can be snapshot-tested without sheet chrome. Stateless: the caller owns visibility.
  - `chips/` - chip family per `inputs-and-chips.md` §6.
    - `HealthBadge.kt` - trigger list-row health badge on `StatusPill` geometry but a **glyph** (not the dot) + tinted fill, so state reads by icon + label (`Healthy` / `Overdue` / `Last run failed`), never colour alone; collapses to icon-only at font-scale ≥ 2.0 (label kept for TalkBack).
    - `KnotworkChip.kt` - general-purpose pill chip with `Default / Tonal / Outline` styles, optional leading/trailing icons and a decorative (no-`onClick`) variant for tag rows; complements the intent-specific split family above.
    - `KnotworkChipDefaults.kt` - `KnotworkChipSize` enum + shared size / padding / motion constants.
    - `KnotworkChipsInput.kt` - composite for list-of-strings entry: [`FlowRow`] of input chips + inline `BasicTextField` that commits on Enter / `,`, honours optional `maxItems` cap.
    - `KnotworkChipsPreview.kt` - `@Preview` wrappers.
    - `KnotworkDateChip.kt` - non-interactive section-divider pill for chat (Today / Yesterday / locale date).
    - `KnotworkFilterChip.kt` - toggle / segmented chip (selected ↔ unselected, 180 ms cross-fade, optional trailing count).
    - `KnotworkInputChip.kt` - removable chip with trailing `×`.
    - `KnotworkSuggestionChip.kt` - action-only chip (quick-reply, onboarding suggestions; outline + surface1 so it reads on chat bubbles).
    - `KnotworkVariableChip.kt` - mono accent-coloured `$VAR` insert chip.
    - `RiskPill.kt` - transparent + 1 dp risk-coloured border + 6 dp dot + Mono13 label (Read only / Sensitive / Destructive).
    - `StatusPill.kt` - same geometry as `RiskPill`, status-driven colour family (`Queued`, `Idle`, `Running`, `Success`, `Warning`, `Error`, `Cancelled`); pulses the dot for `Running` (respects `KnotworkTheme.a11y.reducedMotion()`).
  - `ComponentsCatalogPage.kt` - single-scroll page composing every component category (Buttons, Chips & pills, List rows, Misc, Chat, Console).
  - `ComponentsCatalogPagePreview.kt` - Android Studio `@Preview` wrappers for the page in both themes.
  - `console/` - agent console components.
    - `ConsoleCatalogContent.kt` - single-column harness covering each snap × tab combination + theme previews.
    - `ConsoleEntryStrip.kt` - the console's own entry point: `console │ [NODE] idle · ready ⌃`. One element in two positions — above the composer when closed, the console sheet's own header when open. It carries the literal word because `Role.Button` plus a content description was not enough to make it findable by eye.
    - `ConsoleModels.kt` - `ConsoleSnap`, `ConsoleTab`, `ConsoleSource`, `ConsoleLevel`, `ConsoleFilter`, `ConsoleLine`, `ConsoleVarRow`, `ConsoleTraceSpan`, `SpanStatus`.
    - `ConsolePane.kt` - bottom-sheet container with sticky header, three tabs, source-filter chips, and per-tab body.
  - `controls/` - text-input + slider atoms per `inputs-and-chips.md` §1–§5.
    - `KnotworkCompactSlider.kt` - 4×18 dp pill thumb + 4 dp track.
    - `KnotworkField.kt` - caps-label + helper / error wrapper (M3 floating label intentionally off across the design system).
    - `KnotworkFieldDefaults.kt` - `KnotworkFieldSize` enum + heights / paddings / borders / icon gaps shared by every text input.
    - `KnotworkPasswordField.kt` - masked text + eye-toggle on top of `KnotworkTextField`.
    - `KnotworkSegmentedControl.kt` - segmented row of filter chips.
    - `KnotworkTextArea.kt` - multi-line counterpart; live `\$[A-Z_]+` highlight, optional `insertChips` strip.
    - `KnotworkTextField.kt` - single-line `BasicTextField` with full state table (default / hovered / focused / filled / disabled / readOnly / error), mono flag, search-bar variant.
    - `LabeledSwitchRow.kt` - settings-style toggle row (label + muted description, trailing `Switch` scaled down to sit beside the 14 sp title). The whole row is the tap target — the switch itself is non-interactive — so the 48 dp floor holds wherever it is reused.
  - `dialogs/` - dialog components.
    - `OutcomeDialog.kt` - the one "here is what happened" dialog shape. `OutcomeTone` picks the layout and glyph: `INFO` / `GUARD` / `ERROR` draw an icon above a centred headline, `QUESTION` draws none and left-aligns. The distinction that carries weight is **GUARD vs ERROR** — a safeguard that held is not a failure, so `GUARD` is the shield and red is reserved for "nothing happened". Carries an optional `OutcomeNamedList` ("left out", capped at `MAX_NAMED_LIST_ITEMS` with a caller-resolved summary line) and one to three `OutcomeAction`s, one of which may be emphasised. The body scrolls so the buttons survive large font scales. Shared by prompt import and the pipeline-import schema-mismatch dialog, which was the hand-rolled original.
    - `TypedConfirmDialog.kt` - canonical destructive typed-confirm dialog (`TypedConfirmDialogState` payload + `typedConfirmMatches` keyword rule). Shared by the Settings destructive actions and the splash data-recovery wipe so the confirmation contract cannot drift.
  - `lists/` - `PipelineListRow` / `ToolListRow` / `MemoryEntryRow` / `KnotworkNavListRow` (leading-icon + title + chevron routing row) + previews.
    - `KnotworkListsPreview.kt` - Harness rendering every list-row variant, including a pipeline row in the swipe-revealed state, shared by the preview pane and the Roborazzi baseline.
    - `KnotworkNavListRow.kt` - Compact navigation list row used by surfaces that primarily route the user to other screens (e.g. the `More` tab).
    - `KnotworkSectionHeader.kt` - the one section header for list surfaces: mono title, formatted row count, optional warning, optional collapse chevron with `expand`/`collapse` semantics. Replaces the private copies `ToolsContent` and `ModelsContent` had each grown, and serves the More tab's headings. Encodes two rules: the count describes the rows the section *contains*, and a collapsed section may not hide a problem.
    - `MemoryEntryRow.kt` - Knotwork memory-entry row.
    - `PipelineListRow.kt` - Knotwork pipeline-library list row.
    - `SwipeRevealRow.kt` - wraps a list row in a **single** 64 dp swipe-revealed action (`SwipeRevealAction`: glyph + spoken label + tint). `PipelineListRow`'s mechanics — `Animatable` offset driven by `Modifier.draggable`, clamped and snapped at half the reveal, partial reveal never a dismiss — with one action instead of three, so the strip claims 20 % of a 320 dp drawer instead of 60 %. Shared by the chat drawer row (Archive) and the archive row (Restore).
    - `ToolListRow.kt` - Knotwork tool / MCP-server list row.
  - `MarkdownTheme.kt` - typography + colour bindings for the multiplatform-markdown-renderer used in content surfaces.
  - `misc/` - `KnotworkLoader` / `KnotworkSnackbar` / `EmptyState` / `StripedPlaceholder` / `KnotworkSectionAction` (right-aligned action link) / `KnotworkStatCell` (counter grid cell) + previews.
    - `EmptyState.kt` - Default illustration slot size when the caller does not override the slot.
    - `KnotworkLoader.kt` - Knotwork brand loader — three pulsing dots in the accent ramp `Accent300 → Accent400 → Accent500` (no `Accent600`; the spec ramp ends at 500).
    - `KnotworkMiscPreview.kt` - Harness rendering the loose components — loader, snackbar, empty state, striped placeholder, section action, stat cell — for the preview pane and the Roborazzi baseline.
    - `KnotworkSectionAction.kt` - Right-aligned action link sitting next to a section header (e.g. "Reset to defaults", "Manage", "+ Add provider").
    - `KnotworkSnackbar.kt` - Knotwork snackbar — thin wrapper over Material3 `Snackbar` that recolours by `variant` and pushes the action label through `KnotworkTextStyles`.
    - `KnotworkStatCell.kt` - Single stat-grid cell used by the Settings → Memory card's 4-up counter row (CHUNKS / SIZE / THREADS / AVG SCORE).
    - `KnotworkWarningBanner.kt` - A persistent, warning-toned notice with one inline action.
    - `StripedPlaceholder.kt` - Knotwork striped placeholder — the canonical "missing asset" stand-in for any product surface that ships before its real illustration / hero image is available.
  - `pipelineeditor/` - pipeline-editor base components.
    - `EdgeLabel.kt` - floating edge-label chip (branch conditions True/False/Item/Done/Pass/Retry/Fail or intent class names).
    - `EditorToolbar.kt` - editor top toolbar (back, inline-editable name, subtitle, overflow). No run action: the editor composes pipelines, it does not execute them.
    - `NodeCard.kt` - unified node card covering idle / selected / multi-selected / error states with dynamic port dots and header tint.
    - `NodeConfig.kt` - sealed `NodeConfig` interface + 12 per-type payload data classes (`InputConfig`, `OutputConfig`, …).
    - `NodeConfigForms.kt` - per-type form bodies (12 variants) with shared helpers (`FieldLabel`, `InlineError`, `VariableChipsRow`).
    - `NodeConfigSheet.kt` - modal bottom sheet hosting the per-type forms with sticky Cancel/Save row and optional app-provided sections.
    - `NodeConfigValidation.kt` - pure-Kotlin validator for `NodeConfig` rules (title uniqueness, range bounds, JSON parsing) → field-keyed error map.
    - `NodeError.kt` - sealed `Validation` / `Runtime` error states surfaced on `NodeCard` borders and bodies.
    - `NodeIcons.kt` - `NodeType` → header glyph extension over `AppIcons`.
    - `NodePorts.kt` - `OutboundPort` sealed class + `NodePorts` descriptor of inbound/outbound port topology per node type.
    - `NodeType.kt` - enum of the 12 editor node types (Input, Output, LiteRT, Cloud, IntentRouter, IfCondition, Clarification, Tool, Decomposition, QueueProcessor, Evaluation, Summary).
    - `NodeTypeColors.kt` - composables mapping `NodeType` to header tint, luminance-banded foreground, and display label.
    - `PipelineEditorCatalogContent.kt` - scrollable harness exercising every pipeline-editor base component + theme previews.
  - `topbar/` - top-app-bar chrome.
    - `JournalExportActions.kt` - The pair of top-bar actions that hands a journal to the user: **share it** and **save it to a file**.
    - `KnotworkTopAppBarShell.kt` - wraps a `TopAppBar` in a column with an attached hairline divider so it never bleeds into scrolled content.
- `foundations/` - catalog pages.
  - `FoundationsCatalogPage.kt` - palette + type scale + spacing surface, used for design review and the snapshot baseline.
  - `FoundationsCatalogPagePreview.kt` - Android Studio `@Preview` for the page in both themes.
- `icons/` - brand + node icon catalogue.
  - `AppIcons.kt` - facade exposing every custom Knotwork `ImageVector` (brand mark, 12 node glyphs, auto-layout, brain).
  - `IconCatalogPage.kt` - scrollable preview of the full icon set (used for design review + Roborazzi baseline).
  - `IconCatalogPagePreview.kt` - Android Studio `@Preview` for the page in both themes.
  - `imagevector/` - one file per custom `ImageVector` (brand mark, wordmark, flow, 12 node glyphs, auto-layout, brain).
    - `Add.kt` - `I.add` glyph (add / FAB +) — single-stroke icon family.
    - `AlertCircle.kt` - `I.alertCircle` glyph (error/info in circle (≠ warn triangle)) — single-stroke icon family.
    - `Archive.kt` - `I.archive` glyph (archive) — single-stroke icon family.
    - `ArrowDown.kt` - `I.arrowDown` glyph (expand ▽) — single-stroke icon family.
    - `ArrowR.kt` - `I.arrowR` glyph (forward / expand →) — single-stroke icon family.
    - `ArrowUp.kt` - `I.arrowUp` glyph (collapse ▲ (chevron, pairs arrowDown)) — single-stroke icon family.
    - `ArrowUpLine.kt` - `I.arrowUpLine` glyph (up / scroll-to-top (straight)) — single-stroke icon family.
    - `AutoLayout.kt` - Editor toolbar "Auto layout" affordance — grid lines (decorative, dimmed) plus four anchor dots.
    - `Back.kt` - `I.back` glyph (back) — single-stroke icon family.
    - `Battery.kt` - `I.battery` glyph (battery alert) — single-stroke icon family.
    - `Block.kt` - `I.block` glyph (blocked) — single-stroke icon family.
    - `Bolt.kt` - `I.bolt` glyph (quick action / energy) — single-stroke icon family.
    - `Book.kt` - `I.book` glyph (documentation) — single-stroke icon family.
    - `Bookmark.kt` - `I.bookmark` glyph (saved / bookmark) — single-stroke icon family.
    - `BookmarkAdd.kt` - `I.bookmarkAdd` glyph (add to bookmarks) — single-stroke icon family.
    - `Brain.kt` - Memory-screen entry glyph (two-lobed brain).
    - `Branch.kt` - `I.branch` glyph (branch / IF — git-branch) — single-stroke icon family.
    - `Camera.kt` - `I.camera` glyph (camera capture source) — single-stroke icon family.
    - `Chat.kt` - `I.chat` glyph (chat tab), idle and active — single-stroke icon family.
    - `Check.kt` - `I.check` glyph (confirm / tick) — single-stroke icon family.
    - `CheckSquare.kt` - `I.checkSquare` glyph (select all / checkbox tick) — single-stroke icon family.
    - `Chip.kt` - `I.chip` glyph (on-device / NPU) — single-stroke icon family.
    - `Circle.kt` - `I.circle` glyph (empty state dot (radio unchecked)) — single-stroke icon family.
    - `Cloud.kt` - `I.cloud` glyph (cloud LLM) — single-stroke icon family.
    - `Cog.kt` - `I.cog` glyph (settings) — single-stroke icon family.
    - `Copy.kt` - `I.copy` glyph (copy) — single-stroke icon family.
    - `Db.kt` - `I.db` glyph (storage / DB) — single-stroke icon family.
    - `Dot.kt` - `I.dot` glyph (marker dot (solid)) — single-stroke icon family.
    - `Download.kt` - `I.download` glyph (download) — single-stroke icon family.
    - `Download2.kt` - `I.download2` glyph (import / export (alt)) — single-stroke icon family.
    - `Edit.kt` - `I.edit` glyph (edit / rename) — single-stroke icon family.
    - `Expand.kt` - `I.expand` glyph (fullscreen / open in full) — single-stroke icon family.
    - `ExportFile.kt` - `I.exportFile` glyph (export a file out of the workspace) — single-stroke icon family.
    - `Extension.kt` - `I.extension` glyph (plugin / extension) — single-stroke icon family.
    - `External.kt` - `I.external` glyph (open external link) — single-stroke icon family.
    - `Eye.kt` - `I.eye` glyph (show password) — single-stroke icon family.
    - `EyeOff.kt` - `I.eyeOff` glyph (hide password) — single-stroke icon family.
    - `File.kt` - `I.file` glyph (generic file) — single-stroke icon family.
    - `FileAudio.kt` - `I.fileAudio` glyph (an audio document — file outline + a mini waveform) — single-stroke icon family.
    - `FileBin.kt` - `I.fileBin` glyph (binary / non-previewable file) — single-stroke icon family.
    - `FileText.kt` - `I.fileText` glyph (text / previewable file) — single-stroke icon family.
    - `Filter.kt` - `I.filter` glyph (filter / facets) — single-stroke icon family.
    - `Flow.kt` - `I.flow` glyph (pipelines tab), idle and active — three connected nodes mirroring the editor canvas.
    - `Folder.kt` - `I.folder` glyph (folder / Files) — single-stroke icon family.
    - `FolderOpen.kt` - `I.folderOpen` glyph (open folder / empty workspace hero) — single-stroke icon family.
    - `Gauge.kt` - `I.gauge` glyph (a speedometer — a semicircular arc, a needle, and a filled hub) — single-stroke icon family.
    - `Globe.kt` - `I.globe` glyph (network / locale) — single-stroke icon family.
    - `Grid.kt` - `I.grid` glyph (snap grid on) — single-stroke icon family.
    - `GridOff.kt` - `I.gridOff` glyph (snap grid off) — single-stroke icon family.
    - `History.kt` - `I.history` glyph (history) — single-stroke icon family.
    - `Hourglass.kt` - `I.hourglass` glyph (pending / queued) — single-stroke icon family.
    - `Hub.kt` - `I.hub` glyph (hub / integrations) — single-stroke icon family.
    - `IconBuilders.kt` - Shared icon construction: the canonical 24×24 builder, the stroke constants and the circle-path helper every glyph in this package is drawn with.
    - `Image.kt` - `I.image` glyph (image attachment affordance / Photo-library row / thumbnail) — single-stroke icon family.
    - `ImageOff.kt` - `I.imageOff` glyph (broken / missing image — bubble + viewer fallback) — single-stroke icon family.
    - `ImportFile.kt` - `I.importFile` glyph (import a file into the workspace) — single-stroke icon family.
    - `Info.kt` - `I.info` glyph (info (i)) — single-stroke icon family.
    - `Key.kt` - `I.key` glyph (API key) — single-stroke icon family.
    - `Link.kt` - `I.link` glyph (link / connect) — single-stroke icon family.
    - `Lock.kt` - `I.lock` glyph (lock) — single-stroke icon family.
    - `Mark.kt` - Knotwork brand mark — the single canonical glyph: **two nodes joined by one edge** (input → output).
    - `Menu.kt` - `I.menu` glyph (hamburger / drawer) — single-stroke icon family.
    - `Mic.kt` - `I.mic` glyph (voice input) — single-stroke icon family.
    - `Minus.kt` - `I.minus` glyph (minus / zoom-out) — single-stroke icon family.
    - `MinusCircle.kt` - `I.minusCircle` glyph (remove item) — single-stroke icon family.
    - `Monitor.kt` - `I.monitor` glyph (monitoring / metrics) — single-stroke icon family.
    - `More.kt` - `I.more` glyph (overflow ⋮ (solid)) — single-stroke icon family.
    - `More2.kt` - `I.more2` glyph (horizontal overflow), idle and active — single-stroke icon family.
    - `NodeBranch.kt` - Pipeline `IF_CONDITION` node glyph — diamond with a check mark.
    - `NodeClarify.kt` - Pipeline `CLARIFICATION` node glyph — chat bubble with question mark.
    - `NodeCloud.kt` - Pipeline `CLOUD` node glyph — cloud shape with up-arrow.
    - `NodeDecompose.kt` - Pipeline `DECOMPOSITION` node glyph — one-to-many fan from a single dot to three small bins.
    - `NodeEval.kt` - Pipeline `EVALUATION` node glyph — balance scale.
    - `NodeInput.kt` - Pipeline `INPUT` node glyph — inbound arrow into a rounded square.
    - `NodeIntentRouter.kt` - Pipeline `INTENT_ROUTER` node glyph — input dot fanning out to three target circles.
    - `NodeLite.kt` - Pipeline `LITE_RT` node glyph — silicon chip with a lightning spark indicating local on-device inference.
    - `NodeOutput.kt` - Pipeline `OUTPUT` node glyph — outbound arrow from a rounded square.
    - `NodePipeline.kt` - Pipeline `PIPELINE` node glyph — a two-node sub-flow framed in a box ("a pipeline nested inside a node").
    - `NodeQueue.kt` - Pipeline `QUEUE_PROCESSOR` node glyph — stacked items plus a return-loop arrow.
    - `NodeSummary.kt` - Pipeline `SUMMARY` node glyph — document with bullet lines.
    - `NodeTool.kt` - Pipeline `TOOL` node glyph — wrench inside a hex frame.
    - `Paste.kt` - `I.paste` glyph (paste (clipboard)) — single-stroke icon family.
    - `Pause.kt` - `I.pause` glyph (pause (solid)) — single-stroke icon family.
    - `Pin.kt` - `I.pin` glyph (pin / keep), outline and filled — single-stroke icon family.
    - `Play.kt` - `I.play` glyph (run (solid)) — single-stroke icon family.
    - `Ram.kt` - `I.ram` glyph (memory / RAM (settings)) — single-stroke icon family.
    - `Redo.kt` - `I.redo` glyph (redo) — single-stroke icon family.
    - `Refresh.kt` - `I.refresh` glyph (retry / refresh) — single-stroke icon family.
    - `Save.kt` - `I.save` glyph (save) — single-stroke icon family.
    - `Search.kt` - `I.search` glyph (search) — single-stroke icon family.
    - `Send.kt` - `I.send` glyph (send message) — single-stroke icon family.
    - `Share.kt` - `I.share` glyph (share sheet) — single-stroke icon family.
    - `Shield.kt` - `I.shield` glyph (privacy / security) — single-stroke icon family.
    - `Skill.kt` - `I.skill` glyph — a star framed in a rounded square ("a packaged, reusable capability").
    - `Sliders.kt` - `I.sliders` glyph (tune (≠ filter funnel)) — single-stroke icon family.
    - `Spark.kt` - `I.spark` glyph (auto / AI action) — single-stroke icon family.
    - `Star.kt` - `I.star` glyph (favorite / pin-list) — single-stroke icon family.
    - `Stop.kt` - `I.stop` glyph (stop run (solid square)) — single-stroke icon family.
    - `Terminal.kt` - `I.terminal` glyph (console >_) — single-stroke icon family.
    - `Theme.kt` - `I.theme` glyph (theme toggle (half-filled circle)) — single-stroke icon family.
    - `Tool.kt` - `I.tool` glyph (tools tab), idle and active — single-stroke icon family.
    - `Trash.kt` - `I.trash` glyph (delete) — single-stroke icon family.
    - `Trigger.kt` - `I.trigger` glyph — a bolt enclosed in a rounded-square frame ("an automatic, event-driven run").
    - `Unarchive.kt` - `I.unarchive` glyph (restore out of the archive) — single-stroke icon family.
    - `Undo.kt` - `I.undo` glyph (undo) — single-stroke icon family.
    - `Warn.kt` - `I.warn` glyph (warning) — single-stroke icon family.
    - `Wordmark.kt` - Knotwork wordmark glyph — 24×24 mark used by `AppIcons.Wordmark`.
    - `X.kt` - `I.x` glyph (close / clear) — single-stroke icon family.
- `screens/` - screen-level catalog surfaces (`*Content` composables + `*ViewState` render contracts) consumed by `:app`'s slim mapper screens.
  - `about/` - About page — content + view-state.
    - `AboutContent.kt` - hero brand mark + version / license / acknowledgments / privacy cards in a scrollable `LazyColumn`.
    - `AboutViewState.kt` - app name / version / build / commit SHA / license / acknowledgments / privacy render contract.
  - `automation/` - External-automation pages — the consent dialog and the request journal. Triggers live in `triggers/`.
    - `ExternalAutomationConsentDialog.kt` - `ExternalAutomationConsentContent`, the stateless card body of the consent moment raised when the master switch is turned on (the host wraps it in a `Dialog`), plus its `ExternalAutomationConsentStrings` copy contract. The body scrolls under a height ceiling so the two buttons stay reachable at font-scale 200 %.
    - `ExternalAutomationJournalContent.kt` - external-automation request journal: posture banner (off / on-but-unbound / accepting), the collapsible wire-contract block, and a per-day timeline of inbound requests with a status tile, the refusal reason as a sentence, the claimed-versus-attested sender line and a repeat-count badge.
    - `ExternalAutomationJournalViewState.kt` - render contract: the catalog mirrors of the domain status (5) and rejection-reason (12) dictionaries, the sender-attestation kind, the per-day groups, the wire-key rows, and the whole localisable copy surface.
    - `ExternalAutomationPreview.kt` - deterministic fixtures for the journal states, including the caller looping against a switched-off contract and the request that tried to redirect its answer at a third package.
  - `chat/` - Chat home page, its navigation drawer and their fixtures. The archive is `chatarchive/`; the composer and console atoms live in `components/`.
    - `ChatHomeContent.kt` - chat surface: message history, composer, console pane, and HITL / clarification / error overlays.
    - `ChatHomeContentPreview.kt` - Android Studio `@Preview` group for the chat variants in both themes.
    - `ChatHomeDrawer.kt` - the alternate-nav drawer overlay extracted from `ChatHomeContent.kt`: sessions list, `+ New chat` pill, the thread row (`⋮` menu = Rename / Archive / — / Delete chat, plus a one-action Archive swipe and TalkBack custom actions; drops its status dot at font-scale 200 %) and the footer rows (`Archived chats` only when the count is positive, `Import chat`, `Settings`).
    - `ChatHomePreviewData.kt` - deterministic preview fixtures for the chat surface (thread, model, message rows, HITL / clarification cards).
    - `ChatHomeViewState.kt` - visual-state enum (Loading / Empty / Idle / Generating / HitlConfirm / Clarification / Error / DrawerOpen / ConsoleExpanded) + message / thread row models.
  - `chatarchive/` - archived-chats surface (the chats a user took out of the drawer without deleting them).
    - `ChatArchiveContent.kt` - list of archived chats, most-recently- archived first: leading archive tile, relative archived-at label, an optional "run finished after archiving" note, inline Restore pill, a one-action Restore swipe and a row overflow (Restore / Export chat / ─ / Delete forever behind a plain confirmation). Teaching empty state with no CTA, skeleton and error branches. At font-scale 200 % the tile is dropped and Restore collapses to an icon-only 48 dp button with the word kept for TalkBack.
    - `ChatArchiveContentPreview.kt` - Android Studio `@Preview` group.
    - `ChatArchivePreview.kt` - deterministic fixtures (long title, starred row, a chat whose run settled after archiving).
    - `ChatArchiveViewState.kt` - visual-state enum (Loading / Default / Empty / Error), `ChatArchiveRowUi`, the localised `ChatArchiveStrings` bundle and the callbacks. `revealedRowId` is a preview/snapshot control only — production leaves each row's swipe under the finger.
  - `discover/` - model discovery over the curated Hugging Face organisation (list → detail → install).
    - `DiscoverContent.kt` - list surface: sticky search field (submits on the IME action), pull-to-refresh repository list with a host-formatted stats line, optional "N files" hint and a gated lock badge, plus the loading / empty / error branches.
    - `DiscoverDetailContent.kt` - detail surface for one repository: header, access-gated notice with an inline token field, the installable `.litertlm` file list, a "View on Hugging Face" link, and the license-confirmation dialog that precedes any download.
    - `DiscoverDetailViewState.kt` - detail visual-state enum, sealed `DiscoverFileStatus` per-file state, `DiscoverFileRow`, and the detail callback bundle.
    - `DiscoverPreview.kt` - deterministic fixtures shared by both surfaces' `@Preview`s and the Roborazzi matrix; `internal`, so `:app` cannot reach them.
    - `DiscoverViewState.kt` - `DiscoverVisualState` (Loading / Populated / Empty / Error) + `DiscoverModelRow`. Stats arrive pre-formatted and the file count stays a raw `Int`, so the catalog neither formats numbers nor resolves plurals.
  - `files/` - the workspace-sandbox browser.
    - `FilesContent.kt` - Files surface: quota header, file list, multi-select bar, detail bottom sheet, and the empty / error states. Deliberately reuses the `MemoryContent` vocabulary rather than introducing new components; all I/O and SAF launching stay in the host.
    - `FilesDialogs.kt` - the screen's two confirmations, split out to keep the composable count per file manageable: the destructive delete (single or bulk) and the import name-collision chooser (keep both / replace / cancel).
    - `FilesViewState.kt` - `FilesVisualState` body state plus the separate overlay/selection fields (preview sheet, dialogs), file kind / quota tone enums, and the row / quota / preview / dialog models, so opening a preview never changes the body branch.
  - `memory/` - Long-term-memory pages.
    - `MemoryContent.kt` - Memory Manager surface (stats header, category chips, semantic search, provenance breakdown, entry cards).
    - `MemoryType.kt` - per-element typography overrides transcribed from the Memory Manager design spec.
    - `MemoryViewState.kt` - visual-state enum + sort/filter enums and segment / stat / category / row models.
  - `models/` - Local-models page and the performance card. Model discovery is `discover/`.
    - `ModelsContent.kt` - Models surface (active model card, Hugging Face auth, preset rows with download progress).
    - `ModelsViewState.kt` - visual-state enum + active-card / preset-row models with download status.
    - `PerformanceCard.kt` - the Performance card below the active-model card: rolling-average TTFT / decode speed / peak memory for the active model, and the controlled benchmark with its segmented progress track.
    - `PerformanceCardState.kt` - sealed card state (no active model / idle with figures / running) + `BenchmarkPhase`, strings and callbacks. Every number arrives **pre-formatted** (`"420 ms"`, `"12.4 tok/s"`, `"1.8 GB"`) — units and localisation live in the host.
  - `monitoring/` - Observability pages.
    - `MonitoringContent.kt` - Monitoring surface (metrics grid, per-node-type breakdown, system log lines).
    - `MonitoringViewState.kt` - visual-state enum + stats / breakdown / log-line models.
  - `more/` - More-tab page.
    - `MoreContent.kt` - navigation-row list (via `KnotworkNavListRow`) with optional badges and a footer network-status pill.
    - `MoreViewState.kt` - ordered navigation rows + network-status render contract.
  - `onboarding/` - Onboarding pages.
    - `OnboardingContent.kt` - four-step pager (Welcome / LiteRtModel / CloudKeys / Ready) with progress bar and per-step CTAs.
    - `OnboardingViewState.kt` - step enum + model options (Gemma4E2B / Gemma4E4B / CustomUrl).
  - `pipelines/` - Pipeline-library page, its view-state and preview fixtures.
    - `PipelineLibraryContent.kt` - pipeline list with sort/filter, per-row overflow, default/active badges, swipe-reveal.
    - `PipelineLibraryPreview.kt` - deterministic preview fixtures (4 sample pipelines) for snapshots.
    - `PipelineLibraryViewState.kt` - visual-state enum + filter enum + pipeline-row model.
    - `PresetManagerContent.kt` - The preset manager: bundled and saved presets, filtered by category, each with rename / export / delete.
    - `PresetManagerPreview.kt` - Fixtures for the preset manager's states.
    - `PresetManagerViewState.kt` - Everything the preset manager renders, already resolved.
  - `prompts/` - Prompt-library page and the prompt-preset picker sheet.
    - `PromptLibraryContent.kt` - prompt library (tabbed categories, card list, FAB, optional edit-sheet overlay, snackbar host slot). The top bar carries the **import** action; each card's footer line carries `used by N` plus Preview · Duplicate · Edit · overflow (Export + Delete), or Preview · Duplicate · Export on a read-only row. Two layout rules are load-bearing: the caption yields and the icon cluster never does, so the overflow — and with it the only route to Delete — survives font scale 200 %; and the top bar drops its subtitle rather than clipping its title at the same scale. An empty **library** (as opposed to an empty category) hides the tabs and the FAB and offers Import / New instead.
    - `PromptLibraryViewState.kt` - visual-state enum + prompt-row / editor state + category / variable tracking + the import/export callbacks.
    - `PromptPresetPickerSheet.kt` - modal preset picker by `NodeType` (Bundled / Mine tabs, searchable rows, tag filter).
  - `settings/` - the settings hub plus one content composable per category sub-screen (the flat single-screen `SettingsContent` was split into this stack).
    - `AboutSettingsContent.kt` - identity card, build info, licenses and the reset actions.
    - `BackgroundSettingsContent.kt` - background work, triggers, notifications and entry-surface bindings.
    - `GenerationSettingsContent.kt` - system instructions, restrictions and the LLM generation parameters.
    - `KnotworkMonoTextArea.kt` - multi-line monospace textarea (brand outline) for system instructions.
    - `KnotworkParamSlider.kt` - labelled numeric-parameter slider with value label, optional validation error, and (inside a settings screen) its own help glyph and explanation panel under the track.
    - `KnotworkProviderRow.kt` - cloud-provider row with optional Ollama-specific fields (base URL, model) and validation.
    - `MemorySettingsContent.kt` - long-term-memory controls (extraction, retrieval thresholds, re-embed).
    - `ModelsSettingsContent.kt` - local model + inference backend and the cloud-provider list.
    - `PipelinesSettingsContent.kt` - pipeline / structured-output controls. Basic tier is the **Run limits** entry row, carrying the current step and token limits as its subtitle.
    - `PrivacySettingsContent.kt` - privacy, retention and telemetry controls (links out to the usage-statistics surface).
    - `ProviderDetailContent.kt` - Everything the provider detail screen renders, with every string already resolved by `:app`.
    - `ProviderDetailViewState.kt` - Everything the provider detail surface needs, already resolved.
    - `ProviderPickerContent.kt` - The list of cloud providers a user can configure.
    - `ProviderPickerViewState.kt` - The provider picker's contents, already resolved.
    - `RunLimitsContent.kt` - the run-limits screen: four ceilings plus the spend statement. Carries two components of its own. `LimitSliderRow` is a slider with a description and an optional state qualifier, laid out in a `FlowRow` rather than a `Row` — at a 200 % font scale a title and a trailing chip cannot share one line, and a `Row` resolves that by clipping the chip off the screen. `StatementRow` is an axis the product *states* rather than controls, deliberately not a disabled slider: a disabled control implies something could enable it, and nothing will.
    - `RunLimitsViewState.kt` - `RunLimitsViewState`, `LimitSliderRowState`, `StatementRowState` and `RunLimitsCallbacks`. Every axis has a *move* and a *commit*, because writing a background limit is what stops it following the interactive one.
    - `SettingsCallbacks.kt` - the single typed callback bundle shared by the hub and every sub-screen.
    - `SettingsCommon.kt` - the shared building blocks of the stack (category scaffold, section labels, advanced disclosure, toggle / nav / provider rows).
    - `SettingsHint.kt` - the settings help affordance: `KnotworkHelpEntry` (18 dp glyph after a row label, 48 dp touch bounds, TalkBack action + state), `KnotworkHintPanel` (tinted in-place panel, owning its motion and its reduced-motion branch), and `SettingsHintController` / `LocalSettingsHints` / `LocalSettingsRowAnchor`, which enforce one explanation open at a time and bind each hint to its own row.
    - `SettingsHubContent.kt` - category hub: search field with live result rows, category cards, and the entry points into every sub-screen below.
    - `SettingsModels.kt` - shared settings models and enums (category ids, hub search rows, slider rows, identity / system-instruction card states).
    - `SettingsRowAnchors.kt` - stable deep-link anchor constants mirroring the app-side `SettingsRegistry` (app-side test asserts the subset relation, so a registry rename can't orphan a highlight).
    - `SettingsViewStates.kt` - per-screen view states (hub, generation, models, memory, pipelines, tools, …).
    - `ToolsSettingsContent.kt` - tool-calling and approval controls.
    - `UsageTelemetryContent.kt` - on-device usage-statistics surface (recording toggle, run / pipeline / trigger / active-day stat sections, the **Setup** install-to-first-value section, export and reset actions) plus its own `UsageTelemetryViewState` / `UsageTelemetryCallbacks` / `UsageStatRow` models and empty state.
  - `skills/` - reusable-skill surfaces (a skill = instruction + tool allowlist + context flags).
    - `SkillDeleteDialog.kt` - delete-confirmation body (the host supplies the dialog container) with three branches keyed off the dependent pipelines: none, one, or an N-dependent "will break" list.
    - `SkillEditorContent.kt` - full-screen create-or-edit surface for a skill's name, description, instruction, tri-state tool allowlist and context flags.
    - `SkillLibraryContent.kt` - library list: Bundled / Mine tabs, per-row overflow actions and a "New skill" FAB, over the loader / list / empty / error branches.
    - `SkillLibraryPreview.kt` - canonical sample states shared by the `@Preview`s and the Roborazzi baselines, so the two cannot drift.
    - `SkillLibraryViewState.kt` - visual-state and tab enums, the tool-indicator / tool-mode enums behind the tri-state allowlist, and the row / tool-option / context-flag / editor / delete models.
  - `splash/` - Splash page — content + view-state.
    - `SplashContent.kt` - splash surface (brand logo, app name, determinate progress or error + Retry CTA).
    - `SplashViewState.kt` - sealed Initializing / Loading / Error cold-start state.
  - `taskmonitor/` - Task-monitor page.
    - `TaskMonitorContent.kt` - task list (filter row, task cards, expandable detail sheet with logs + actions), plus the top-bar "stop all scheduled tasks" action and its confirmation. The action appears only when something is actually stoppable; the confirm is a plain dialog rather than the typed-keyword one, since this is recoverable and is itself a recovery action.
    - `TaskMonitorViewState.kt` - visual-state enum + filter enum + task row / detail / status-lifecycle models, plus the scheduled-task count and bulk-cancel confirmation flag.
  - `tools/` - Tools page and the `http_request` domain-allowlist editor.
    - `AllowedDomainsContent.kt` - pushed editor for the `http_request` host allowlist — the gesture that opts the device into outbound HTTP. Empty state (globe hero, the tool-is-off explanation, an amber risk note) and populated state (explainer + host list with per-row removal), mirroring the MCP-server editor's structure.
    - `AllowedDomainsViewState.kt` - screen state plus the sealed `AddHostState` feedback for the add-a-host field (`Idle` / `NormalizedPreview` / `Duplicate` / `Invalid`); only `NormalizedPreview` enables **Add**. Normalisation itself is the host's (`HttpRequestPolicy`) job.
    - `ToolsContent.kt` - Tools surface (built-in AppFunctions section + MCP servers with expandable tool lists and connection states).
    - `ToolsViewState.kt` - visual-state enum + risk-tier / MCP connection enums + tool / server row models.
  - `triggers/` - automation-trigger surfaces (`TriggersContent` list with inline enable switch + health badge, `TriggerEditorContent` full-screen editor, `TriggerDeleteDialogContent`, and the detail below).
    - `TriggerDeleteDialog.kt` - `TriggerDeleteDialogContent`, the destructive delete-confirmation body (the host owns the dialog container).
    - `TriggerDetailContent.kt` - trigger-detail surface: identity header (When / Runs / State + enable switch), Edit / Delete, an optional overdue stale banner, and the **evaluation journal** timeline (per-day groups; each entry = verdict tile + source + timestamp, plus a settled outcome line for fired rows or a human skip / re-arm sentence, and a third HITL line when the run stopped to ask the user — state plus a "from the notification" qualifier when it had to park); loading / empty / populated states, reduced-motion-aware pending dot.
    - `TriggerDetailViewState.kt` - detail view-state + the journal vocabulary mirrors (`TriggerHealthUi`, source / verdict / skip-reason / outcome / `TriggerJournalHitlUi` enums, entry / day-group models, `TriggerJournalVisualState`), `TriggerDetailStrings` (final English copy defaults), callbacks.
    - `TriggerEditorContent.kt` - Stateless full-screen Trigger editor — create or edit a trigger's name, condition (type + parameters), bound pipeline, input prompt and enabled flag.
    - `TriggersContent.kt` - Stateless Knotwork Triggers surface — a list of automation rules (condition → bound pipeline) with an inline enable switch and per-row overflow actions, a "New trigger" FAB, and a bespoke teaching empty state.
    - `TriggersPreview.kt` - deterministic fixtures behind the triggers `@Preview`s and the Roborazzi baselines (list, editor, detail).
    - `TriggersViewState.kt` - list view-state (`TriggerRowUi` incl. the optional `health` badge, editor / delete models, strings, callbacks).
- `theme/` - root theme.
  - `KnotworkTheme.kt` - `@Composable fun KnotworkTheme(...)` wires Knotwork tokens into `MaterialTheme` and installs the extended / spacing / shape / elevation / motion / a11y composition locals; sibling `object KnotworkTheme` exposes them via `KnotworkTheme.extended` / `.spacing` / `.shapes` / `.elevation` / `.motion` / `.a11y`.
- `tokens/` - design tokens.
  - `Color.kt` - `KnotworkPalette`, `KnotworkLight`, `KnotworkDark`, plus `knotworkLightColorScheme()` / `knotworkDarkColorScheme()` Material3 mappings.
  - `Elevation.kt` - `KnotworkElevation` levels + `LocalKnotworkElevation`.
  - `ExtendedColors.kt` - `KnotworkExtendedColors` data class (chat surfaces, console, risk pills, 12 node hues) and the `LocalKnotworkExtendedColors` composition local provider.
  - `KnotworkIconSizes.kt` - icon-size tokens (AppBar / Nav / Inline / Fab / Micro) for consistent glyph render targets across surfaces.
  - `Motion.kt` - `KnotworkMotion` durations / easings + `LocalKnotworkMotion`.
  - `Shape.kt` - `KnotworkShapes` corner radii + `MaterialKnotworkShapes` M3 mapping + `LocalKnotworkShapes`.
  - `Spacing.kt` - `KnotworkSpacing` 4 dp grid + `LocalKnotworkSpacing`.
  - `Type.kt` - `KnotworkFonts.install(...)` font-family registry, `KnotworkTextStyles` raw scale and `knotworkTypography()` Material3 mapping.
<!-- /AUTO-GEN:FILE_MAP -->

## Tests and snapshot baselines

The test tree is summarised by area rather than file by file, and is not
generated: much of it is `.png` baselines, which no Kotlin source map covers.

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
- `src/test/java/app/knotwork/design/components/console/ConsoleEntryStripAffordanceTest.kt`
  — the strip says its own name, and its description speaks the live
  status rather than only the verb.
- `src/test/java/app/knotwork/design/components/console/ConsoleEntryStripSnapshotTest.kt`
  — `chat_console_strip_*` baselines: every status state, the open form,
  and font-scale 200 %. No chat fixture set `agentStatusLine` before, so
  the strip had no visual coverage at all.
- `src/test/java/app/knotwork/design/screens/tools/ToolsGroupAffordanceTest.kt`
  — one door to adding a server, a collapsed group that still reports a
  disconnected one, an empty group that does not collapse. Observed
  failing on both mutations.
- `src/test/java/app/knotwork/design/screens/tools/ToolsGroupsSnapshotTest.kt`
  — `tools_group*` baselines, reached by tapping the headers rather than
  by handing the surface a pre-folded state.
- `src/test/java/app/knotwork/design/screens/more/MoreSectionsTest.kt`
  — Triggers first, App last, no row lost in the regrouping, Tasks the
  only badge.
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
- `src/test/java/app/knotwork/design/components/chat/ChatMessageContextMenuTest.kt`
  — pins the long-press menu roster: no `Rate` row (removed as inert),
  surviving rows still dispatch.
- `src/test/java/app/knotwork/design/components/console/ConsoleCatalogPageSnapshotTest.kt`
  — Roborazzi baseline for `ConsoleCatalogContent` (light / dark).
- `src/test/java/app/knotwork/design/components/console/ConsoleFilterTest.kt`
  — pure-JVM coverage of `ConsoleFilter.matches` + `allOn`.
- `src/test/java/app/knotwork/design/components/console/ConsoleSnapTest.kt`
  — pure-JVM lock-down of the three snap-point heights.
- `src/test/java/app/knotwork/design/components/console/ConsoleSearchAffordanceTest.kt`
  — pins Search to the Logs tab (the only tab with a search field);
  Copy-all / Clear stay on every tab.
- `src/test/java/app/knotwork/design/icons/AppIconsTest.kt`
- `src/test/java/app/knotwork/design/icons/IconCatalogPageSnapshotTest.kt`
- `src/test/java/app/knotwork/design/components/lists/ConnectionStatusTest.kt`
  — pure-JVM coverage of the connection-status → pill mapping.
- `src/test/java/app/knotwork/design/components/pipelineeditor/NodePortsTest.kt`
  — pure-JVM check of the per-type port topology.
- `src/test/java/app/knotwork/design/components/pipelineeditor/NodeConfigValidationTest.kt`
  — pure-JVM coverage of the `NodeConfig` validation rules.
- `src/test/java/app/knotwork/design/components/pipelineeditor/NodeConfigSheetSnapshotTest.kt`
  — Roborazzi baselines for the node configuration sheets, which had none
  until the sheets were pruned. Covers the seven types whose fields changed,
  not all fourteen: a frame nobody has inspected is not coverage.
- `src/test/java/app/knotwork/design/components/pipelineeditor/PipelineEditorCatalogPageSnapshotTest.kt`
  — Roborazzi baseline for the pipeline-editor catalog page.
- `src/test/java/app/knotwork/design/components/pipelineeditor/HeroSnapshotTest.kt`
  — Roborazzi hero baseline for the pipeline editor.
- `src/test/java/app/knotwork/design/components/pipelineeditor/EditorToolbarTest.kt`
  — pins the toolbar's action roster: no run affordance (the editor
  composes pipelines, it does not execute them); back / overflow work.
- `src/test/java/app/knotwork/design/tokens/WcagContrastTest.kt`
  — pure-JVM WCAG contrast-ratio checks over the palette.
- `src/test/java/app/knotwork/design/a11y/A11yMatrixSnapshotTest.kt`
  — Roborazzi font-scale × theme accessibility matrix.
- `src/test/java/app/knotwork/design/a11y/TalkBackHappyPathsTest.kt`
  — Robolectric TalkBack semantics happy-path coverage.
- `src/test/java/app/knotwork/design/screens/` — per-screen Roborazzi
  snapshot baselines (`*ContentSnapshotTest`, light / dark / a11y
  font-scale variants), `*AccessibilityTest` semantics checks,
  `memory/MemoryAffordanceTest.kt` (which Memory states may offer a
  search action), and per-screen `HeroSnapshotTest` README heroes,
  covering about /
  automation / chat / memory / models / monitoring / more / onboarding /
  pipelines / prompts / settings / splash / taskmonitor / tools.
- `src/test/snapshots/` — committed Roborazzi baselines: one `*.png`
  per catalog page / component group / screen state, each in light and
  dark (plus reduced-motion and font-scale variants where exercised by
  the matching snapshot test above).
