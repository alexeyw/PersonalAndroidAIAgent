# Contributing

Thank you for taking an interest in the **On-Device AI Agent for Android**.
This document covers everything you need to know to set up the project,
make a change, and open a pull request.

## Pre-release notice

The project is currently at version **0.1.0** and is published primarily
for review and experimentation. Expect breaking changes between minor
versions: the Kotlin public surface, pipeline JSON schema, on-device
storage formats, and settings layout are **not** stability-guaranteed
until `1.0.0`. See the *Pre-release notice* in [`README.md`](README.md) for
details, and [`SECURITY.md`](SECURITY.md) for the security posture.

## Code of Conduct

By participating in this project you agree to abide by our
[Code of Conduct](CODE_OF_CONDUCT.md), adapted from Contributor Covenant
2.1.

## Dev setup

Required toolchain:

- **JDK 17** — the project compiles against `JavaVersion.VERSION_17` and
  targets `JvmTarget.JVM_17`. Newer JDKs are not supported by AGP at the
  pinned version.
- **Android Studio** — current stable channel (or any IDE that supports
  AGP 9.2.x and Kotlin 2.3.x).
- **Android SDK** — install platform **API 36** (compileSdk) and
  **API 37** (targetSdk). Minimum runtime is API 36 (Android 16).
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
```

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

- `main` is the default branch and always reflects shipped, reviewed work.
- Feature branches use the pattern `feature/<kebab-name>`.
- Open pull requests against `main`.
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
      `app/src/main/java/ai/agent/android/`, and the root one for
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
- [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) — community standards.
- [`SECURITY.md`](SECURITY.md) — threat model and how to report a
  vulnerability.
