package app.knotwork.android.testing

/**
 * Marks an instrumented test that **cannot produce a meaningful verdict on an
 * emulator** and is therefore excluded from every automated CI run by name.
 *
 * This annotation is the mechanism behind an explicit exclusion *list*: the
 * emulator workflow passes the annotation's fully-qualified name to
 * `AndroidJUnitRunner` as `notAnnotation`, so an excluded class is filtered out
 * before it runs rather than degrading into a silent skip inside the suite. The
 * distinction matters — a test that reports "skipped" from inside a green run is
 * indistinguishable from a test that quietly stopped covering anything, whereas
 * an exclusion list has to be edited (and justified) to grow.
 *
 * Two rules keep the list honest:
 *
 * 1. [reason] is mandatory and must state *what about a real device* the test
 *    needs — not merely that it fails on CI.
 * 2. `InstrumentedTestExclusionGuardTest` (in the JVM test source set) pins the
 *    set of annotated classes against an explicit roster, so adding an exclusion
 *    is a deliberate edit in two places instead of a one-line escape hatch.
 *
 * An excluded test is **not** dead: it still compiles under `./gradlew check`
 * (the instrumented source set is compiled by the merge gate) and is still run
 * during the manual smoke on the reference device.
 *
 * @property reason Why an emulator cannot exercise this test — the platform
 *   capability, permission protection level, hardware or external service that
 *   only a real device provides. Shown by the guard test when the roster drifts.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class DeviceOnlyInstrumentedTest(val reason: String)
