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
  (Phase 21 / Task 5/11).
  - `ComponentsCatalogPage.kt` — single-scroll page composing every
    component category (Buttons, Chips & pills, List rows, Misc, Chat,
    Console).
  - `ComponentsCatalogPagePreview.kt` — Android Studio `@Preview`
    wrappers for the page in both themes.
  - `buttons/` — `KnotworkPrimaryButton` / `KnotworkSecondaryButton` /
    `KnotworkTextButton` / `KnotworkIconButton` + previews.
  - `chips/` — `KnotworkChip` / `RiskPill` / `StatusPill` + previews.
  - `lists/` — `PipelineListRow` / `ToolListRow` / `MemoryEntryRow` +
    previews.
  - `misc/` — `KnotworkLoader` / `KnotworkSnackbar` / `EmptyState` /
    `StripedPlaceholder` + previews.
  - `chat/` — chat-surface components (Phase 21 / Task 6/11).
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
  - `console/` — agent console components (Phase 21 / Task 6/11).
    - `ConsoleModels.kt` — `ConsoleSnap`, `ConsoleTab`, `ConsoleSource`,
      `ConsoleLevel`, `ConsoleFilter`, `ConsoleLine`, `ConsoleVarRow`,
      `ConsoleTraceSpan`, `SpanStatus`.
    - `ConsolePane.kt` — bottom-sheet container with sticky header,
      three tabs, source-filter chips, and per-tab body.
    - `ConsoleCatalogContent.kt` — single-column harness covering each
      snap × tab combination + theme previews.
- `src/main/java/app/knotwork/design/foundations/` — catalog pages.
  - `FoundationsCatalogPage.kt` — palette + type scale + spacing
    surface, used for design review and the snapshot baseline.
  - `FoundationsCatalogPagePreview.kt` — Android Studio `@Preview` for
    the page in both themes.
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
- `src/test/snapshots/` — committed Roborazzi baselines.
  - `foundations_light.png`, `foundations_dark.png`,
    `icon_catalog_light.png`, `icon_catalog_dark.png`,
    `buttons_light.png`, `buttons_dark.png`,
    `chips_light.png`, `chips_dark.png`,
    `lists_light.png`, `lists_dark.png`,
    `misc_light.png`, `misc_dark.png`,
    `components_light.png`, `components_dark.png`,
    `chat_light.png`, `chat_dark.png`, `chat_reduced_motion.png`,
    `console_light.png`, `console_dark.png`.
