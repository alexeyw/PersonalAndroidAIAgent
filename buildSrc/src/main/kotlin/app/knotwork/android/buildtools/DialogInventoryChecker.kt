package app.knotwork.android.buildtools

/**
 * Guards the rule that a dialog or sheet is composed in `:catalog`, not in
 * `:app`.
 *
 * The inventory this rule exists for was wrong **twice**. The first pass listed
 * seven screens without a catalog twin; three of them already had one, under a
 * file named after the feature rather than after the screen. The second pass
 * missed the dialogs entirely, because it was built by matching `*Screen(`
 * composables — so `SaveAsPresetDialog` was never counted, never covered, and
 * shipped with its selected category chip visually indistinguishable from the
 * unselected ones until somebody ran the app by hand.
 *
 * Both misses have the same shape: a hand-built list of what to cover, fitted to
 * the examples already in mind. So the list is derived from the sources instead.
 * A file in `:app` that calls `AlertDialog`, `BasicAlertDialog` or
 * `ModalBottomSheet` is either *hosting* a catalog body — which is the sanctioned
 * arrangement, because the host owns scrim and IME behaviour — or it is composing
 * a dialog that no baseline can reach.
 *
 * The check cannot tell those apart by parsing alone, so it does not try: it
 * reports every call site, and each one is answered once in an explicit
 * allowlist that records **why**. That list is the deliverable — a file that has
 * to say what it is doing beats a rule that quietly decides for itself.
 *
 * A pure `Map<path, content> -> List<Violation>` transform: no file-system or
 * Git access, so it is trivially unit-testable.
 */
object DialogInventoryChecker {

    /**
     * A dialog or sheet composed in `:app` that no entry accounts for.
     *
     * @property file Path of the offending file, relative to the repository root.
     * @property line 1-based line of the call.
     * @property host The Material composable being called.
     */
    data class Violation(val file: String, val line: Int, val host: String) {
        /** One-line rendering for the build failure message. */
        override fun toString(): String = "$file:$line: $host composed in :app"
    }

    /** Material entry points that put a surface above the screen. */
    private val HOSTS: List<String> = listOf("AlertDialog(", "BasicAlertDialog(", "ModalBottomSheet(")

    /**
     * Scans `:app` sources for dialog and sheet call sites not covered by
     * [allowed].
     *
     * A call inside a comment is ignored — the codebase discusses these
     * composables in prose constantly, and a check that fired on its own
     * documentation would be turned off within the day.
     *
     * @param files Path (repository-relative) to file content.
     * @param allowed Repository-relative paths that have been answered for, each
     *   mapped to the reason. Only the keys are consulted here; the reasons
     *   exist so the list cannot grow silently.
     * @return Every unaccounted call site, in the iteration order of [files].
     */
    fun scan(files: Map<String, String>, allowed: Map<String, String>): List<Violation> =
        files.filterKeys { it !in allowed.keys }.flatMap { (path, content) ->
            scanFile(path, content.lines())
        }

    /**
     * Entries in [allowed] that no longer match any file.
     *
     * Reported separately and just as loudly: an allowlist that outlives what it
     * excused is how a gate rots into decoration. This is the half a check
     * usually forgets, and the half that makes the list trustworthy a year on.
     *
     * @param files The files that were scanned.
     * @param allowed The allowlist to audit.
     * @return Paths listed but no longer holding a dialog, sorted.
     */
    fun staleEntries(files: Map<String, String>, allowed: Map<String, String>): List<String> =
        allowed.keys.filter { path ->
            val content = files[path]
            content == null || scanFile(path, content.lines()).isEmpty()
        }.sorted()

    /**
     * Scans one already-split file.
     *
     * @param path Repository-relative path, used only in the violation.
     * @param lines The file's lines.
     * @return The call sites in this file.
     */
    private fun scanFile(path: String, lines: List<String>): List<Violation> {
        val violations = mutableListOf<Violation>()
        var inBlockComment = false
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (inBlockComment) {
                if (line.contains("*/")) inBlockComment = false
                return@forEachIndexed
            }
            if (line.startsWith("/*")) {
                if (!line.contains("*/")) inBlockComment = true
                return@forEachIndexed
            }
            if (line.startsWith("//") || line.startsWith("*")) return@forEachIndexed
            val host = HOSTS.firstOrNull { line.contains(it) } ?: return@forEachIndexed
            // An import names the composable without composing it.
            if (line.startsWith("import ")) return@forEachIndexed
            violations += Violation(file = path, line = index + 1, host = host.removeSuffix("("))
        }
        return violations
    }
}
