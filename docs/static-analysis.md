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
| `:app:detektFullDebug` + `:app:detektFossDebug` | Type-resolution gate (the rules the plain task cannot run, one per flavour). See below. |
| `:app:ktlintCheck`                            | Kotlin formatting & idiomatic style rules. Run `ktlintFormat` to fix.  |
| `:app:lintFullDebug` + `:app:lintFossDebug`   | Android Lint over both distribution flavours + library dependencies. `:catalog:lint` runs too. |
| `:app:testFullDebugUnitTest`                      | JVM unit tests for the debug variant.                                   |
| `:app:koverVerifyFullDebug`                       | Test-coverage threshold enforcement.                                    |
| `:app:checkNoInternalFqn`                     | Custom rule: forbid `app.knotwork.android.*` FQN references in code body.   |
| `:app:verifyBrowserEditorConstants`           | Fails if `pipeline-editor.html` `AUTO-GEN` blocks drift from the domain sources. |
| `:app:verifyDocsHygiene`                      | Custom rule: guard the public docs against LLM tool-call artifacts and internal-document references (see below). |
| `:app:verifyExternalAutomationDocs`           | Fails if the `docs/external-automation.md` `AUTO-GEN` tables drift from the contract sources (see below). |
| `:app:verifySettingsHelpDocs`                 | Fails if the settings reference table in `docs/user-guide.md` drifts from the shipped help strings (see below). |
| `:app:testFullDebugUnitTest` (`SettingsHelpCatalogTest`) | Fails if a registered setting has no help decision, or its text is blank, over-long, duplicated or in a forbidden register (see below). |
| `:app:verifyLintBaselineOverrides`            | Custom rule: fail if a lint baseline suppresses a check demoted to informational severity (see below). |
| `:app:verifyDetektAnalysisMode`               | Custom rule: fail if `detekt.yml` activates a rule that only runs under type resolution (see below). |
| `:app:testFullDebugUnitTest` (Konsist suite)      | Architecture guard: Clean-Architecture layer boundaries (see below).        |

Pre-flight tip: run `./gradlew :app:ktlintFormat` first to auto-fix the
safely-correctable subset before invoking `check`.

---

## Detekt — Kotlin rules

Plugin: `dev.detekt`, 2.x line (required for Kotlin 2.4.x / AGP 9.x
compatibility). The exact version is pinned in
[`gradle/libs.versions.toml`](../gradle/libs.versions.toml) under the `detekt`
key — that catalog entry, not this page, is the source of truth. Configuration:
[`config/detekt/detekt.yml`](../config/detekt/detekt.yml), layered on top of
detekt's bundled defaults via `buildUponDefaultConfig = true` in
`app/build.gradle.kts`.

