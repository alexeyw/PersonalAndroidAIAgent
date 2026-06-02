# Test-Coverage Baseline

Living record of the project's unit-test LINE coverage and the gate
configuration that backs it. Updated at the end of each phase that touches
coverage materially.

## Snapshot — Phase 23 / Task 9/9 (current)

| Field        | Value                                                          |
|--------------|----------------------------------------------------------------|
| Date         | 2026-05-26                                                     |
| Branch base  | `phase/23` (closing the phase)                                 |
| Kover        | 0.9.8 (latest on the Gradle Plugin Portal)                     |
| Variant      | `debug` (Android default unit-test variant)                    |
| Test command | `./gradlew :app:testDebugUnitTest`                             |
| Build gate   | aggregate LINE ≥ **75 %** (raised 70 % → 75 % in this task)    |

`./gradlew :app:koverLog` headline figure: **`application line coverage: 77.62 %`**
(5 968 / 7 689 lines after exclusions).

> **Phase 26 / Task 4/10 refresh (2026-06-02):** aggregate LINE is now
> **79.05 %** (7 816 / 9 888 lines after exclusions) — the line total grew with
> the Phase 24/25 feature work, and Task 4 closed the `presentation.ui.more`
> (→ 100 %) and `presentation.ui.settings.provider` (→ 100 %) gaps and raised
> `presentation.ui.prompts` to 67.9 %. The three rows below carry an
> `↑ Phase 26 / Task 4` note; the remaining per-package figures are still the
> Phase-23 measurement and will be re-measured wholesale at the next coverage
> task.

### Historical snapshots

| Phase / Task            | Date       | Aggregate LINE | Gate  |
|-------------------------|------------|----------------|-------|
| Phase 18 / Task 2/10    | 2026-05-10 | 45.3 % (raw, no exclusions yet) | — (measurement only) |
| Phase 18 / Task 9-10    | 2026-05-?? | ~80 % (with exclusions, claimed) | 70 % |
| Phase 23 / Task 9/9     | 2026-05-26 | 77.6 % (after extending exclusions for nav/about/more/provider screens + AppFunctions glue) | **75 %** |
| Phase 26 / Task 4/10    | 2026-06-02 | 79.05 % (closed `MoreViewModel` / `ProviderDetailViewModel` gaps) | **75 %** |

## Per-package LINE coverage (post-exclusions)

Numbers measured against `phase/23` HEAD, 2026-05-26. Sorted by layer.
Packages excluded from the gate (Composables, Android-runtime glue, DI,
generated code) do not appear in this table — they live in the
"Exclusions applied" section below.

### `domain/` — 90.0 % (1 764 / 1 960 lines)

| Package                       | Coverage | Covered / Total | Target  |
|-------------------------------|----------|-----------------|---------|
| `domain.constants`            | 100.00 % | 34 / 34         | ≥ 95 %  |
| `domain.engine`               | 93.56 %  | 407 / 435       | ≥ 90 %  |
| `domain.engine.executors`     | 78.42 %  | 378 / 482       | ≥ 75 %  |
| `domain.models`               | 94.50 %  | 378 / 400       | ≥ 90 %  |
| `domain.pipelineio`           | 92.91 %  | 118 / 127       | ≥ 90 %  |
| `domain.prompt`               | 100.00 % | 53 / 53         | ≥ 95 %  |
| `domain.repositories`         | 60.00 %  | 3 / 5           | n/a — interface declarations |
| `domain.usecases`             | 85.06 %  | 393 / 462       | ≥ 80 %  |

`domain` remains the strongest layer. The 60 % figure on `domain.repositories`
is a single `companion object` constant in an otherwise pure-interface
package — not a meaningful target (`n/a`).

### `data/` — 72.6 % (1 363 / 1 877 lines)

| Package                       | Coverage | Covered / Total | Target  |
|-------------------------------|----------|-----------------|---------|
| `data.engine`                 | 55.39 %  | 190 / 343       | ≥ 50 %  |
| `data.local`                  | 48.30 %  | 327 / 677       | ≥ 45 %  |
| `data.local.models`           | 93.02 %  | 80 / 86         | ≥ 90 %  |
| `data.mappers`                | 100.00 % | 43 / 43         | ≥ 95 %  |
| `data.mcp`                    | 79.45 %  | 58 / 73         | ≥ 75 %  |
| `data.network`                | 92.68 %  | 38 / 41         | ≥ 90 %  |
| `data.prompt`                 | 49.25 %  | 33 / 67         | ≥ 45 %  |
| `data.repositories`           | 82.20 %  | 494 / 601       | ≥ 80 %  |
| `data.services`               | 99.42 %  | 171 / 172       | ≥ 95 %  |
| `data.tools.local`            | 74.53 %  | 199 / 267       | ≥ 70 %  |
| `data.tools.local.executors`  | 100.00 % | 21 / 21         | ≥ 95 %  |

