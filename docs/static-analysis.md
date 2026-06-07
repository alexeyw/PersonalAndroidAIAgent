# Static Analysis & Coverage — Permanent Rules

This document is the source of truth for the project's quality gates. Every
PR must pass `./gradlew check`, which is also wired as the required CI job
on `pull_request → main`. Failing any sub-task blocks the merge.

> Test-coverage measurement and thresholds live alongside the rules here;
> the per-package baseline numbers used to seed the thresholds are kept in
> [`coverage-baseline.md`](coverage-baseline.md).

---

## `./gradlew check`

A single command runs the entire gate locally:

```bash
./gradlew check
```

This invokes (transitively):

| Sub-task                                      | Purpose                                                                 |
|-----------------------------------------------|-------------------------------------------------------------------------|
| `:app:detekt`                                 | Kotlin static analysis. Fails on any unsuppressed finding.              |
| `:app:ktlintCheck`                            | Kotlin formatting & idiomatic style rules. Run `ktlintFormat` to fix.  |
| `:app:lintDebug`                              | Android Lint over the debug variant + library dependencies.             |
| `:app:testDebugUnitTest`                      | JVM unit tests for the debug variant.                                   |
| `:app:koverVerifyDebug`                       | Test-coverage threshold enforcement.                                    |
| `:app:checkNoInternalFqn`                     | Custom rule: forbid `app.knotwork.android.*` FQN references in code body.   |

Pre-flight tip: run `./gradlew :app:ktlintFormat` first to auto-fix the
safely-correctable subset before invoking `check`.

---

## Detekt — Kotlin rules

Plugin: `dev.detekt` `2.0.0-alpha.3` (the 2.x line is required for Kotlin
2.3.21 / AGP 9.x compatibility). Configuration:
[`config/detekt/detekt.yml`](../config/detekt/detekt.yml), layered on top of
detekt's bundled defaults via `buildUponDefaultConfig = true` in
`app/build.gradle.kts`.

**Strict mode**: `detekt { ignoreFailures = false }`. The default
`failOnSeverity = Error` means any rule emitting at `severity: error`
(everything we enable) fails the build. The legacy 1.x top-level
`failFast: true` switch was removed in detekt 1.22 and is intentionally not
used.

### Tuned thresholds & disabled rules

| Rule                              | Setting                           | Why                                                                             |
|-----------------------------------|-----------------------------------|---------------------------------------------------------------------------------|
| `complexity.LongMethod`           | `allowedLines: 120`               | Graph-execution loops, JSON serialisers, pipeline factories genuinely run long. |
| `complexity.LongMethod`           | `ignoreAnnotated: ['Composable']` | `@Composable` trees are declarative; ktlint handles formatting.                 |
| `complexity.CyclomaticComplexMethod` | `allowedComplexity: 25`        | Orchestrator branches over node/provider/error states.                          |
| `complexity.CyclomaticComplexMethod` | `ignoreAnnotated: ['Composable']` | Composables aggregate conditional rendering.                                    |
| `complexity.LargeClass`           | `excludes: presentation/ui/**`    | `*Screen` files host many top-level composables.                                |
| `complexity.TooManyFunctions`     | `allowedFunctionsPer*: 25`        | DAOs, Repository contracts, Hilt modules naturally expose >11 functions.        |
| `complexity.LongParameterList`    | `allowedFunctionParameters: 10`   | Composable slot APIs commonly take many lambdas.                                |
| `style.MagicNumber`               | tuned excludes + named-arg ignore | See `MagicNumber` block in YAML for rationale; `AppDatabase.kt` is excluded.    |
| `style.MaxLineLength`             | `maxLineLength: 120`, comments excluded | Code lines ≤ 120 are enforced by ktlint too; KDoc references unavoidably overshoot.|
| `style.ReturnCount`               | `max: 5`                          | Multi-return early-exit is idiomatic Kotlin.                                    |
| `style.ThrowsCount`               | `max: 4`                          | Per-field schema validators want specific error messages per case.              |
| `style.LoopWithTooManyJumpStatements` | disabled                      | Pipeline / graph traversal legitimately uses multiple `break/continue`.         |
| `exceptions.TooGenericExceptionCaught` | disabled                     | LLM SDK / native / Android-IO call sites have an open exception surface.        |
| `exceptions.SwallowedException`   | disabled                          | Catching → mapping to `Result.failure(domainError)` is the boundary contract.   |
| `naming.FunctionNaming`           | `ignoreAnnotated: ['Composable']` | Compose convention is PascalCase.                                               |
| `comments.UndocumentedPublic*`    | scoped to `domain/` and `data/repositories/` | Enforces the KDoc rule from `CLAUDE.md`.                                |

### Adding an intentional suppression

When a finding is genuinely intentional, suppress it at the **narrowest
scope** with a reason:

```kotlin
// Reason: the validate() function accumulates a fixed set of structural
// checks into a single list. Each branch is one independent rule;
// extracting helpers would mostly rename, not decompose, the cyclomatic count.
@Suppress("CyclomaticComplexMethod")
fun validate(): List<PipelineValidationError> { … }
```

Bare `@Suppress("X")` without a reason comment is rejected in code review.

**Reports**:
- `app/build/reports/detekt/detekt.html` — visual checklist.
- `app/build/reports/detekt/detekt.xml` — checkstyle-compatible for CI parsers.

---

## ktlint — formatting & idiomatic style

Plugin: `org.jlleitschuh.gradle.ktlint` `14.2.0`, bundled engine `1.5.0`.
Strict mode: `ktlint { ignoreFailures.set(false) }`. Rule overrides live in
[`.editorconfig`](../.editorconfig):

- `ktlint_function_naming_ignore_when_annotated_with = Composable` — Compose
  PascalCase is allowed.
- `ktlint_standard_backing-property-naming = disabled` — the `_uiState`
  /`uiState` ViewModel pattern is project-wide.

Run `./gradlew :app:ktlintFormat` for the auto-fixable subset; remaining
issues are reported by `:app:ktlintCheck`.

**Reports**:
- `app/build/reports/ktlint/ktlintMainSourceSetCheck/*` (HTML + plain).

---

## Android Lint

Provided by AGP 9.2.1. Strict mode in `app/build.gradle.kts`:

```kotlin
android {
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
    }
}
```

`lint-baseline.xml` grandfathers existing issues so only **newly introduced**
warnings or errors fail the build. Regenerate the baseline only after a
deliberate batch of fixes:

```bash
./gradlew :app:updateLintBaseline    # rewrites the baseline; commit it.
```

**Reports**:
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/lint-results-debug.xml`

---

## Kover — coverage measurement & threshold

Plugin: `org.jetbrains.kotlinx.kover` `0.9.8`. Strict mode: a single
aggregate rule enforces **≥ 75 % LINE coverage** over the unit-testable
surface (raised from 70 % in Phase 23 / Task 9/9).

Kover 0.9.x does not support per-rule filters (that landed in 0.10+), so
filtering is done globally via `reports.filters.excludes`. The excluded
class set covers:

- Generated code (Hilt factories, Room `*_Impl`, AppDatabase, AutoMigrations,
  ComposableSingletons, `BuildConfig`, BR, DataBinding).
- All `*Preview.kt` files and `@Preview`-annotated functions.
- Hilt DI modules (`app.knotwork.android.di.*`).
- `App.kt` and `MainActivity` — Android-runtime-bound bootstrap.
- All `*Screen` Composables and `presentation.ui.*.components.*`, plus the
  Phase 23 sub-packages `presentation.ui.navigation.*`,
  `presentation.ui.about.AboutScreen*` / `AboutAcknowledgments*`,
  `presentation.ui.more.MoreScreen*`, and
  `presentation.ui.settings.provider.{ProviderPickerScreen, ProviderDetailScreen}*`.
- `presentation.theme/state.*` — declarative Compose constants.
- `data.tools.local.*` Android-runtime glue (AppFunctions service, search HTTP,
  delegate-task), including the Phase 23 sub-package
  `data.tools.local.appfunctions.*`.
- `data.local.dao.*` interfaces (impls are auto-excluded via the `*_Impl` pattern).
- `data.logging.CrashlyticsTimberTree*` — Firebase Crashlytics Timber bridge.

After these exclusions the aggregate runs at ~77.6 %;
[`coverage-baseline.md`](coverage-baseline.md) keeps the per-package
breakdown and the (informational) per-package targets. The 75 % floor
protects against regression with ~2.6 pp of headroom for in-flight
refactors.

Verification is wired into the `check` lifecycle:

```kotlin
tasks.named("check") { dependsOn("koverVerifyDebug") }
```

**Local commands**:

```bash
./gradlew :app:koverVerifyDebug      # threshold check
./gradlew :app:koverHtmlReportDebug  # drill-down per package/file
./gradlew :app:koverLog              # one-liner aggregate %
```

**Reports**:
- `app/build/reports/kover/htmlDebug/index.html`
- `app/build/reports/kover/reportDebug.xml`

---

## CI

The required job is defined in `.github/workflows/check.yml`. The workflow runs
`./gradlew check` on every `pull_request → main` and every `push` to `main`
(plus a manual `workflow_dispatch` trigger), uploads each report set —
detekt / ktlint / lint / unit-test / Kover / Roborazzi diffs — as a
downloadable artifact on failure, and is configured with
`concurrency.cancel-in-progress` so a new push supersedes any older run on the
same branch.

---

## What this gate does **not** do

- It does not yet collect instrumented (androidTest / Compose UI test)
  coverage — `*Screen.kt` Composables remain outside the Kover scope.
- It does not run the `release` variant — lint and tests target `debug`.
- It does not perform dependency-vulnerability scanning — that is a
  separate workstream.
