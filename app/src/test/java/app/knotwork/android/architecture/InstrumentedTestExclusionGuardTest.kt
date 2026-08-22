package app.knotwork.android.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guard over the **instrumented-test exclusion list** — the set of instrumented
 * tests the automated emulator runs deliberately do not execute.
 *
 * The emulator workflow excludes those tests by passing the fully-qualified name
 * of the `DeviceOnlyInstrumentedTest` annotation to `AndroidJUnitRunner` as
 * `notAnnotation`. That mechanism has two silent-failure modes, and this test
 * closes both:
 *
 * 1. **The list grows without anyone deciding.** Annotating one more class is a
 *    one-line edit that shrinks CI coverage and leaves the build green. Pinning
 *    the annotated set against [EXPECTED_EXCLUSIONS] makes every addition a
 *    deliberate edit in two files, with a reason that a reviewer reads.
 * 2. **The list quietly stops applying.** The workflow names the annotation as a
 *    *string*; moving or renaming the annotation class still compiles, and the
 *    filter then matches nothing — the exclusion list becomes fiction while
 *    every run stays green. So the FQN is pinned from three directions here:
 *    the declaration in the instrumented source set, the constant below, and
 *    the literal in the workflow file.
 *
 * Konsist is used rather than reflection because the annotated classes live in
 * the `androidTest` source set, which is not on the JVM unit-test classpath —
 * they can be parsed, not loaded.
 */
class InstrumentedTestExclusionGuardTest {

    @Test
    fun `the instrumented exclusion list matches the pinned roster`() {
        val excluded = excludedClasses().map { it.name }.sorted()

        assertEquals(
            "The set of instrumented tests excluded from the automated emulator runs changed. Excluding a test " +
                "removes it from every CI run, so the roster in this guard has to be updated in the same change " +
                "— together with the exclusion table in docs/testing.md, which is what a reader consults. " +
                "Add an entry only when an emulator genuinely cannot reach a verdict, never to silence a failure.",
            EXPECTED_EXCLUSIONS.sorted(),
            excluded,
        )
    }

    @Test
    fun `every excluded instrumented test states why an emulator cannot run it`() {
        excludedClasses().forEach { klass ->
            val reason = klass
                .annotations
                .first { it.name == ANNOTATION_SIMPLE_NAME }
                .arguments
                .firstOrNull { it.name == REASON_ARGUMENT }
                ?.value
                .orEmpty()

            assertTrue(
                "${klass.name} is excluded from the automated emulator runs but gives no `$REASON_ARGUMENT`. " +
                    "State what about a real device the test needs — the platform capability, permission " +
                    "protection level, hardware or external service an emulator cannot provide. \"It fails on " +
                    "CI\" is not a reason to stop running it.",
                reason.isNotBlank(),
            )
        }
    }

    @Test
    fun `the exclusion annotation is where the emulator workflow says it is`() {
        val declaration = instrumentedScope
            .classes()
            .single { it.name == ANNOTATION_SIMPLE_NAME }

        assertEquals(
            "The exclusion annotation moved or was renamed. The emulator workflow names it as a plain string in " +
                "`notAnnotation`, so a rename does not break the build — it silently stops excluding anything. " +
                "Update $WORKFLOW_PATH and the constant in this guard in the same change.",
            ANNOTATION_FQN,
            declaration.fullyQualifiedName,
        )

        val workflow = workflowFile().readText()
        assertTrue(
            "$WORKFLOW_PATH no longer passes `$ANNOTATION_FQN` to the instrumentation runner. Without that " +
                "argument the exclusion list is inert and the excluded tests run anyway — which is not a failure " +
                "anyone would notice, because they degrade to skips inside a green suite.",
            workflow.contains(ANNOTATION_FQN),
        )
    }

    /**
     * Classes in the instrumented source set carrying the exclusion annotation.
     *
     * The annotation's own declaration is not one of them — it is matched by
     * name here only through the classes that *apply* it.
     */
    private fun excludedClasses(): List<KoClassDeclaration> = instrumentedScope
        .classes()
        .filter { klass -> klass.annotations.any { it.name == ANNOTATION_SIMPLE_NAME } }

    /**
     * Resolves the emulator workflow from the Gradle root, walking up from the
     * test JVM's working directory (the `:app` module, not the root project).
     */
    private fun workflowFile(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            val workflow = File(candidate, WORKFLOW_PATH)
            if (workflow.isFile) return workflow
            candidate = candidate.parentFile
        }
        error("Could not find $WORKFLOW_PATH walking up from ${File("").absolutePath}")
    }

    private companion object {
        /**
         * Konsist scope over the instrumented source set. Pinned to the single
         * directory rather than the whole project for the same reason as
         * [ArchitectureScope]: a project-wide scope also parses stale git
         * worktrees under `.claude/worktrees/`, which would make the verdict
         * depend on the developer's machine.
         */
        val instrumentedScope: KoScope = Konsist.scopeFromDirectory("app/src/androidTest")

        const val ANNOTATION_SIMPLE_NAME = "DeviceOnlyInstrumentedTest"
        const val ANNOTATION_FQN = "app.knotwork.android.testing.$ANNOTATION_SIMPLE_NAME"
        const val REASON_ARGUMENT = "reason"
        const val WORKFLOW_PATH = ".github/workflows/instrumented.yml"

        /**
         * The exclusion list itself. One entry today: the AppFunctions
         * end-to-end suite, whose permission gate no stock emulator image can
         * open for a third-party caller.
         */
        val EXPECTED_EXCLUSIONS = listOf("AppFunctionsEndToEndTest")
    }
}