`data.services` jumped from 44 % to 99 % during Phase 23 / Task 4/9 when
Robolectric coverage of `AgentForegroundService`, `AgentWorker`,
`AgentIdleManager`, `AgentPowerManager`, and `LongRunningTaskNotifierImpl`
landed; the previous `data.services.*` exclusion was lifted in that task
and remains lifted.

### `presentation/` — 74.7 % (2 837 / 3 800 lines)

| Package                                       | Coverage | Covered / Total | Target  |
|-----------------------------------------------|----------|-----------------|---------|
| `presentation.notifications`                  | 100.00 % | 63 / 63         | ≥ 95 %  |
| `presentation.receivers`                      | 100.00 % | 14 / 14         | ≥ 95 %  |
| `presentation.ui.chat.home`                   | 84.29 %  | 692 / 821       | ≥ 80 %  |
| `presentation.ui.common`                      | 37.50 %  | 6 / 16          | ≥ 35 %  |
| `presentation.ui.memory`                      | 92.86 %  | 52 / 56         | ≥ 85 %  |
| `presentation.ui.models`                      | 82.14 %  | 92 / 112        | ≥ 75 %  |
| `presentation.ui.monitoring`                  | 100.00 % | 30 / 30         | ≥ 95 %  |
| `presentation.ui.more`                        | 100.00 % | 80 / 80         | ↑ Phase 26 / Task 4 — `MoreViewModelTest` closes the gap |
| `presentation.ui.onboarding`                  | 86.61 %  | 110 / 127       | ≥ 80 %  |
| `presentation.ui.orchestrator`                | 85.03 %  | 335 / 394       | ≥ 80 %  |
| `presentation.ui.pipeline.editor.config`      | 52.40 %  | 175 / 334       | known gap — `NodeConfigCodec` round-trip tests (Phase 24 follow-up) |
| `presentation.ui.pipeline.editor.core`        | 85.16 %  | 310 / 364       | ≥ 80 %  |
| `presentation.ui.prompts`                     | 67.91 %  | 91 / 134        | ↑ Phase 26 / Task 4 — the baseline's `PromptVariablesViewModel` no longer exists; the surface is `PromptLibraryViewModel` (covered). Residual gap is the `toViewState` mapper. |
| `presentation.ui.settings`                    | 88.89 %  | 256 / 288       | ≥ 80 %  |
| `presentation.ui.settings.provider`           | 100.00 % | 83 / 83         | ↑ Phase 26 / Task 4 — `ProviderDetailViewModelTest` closes the gap |
| `presentation.ui.splash`                      | 97.30 %  | 36 / 37         | ≥ 90 %  |
| `presentation.ui.taskmonitor`                 | 90.91 %  | 100 / 110       | ≥ 85 %  |
| `presentation.ui.tools`                       | 92.34 %  | 205 / 222       | ≥ 85 %  |

The 0 % / sub-50 % rows above are **testable** code (ViewModel + UiState
classes) that simply lacks coverage today. They are not excluded from the
gate — they pull the aggregate down — and are tracked as Phase 24 follow-up
items rather than being silently filtered. The "Target" column is
**informational**: with Kover 0.9.x the build only enforces the global 75 %
aggregate; per-package floors will be promoted to enforced rules when Kover
0.10 ships (per-rule `filters { ... }` is a 0.10+ feature).

### Other

| Package                            | Coverage | Covered / Total |
|------------------------------------|----------|-----------------|
| `ai.agent.android` (root, `App.kt`)| 23.53 %  | 8 / 34          |
| `appfunctions_aggregated_deps`     | 0.00 %   | 0 / 5           |

`App.kt` is the Hilt `Application` subclass; the lit lines are the
Crashlytics opt-in gate. `appfunctions_aggregated_deps` is KSP-generated
boilerplate and could be added to the exclusion list later; at 5 lines it
does not move the aggregate.

## Exclusions applied

The following classes are removed from the metric in
`app/build.gradle.kts` → `kover { reports { filters { excludes { … } } } }`.
Each is justified below.

