# Testing

This document describes the test strategy and tooling used across the
project — what kinds of tests live where, how to run them, and what the
quality bar is. It covers both unit tests (`app/src/test/`) and Compose /
instrumented tests (`app/src/androidTest/`).

## Coverage policy

Target **100% logic coverage** in new or modified code of the `domain` and
`data` layers; the build-side gate is **75% LINE aggregate** across the
module (raised from 70 % — see
[`coverage-baseline.md`](coverage-baseline.md) § *Enforced threshold*).
Per-package decomposition and informational per-package targets are also
documented in [`coverage-baseline.md`](coverage-baseline.md); the full
policy — what is counted, what is excluded, and how regressions are
handled — lives in [`static-analysis.md`](static-analysis.md).

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

> **Note:** instrumented tests are **not** part of the automated CI gate —
> they require a connected device or emulator and are run manually. See
> [What the automated gate does NOT cover](#what-the-automated-gate-does-not-cover).

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

## What the automated gate does NOT cover

The entire CI gate is **JVM-based**: plain JUnit + MockK unit tests,
Robolectric-driven Compose tests, and Roborazzi screenshot tests (in the
`:catalog` module). CI runs on `ubuntu-latest` with **no emulator and no
physical device attached**. This is a deliberate trade-off — it keeps the
gate fast, deterministic, and runnable on every pull request without a
device farm — but it has a hard consequence that contributors should not
overestimate:

> **A green `./gradlew check` is NOT a guarantee that the app works on a
> physical device.** It guarantees that the JVM-testable logic behaves as
> specified. Everything that needs real Android system services, native
> libraries, or hardware is outside the gate.

The areas below are **not** exercised by CI, and why:

- **Instrumented / Espresso / on-device tests.** The
  `app/src/androidTest/` source set (~50 test classes: Room DAO tests,
  schema-migration tests on `MigrationTestHelper`, Compose UI flow tests,
  the AppFunctions end-to-end test) is neither run **nor even compiled**
  by `./gradlew check`. A change can break the instrumented-test build and
  CI stays green — compile them locally with
  `./gradlew :app:compileDebugAndroidTestKotlin` and run them with
  `./gradlew connectedDebugAndroidTest` on a connected device.
- **Real TalkBack navigation.** `TalkBackHappyPathsTest` (in the
  `:catalog` test source set) only asserts a structural pre-condition:
  every surface on the ratified happy paths publishes focusable
  interactive nodes with non-blank content descriptions. It does **not**
  drive the actual screen reader — the AccessibilityService bridge cannot
  be toggled from a Compose test. Whether TalkBack focus order, custom
  actions, and announcements actually work is verified only by a manual
  walkthrough with TalkBack enabled.
- **LiteRT-LM inference.** The native inference engine and real model
  weights never run in CI. Unit tests mock the engine boundary; model
  loading, token streaming, delegate selection (CPU/GPU), and memory
  behaviour under real weights are device-only concerns.
- **AppFunctions caller/callee.** The end-to-end test that resolves
  function metadata and invokes a function through
  `AppFunctionManager.executeAppFunction(...)` is an instrumented test,
  and it additionally skips on stock Android 16 because the
  `EXECUTE_APP_FUNCTIONS` permission is signature-level. The full
  caller → callee round-trip is verifiable only on a device build where
  the gate applies.
- **Opening the SQLCipher-encrypted database.** Robolectric cannot load
  the SQLCipher native library, so JVM tests never open the real
  encrypted database. That the passphrase provisioning, keystore-backed
  storage, and encrypted open actually succeed is observable only on a
  device.
- **Foreground Service and WorkManager.** Unit tests mock the lifecycle
  and scheduling boundaries. Real service start/stop semantics,
  notification behaviour, Doze interactions, and worker execution under
  OS constraints are not reproduced on the JVM.

### Compensating control: manual smoke on the reference device

These gaps are covered by a **manual smoke test on the reference
device — Samsung Galaxy S25 Ultra (Android 16)** — performed before every
integration merge into `main`, plus a manual TalkBack walkthrough of the
ratified happy paths. The pre-release quality gate in
[`release.md`](release.md) § *Quality gate before release* builds on the
same rule: automated checks first, manual on-device verification as the
final word.

This compromise is reasonable for a small-team project without a device
farm, but it is a compromise. If a change touches any of the areas listed
above, do not rely on CI alone — state in the pull request what was
verified on-device and how.
