package app.knotwork.android.presentation.ui.chat.home

import kotlinx.coroutines.flow.SharedFlow
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the instrumented mock factory against a one-shot event flow it does not
 * stub.
 *
 * `ChatHomeScreen` collects each delegate's `SharedFlow` one-shots from a
 * `LaunchedEffect`. In the instrumented tests the delegates are **relaxed**
 * MockK mocks, and a relaxed mock of a `SharedFlow` does not behave like an
 * empty flow: collecting one throws `KotlinNothingValueException`, which escapes
 * the effect and takes the whole screen down. So
 * `ChatHomeViewModelMockFactory` stubs every collected flow with a
 * `MutableSharedFlow()` that never completes — a list that has to stay in step
 * with the screen, and did not.
 *
 * **This test exists because that drift is invisible to `./gradlew check`.**
 * Adding `attachmentReplacedEvents` to the attachment delegate cost nothing at
 * compile time, passed every unit test, and broke **33 instrumented tests across
 * six classes** — every test that renders the chat screen. It surfaced only on
 * the emulator matrix, which does not run on phase branches, so it stayed
 * invisible for twenty-one merges. A one-line stub fixes today's instance; this
 * fixes the class.
 *
 * The list of delegates is **derived, not written**: any property of
 * [ChatHomeViewModel] whose type is a `ChatHome*Delegate` is inspected, so a new
 * delegate is covered the day it is added rather than the day someone remembers
 * this file. Reflection is over `Class` objects only — nothing is instantiated,
 * so no Android framework call is reached.
 *
 * The factory is read as **source text** rather than executed: it lives in the
 * `androidTest` source set, which this JVM suite cannot load. That is sound
 * because the assertion is about a name appearing in a stub, and
 * `app/build.gradle.kts` declares `src/androidTest` as an input of every `Test`
 * task — without which this guard would answer from a cached run of the very
 * edit it polices.
 */
class ChatHomeMockFactoryCoverageTest {

    @Test
    fun `given every delegate event flow when the mock factory is read then each one is stubbed`() {
        val factory = File(MOCK_FACTORY_PATH)
        assertTrue(
            "Expected the instrumented mock factory at $MOCK_FACTORY_PATH. If it moved, move this " +
                "guard's path with it — a guard that cannot find its subject passes everything.",
            factory.isFile,
        )
        val source = factory.readText()

        val missing = eventFlowNames().filterNot { name -> source.contains(".$name }") }

        assertTrue(
            "ChatHomeViewModelMockFactory does not stub $missing. A relaxed MockK mock of a SharedFlow " +
                "throws KotlinNothingValueException when collected, so every instrumented test that " +
                "renders ChatHomeScreen will fail on the emulator — where nothing in `check` can see it. " +
                "Add `every { <delegate>.<flow> } returns MutableSharedFlow()` to the factory.",
            missing.isEmpty(),
        )
    }

    @Test
    fun `given the reflection walk when it runs then it finds the known event flows`() {
        // Without this, a refactor that renamed the delegates or their flows
        // would leave the walk finding nothing — and a guard over an empty set
        // passes forever. Two known names rather than a count, so adding an
        // event flow never has to edit this test.
        val names = eventFlowNames()

        assertTrue("Expected the walk to find event flows, found none.", names.isNotEmpty())
        assertTrue(
            "Expected the walk to reach the attachment delegate's flows, found $names.",
            "attachmentReplacedEvents" in names && "attachmentErrorEvents" in names,
        )
    }

    /**
     * Every `SharedFlow`-typed property exposed by a delegate of
     * [ChatHomeViewModel], by getter name.
     *
     * @return Property names such as `attachmentReplacedEvents`, deduplicated.
     */
    private fun eventFlowNames(): List<String> = ChatHomeViewModel::class.java.methods
        .filter { it.parameterCount == 0 && it.returnType.simpleName.matches(DELEGATE_TYPE) }
        .map { it.returnType }
        .distinct()
        .flatMap { delegate ->
            delegate.methods
                .filter { it.parameterCount == 0 && SharedFlow::class.java.isAssignableFrom(it.returnType) }
                .mapNotNull { propertyName(it.name) }
        }
        .distinct()
        .sorted()

    /**
     * Turns a getter name into the property it exposes.
     *
     * @param getter Reflected method name.
     * @return The property name, or `null` when the method is not a getter.
     */
    private fun propertyName(getter: String): String? = getter
        .takeIf { it.startsWith("get") && it.length > "get".length }
        ?.removePrefix("get")
        ?.replaceFirstChar { it.lowercase() }

    private companion object {
        /** Types whose properties are inspected; matched on name so new delegates are included. */
        val DELEGATE_TYPE = Regex("""ChatHome\w+Delegate""")

        /** The instrumented factory, relative to the `:app` module directory the test runs in. */
        const val MOCK_FACTORY_PATH =
            "src/androidTest/java/app/knotwork/android/presentation/ui/chat/home/ChatHomeViewModelMockFactory.kt"
    }
}