**That file may only hold rules that run without type resolution.** Detekt 2.x
splits its rules by what they need to answer, and a rule implementing
`dev.detekt.api.RequiresAnalysisApi` is skipped by the plain `:app:detekt` task
*in silence* — no error, no warning, no report entry. The rules that need it
live in the type-resolution gate below, and
[`:app:verifyDetektAnalysisMode`](#detekt-analysis-mode-guard) fails the build
if one is put back here.

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
| `complexity.LongParameterList`    | `allowedFunctionParameters: 10`, `allowedConstructorParameters: 14` | Composable slot APIs commonly take many lambdas; Hilt assembles long constructor lists. Lives in the type-resolution gate. |
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

### Type-resolution gate (`detektFullDebug` + `detektFossDebug`)

Detekt 2.x splits its rules by what they need in order to answer. A rule that
only walks the syntax tree runs anywhere; a rule that needs resolved types
implements `dev.detekt.api.RequiresAnalysisApi` and runs only in `full`
analysis mode. **A rule of the second kind, configured into a `light`-mode run,
is skipped without a word** — no error, no warning, no entry in the report. Of
detekt 2.x's rules, 93 are of the second kind.

That is not a hypothetical. Four rules sat in `detekt.yml` — `LongParameterList`,
`UnusedImport`, `UnusedPrivateFunction`, `UnusedPrivateProperty` — each with a
comment explaining its threshold, and none of them had ever run: an
18-parameter constructor passed `:app:detekt` clean. When they were finally
executed they had 52 findings waiting.

So the plugin-generated type-resolution tasks for each flavour's debug variant
(`:app:detektFullDebug` / `:app:detektFossDebug`) are rewired in
`app/build.gradle.kts` to
[`config/detekt/detekt-type-resolution.yml`](../config/detekt/detekt-type-resolution.yml)
and added to `check`. Both flavours run so the gate also covers the
flavour-specific sources (`src/full` / `src/foss`), which the plain task's
`source` never reached either.

| Rule                                    | What it catches                                                        |
|-----------------------------------------|------------------------------------------------------------------------|
| `coroutines.SuspendFunSwallowedCancellation` | `runCatching` wrapping suspend calls, and `try`/`catch` in suspend functions that catches `CancellationException` (or a superclass such as `Exception` / `Throwable`) without immediately re-throwing. |
| `complexity.LongParameterList`          | >10 function parameters, >14 constructor parameters; `@Composable` exempt. |
| `style.UnusedImport`                    | Imports no longer referenced — ktlint's own check cannot see through extension-property imports. |
| `style.UnusedPrivateFunction`           | Dead private/internal functions.                                        |

**Why not simply run the strict config under type resolution.** Measured on
this repository, that surfaces 296 findings, 263 of them from rules the project
never declared it wants (`InjectDispatcher` alone accounts for 131). Adopting
them is a deliberate piece of work, not a side effect of making four declared
rules run. Full-config type-resolution analysis remains available for that
triage via `:app:detektMain` / `:app:detektRelease` (not part of the gate).

**One rule is deliberately parked.** `style.UnusedPrivateProperty` belongs to
the same family but is not enabled: in the pinned detekt version it does not
count a reference made from a *property initializer*, so

```kotlin
class C(private val dep: Dep) { val holder = Holder(dep = dep) }  // `dep` reported unused
```

On this repository that is 24 of its 29 findings — every dependency a ViewModel
forwards into a delegate built as a property initializer. Enabling it would mean
24 `@Suppress` annotations asserting something untrue about live code. The five
genuine findings it did surface were fixed by hand.

**Compliant cancellation patterns**: a dedicated first catch clause
`catch (e: CancellationException) { throw e }` before the generic catch, or a
`try`/`finally` without a catch. `runCatching` must never wrap suspend calls.

**Known blind spot**: `SuspendFunSwallowedCancellation` does not analyse
`try`/`catch` blocks nested inside non-suspend inline lambdas (e.g.
`forEach { ... }`), because the enclosing function literal is not itself
suspend-typed. Reviewers must still check those sites manually.

### Detekt analysis-mode guard

`:app:verifyDetektAnalysisMode` (wired into `check`) is what keeps the split
above from silently rotting. It reads the rules
[`config/detekt/detekt.yml`](../config/detekt/detekt.yml) explicitly activates,
resolves which rule classes on detekt's own classpath implement
`RequiresAnalysisApi`, and fails when the two sets intersect — i.e. when a rule
has been added to the light-mode config that the `detekt` task would skip
without saying so.

It reads the rule set from the `detekt` configuration's jars rather than from a
list pinned in the repository, so an upgrade that moves a rule across the
boundary is caught by the next build. It also fails when that scan comes back
empty, because a guard with nothing to compare against passes everything —
which is precisely the failure mode it exists to prevent.

Scope is stated rather than implied: the guard judges the rules this repository
*declares*, not the ones detekt's bundled defaults switch on underneath them.
Those defaults do include rules requiring the Analysis API, and the light-mode
task skips them in the same silence; adopting them is the 296-finding triage
described above, and a guard should not force it by failing the build. The
enforceable promise is the narrower one: a rule the project went to the trouble
of naming and tuning actually runs.

Implementation: `buildSrc/.../DetektAnalysisModeGuard.kt`, unit-tested via
`./gradlew -p buildSrc test`.

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

Provided by the Android Gradle Plugin (version pinned in
[`gradle/libs.versions.toml`](../gradle/libs.versions.toml)). Strict mode is
configured identically in `app/build.gradle.kts` and `catalog/build.gradle.kts`:

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

> **Regenerating also re-absorbs the informational checks below.** Lint records
> informational findings into a baseline exactly as it records errors, and a
> baselined finding disappears from the report — which would silently empty the
> dependency-drift signal while the build stays green. `verifyLintBaselineOverrides`
> fails the build if that happens, naming the lines to delete. Prefer editing the
> baseline by hand over regenerating it wholesale.

### Version-freshness checks report, they do not gate

Four checks are demoted to **informational** severity in both modules:

| Check | Answers from |
|-------|--------------|
| `GradleDependency`          | an external version index |
| `AndroidGradlePluginVersion`| an external version index |
| `NewerVersionAvailable`     | an external version index (a network round-trip per dependency) |
| `ExpiringTargetSdkVersion`  | the calendar |

The reason is determinism, not leniency. Whether the build passes ought to be a
function of the contents of the repository: these four are not, so the same
commit can be green today and red tomorrow with no edit in between, and green on
a laptop while red in CI, because the two version indexes were refreshed at
different times. A check whose verdict moves without the checked object moving is
a report.

**Deliberate exceptions.** Seven checks stay build-failing even though their
verdict is not purely a function of this repository, because each encodes a
store publishing blocker rather than a matter of hygiene — being interrupted by
them is the point:

- `ExpiredTargetSdkVersion` (fatal), which is calendar-driven;
- `PlaySdkIndexNonCompliant`, `PlaySdkIndexVulnerability`,
  `PlaySdkIndexGenericIssues`, `PlaySdkIndexDeprecated`, `RiskyLibrary` and
  `OutdatedLibrary`, which are decided by the Google Play SDK Index — a
  network-refreshed dataset with a bundled offline snapshot as fallback.

Anything outside that list is expected to hold the rule: a check that reads
network reachability, a third-party service or the clock belongs in a report,
not in the gate.

Demoted is not disabled. The checks still run, still respect the baseline, and
still appear in the reports — `disable` would drop the findings before any
reporter sees them. `warningsAsErrors = true` does not undo the demotion: lint
promotes `WARNING` to `ERROR`, and informational findings are exempt. Do **not**
add `ignoreWarnings = true` next to it — that flag suppresses informational
findings as well, which would delete the report.

Dependencies are therefore updated deliberately — when a task needs the newer
version, or in one pass before a release — rather than because a check went red
in the middle of unrelated work.

### Where the drift report lands

- Locally: the lint reports below, produced by every `./gradlew check`.
- In CI: the **Informational lint findings** table in the job summary, plus the
  `lint-report` artifact, which is uploaded on every run — including green ones,
  since a green run is precisely the run whose report carries the drift.

**Reports**:
- `app/build/reports/lint-results-fullDebug.{html,xml}`
- `app/build/reports/lint-results-fossDebug.{html,xml}`
- `catalog/build/reports/lint-results-debug.{html,xml}`

---

## Lint baseline guard (`verifyLintBaselineOverrides`)

The checks demoted above report at informational severity, which makes the lint
report their only signal — and makes the baseline a way to delete that signal
without failing anything. Lint records informational findings into a regenerated
baseline exactly as it records errors (the write path filters by issue id, never
by severity) and then filters baselined findings out of the reports, so a single
routine `updateLintBaseline` run would empty the drift report while `check`
stayed green.

`verifyLintBaselineOverrides` closes that path: a demoted id may not appear in a
committed baseline. Unlike the checks it protects, the guard is a legitimate gate
— its verdict is a function of the committed baselines and nothing else.

The pure scanner lives in `buildSrc`
([`LintBaselineGuard`](../buildSrc/src/main/kotlin/app/knotwork/android/buildtools/LintBaselineGuard.kt))
and holds the demoted-id list as its single declaration site: both lint blocks
read it as `informational += LintBaselineGuard.DEMOTED_ISSUE_IDS`, so the set
cannot drift between the modules and the guard. Its unit tests are not reachable
from the root `check` graph (`buildSrc` is a separate build):

```bash
./gradlew -p buildSrc test
```

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

## External-automation contract documentation guard (`verifyExternalAutomationDocs`)

`:app:verifyExternalAutomationDocs` is a custom Gradle verification task, wired
into `check`, that fails the build when the reference tables in
[`docs/external-automation.md`](external-automation.md) no longer match the
Kotlin declarations they document — the action strings and extra keys of
`ExternalAutomationContract`, the members of `ExternalAutomationStatus`, and the
constants of `ExternalAutomationRejectionReason`.

The guard exists because this contract's callers live in **other apps**. A
Tasker profile or `adb` one-liner written against a documented key keeps using
it forever, and a key that has quietly changed does not fail loudly on the
caller's side: the request simply looks malformed to the app, and the person who
wrote the profile has no way to see why. Documentation drift here is therefore a
compatibility defect, not a tidiness issue.

Regenerate the tables with:

```bash
./gradlew :app:generateExternalAutomationDocs
```

Only the content between the `<!-- AUTO-GEN:… -->` markers is generated. The
prose, the trust model and the worked examples around them are hand-written and
never touched.

Each row's description is the declaration's **own KDoc first paragraph**, so —
unlike the browser-editor generator — there is no second, hand-maintained table
that can be forgotten. What replaces that cross-check is a stricter demand on
the source: a contract member with no KDoc fails generation outright, because an
undocumented member would otherwise publish an empty cell.

The generation logic is a pure string transform in `buildSrc`
([`ExternalAutomationDocsGenerator`](../buildSrc/src/main/kotlin/app/knotwork/android/buildtools/ExternalAutomationDocsGenerator.kt))
and is unit-tested there (`./gradlew -p buildSrc test`), including idempotence,
per-block drift detection, and the two failure modes above. The gate itself was
verified by renaming a wire key and watching `check` fail with the drifted block
named.

---

## Settings help-text guards (`SettingsHelpCatalogTest`, `verifySettingsHelpDocs`)

Two guards, one rule: **a setting's meaning is written once**, in
`app/src/main/res/values/strings_settings_help.xml`, and everything else quotes
it.

The rule exists because the alternative was measured. Before it, one setting's
explanation lived in four places — the catalog row subtitle, the search-index
description, a second copy of the subtitle in the app resources, and the user
guide's prose — and closed testing found them already disagreeing. The
long-running-tasks row said "runs > 8 s", the search index said "runs long", the
guide said "exceeds the long-running threshold", and **no constant anywhere in
the code held any such number**. Prose kept in sync by review alone drifts, and a
reader cannot tell which copy is true.

### `SettingsHelpCatalogTest` — completeness and register

A Robolectric unit test, so it can resolve real string resources. It fails when:

- a row in `SettingsRegistry` has no decision in `SettingsHelpCatalog` (or a
  decision exists for a row that does not);
- an explained row's string resource is missing or resolves to blank text;
- an explanation exceeds **140 characters** — the ceiling measured against the
  200 % font-scale frame, past which the panel pushes the row it explains off
  the top of the screen;
- two rows are explained by the same sentence;
- an explanation uses a phrasing the copy standard rules out (`enables …`,
  `allows you to …`, `this setting …`, `when enabled …`) — each describes the
  control rather than what the reader will notice, which is the register that
  went unread.

Note what this fixes about its predecessor. `SettingsSearchCatalogTest` reads
like a completeness guard and is one on **names only**: `descRes` is nullable and
no assertion looks at it, so a description could be deleted and the build stayed
green. Here the decision is an explicit `SettingHelp.Text | SettingHelp.None`
with a recorded reason, which is what makes "every row decided" assertable at
all.

- an explanation exists for a row that no screen renders (the set of such rows is
  pinned by name, so it can only shrink — a *new* unreachable explanation fails
  the build).

Each failure mode was observed failing before the gate was wired in, on its own
assertion. Note the one thing this gate cannot see: it knows an anchor is
*declared*, not that the control emits both the glyph and the panel. A header
that shipped a glyph opening nothing passed it, and was caught by
`SettingsHintBehaviourTest` — which opens each bespoke control and demands the
panel — instead.

### `verifySettingsHelpDocs` — the guide quotes rather than restates

`:app:verifySettingsHelpDocs` fails `check` when the `AUTO-GEN:SETTINGS_HELP`
block of [`docs/user-guide.md`](user-guide.md) no longer matches the shipped
strings. Regenerate with:

```bash
./gradlew :app:generateSettingsHelpDocs
```

The generated table answers *"what does this option mean"*. The hand-written
prose around it answers *"what happens when you change it"* — the measured
timeouts, the retry behaviour, the ordering rules — and is never touched.

**One thing to know before editing this generator.** A doc generator paired with
its own drift check can delete a real row and stay green: if the parser stops
seeing a declaration, the block loses that row and the verify task then confirms
the shortened block matches. Guarding against it needs a count taken from a
**different file read by a different parser** — here, the help catalogue, whose
keys `SettingsHelpCatalogTest` independently pins to the registry. Comparing the
registry parse against itself does not work, and this generator shipped two
drafts that did exactly that: the first emitted **five rows for fifty-six
settings** with its own check green.

The logic is a pure string transform in `buildSrc`
([`SettingsHelpDocsGenerator`](../buildSrc/src/main/kotlin/app/knotwork/android/buildtools/SettingsHelpDocsGenerator.kt)),
unit-tested there including idempotence, drift detection, a missing string
resource, a display name that lives in another values file, and a crippled
parser. The gate was verified by editing a help string without regenerating, by
hand-editing the generated table, and by breaking the registry parser — each
failed with the cause named.

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
detekt / ktlint / unit-test / Kover / Roborazzi diffs — as a downloadable
artifact on failure, and is configured with `concurrency.cancel-in-progress` so
a new push supersedes any older run on the same branch. The **lint** report is
the exception: it is uploaded on every run, green ones included, because it
carries the informational dependency-drift findings described above. The same
findings are also rendered into the job summary as an *Informational lint
findings* table, so the answer to "what is out of date?" needs no artifact
download.

The same job also compiles the instrumented source set for both flavours, in a
Gradle invocation of its own. `check` does not compile `androidTest`, so
without that step an instrumented test could stop building while every gate
stayed green. Whether those tests *pass* is decided elsewhere — by
`.github/workflows/instrumented.yml`, which runs them on an emulator matrix and
is deliberately kept out of the required `check` workflow (and therefore out of
the release path) because an emulator's verdict is not purely a function of the
repository. See [`testing.md`](testing.md) § *The instrumented gate*.

The same workflow is also exposed as a reusable one (`workflow_call`) and is
called as the first job of `.github/workflows/release.yml`, so a release cannot
be built against a definition of "green" that has drifted from the one pull
requests are measured by. `release.yml` adds the checks that only make sense on
a release build — the tag ↔ `versionName` agreement, `verify<Variant>KeepRules`
on both flavours, and a signature check of every published artefact against the
expected certificate fingerprint. The release procedure itself is documented in
[`release.md`](release.md) §9.

---

## What this gate does **not** do

- It does not collect instrumented (androidTest / Compose UI test)
  **coverage** — `*Screen.kt` Composables remain outside the Kover scope. The
  instrumented tests themselves do run in CI, in their own workflow; their
  results simply do not feed the coverage number.
- It does not run the `release` variant — lint and tests target `debug`. R8
  regressions are therefore invisible here; the release-only guard above
  (`verify<Variant>KeepRules`) runs as part of the release assemble instead,
  which on CI means `release.yml` rather than this workflow.
- It does not perform dependency-vulnerability scanning — that is a
  separate workstream.
