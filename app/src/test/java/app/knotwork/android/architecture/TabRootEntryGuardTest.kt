package app.knotwork.android.architecture

import app.knotwork.android.presentation.ui.navigation.NavRoutes
import app.knotwork.android.presentation.ui.navigation.TAB_ROOT_ROUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural guard over the one navigation invariant the closed test bought us:
 *
 * > **A bottom-nav tab root is entered only as a tab switch — never pushed on
 * > top of another subtree's back stack.**
 *
 * Violating it produced finding `#14`: `Settings → Tools & workspace → Manage
 * tools` ran a bare `navigate(NavRoutes.TOOLS)`, so the user landed on the Tools
 * tab with the whole settings subtree buried underneath, and the bottom-nav
 * highlight jumped to a tab they had not chosen — reported as being thrown out
 * of Settings onto the main screen's Tools tab.
 *
 * The rule is enforced textually rather than by review because the offending
 * shape is a one-line edit that reads perfectly natural — and because the
 * project has already paid twice for a hand-maintained navigation table
 * (see `TabOwnership`).
 *
 * Two rules are checked:
 *
 * Scanning the navigation package is complete coverage, not a convenient
 * subset: every `navigate(NavRoutes.…)` call in `app/src/main` lives there, and
 * screens receive navigation as lambdas rather than reaching for the controller
 * themselves.
 *
 *  1. Every literal `navigate(NavRoutes.<tab root>)` in the navigation package
 *     must be a **self-replacing launch gate** — an options block containing
 *     `inclusive = true`, which leaves the tab root at the bottom of the stack
 *     rather than on top of one. Everything else goes through `navigateToTab`,
 *     whose argument is a parameter and so never matches this scan.
 *  2. Every nested `navigation(startDestination = …, route = <tab route>)`
 *     graph declares a start destination that is itself a tab root, so
 *     `TabOwnership`'s notion of "on a tab's root" stays complete when a second
 *     tab becomes a graph.
 */
class TabRootEntryGuardTest {

    @Test
    fun `given the tab-root constants when compared to the runtime set then the mapping is complete`() {
        // Keeps rule 1 honest: a new tab root that nobody adds to
        // [TAB_ROOT_CONSTANT_NAMES] would otherwise be scanned for by nothing
        // and the guard would pass while the invariant went unchecked.
        assertEquals(
            "TAB_ROOT_CONSTANT_NAMES must name exactly the routes in TAB_ROOT_ROUTES",
            TAB_ROOT_ROUTES,
            TAB_ROOT_CONSTANT_NAMES.values.toSet(),
        )
    }

