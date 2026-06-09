# Contributing

Thank you for taking an interest in the **On-Device AI Agent for Android**.
This document covers everything you need to know to set up the project,
make a change, and open a pull request.

## Pre-release notice

The project is currently at version **0.4.0** and is published primarily
for review and experimentation. Expect breaking changes between minor
versions: the Kotlin public surface, pipeline JSON schema, on-device
storage formats, and settings layout are **not** stability-guaranteed
until `1.0.0`. See the *Pre-release notice* in [`README.md`](README.md) for
details, and [`SECURITY.md`](SECURITY.md) for the security posture.

## Code of Conduct

By participating in this project you agree to abide by our
[Code of Conduct](CODE_OF_CONDUCT.md), adapted from Contributor Covenant
2.1.

## Where to start

- The [roadmap](docs/roadmap.md) describes where the project is headed
  and which directions welcome outside help.
- Issues labelled
  [`good first issue`](https://github.com/alexeyw/PersonalAndroidAIAgent/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
  are scoped to be approachable without deep knowledge of the codebase:
  each one states the motivation, the files involved, and concrete
  acceptance criteria. Issues labelled
  [`help wanted`](https://github.com/alexeyw/PersonalAndroidAIAgent/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22)
  mark work where contributions are especially welcome.
- [`docs/extending.md`](docs/extending.md) has step-by-step recipes for
  the most self-contained contributions — new node types, tools, cloud
  providers, and prompt variables.
- Leave a comment on an issue before starting non-trivial work, so effort
  is not duplicated and the approach can be sanity-checked early.

## Dev setup

Required toolchain:

- **JDK 21** — required to run the unit-test suite. Roborazzi's
  Robolectric backend in `:catalog` only renders against the project's
  `minSdk 36` on JDK 21. Production code still compiles to
  `JavaVersion.VERSION_17` / `JvmTarget.JVM_17` — building the APK works
  on JDK 17 — but `./gradlew check` (the merge gate) needs JDK 21. The
  Android Studio bundled JBR ships JDK 21 already, so installing
  Android Studio satisfies the requirement out of the box.
- **Android Studio** — current stable channel (or any IDE that supports
  AGP 9.2.x and Kotlin 2.3.x).
- **Android SDK** — install platform **API 37** (`compileSdk` +
  `targetSdk`). Minimum runtime is API 36 (Android 16).
- **NDK** is not required.

Local configuration:

- Create `local.properties` in the repository root with at least
  `sdk.dir=<absolute path to your Android SDK>`. The file is gitignored
  and will never be committed.
- All other configuration is checked in (`gradle.properties`, the version
  catalog under `gradle/libs.versions.toml`, and the build scripts under
  `app/`).

## Build & test

Common commands:

```bash
./gradlew assembleDebug   # build the debug APK
./gradlew test            # run JVM unit tests
./gradlew check           # full local quality gate (see below)
./gradlew bundleRelease   # build the Play-Store-shaped AAB (R8-minified)
```

The `release` variant runs through R8 + resource shrinking + Jansi-native
stripping; the keep rules live in [`app/proguard-rules.pro`](app/proguard-rules.pro)
and the full release playbook (signing posture, AAB build, APK size
breakdown, future-keystore plan) lives in
[`docs/release.md`](docs/release.md).

`./gradlew check` aggregates the same checks CI runs on every pull
request:

- **detekt** — Kotlin static analysis (configured in
  [`config/detekt/detekt.yml`](config/detekt/detekt.yml)).
- **ktlint** — formatting.
- **Android lint** (`lintDebug`).
- **Unit tests** (`testDebugUnitTest`).
- **Kover** coverage verification (`koverVerifyDebug`).

Run `./gradlew check` **locally before pushing**. Pushing without running
it just trades local feedback for slower CI feedback.

## Branch model

Development proceeds in thematic batches, each integrated on a
long-lived branch before reaching `main`:

- `main` is the default branch and always reflects shipped, reviewed
  work. Every commit on `main` is expected to pass `./gradlew check`.
- Active development happens on integration branches named `phase/<N>`.
  Individual changes branch off the current integration branch as
  `feature/<kebab-name>`, and their pull requests **target the open
  `phase/<N>` branch**, not `main`. When the batch is complete, the
  integration branch is merged into `main` with a merge commit and
  deleted.
- If no `phase/<N>` branch is currently open (check the
  [branch list](https://github.com/alexeyw/PersonalAndroidAIAgent/branches)),
  target `main` directly. When in doubt, open the PR against `main` —
  maintainers will retarget it if an integration branch is active.
- Direct pushes to `main` are not permitted; every change lands through a
  reviewed PR.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/). The
following prefixes are accepted:

| Prefix       | Meaning                                                    |
|--------------|------------------------------------------------------------|
| `feat:`      | A new user-facing feature.                                 |
| `fix:`       | A bug fix.                                                 |
| `chore:`     | Maintenance work that is neither a feature nor a fix.      |
| `docs:`      | Documentation-only changes.                                |
| `test:`      | Adding or correcting tests, no production change.          |
| `refactor:`  | Internal restructuring with no behaviour change.           |
| `build:`     | Build system, CI config, dependency or toolchain change.   |

Guidelines:

- Keep the subject line in imperative mood (`add`, `fix`, `remove`) and
  under 72 characters.
- Reference an issue in the body or footer with `Closes #N` (auto-close
  on merge) or `Refs #N`.
- Squash trivial fixups before opening the PR.

## Pull requests

Before requesting review, please confirm:

- [ ] Tests added or updated for the change.
- [ ] `./gradlew check` passes locally.
- [ ] Public documentation is updated where the change affects user-
      facing behaviour, the public API surface, or the build / dev setup.
- [ ] `FILE_MAP.md` (the file map under
      `app/src/main/java/app/knotwork/android/`, and the root one for
      top-level changes) is updated when files or directories are added,
      moved, or removed.
- [ ] The PR description summarises **what** changed, **why** it changed,
      and lists the linked issue.

Maintainers will review on a best-effort basis; please be patient and
willing to iterate. Small, focused PRs are easier to review and land than
large omnibus ones.

## Language policy

All public artifacts in this repository are in **English**:

- Documentation (`README.md`, `CHANGELOG.md`, files under `docs/`, this
  document, and any future contributor-facing material).
- Code comments and KDoc.
- Commit messages and pull request descriptions.
- User-visible strings inside the application.

This applies to new content as well as to edits of existing content. If
you encounter non-English text in any of the locations above, treat it as
a bug.

## Further reading

- [`docs/roadmap.md`](docs/roadmap.md) — where the project is headed and
  which directions welcome help.
- [`docs/code-style.md`](docs/code-style.md) — Kotlin style and naming
  conventions.
- [`docs/testing.md`](docs/testing.md) — test strategy, coverage policy,
  Kover commands.
- [`docs/api-conventions.md`](docs/api-conventions.md) — conventions for
  LiteRT-LM, AppFunctions, MCP, cloud LLM APIs, Room, DataStore.
- [`docs/extending.md`](docs/extending.md) — recipes for adding a new
  node type, tool, cloud provider, or prompt variable.
- [`docs/architecture.md`](docs/architecture.md) — Clean Architecture
  overview, pipeline engine, integrations.
- [`docs/user-guide.md`](docs/user-guide.md) — end-user documentation.
- [`docs/static-analysis.md`](docs/static-analysis.md) — detekt / ktlint
  / lint configuration and severity policy.
- [`docs/release.md`](docs/release.md) — release-build playbook (R8
  keep rules, signing posture, AAB build, APK size breakdown).
- [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) — community standards.
- [`SECURITY.md`](SECURITY.md) — threat model and how to report a
  vulnerability.
