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
- `src/main/java/app/knotwork/design/tokens/` — design tokens
  (Phase 21 / Task 2/11).
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
    spacing / shape / elevation / motion composition locals; sibling
    `object KnotworkTheme` exposes them via
    `KnotworkTheme.extended` / `.spacing` / `.shapes` / `.elevation` /
    `.motion`.
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
- `src/test/snapshots/` — committed Roborazzi baselines.
  - `foundations_light.png`, `foundations_dark.png`.
