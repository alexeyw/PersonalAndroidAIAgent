# Directory Map: `:catalog` (Knotwork design system)

This file maps the contents of the `:catalog` Android library module.

The module hosts the Knotwork design system: tokens, atomic components,
and screen-level catalog pages. It depends on nothing else in this
project — `:app` consumes it as an `implementation` dependency.

- `build.gradle.kts` — Android library build script (Compose, ktlint,
  detekt).
- `consumer-rules.pro` — ProGuard rules contributed to `:app`; empty in
  Phase 21 / Task 1/11.
- `src/main/AndroidManifest.xml` — minimal library manifest (no
  `<application>` element).
- `src/main/java/app/knotwork/design/theme/` — root theme.
  - `KnotworkTheme.kt` — Phase 21 / Task 1/11 scaffold: pass-through to
    `MaterialTheme`. Token-driven palettes land in Task 2/11.
  - `KnotworkThemePreview.kt` — Android Studio `@Preview` smoke (light +
    dark) for the scaffolded theme.