| Pattern                                           | Why excluded                                                             |
|---------------------------------------------------|--------------------------------------------------------------------------|
| `*_HiltModules*`, `*_HiltModules_*`, `Hilt_*`     | Hilt-generated component / module classes — wiring code, no logic.       |
| `*_Factory`, `*_Factory$*`, `*_MembersInjector`   | Hilt-generated provider factories.                                       |
| `*_Provide*Factory`, `*_Provide*Factory$*`        | Hilt module-provider factories.                                          |
| `dagger.hilt.internal.*`, `hilt_aggregated_deps.*`| Hilt internals.                                                          |
| `*_Impl`, `*_Impl$*`                              | Room-generated DAO implementations.                                      |
| `ai.agent.android.data.local.AppDatabase`        | Room database class (declarations + migration shells).                    |
| `ai.agent.android.data.local.AppDatabase_Impl*`  | Room-generated database implementation.                                  |
| `*_AutoMigration_*`                               | Room auto-migration generated bridges.                                   |
| `ai.agent.android.data.local.dao.*`              | Room DAO **interfaces** (only `companion object` constants in source).   |
| `*ComposableSingletons*`, `ComposableSingletons$*`| Compose-compiler-emitted lambda singletons.                              |
| `*Preview`, `*PreviewKt`                          | Compose preview files (project convention: `*Preview.kt`).               |
| `ai.agent.android.di.*`                           | Hilt DI modules — only `@Provides` wiring, no business behaviour.        |
| `ai.agent.android.BuildConfig`                    | Android-Gradle-generated build constants.                                |
| `*.databinding.*`, `*.BR`                         | DataBinding generated code (defensive — project does not use DataBinding today). |
| `@androidx.compose.ui.tooling.preview.Preview`    | Belt-and-braces annotation filter for preview functions outside `*Preview.kt`. |
| `ai.agent.android.App`                            | Hilt `Application` subclass; needs Android runtime to instantiate.       |
| `ai.agent.android.presentation.ui.MainActivity*`  | Compose host `Activity`; covered by androidTest, not JVM unit tests.      |
| `ai.agent.android.presentation.ui.*Screen*`       | Top-level Compose screen files (project convention: `*Screen.kt`).        |
| `ai.agent.android.presentation.ui.components.*`   | Reusable Compose components used by multiple screens.                     |
| `ai.agent.android.presentation.ui.orchestrator.components.*` | Sub-package of orchestrator Compose components.                |
| `ai.agent.android.presentation.ui.chat.legacy.*` (selected) | Phase 21 / Task 8 — legacy chat surface kept until orchestrator rewire.|
| `ai.agent.android.presentation.ui.chat.home.ChatHomeScreen*`, `ChatHomeDebugStatePicker*`, `DebugStateRows*` | Compose surfaces of the redesigned chat home. |
| `ai.agent.android.presentation.ui.pipeline.editor.canvas.*` | Phase 21 / Task 9 — gesture / animation / Bezier draw layer.       |
| `ai.agent.android.presentation.ui.pipeline.editor.bars.*`   | Editor top / bottom bars (Compose).                                  |
| `ai.agent.android.presentation.ui.pipeline.editor.sheet.*`  | Editor bottom sheets (Compose).                                      |
| `ai.agent.android.presentation.ui.pipeline.editor.PipelineEditorContent*`, `PipelineEditorScreen*` | Pipeline editor host Composables.        |
| `ai.agent.android.presentation.ui.splash.SplashScreen*`     | Splash Composable.                                                   |
| `ai.agent.android.presentation.theme.*`           | Material 3 colour / typography constants. Pure declarative data.         |
| `ai.agent.android.presentation.state.*`           | Tiny constant-only state types historically used by Compose code.        |
| `ai.agent.android.presentation.ui.navigation.*`   | **New in Phase 23 / Task 9/9.** `AppShellScaffold`, `AppNavGraph`, `TabDestination`, `BottomNavVisibility`, `KnotworkModalRoute`, `NavRoutes` — bottom-nav shell + nav-graph wiring. Pure UI / nav glue; route constants unreachable in JVM tests. |
| `ai.agent.android.presentation.ui.about.AboutScreen*`, `AboutAcknowledgments*` | **New in Phase 23 / Task 9/9.** Single-file Compose About surface plus its private declarative acknowledgments list. |
| `ai.agent.android.presentation.ui.more.MoreScreen*` | **New in Phase 23 / Task 9/9.** Bottom-nav More hub Composable. The sibling `MoreViewModel` / `MoreUiState` remain inside the gate. |
| `ai.agent.android.presentation.ui.settings.provider.ProviderPickerScreen*`, `ProviderDetailScreen*` | **New in Phase 23 / Task 9/9.** Provider picker and per-provider configuration screens — covered by the catalog Roborazzi snapshots, not JVM unit tests. |
| `ai.agent.android.data.tools.local.appfunctions.*` | **New in Phase 23 / Task 9/9.** AppFunctions callee wrapper (`SearchAppFunction`); the platform `PlatformAppFunctionService` host needs the Android runtime to dispatch. |
| `ai.agent.android.data.tools.local.AgentAppFunctionService*`, `LocalAppFunctionManager`, `SearchTool*`, `DelegateTaskTool*` | Tool-execution Android glue (live HTTP / LLM bridge).                    |
| `ai.agent.android.data.logging.CrashlyticsTimberTree*` | Firebase Crashlytics Timber bridge; `getInstance()` paths need Google Play services on Android. |