    @Test
    fun `given the navigation sources when scanning for tab-root pushes then only launch gates remain`() {
        val violations = navigationSources().flatMap { file ->
            val lines = file.readText().lines()
            lines.withIndex()
                // Prose describing the forbidden shape is not the forbidden
                // shape: the KDoc on `navigateToTab` quotes the offending call
                // verbatim, and the rule would otherwise forbid explaining
                // itself.
                .filterNot { (_, line) -> isComment(line) }
                .filter { (_, line) -> TAB_ROOT_CONSTANT_NAMES.keys.any { line.contains("navigate($it)") } }
                .filterNot { (index, _) -> isSelfReplacingLaunchGate(lines, index) }
                .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
        }

        assertTrue(
            TAB_ROOT_PUSH_FAILURE + violations.joinToString(separator = "\n", prefix = "\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `given a nested tab graph when reading its start destination then that destination is a tab root`() {
        val text = navigationSources().single { it.name == NAV_GRAPH_FILE }.readText()
        val declarations = NESTED_GRAPH_REGEX.findAll(text)
            .map { match -> match.groupValues[1] to match.groupValues[2] }
            .toList()

        assertTrue("expected at least one nested navigation graph in $NAV_GRAPH_FILE", declarations.isNotEmpty())

        val tabGraphs = declarations.filter { (_, graphRoute) -> graphRoute in TAB_ROOT_CONSTANT_NAMES }
        tabGraphs.forEach { (startDestination, graphRoute) ->
            assertTrue(
                "$graphRoute is a bottom-nav tab whose start destination $startDestination is not a tab root — " +
                    "add it to TAB_GRAPH_START_DESTINATIONS in TabOwnership.kt, or the tab's own root will stop " +
                    "highlighting the tab and Back will stop exiting the app there",
                startDestination in TAB_ROOT_CONSTANT_NAMES,
            )
        }
    }

    /** Whether [line] is a comment or KDoc line rather than executable code. */
    private fun isComment(line: String): Boolean = line.trimStart().let { trimmed ->
        COMMENT_PREFIXES.any { trimmed.startsWith(it) }
    }

    /**
     * Whether the `navigate(...)` call opened at [index] carries an
     * `inclusive = true` pop — the splash / onboarding shape, which *replaces*
     * itself with the tab root instead of stacking on top of anything.
     *
     * The window is deliberately small: a legitimate launch gate states the
     * inclusive pop in the very next lines of its own options block.
     */
    private fun isSelfReplacingLaunchGate(lines: List<String>, index: Int): Boolean =
        lines.subList(index, minOf(index + LAUNCH_GATE_WINDOW, lines.size))
            .any { it.contains("inclusive = true") }

    private fun navigationSources(): List<File> {
        val dir = repoRoot().resolve(NAVIGATION_PACKAGE_PATH)
        val sources = dir.listFiles { file: File -> file.extension == "kt" }?.toList().orEmpty()
        assertTrue("no Kotlin sources found under $NAVIGATION_PACKAGE_PATH", sources.isNotEmpty())
        return sources
    }

    /**
     * Resolves the Gradle root by walking up from the test JVM's working
     * directory (the `:app` module), the same way
     * [InstrumentedTestExclusionGuardTest] does.
     */
    private fun repoRoot(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            if (File(candidate, NAVIGATION_PACKAGE_PATH).isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Could not find $NAVIGATION_PACKAGE_PATH walking up from ${File("").absolutePath}")
    }

    private companion object {
        const val NAVIGATION_PACKAGE_PATH =
            "app/src/main/java/app/knotwork/android/presentation/ui/navigation"

        const val NAV_GRAPH_FILE = "AppNavGraph.kt"

        /** Line prefixes that mark a source line as commentary, not code. */
        val COMMENT_PREFIXES = listOf("*", "//", "/*")

        /** Lines of an options block scanned for the launch-gate `inclusive = true`. */
        const val LAUNCH_GATE_WINDOW = 4

        /** `navigation(startDestination = X, route = Y)` — captures X and Y. */
        val NESTED_GRAPH_REGEX =
            Regex("""navigation\(\s*startDestination\s*=\s*([\w.]+)\s*,\s*route\s*=\s*([\w.]+)\s*,?\s*\)""")

        /**
         * Source spelling of every tab-root route, mapped to its value. The
         * scan matches source text, so it needs the constant *names*; the value
         * side is what the first test compares against [TAB_ROOT_ROUTES].
         */
        val TAB_ROOT_CONSTANT_NAMES: Map<String, String> = mapOf(
            "NavRoutes.CHAT_TAB" to NavRoutes.CHAT_TAB,
            "NavRoutes.PIPELINES_GRAPH" to NavRoutes.PIPELINES_GRAPH,
            "NavRoutes.PIPELINE_LIBRARY" to NavRoutes.PIPELINE_LIBRARY,
            "NavRoutes.TOOLS" to NavRoutes.TOOLS,
            "NavRoutes.MORE" to NavRoutes.MORE,
        )

        const val TAB_ROOT_PUSH_FAILURE =
            "a bottom-nav tab root must be entered through `navigateToTab` (a tab switch), never pushed on top of " +
                "another subtree's back stack: doing so strands the user on a tab they did not choose and moves " +
                "the bottom-nav highlight there with them. Offending call sites:"
    }
}
