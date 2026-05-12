# Testing

This document describes the test strategy and tooling used across the
project — what kinds of tests live where, how to run them, and what the
quality bar is. It covers both unit tests (`app/src/test/`) and Compose /
instrumented tests (`app/src/androidTest/`).

## Coverage policy

Target **100% logic coverage** in newly written or modified code in the
`domain` and `data` layers. The build-side gate is more lenient: a
**70% LINE aggregate** threshold across the module, with per-package
decomposition documented in
[`coverage-baseline.md`](coverage-baseline.md). The full policy — what is
counted, what is excluded, and how regressions are handled — lives in
[`static-analysis.md`](static-analysis.md).

Every public method in `domain` and `data` should have at least one unit
test.

## Coverage measurement (Kover)

Test coverage is measured by
[Kover](https://kotlin.github.io/kotlinx-kover/gradle-plugin/) (Gradle plugin
`org.jetbrains.kotlinx.kover`, version pinned in
`gradle/libs.versions.toml`). Generated code (Hilt factories and modules,
Room `*_Impl` DAOs, Compose previews and synthetic singletons, DI modules,
`BuildConfig`) is excluded by the filter in `app/build.gradle.kts`.

Run locally:

```bash
./gradlew :app:koverHtmlReportDebug   # HTML report — drill-down per package/file
./gradlew :app:koverXmlReportDebug    # XML report — for CI parsers
./gradlew :app:koverLog               # console one-liner: line coverage %
```

Report locations:

- `app/build/reports/kover/htmlDebug/index.html` — primary visual entry
  point.
- `app/build/reports/kover/reportDebug.xml` — JaCoCo-compatible XML.

The current baseline numbers and the rationale behind every exclusion are in
[`coverage-baseline.md`](coverage-baseline.md).

## Unit tests (JUnit + MockK)

### File layout

Mirror the source-file path under the `test` source set:

```
src/main/.../domain/usecase/SendMessageUseCase.kt
src/test/.../domain/usecase/SendMessageUseCaseTest.kt
```

### Naming pattern

```kotlin
@Test
fun `given <precondition> when <action> then <expected result>`() { ... }
```

### Standard structure (Given–When–Then)

```kotlin
@Test
fun `given valid input when execute then emits success state`() {
    // Given
    val input = "Hello"
    every { mockRepository.findChat(any()) } returns flowOf(fakeChat)

    // When
    val result = useCase.execute(input).first()

    // Then
    assertThat(result).isInstanceOf(Result.Success::class.java)
}
```

### Mocking

- Use **MockK** for all mocking: `mockk<T>()`, `coEvery { }`, `coVerify { }`.
- Use `relaxed = true` only when the mock's return values are irrelevant to
  the test.
- Use `spyk()` only for testing real class behaviour with partial overrides.

### ViewModel tests

- Use `kotlinx-coroutines-test` with a `StandardTestDispatcher`.
- Always call `advanceUntilIdle()` after triggering state changes.
- Use the **Turbine** library when asserting on a sequence of `Flow`
  emissions.

### Use-case tests

- Cover every branch: happy path, error / exception, empty state, boundary
  values.
- Mock **all** external dependencies (repositories, dispatchers, clocks).

## Instrumented / Compose UI tests

### Setup

```kotlin
@get:Rule
val composeTestRule = createAndroidComposeRule<MainActivity>()
```

### Naming

`<ScreenName>ScreenTest.kt` under the `androidTest` source set.

### Must-test scenarios per screen

- The initial loading state renders correctly.
- The success state renders the expected content.
- The error state shows the error UI with a retry action.
- User interactions (click, swipe, input) dispatch the correct ViewModel
  events.

## What not to test

- Generated code (Room DAO auto-generated implementations, Hilt factories).
- Trivial data classes with no logic.
- `@Preview` Composables.

## CI gate

All tests, static analysis and coverage must pass via:

```bash
./gradlew check
```

This is the same task that CI runs on every pull request. It executes
detekt, ktlint, Android lint, the unit-test suite, and `koverVerifyDebug`.
Lint must pass with no new warnings.
