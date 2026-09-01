package app.knotwork.android.buildtools

/**
 * Finds KDoc blocks that document nothing.
 *
 * Kotlin attaches a KDoc block to the declaration that follows it, and only to
 * that one. So when two blocks sit back to back with no declaration between
 * them, the first documents nothing: it is invisible to Dokka, invisible to the
 * IDE, and — worse — it *reads* as documentation of the declaration below,
 * which already has its own.
 *
 * The four instances found when this check was written were all the same
 * editing accident, and none of them looked like an accident in review: a new
 * function had been inserted **between** an existing KDoc and the function it
 * described. The old doc stayed where it was, the new function kept its own
 * doc, and the original function silently lost its documentation — including,
 * in one case, the entire migration policy of the Room database. Nothing
 * flagged it, because every individual line of the diff was correct.
 *
 * A pure `Map<path, content> -> List<Violation>` transform: no file-system or
 * Git access, so it is trivially unit-testable. The Gradle task resolves the
 * source tree and feeds it here.
 */
object OrphanedKdocChecker {

    /**
     * One KDoc block that documents nothing.
     *
     * @property file Path of the offending file, relative to the repository root.
     * @property line 1-based line on which the orphaned block opens.
     * @property firstLine First non-empty content line of the block, so the
     *   failure message can say *which* block without printing all of it.
     */
    data class Violation(val file: String, val line: Int, val firstLine: String) {
        /** One-line rendering for the build failure message. */
        override fun toString(): String = "$file:$line: KDoc documents no declaration — \"$firstLine\""
    }

    /**
     * Scans Kotlin sources for KDoc blocks followed by another KDoc block.
     *
     * **A file-level block is not a violation.** A file whose first block
     * explains the file as a whole, followed by the first declaration's own
     * block, is a deliberate and common shape — three files in this repository
     * use it. It is told apart structurally rather than by heuristics: a
     * file-level block is one with nothing but `package`, `import`, file
     * annotations, comments and blank lines before it. Anything after a real
     * declaration has begun is inside a body, and a doc block there can only
     * belong to a declaration.
     *
     * @param files Path (repository-relative) to file content, for every Kotlin
     *   file to scan.
     * @return Every orphaned block, in the iteration order of [files].
     */
    fun scan(files: Map<String, String>): List<Violation> = files.flatMap { (path, content) ->
        scanFile(path, content.lines())
    }

    /**
     * Scans one already-split file.
     *
     * @param path Repository-relative path, used only in the violation.
     * @param lines The file's lines.
     * @return The orphaned blocks in this file.
     */
    private fun scanFile(path: String, lines: List<String>): List<Violation> {
        val violations = mutableListOf<Violation>()
        var index = 0
        var seenDeclaration = false
        while (index < lines.size) {
            val trimmed = lines[index].trim()
            if (trimmed.startsWith("/**")) {
                val close = closingLineOf(lines, index)
                if (close == null) return violations
                // Only a block that is *followed by another block* is suspect,
                // and only once a declaration has begun — before that, the
                // first block is the file's own.
                if (seenDeclaration && nextConstructIsKdoc(lines, close + 1)) {
                    violations += Violation(
                        file = path,
                        line = index + 1,
                        firstLine = firstContentLine(lines, index, close),
                    )
                }
                index = close + 1
                continue
            }
            if (!seenDeclaration && isDeclarationStart(trimmed)) seenDeclaration = true
            index++
        }
        return violations
    }

    /**
     * Index of the line closing the block opened at [open], or `null` when the
     * file ends first (an unterminated comment, which the compiler will reject
     * far more clearly than this check would).
     */
    private fun closingLineOf(lines: List<String>, open: Int): Int? {
        // A single-line `/** … */` closes on its own line.
        if (lines[open].trimEnd().endsWith("*/") && lines[open].trim().length > "/**".length) return open
        for (i in open + 1 until lines.size) {
            if (lines[i].trim().endsWith("*/")) return i
        }
        return null
    }

    /**
     * Whether the next thing after [from] that is not blank and not an ordinary
     * comment opens another KDoc block.
     *
     * Plain comments are skipped rather than treated as "something else came
     * first". Neither a line comment nor a plain block comment documents
     * anything, so neither rescues the block above it: the declaration still
     * takes the *last* doc block, and everything before it is still attached to
     * nothing. Stopping at them would let a stray note hide the very defect
     * this check exists to find.
     *
     * @param lines The file's lines.
     * @param from Index to start looking from.
     * @return `true` when another KDoc block comes next.
     */
    private fun nextConstructIsKdoc(lines: List<String>, from: Int): Boolean {
        var i = from
        while (i < lines.size) {
            val trimmed = lines[i].trim()
            when {
                trimmed.isEmpty() || trimmed.startsWith("//") -> i++
                trimmed.startsWith("/**") -> return true
                trimmed.startsWith("/*") -> {
                    val close = closingLineOf(lines, i) ?: return false
                    i = close + 1
                }
                else -> return false
            }
        }
        return false
    }

    /**
     * First line of the block that carries words, with the comment furniture
     * stripped — enough to identify the block in a failure message.
     */
    private fun firstContentLine(lines: List<String>, open: Int, close: Int): String {
        for (i in open..close) {
            val text = lines[i].trim().removePrefix("/**").removePrefix("*").removeSuffix("*/").trim()
            if (text.isNotEmpty()) return text.take(MAX_QUOTED_CHARS)
        }
        return ""
    }

    /**
     * Whether [trimmed] begins a top-level declaration, after which any further
     * KDoc necessarily belongs to something inside it.
     *
     * Annotations and modifiers are deliberately **not** treated as the start:
     * they may precede a declaration whose own KDoc sits above them, and
     * treating `@JvmInline` as "the file body has begun" would report that
     * perfectly ordinary shape.
     */
    private fun isDeclarationStart(trimmed: String): Boolean =
        DECLARATION_KEYWORDS.any { trimmed.startsWith(it) }

    /** Longest quoted excerpt in a violation message. */
    private const val MAX_QUOTED_CHARS: Int = 72

    /**
     * Keywords that begin a declaration at file scope. The list is deliberately
     * short: it only has to recognise that *some* declaration has started, not
     * to parse Kotlin.
     */
    private val DECLARATION_KEYWORDS: List<String> = listOf(
        "class ", "interface ", "object ", "fun ", "val ", "var ", "enum ",
        "sealed ", "data ", "abstract ", "open ", "internal ", "private ",
        "public ", "annotation ", "typealias ",
    )
}
