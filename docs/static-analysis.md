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
| `:app:detektFullDebug` + `:app:detektFossDebug` | Coroutine-cancellation gate (type-resolution, single rule, one per flavour). See below. |
| `:app:ktlintCheck`                            | Kotlin formatting & idiomatic style rules. Run `ktlintFormat` to fix.  |
| `:app:lintFullDebug`                              | Android Lint over the debug variant + library dependencies.             |
| `:app:testFullDebugUnitTest`                      | JVM unit tests for the debug variant.                                   |
| `:app:koverVerifyFullDebug`                       | Test-coverage threshold enforcement.                                    |
| `:app:checkNoInternalFqn`                     | Custom rule: forbid `app.knotwork.android.*` FQN references in code body.   |
| `:app:verifyBrowserEditorConstants`           | Fails if `pipeline-editor.html` `AUTO-GEN` blocks drift from the domain sources. |
| `:app:verifyDocsHygiene`                      | Custom rule: guard the public docs against LLM tool-call artifacts and internal-document references (see below). |
| `:app:testFullDebugUnitTest` (Konsist suite)      | Architecture guard: Clean-Architecture layer boundaries (see below).        |

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
| `comments.UndocumentedPublic*`    | scoped to `domain/` and `data/repositories/` | Enforces the project rule that public API in `domain/` and `data/repositories/` carries KDoc. |

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

### Coroutine-cancellation gate (`detektFullDebug` + `detektFossDebug`)

