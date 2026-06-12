package app.knotwork.android.domain.services

/**
 * Pure glob matcher for agent-workspace relative paths.
 *
 * The `find_files` tool lets the agent (and pipeline authors) locate workspace
 * files by a shell-style glob (for example a `dot-md` extension glob, or a
 * recursive prefix under a directory). Matching is kept as a small,
 * dependency-free translator from glob to [Regex] so it lives in the `domain`
 * layer (no Android, no `java.nio.file.PathMatcher` API-level concern) and is
 * exhaustively unit-testable.
 *
 * Supported syntax (paths always use the forward slash as the separator,
 * matching [app.knotwork.android.domain.models.WorkspaceFile.relativePath]):
 *
 *  - A single star matches any run of characters **except** the path separator,
 *    so an extension glob matches a top-level file but not one in a
 *    sub-directory.
 *  - A double star matches any run of characters **including** the separator,
 *    spanning directories — a recursive prefix matches everything beneath it.
 *  - A double star immediately followed by a separator (a leading recursive
 *    prefix) matches zero or more leading directory segments, so such a pattern
 *    matches both a top-level file and a nested one.
 *  - A question mark matches exactly one character other than the separator.
 *  - Every other character (including regex metacharacters such as the dot and
 *    parentheses) is treated as a literal.
 *
 * The match is anchored: the whole relative path must match the whole glob. See
 * [WorkspaceGlobTest] for the worked examples.
 */
object WorkspaceGlob {

    /**
     * Reports whether [relativePath] matches [glob] under the syntax documented
     * on [WorkspaceGlob].
     *
     * @param glob A shell-style glob over workspace-relative paths.
     * @param relativePath A workspace-relative path (`/`-separated, no leading
     *   slash), as produced by the workspace listing.
     * @return `true` when the entire path matches the entire glob.
     */
    fun matches(glob: String, relativePath: String): Boolean = compile(glob).matches(relativePath)

    /**
     * Translates [glob] into an anchored [Regex] implementing the documented
     * semantics. Exposed (rather than inlined into [matches]) so a caller that
     * tests many paths against one glob can compile once.
     *
     * @param glob A shell-style glob over workspace-relative paths.
     * @return A `^…$`-anchored [Regex] equivalent to [glob].
     */
    fun compile(glob: String): Regex {
        val pattern = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            when (c) {
                '*' -> {
                    val isDoubleStar = i + 1 < glob.length && glob[i + 1] == '*'
                    if (isDoubleStar) {
                        // A double-star-then-separator collapses any number of leading segments
                        // (including none); a bare double-star matches across directory boundaries.
                        if (i + 2 < glob.length && glob[i + 2] == '/') {
                            pattern.append("(?:.*/)?")
                            i += DOUBLE_STAR_SLASH_LEN
                        } else {
                            pattern.append(".*")
                            i += 2
                        }
                    } else {
                        pattern.append("[^/]*")
                        i += 1
                    }
                }
                '?' -> {
                    pattern.append("[^/]")
                    i += 1
                }
                else -> {
                    pattern.append(Regex.escape(c.toString()))
                    i += 1
                }
            }
        }
        pattern.append('$')
        return Regex(pattern.toString())
    }

    /** Number of glob characters consumed by a double-star-then-slash token (two `*` and one `/`). */
    private const val DOUBLE_STAR_SLASH_LEN = 3
}
