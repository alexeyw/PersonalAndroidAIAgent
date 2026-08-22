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

        /** An `adb shell am` example whose command is not quoted as a whole. */
        UNQUOTED_ADB_SHELL("unquoted adb shell command"),
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

    /** Filenames of the internal planning documents (fragment-assembled; see [PRIVATE_DOC_REGEXES]). */
    private val PRIVATE_DOC_FILENAMES: List<String> = listOf(
        "decisions" + ".md",
        "DESCRIPTION" + ".md",
        "PLAN" + ".md",
        "VISION" + ".md",
        "TODO" + ".md",
        "CLAUDE" + ".md",
    )

    /** Prefix of the internal docs tree; a real path continues past the slash. */
    private val PRIVATE_DOCS_DIR: String = "project_docs" + "/"

    /** Matches a start-of-token boundary: not preceded by a filename-continuation character. */
    private const val LEADING_BOUNDARY = "(?<![A-Za-z0-9_-])"

    /** Matches an end-of-token boundary: not followed by an alphanumeric (so `PLAN.mdx` does not match `PLAN.md`). */
    private const val TRAILING_BOUNDARY = "(?![A-Za-z0-9])"

    /**
     * Regexes for [Category.PRIVATE_DOC_REFERENCE].
     *
     * Filename tokens are anchored on both sides to a path/word boundary so a
     * longer filename that merely *contains* a token — `OLDPLAN.md`,
     * `PLAN-archive.md`, `DESCRIPTIONS.md`, `PLAN.mdx` — does not false-positive,
     * while genuine references (`decisions.md`, `docs/PLAN.md`, `[x](VISION.md)`)
     * still match. The directory token is anchored only on the left because a
     * real path continues after the slash.
     */
    private val PRIVATE_DOC_REGEXES: List<Regex> =
        PRIVATE_DOC_FILENAMES.map { name ->
            Regex(LEADING_BOUNDARY + Regex.escape(name) + TRAILING_BOUNDARY)
        } + Regex(LEADING_BOUNDARY + Regex.escape(PRIVATE_DOCS_DIR))

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
                PRIVATE_DOC_REGEXES.forEach { regex ->
                    regex.find(line)?.let { match ->
                        violations += Violation(path, lineNumber, match.value, Category.PRIVATE_DOC_REFERENCE)
                    }
                }
                UNQUOTED_ADB_SHELL_REGEX.find(line)?.let { match ->
                    violations += Violation(path, lineNumber, match.value, Category.UNQUOTED_ADB_SHELL)
                }
            }
        }
        return violations.sortedWith(
            compareBy({ it.file }, { it.line }, { it.category }),
        )
    }

    /**
     * Matches an `adb shell am ...` example whose command is not wrapped in quotes.
     *
     * **This exists because a shipped example was wrong in exactly this way.**
     * `adb shell` does not forward argv: it joins the arguments with spaces and
     * hands one string to the device shell, which splits it again. Quotes written
     * on the host are consumed before `adb` sees them, so
     * `--es prompt \'What is on my calendar today?\'` reaches the app as the
     * prompt `What`, and a pipeline named `Morning brief` is looked up as
     * `Morning`. Both failures look like the app refusing a valid request, which
     * is the worst possible shape for a first contact with a public contract.
     *
     * Scoped to `am` rather than to every `adb shell`, because that is the family
     * carrying `--es` values. It flags commands whose values happen to contain no
     * spaces as well: quoting is never wrong, the documentation teaches it as one
     * unconditional rule, and a guard firing only on the already-broken subset
     * would let the next example be written in the shape that breaks.
     */
    private val UNQUOTED_ADB_SHELL_REGEX: Regex = Regex("adb shell +am\\b")
}