## Justified zero-coverage packages

These appear as 0 % (or near-zero) but are intentional / out-of-scope for
unit-test coverage. They are documented here so a future reader does not
mistake them for missing work.

- **`presentation.theme`** — Material 3 colour / typography constants and a
  thin `Theme` composable. Pure declarative composition; no branching
  business logic to test. Excluded via the
  `ai.agent.android.presentation.theme.*` pattern above.
- **`presentation.ui`** (root entry-points) — `MainActivity` plus the
  `*ScreenKt` host shells under the top-level `presentation/ui/` directory.
  These are Android-runtime-bound Compose roots; their coverage is the
  domain of `connectedDebugAndroidTest`, not the JVM Kover pipeline.
- **`data.local.dao`** — Room DAO interfaces. Their generated `*_Impl`
  implementations are covered, the interface lines themselves are
  declarative.
- **`appfunctions_aggregated_deps`** — KSP-generated boilerplate; 5 lines.

## How to regenerate locally

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# HTML report (visual drill-down):
./gradlew :app:koverHtmlReportDebug
open app/build/reports/kover/htmlDebug/index.html

# XML report (machine-readable, used by CI parsers):
./gradlew :app:koverXmlReportDebug
# → app/build/reports/kover/reportDebug.xml

# Console summary (single number, useful in scripts):
./gradlew :app:koverLog
```

The Android-variant suffix (`Debug`) is mandatory — Kover exposes per-variant
tasks for projects that use the `com.android.application` plugin. Tasks like
`koverHtmlReport` (no suffix) exist but cover all variants merged together,
which is unnecessary for this single-variant project.

## CI integration — deferred

This PR does **not** add a `.github/workflows/coverage.yml` job that publishes
the HTML report as a PR artefact. The PAT used for the current Git remote
lacks the `workflow` scope required to push files into `.github/workflows/`,
so the same constraint that blocked Task 1/10's CI workflow applies here.

The eventual workflow should:

1. Run `./gradlew :app:koverHtmlReportDebug :app:koverXmlReportDebug` on each
   PR to publish the report (the build-failing threshold itself is already
   enforced locally and in `./gradlew check` via `koverVerifyDebug` — see
   *Enforced threshold* below).
2. Upload `app/build/reports/kover/htmlDebug/` and
   `app/build/reports/kover/reportDebug.xml` via `actions/upload-artifact`.
3. Optionally print the `koverLog` headline figure as a PR comment.

This artefact-publishing job is still pending; the coverage **gate**
(`koverVerifyDebug`) is already wired into `./gradlew check` and is not
blocked on it.

## What this baseline does **not** do

- It does **not** add coverage for `*Screen.kt` Composables — those need
  androidTest / Compose UI tests, which is a separate workstream.
- It does **not** cover the `release` variant — Kover's per-variant tasks are
  invoked with the `Debug` suffix throughout. `release` is irrelevant for
  unit-test coverage.

## Enforced threshold (current)

`koverVerifyDebug` fails the build when aggregate LINE coverage over the
unit-testable surface drops below **75 %** (raised from 70 % in Phase 23 /
Task 9/9). Today's measurement is **77.6 %**, giving the gate ~2.6 pp of
headroom against silent regression.

Kover 0.9.x has no rule-level `filters { ... }` block — that DSL element is
gated behind a future 0.10 release — so the gate is a single
application-wide rule operating over the globally-filtered class set in
`app/build.gradle.kts` → `kover { reports { filters { excludes { ... } } } }`.
The per-package floors in the tables above are **informational targets**;
promote them to enforced rules once Kover 0.10 ships.

Phase 23 / Task 9/9 also added several Compose-surface / Android-runtime
exclusions that fell through the existing wildcards because they live in
new sub-packages introduced during this phase:
`presentation.ui.navigation.*`, `presentation.ui.about.AboutScreen*` (+ its
private `AboutAcknowledgments` data list), `presentation.ui.more.MoreScreen*`,
`presentation.ui.settings.provider.{ProviderPickerScreen, ProviderDetailScreen}*`,
and `data.tools.local.appfunctions.*`. Without these, the aggregate would
sit at 73.8 % — i.e. the previously-claimed "~80 %" was already incorrect
because the new screens never made it into the filter when they shipped.

Raise the threshold deliberately as coverage grows; do **not** lower it
without team agreement.
