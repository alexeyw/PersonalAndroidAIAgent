# Code Style

This document describes the Kotlin code style enforced in this project. It
applies to every file under `app/src/main/java/` and the corresponding test
source sets. Automated checks (detekt, ktlint, Android lint) are wired into
`./gradlew check`; see [`docs/static-analysis.md`](static-analysis.md) for the
exact configuration and severity policy.

## Kotlin style

- Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Use **ktlint** for automatic formatting. Run `./gradlew ktlintFormat` before
  every commit; the same checks run on CI as part of `./gradlew check`.
- Max line length: **120 characters**.
- Use **trailing commas** in multi-line collections and function signatures.
- Prefer **expression bodies** for single-expression functions.
- Use **named arguments** when calling functions with three or more parameters
  of the same type.

## Naming conventions

| Entity                 | Convention                          | Example                        |
|------------------------|-------------------------------------|--------------------------------|
| Classes / interfaces   | PascalCase                          | `AgentOrchestrator`            |
| Functions / variables  | camelCase                           | `executeToolCall()`            |
| Constants              | SCREAMING_SNAKE_CASE                | `MAX_CONTEXT_TOKENS`           |
| Composables            | PascalCase, verb-free noun          | `ChatScreen`, `MessageBubble`  |
| ViewModels             | `<Feature>ViewModel`                | `ChatViewModel`                |
| Use cases              | `<Verb><Noun>UseCase`               | `SendMessageUseCase`           |
| Repositories (i-face)  | `<Noun>Repository`                  | `ModelRepository`              |
| Repositories (impl)    | `<Noun>RepositoryImpl`              | `ModelRepositoryImpl`          |
| DI modules             | `<Feature>Module`                   | `LiteRtModule`                 |

## Architecture constraints

- **`domain` layer** — zero Android SDK imports (`android.*`, `androidx.*`).
  Pure Kotlin plus Coroutines only. Dependencies flow inward: `data` and
  `presentation` depend on `domain`; `domain` depends on nothing else in the
  project.
- **`presentation` layer** — Composables observe `StateFlow` / `UiState` from
  a `ViewModel`. No direct repository or use-case calls from inside a
  `@Composable` function.
- **`data` layer** — implements repository interfaces declared in `domain`.
  Heavy I/O runs on `Dispatchers.IO`.

For the broader layering rationale, see
[`docs/architecture.md`](architecture.md).

## Compose guidelines

- Every screen-level Composable receives only a `ViewModel` (or a `UiState` +
  callback lambdas) — never raw data types from `data` or `domain`.
- Use `remember { }` and `derivedStateOf { }` to avoid unnecessary
  recompositions.
- Use `LazyColumn` / `LazyRow` for all scrollable lists.
- Separate stateful (screen-level) and stateless (component-level)
  Composables.
- Mark preview-only Composables with `@Preview` and keep them in dedicated
  `*Preview.kt` files, separate from the production Composables they render.

## Coroutines & Flow

- Launch coroutines from `viewModelScope` (inside a `ViewModel`) or from a
  `CoroutineScope` injected via Hilt.
- **Never** use `GlobalScope`.
- Use `StateFlow` for UI state and `SharedFlow` for one-shot events.
- Handle exceptions via `CoroutineExceptionHandler` or `runCatching` —
  raw exceptions must not propagate to the presentation layer.

## Dependency injection (Hilt)

- Use `@HiltViewModel` for every `ViewModel`.
- Provide external dependencies (Retrofit, Room, LiteRT, Koog) in dedicated
  `@Module` objects, grouped by feature.
- Prefer constructor injection over field injection.

## Enforcement

Style is enforced by the build, not by reviewer discretion. Before pushing a
branch, run:

```bash
./gradlew check
```

This aggregates detekt, ktlint, Android lint, unit tests and Kover. The same
task gates every pull request in CI. See
[`docs/static-analysis.md`](static-analysis.md) for the rule set, current
baselines, and how to handle a new finding.
