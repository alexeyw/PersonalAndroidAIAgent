# Static Analysis (Phase 18 — Task 1/10)

This document describes the static-analysis tooling wired into the project as
of Phase 18, Task 1/10. **All tools are in report-only mode**: they generate
reports, but their findings do **not** fail the build. Strict enforcement
(failing CI on violations) is enabled by Task 9/10.

The reports below serve as the input checklist for Tasks 3–8:

| Task | Driven by                                                                |
|------|--------------------------------------------------------------------------|
| 3/10 | Hardcoded strings (not surfaced by detekt directly, but `MaxLineLength` hits often correlate) |
| 4/10 | Inline LLM prompts (manual grep, complemented by `MaxLineLength`)        |
| 5/10 | Magic identifier strings (manual grep)                                   |
| 6/10 | `MagicNumber` rule in detekt                                             |
| 7/10 | `WildcardImport`, `UnusedImport`, import ordering — both ktlint & detekt |
| 8/10 | `UnusedPrivate*` rules + manual `TODO/FIXME` grep                        |

---

## Detekt

Plugin: `dev.detekt` version `2.0.0-alpha.3`. This is the alpha release
because detekt 1.23.x is incompatible with Kotlin 2.3.21 / AGP 9.x used by
this project. The plugin id `io.gitlab.arturbosch.detekt` was renamed to
`dev.detekt` in the 2.0 line.

Configuration lives in [`config/detekt/detekt.yml`](../config/detekt/detekt.yml).
It layers on top of detekt's bundled defaults via `buildUponDefaultConfig =
true`, so unspecified rules keep their default thresholds.

Enabled / tuned rules (see brief for rationale):
- `style.MagicNumber` — ignores `-1, 0, 1, 2`, plus enums / annotations /
  named arguments / constants / ranges.
- `style.MaxLineLength` — 120, excludes package & import statements.
- `style.UnusedImport`.
- `complexity.LongMethod` — `allowedLines: 60`.
- `complexity.ComplexCondition` — `allowedConditions: 4`.
- `complexity.TooManyFunctions` — `allowedFunctionsPer*: 11`.

**Run:**
```bash
./gradlew :app:detekt
```

**Reports:**
- HTML: `app/build/reports/detekt/detekt.html` — primary visual checklist.
- Checkstyle XML: `app/build/reports/detekt/detekt.xml` — for IDE / CI parsers.

The Gradle task is configured with `ignoreFailures = true`, so the build
exits `0` even when violations are present.

---

## ktlint

Plugin: `org.jlleitschuh.gradle.ktlint` version `14.2.0`, bundled ktlint
engine pinned to `1.5.0`. Rule overrides live in
[`.editorconfig`](../.editorconfig) — Kotlin coding-style, max line 120,
trailing commas on, import layout per IntelliJ.

**Run:**
```bash
./gradlew :app:ktlintCheck         # report
./gradlew :app:ktlintFormat        # auto-fix the safely-fixable subset
```

**Reports:**
- `app/build/reports/ktlint/ktlintMainSourceSetCheck/ktlintMainSourceSetCheck.html`
- `app/build/reports/ktlint/ktlintMainSourceSetCheck/ktlintMainSourceSetCheck.txt`
- Equivalent subdirectories exist for `Test`, `AndroidTest`, and
  `KotlinScript` source sets.

The plugin is configured with `ignoreFailures.set(true)`.

---

## Android Lint

Provided by AGP 9.2.1, configured in `app/build.gradle.kts`:

```kotlin
android {
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
        warningsAsErrors = false
    }
}
```

The baseline file [`app/lint-baseline.xml`](../app/lint-baseline.xml)
captures the existing 14 warnings/hints reported by AGP 9.2.1 against the
codebase at the start of Phase 18. Only **newly introduced** issues will be
surfaced by future runs.

**Run:**
```bash
./gradlew :app:lintDebug                 # debug variant report
./gradlew :app:updateLintBaseline        # regenerate the baseline (do this only
                                         # after deliberately addressing a batch
                                         # of issues; commit the new baseline)
```

**Reports:**
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/lint-results-debug.xml`

---

## What this phase does **not** do

- It does **not** wire `./gradlew check` as a mandatory step in `CLAUDE.md`
  Step 3 — that is Task 9/10's job.
- It does **not** make CI fail on static-analysis findings — also Task 9/10.
- It does **not** measure test coverage — that is Task 2/10 (Kover).
- It does **not** rewrite the violations themselves — Tasks 3–8 do.

If you see a violation in any of the reports, it is an entry on the
checklist for one of Tasks 3–8.
