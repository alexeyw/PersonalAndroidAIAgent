# Test-Coverage Baseline (Phase 18 — Task 2/10)

This document captures the **starting** test-coverage numbers for the project at
the moment Kover is wired into the build. It exists to:

1. Make the impact of the upcoming string/prompt/constant refactors (Tasks 3–6)
   visible — those tasks shuffle a lot of lines and we want to see whether
   coverage moves up, down, or stays flat as a result.
2. Provide concrete percentages that Task 9/10 can use to seed `koverVerify`
   thresholds (currently the verify rule is **not configured** — Kover runs in
   pure measurement mode in this phase).

## Snapshot

| Field        | Value                                              |
|--------------|----------------------------------------------------|
| Date         | 2026-05-10                                         |
| Branch base  | `main` @ `2b43507`                                 |
| Kover        | 0.9.8 (plugin id `org.jetbrains.kotlinx.kover`)    |
| Variant      | `debug` (Android default unit-test variant)        |
| Test command | `./gradlew :app:testDebugUnitTest`                 |

## Aggregate

| Counter      | Coverage | Covered / Total |
|--------------|----------|-----------------|
| LINE         | **45.3%**| 3 601 / 7 952   |
| INSTRUCTION  | 35.9%    | 29 370 / 81 904 |
| BRANCH       | 31.4%    | 1 067 / 3 401   |
| METHOD       | 48.8%    | 703 / 1 440     |
| CLASS        | 67.7%    | 387 / 572       |

The headline number quoted by `./gradlew :app:koverLog` is the LINE coverage
(`application line coverage: 45.2557%`).

## Per-package (LINE)

Sorted alphabetically. `n/a`-style 0% entries on tiny packages (a few lines)
are typically pure-interface or DI-glue files that fall under the exclusion
filters in spirit but slip through because they live outside the
excluded-package wildcards — see "Known noise" below.

### `domain/` — 89.4% (1 454 / 1 627 lines)

| Package                                   | Coverage | Covered / Total |
|-------------------------------------------|----------|-----------------|
| `domain.constants`                        | 90.9%    | 10 / 11         |
| `domain.engine`                           | 92.8%    | 324 / 349       |
| `domain.engine.executors`                 | 75.4%    | 318 / 422       |
| `domain.models`                           | 96.4%    | 319 / 331       |
| `domain.pipelineio`                       | 92.9%    | 118 / 127       |
| `domain.prompt`                           | 100.0%   | 52 / 52         |
| `domain.repositories`                     | 33.3%    | 1 / 3           |
| `domain.usecases`                         | 94.0%    | 312 / 332       |

`domain` is the strongest layer — this matches the project rule that all
business logic must sit here and ship with unit tests. The `domain.repositories`
package contains only interface declarations; the 1/3 non-zero figure is
incidental (a `companion object` constant) and not a meaningful target.

### `data/` — 58.5% (950 / 1 625 lines)

| Package                                   | Coverage | Covered / Total |
|-------------------------------------------|----------|-----------------|
| `data.engine`                             | 52.7%    | 167 / 317       |
| `data.local`                              | 41.3%    | 160 / 387       |
| `data.local.dao`                          | 0.0%     | 0 / 7           |
| `data.local.models`                       | 92.6%    | 75 / 81         |
| `data.mappers`                            | 100.0%   | 38 / 38         |
| `data.mcp`                                | 77.1%    | 37 / 48         |
| `data.network`                            | 92.7%    | 38 / 41         |
| `data.prompt`                             | 86.8%    | 33 / 38         |
| `data.repositories`                       | 83.1%    | 301 / 362       |
| `data.services`                           | 44.0%    | 62 / 141        |
| `data.tools.local`                        | 27.7%    | 39 / 141        |
| `data.tools.local.executors`              | 0.0%     | 0 / 24          |

The notable holes here — `data.services` (foreground service / WorkManager
glue), `data.tools.local` (tool implementations), `data.tools.local.executors`
(LocalToolExecutor multibinding strategies), and `data.local.dao` (Room DAO
interfaces themselves) — are dominated by Android-runtime-bound code that is
hard to exercise with plain JVM unit tests. They are explicit candidates for
either Robolectric / instrumented coverage or for refactoring extractions in
later phases.

### `presentation/` — 25.5% (1 197 / 4 687 lines)

