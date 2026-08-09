package app.knotwork.android.presentation.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Drift guard for the outbound links of the About screen ([AboutLinks]).
 *
 * Both links are hand-written strings that no compiler check covers. The privacy
 * link is the fragile one: it targets a heading **anchor** inside the repository
 * `README.md`, so renaming or dropping that heading turns the user-facing
 * "Privacy policy" button into a link that silently lands at the top of the
 * README instead of the privacy section. That exact defect shipped once — the
 * link pointed at `#privacy` while the README had no such heading — and these
 * assertions are what keep it from coming back.
 */
class AboutLinksTest {

    @Test
    fun `given the license link when read then it is the canonical Apache 2 0 url`() {
        assertEquals(
            "License link must point at the canonical Apache 2.0 text",
            "https://www.apache.org/licenses/LICENSE-2.0",
            AboutLinks.LICENSE_URL,
        )
    }

    @Test
    fun `given the privacy link when read then it points at the current repository`() {
        // Guards against the repository rename leaving a stale URL behind: GitHub
        // keeps a redirect from the old name, but that redirect dies the moment
        // anyone creates a repository under the old name.
        assertTrue(
            "Privacy link must target the current repository, was ${AboutLinks.PRIVACY_URL}",
            AboutLinks.PRIVACY_URL.startsWith("$REPOSITORY_URL#"),
        )
    }

    @Test
    fun `given the privacy link fragment when resolved then the README has a matching heading`() {
        val fragment = AboutLinks.PRIVACY_URL.substringAfter('#', missingDelimiterValue = "")
        assertTrue("Privacy link must carry an anchor fragment", fragment.isNotBlank())

        val anchors = readmeHeadingAnchors()
        assertTrue(
            "README has no heading whose GitHub anchor is '#$fragment'. Known anchors: $anchors",
            fragment in anchors,
        )
    }

    /**
     * Collects the GitHub anchor slug of every ATX heading in the repository
     * README, applying GitHub's slug rules: lower-case, drop everything that is
     * not a word character / space / hyphen, then replace spaces with hyphens.
     *
     * Lines inside fenced code blocks are skipped — a shell comment such as
     * `# Privacy` is not a heading, and counting it would let the guard pass on
     * an anchor that GitHub cannot resolve.
     *
     * @return The set of fragments a README link may legally target.
     */
    private fun readmeHeadingAnchors(): Set<String> {
        var inFence = false
        return readmeText()
            .lineSequence()
            .filter { line ->
                if (line.trimStart().startsWith("```")) {
                    inFence = !inFence
                    false
                } else {
                    !inFence && line.startsWith("#")
                }
            }
            .map { line -> line.dropWhile { it == '#' }.trim() }
            .map { title ->
                title.lowercase(Locale.ROOT)
                    .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
                    .replace(' ', '-')
            }
            .toSet()
    }

    /**
     * Reads the repository README by walking up from the test's working directory
     * (the Gradle module directory) to the repository root that holds it.
     *
     * @return Full README text.
     */
    private fun readmeText(): String {
        val workingDir = requireNotNull(System.getProperty("user.dir")) { "user.dir is not set" }
        var dir: File? = File(workingDir)
        while (dir != null) {
            val candidate = File(dir, "README.md")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("README.md not found walking up from $workingDir")
    }

    private companion object {
        /** Canonical public repository URL, without a trailing slash or fragment. */
        const val REPOSITORY_URL = "https://github.com/alexeyw/knotwork"
    }
}