A second, deliberately narrow detekt run enforces the project's
coroutine-cancellation contract (see
[`code-style.md` § Coroutines & Flow](code-style.md#coroutines--flow)):

- **Rule**: `coroutines.SuspendFunSwallowedCancellation` — flags
  `runCatching` wrapping suspend calls, and `try`/`catch` blocks in suspend
  functions that catch `CancellationException` (or a superclass such as
  `Exception` / `Throwable` / `IllegalStateException`) without immediately
  re-throwing it.
- **Why a separate run**: the rule requires type resolution, which the plain
  `:app:detekt` task cannot provide. The plugin-generated `:app:detektFullDebug` & `:app:detektFossDebug`
  tasks (type resolution over each flavour's debug variant) are rewired in
  `app/build.gradle.kts` to
  [`config/detekt/detekt-cancellation.yml`](../config/detekt/detekt-cancellation.yml)
  — a config that activates **only** this rule — and added to `check`.
  Running the full strict config under type resolution would surface ~1.1k
  findings from rules that have never been part of the gate; adopting them
  is a separate effort. Full-config type-resolution analysis remains
  available via `:app:detektMain` / `:app:detektRelease` (not part of the
  gate).
- **Compliant patterns**: a dedicated first catch clause
  `catch (e: CancellationException) { throw e }` before the generic catch,
  or a `try`/`finally` without a catch. `runCatching` must never wrap
  suspend calls.
- **Known blind spot**: the rule does not analyse `try`/`catch` blocks
  nested inside non-suspend inline lambdas (e.g. `forEach { ... }`) because
  the enclosing function literal is not itself suspend-typed. Reviewers
  must still check those sites manually.

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

## Konsist — architecture guard

[Konsist](https://docs.konsist.lemonappdev.com/) (Apache-2.0) is a Kotlin
static analyzer whose checks are written as ordinary JUnit tests. The project
uses it for **one purpose only**: mechanically enforcing the Clean
Architecture layer boundaries that were previously guarded by item 1 of the
manual review checklist. Because the suite lives in the `test` source set, it
runs as part of `:app:testFullDebugUnitTest` — no extra `check` wiring is needed.

The suite lives in
[`app/src/test/java/app/knotwork/android/architecture/`](../app/src/test/java/app/knotwork/android/architecture)
and is pinned to the `app` module's **production** source set
(`app/src/main`) via the shared `ArchitectureScope`. The pin is deliberate:
a whole-project Konsist scope would also parse test sources (which
legitimately cross layers) and any unrelated copies of the tree under the
project root — notably stale git worktrees under `.claude/worktrees/` — and
flag them with spurious, environment-dependent violations.

| Rule (test class)                  | What it enforces                                                                                  |
|------------------------------------|---------------------------------------------------------------------------------------------------|
| `LayerDependencyKonsistTest`       | `data -> domain <- presentation`; `domain.dependsOnNothing()` (no `domain -> data`/`presentation`). |
| `DomainPurityKonsistTest`          | `domain` imports no `android.*` / `androidx.*` — the only exception is annotation-only `androidx.annotation.*`. |
| `RepositoryPlacementKonsistTest`   | `*Repository` interfaces reside in `domain`; `*RepositoryImpl` classes reside in `data`.          |
| `ComposableUseCaseKonsistTest`     | No `@Composable` takes a `*UseCase` parameter (see scope note below).                             |

**Why these rules and not more.** Konsist analyses *declarations*, not the
data flow inside a function body, so it cannot prove a `@Composable` never
*invokes* a use-case transitively. `ComposableUseCaseKonsistTest` therefore
guards only the structural smell it can express precisely — a use-case handed
to a Composable as a parameter. The deeper "presentation talks to ViewModels
only" intent stays a **manual** review item; the layer-direction and
domain-purity rules above are fully mechanical and supersede the manual
"no Android imports in `domain`" / "repository placement" checks.

The guard is regression-tested against the exact leak class it exists to
catch: reintroducing a `domain -> data` import (or an `android.*` import in
`domain`) turns both `LayerDependencyKonsistTest` and
`DomainPurityKonsistTest` red — verified during the suite's introduction.

---

## Public documentation hygiene guard (`verifyDocsHygiene`)

`:app:verifyDocsHygiene` is a custom Gradle verification task, wired into
`check`, that scans the public-documentation contour — every top-level
`*.md` file plus `NOTICE`, and everything under `docs/` — for two defect
classes that are cheap to introduce and expensive once the repository is
public. The root scope is a **glob**, not a hand-maintained allowlist, so a
newly added top-level public doc is guarded automatically:

1. **LLM tool-call wrapper artifacts** — stray fragments of an assistant's
   tool-call envelope (closing wrapper tags, or the opening of a markup /
   function-results block) that leak into a document when generated prose is
   pasted verbatim. These are never valid Markdown.
2. **References to internal-only documents** — links or mentions of the
   project's private planning files (the roadmap plan, the full description,
   the decision log, the phase backlog, the vision doc, the agent manifest)
   or of the internal `project_docs` tree. External readers cannot see
   them, so such a reference is always dangling.

Two families are deliberately **excluded** from the root glob: `CHANGELOG.md`
(a historical journal whose past entries legitimately name internal documents
as they were called at the time — rewriting history to satisfy a lint rule
would be worse than the dangling reference) and the untracked, internal
`CLAUDE` agent-manifest family, which deliberately references the private
planning docs. Internal-document filenames are matched only at a path/word
boundary, so a longer name that merely contains one (for example an archive
copy) does not trip the guard.

The scanning logic is a pure `Map<path, content> -> List<Violation>`
transform in `buildSrc`
([`DocsHygieneChecker`](../buildSrc/src/main/kotlin/app/knotwork/android/buildtools/DocsHygieneChecker.kt)),
so it is unit-tested there (`./gradlew -p buildSrc test`) with a fixture for
every forbidden token and a clean-input case. The forbidden tokens are
assembled from fragments at runtime so that neither the scanner's own source
nor this document — which describes the tokens in prose — matches itself.

---

## R8 keep-rule guard (`verify<Variant>KeepRules`)

Some keep rules protect code whose failure mode **no test in this gate can
see**, because the gate is JVM-only and debug builds are not minified. The
motivating case: MediaPipe's `tasks-text` pulls in
`com.google.common.flogger`, which resolves a log site by *walking the call
stack* for a frame belonging to flogger itself. When R8 renames or inlines
those frames away, the first `TextEmbedder.createFromOptions` call fails with
`IllegalStateException: no caller found on the stack for: …` — that is the
on-device embedding path, so in a minified build every message that touches
long-term memory kills the process, while every debug build stays green.

The one durable artefact that *can* see it is the mapping R8 emits: a live
`-keep class … { *; }` rule leaves the package **identity-mapped**
(`a.b.C -> a.b.C`). `:app:verify<Variant>KeepRules` asserts exactly that for
every protected package after each `release` assemble (it is wired with
`finalizedBy`, so it runs as part of the release build, not of `check` —
`check` never produces a mapping). A package that comes back renamed **or
missing entirely** fails the build; the missing case is treated as a failure
on purpose, since "all zero classes are identity-mapped" would be a vacuous
pass hiding a dropped dependency.

The parsing is a pure `String -> List<Violation>` transform in `buildSrc`
([`R8MappingChecker`](../buildSrc/src/main/kotlin/app/knotwork/android/buildtools/R8MappingChecker.kt)),
unit-tested there (`./gradlew -p buildSrc test`) against an identity-mapped
fixture, an obfuscated one, an absent-package one, and a member-line shape
that must not be misparsed as a class. Protected packages are listed in
`r8ProtectedPackages` in `app/build.gradle.kts`; add to that list whenever a
new keep rule exists to satisfy a stack-walking or name-reflecting library.

---

## Kover — coverage measurement & threshold

Plugin: `org.jetbrains.kotlinx.kover` `0.9.8`. Strict mode: a single
aggregate rule enforces **≥ 75 % LINE coverage** over the unit-testable
surface (raised from 70 %).

Kover 0.9.x does not support per-rule filters (that landed in 0.10+), so
filtering is done globally via `reports.filters.excludes`. The excluded
class set covers:

- Generated code (Hilt factories, Room `*_Impl`, AppDatabase, AutoMigrations,
  ComposableSingletons, `BuildConfig`, BR, DataBinding).
- All `*Preview.kt` files and `@Preview`-annotated functions.
- Hilt DI modules (`app.knotwork.android.di.*`).
- `App.kt` and `MainActivity` — Android-runtime-bound bootstrap.
- All `*Screen` Composables and `presentation.ui.*.components.*`, plus the
  sub-packages `presentation.ui.navigation.*`,
  `presentation.ui.about.AboutScreen*` / `AboutAcknowledgments*`,
  `presentation.ui.more.MoreScreen*`, and
  `presentation.ui.settings.provider.{ProviderPickerScreen, ProviderDetailScreen}*`.
- `presentation.theme/state.*` — declarative Compose constants.
- `data.tools.local.*` Android-runtime glue (AppFunctions service, search HTTP,
  delegate-task), including the sub-package
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
tasks.named("check") { dependsOn("koverVerifyFullDebug") }
```

**Local commands**:

```bash
./gradlew :app:koverVerifyFullDebug      # threshold check
./gradlew :app:koverHtmlReportFullDebug  # drill-down per package/file
./gradlew :app:koverLog              # one-liner aggregate %
```

**Reports**:
- `app/build/reports/kover/htmlFullDebug/index.html`
- `app/build/reports/kover/reportFullDebug.xml`

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
- It does not run the `release` variant — lint and tests target `debug`. R8
  regressions are therefore invisible here; the release-only guard above
  (`verify<Variant>KeepRules`) runs as part of the release assemble instead.
- It does not perform dependency-vulnerability scanning — that is a
  separate workstream.
