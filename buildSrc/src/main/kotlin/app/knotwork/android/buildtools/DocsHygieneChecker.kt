package app.knotwork.android.buildtools

/**
 * Pure scanner that guards the public documentation contour against two classes
 * of defect that are cheap to introduce and expensive reputationally once the
 * repository is announced:
 *
 *  1. **LLM tool-call wrapper artifacts.** Fragments of an assistant's tool-call
 *     envelope (closing wrapper tags such as the `content` / `invoke` markers,
 *     or the opening of an Anthropic markup / function-results block) that leak
 *     into a committed document when generated prose is pasted verbatim. These
 *     are never legitimate Markdown and always signal an editing mistake.
 *  2. **References to internal-only documents.** The public docs must never link
 *     to the project's internal planning files (the roadmap plan, the full
 *     description, the decision log, the phase backlog, the vision doc, the agent
 *     manifest) or to the internal `project_docs/` tree, because external readers
 *     cannot see them — a dangling reference is worse than no reference.
 *
 * The scanner is deliberately a pure `Map<path, content> -> List<Violation>`
 * transform with no file-system or Git access, so it is trivially unit-testable.
 * The Gradle task in `app/build.gradle.kts` (`verifyDocsHygiene`, wired into
 * `check`) resolves the set of tracked public-contour Markdown files, reads their
 * contents, and feeds them here; `CHANGELOG.md` is excluded by the caller because
 * it is a historical journal whose past entries legitimately mention internal
 * documents as they were named at the time.
 *
 * The forbidden tokens below are assembled from fragments at runtime rather than
 * written as whole literals so that this scanner's own source — and any document
 * that describes it — does not match itself.
 */
object DocsHygieneChecker {

    /** Human-readable category label for a [Violation], surfaced in the failure message. */
    enum class Category(val label: String) {
        /** A leaked fragment of an LLM tool-call envelope. */
        LLM_ARTIFACT("LLM tool-call artifact"),

        /** A reference to an internal-only document not present in the public contour. */
        PRIVATE_DOC_REFERENCE("internal-document reference"),
    }

    /**
     * A single guard hit.
     *
     * @property file Path of the offending file, relative to the repository root.
     * @property line 1-indexed line number where the forbidden token appears.
     * @property token The exact forbidden substring that matched.
     * @property category Which class of defect this hit belongs to.
     */
    data class Violation(
        val file: String,
        val line: Int,
        val token: String,
        val category: Category,
    ) {
        /** Renders the violation in the canonical `path:line: message` failure format. */
        fun format(): String = "$file:$line: ${category.label} `$token`"
    }

    /**
     * Forbidden tokens for [Category.LLM_ARTIFACT].
     *
     * Built from fragments so the whole literal never appears in source; the
     * inner values reconstruct the closing `content` / `invoke` wrapper tags and
     * the opening of an Anthropic-markup / function-results block.
     */
    private val LLM_ARTIFACT_TOKENS: List<String> = listOf(
        "</" + "content>",
        "</" + "invoke>",
        "<" + "antml",
        "<" + "function_results",
    )

    /**
     * Forbidden tokens for [Category.PRIVATE_DOC_REFERENCE] — filenames of the
     * internal planning documents and the internal docs tree prefix.
     */
    private val PRIVATE_DOC_TOKENS: List<String> = listOf(
        "decisions" + ".md",
        "DESCRIPTION" + ".md",
        "PLAN" + ".md",
        "VISION" + ".md",
        "TODO" + ".md",
        "CLAUDE" + ".md",
        "project_docs/",
    )

    /**
     * Scans the supplied documents for both defect classes.
     *
     * @param files Map of repository-root-relative path to full file content.
     *   The caller is responsible for restricting this to the public contour and
     *   for excluding `CHANGELOG.md`.
     * @return Every [Violation] found, ordered by file then line then category, so
     *   the failure message is stable and diff-friendly.
     */
    fun scan(files: Map<String, String>): List<Violation> {
        val violations = mutableListOf<Violation>()
        for ((path, content) in files) {
            content.lineSequence().forEachIndexed { index, line ->
                val lineNumber = index + 1
                LLM_ARTIFACT_TOKENS.forEach { token ->
                    if (line.contains(token)) {
                        violations += Violation(path, lineNumber, token, Category.LLM_ARTIFACT)
                    }
                }
                PRIVATE_DOC_TOKENS.forEach { token ->
                    if (line.contains(token)) {
                        violations += Violation(path, lineNumber, token, Category.PRIVATE_DOC_REFERENCE)
                    }
                }
            }
        }
        return violations.sortedWith(
            compareBy({ it.file }, { it.line }, { it.category }),
        )
    }
}