| Package                                   | Coverage | Covered / Total |
|-------------------------------------------|----------|-----------------|
| `presentation.notifications`              | 0.0%     | 0 / 36          |
| `presentation.receivers`                  | 0.0%     | 0 / 8           |
| `presentation.state`                      | 0.0%     | 0 / 4           |
| `presentation.theme`                      | 0.0%     | 0 / 36          |
| `presentation.ui`                         | 0.0%     | 0 / 42          |
| `presentation.ui.chat`                    | 29.8%    | 431 / 1 448     |
| `presentation.ui.components`              | 4.2%     | 5 / 118         |
| `presentation.ui.memory`                  | 16.7%    | 43 / 258        |
| `presentation.ui.models`                  | 35.9%    | 80 / 223        |
| `presentation.ui.monitoring`              | 19.1%    | 30 / 157        |
| `presentation.ui.orchestrator`            | 27.7%    | 290 / 1 047     |
| `presentation.ui.orchestrator.components` | 0.0%     | 0 / 265         |
| `presentation.ui.prompts`                 | 17.9%    | 43 / 240        |
| `presentation.ui.settings`                | 27.5%    | 110 / 400       |
| `presentation.ui.splash`                  | 43.9%    | 36 / 82         |
| `presentation.ui.taskmonitor`             | 44.3%    | 93 / 210        |
| `presentation.ui.tools`                   | 31.9%    | 36 / 113        |

The non-zero numbers in `presentation.ui.*` come from `*ViewModel` and
`*UiState` classes (covered by JVM unit tests). The 0% numbers represent the
`*Screen.kt` Composables themselves — those need androidTest / Compose UI tests
to register coverage and are intentionally not the focus of this phase.

### Other

| Package                                   | Coverage | Covered / Total |
|-------------------------------------------|----------|-----------------|
| `ai.agent.android` (root — `App.kt`)      | 0.0%     | 0 / 8           |
| `appfunctions_aggregated_deps`            | 0.0%     | 0 / 5           |

`appfunctions_aggregated_deps` is generated by the AppFunctions KSP processor
and could be added to the exclusion list later; at 5 lines it does not move
the aggregate.

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
| `*ComposableSingletons*`, `ComposableSingletons$*`| Compose-compiler-emitted lambda singletons.                              |
| `*Preview`, `*PreviewKt`                          | Compose preview files (project convention: `*Preview.kt`).               |
| `ai.agent.android.di.*`                           | Hilt DI modules — only `@Provides` wiring, no business behaviour.        |
| `ai.agent.android.BuildConfig`                    | Android-Gradle-generated build constants.                                |
| `*.databinding.*`, `*.BR`                         | DataBinding generated code (defensive — project does not use DataBinding today). |
| `@androidx.compose.ui.tooling.preview.Preview`    | Belt-and-braces annotation filter for preview functions outside `*Preview.kt`. |

## Known noise

These packages show 0% but are intentional or out-of-scope for unit-test
coverage:

- `presentation.theme` — Material 3 colour / typography constants. Pure data.
- `presentation.notifications`, `presentation.receivers` — Android-runtime
  classes (`NotificationManager`, `BroadcastReceiver`). Need instrumented tests.
- `data.local.dao` — Room DAO **interfaces**. The generated `*_Impl`
  implementations are correctly excluded; the interface lines themselves
  (constants in `companion object`s) account for the 7 reported lines.
- `data.tools.local.executors` — `LocalToolExecutor` strategies wired through
  Hilt multibinding. Trivial dispatch code; tests live one level up.

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
   PR with `continue-on-error: true` (until Task 9/10 enables `koverVerify`).
2. Upload `app/build/reports/kover/htmlDebug/` and
   `app/build/reports/kover/reportDebug.xml` via `actions/upload-artifact`.
3. Optionally print the `koverLog` headline figure as a PR comment.

This will be added in Task 9/10 alongside the `./gradlew check` aggregator
task, or sooner if the PAT scope is updated.

## What this baseline does **not** do

- It does **not** introduce verification thresholds — `koverVerify` runs but
  does nothing because no `verify { rule { … } }` block is configured. This
  changes in Task 9/10.
- It does **not** add coverage for `*Screen.kt` Composables — those need
  androidTest / Compose UI tests, which is a separate workstream.
- It does **not** cover the `release` variant — Kover's per-variant tasks are
  invoked with the `Debug` suffix throughout. `release` is irrelevant for
  unit-test coverage.
