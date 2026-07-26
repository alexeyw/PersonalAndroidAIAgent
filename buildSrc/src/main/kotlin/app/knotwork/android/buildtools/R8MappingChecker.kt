package app.knotwork.android.buildtools

/**
 * Pure checker that guards a minified build against silently losing a keep rule
 * whose absence only shows up at runtime.
 *
 * The motivating defect: MediaPipe's bundled `com.google.flogger` resolves a log
 * site by **walking the call stack** for a frame belonging to flogger itself.
 * When R8 renames or inlines those frames away, flogger throws
 * `IllegalStateException: no caller found on the stack for: …` from
 * `Graph.<clinit>`, which surfaces as an `ExceptionInInitializerError` the first
 * time the on-device text embedder is created — killing the process. No unit or
 * instrumented test on a debug build can see it, because debug builds are not
 * minified; the only durable evidence is the R8 mapping file itself.
 *
 * So the guard reads the mapping R8 emitted and asserts that the protected
 * packages were left **identity-mapped** (`a.b.C -> a.b.C`), which is what a
 * `-keep class a.b.** { *; }` rule produces. A dropped or weakened rule then
 * fails the release build instead of the user's first message.
 *
 * The checker is a pure `String -> List<Violation>` transform with no
 * file-system access, so it is trivially unit-testable; the Gradle task in
 * `app/build.gradle.kts` supplies the mapping file's contents.
 */
object R8MappingChecker {

    /**
     * One way a protected package can fail its contract.
     *
     * @property originalName Fully-qualified original class name; the package
     *   prefix itself when the whole package is missing from the mapping.
     * @property mappedName The name R8 assigned, or `null` when the package
     *   produced no mapping entries at all.
     */
    data class Violation(val originalName: String, val mappedName: String?) {

        /** Human-readable one-liner for the aggregated failure message. */
        fun format(): String = when (mappedName) {
            null -> "$originalName: no class from this package appears in the mapping — " +
                "the dependency or its keep rule is gone"
            else -> "$originalName: renamed to `$mappedName` — the keep rule no longer pins it"
        }
    }

    /**
     * Verifies that every class under [packagePrefix] kept its original name in
     * [mappingFileContent].
     *
     * @param mappingFileContent Full text of R8's `mapping.txt`.
     * @param packagePrefix Package prefix to protect, e.g.
     *   `com.google.common.flogger.` (trailing dot included by the caller).
     * @return Empty when every matching class is identity-mapped; otherwise one
     *   [Violation] per renamed class, or a single "package absent" violation
     *   when the prefix matched nothing at all (which would make the check
     *   vacuously pass and is therefore treated as a failure).
     */
    fun verifyIdentityMapping(mappingFileContent: String, packagePrefix: String): List<Violation> {
        val entries = classEntries(mappingFileContent).filter { (original, _) -> original.startsWith(packagePrefix) }
        if (entries.isEmpty()) return listOf(Violation(originalName = packagePrefix, mappedName = null))
        return entries
            .filter { (original, mapped) -> original != mapped }
            .map { (original, mapped) -> Violation(originalName = original, mappedName = mapped) }
    }

    /**
     * Extracts the `original -> mapped` class pairs from a mapping file.
     *
     * Class lines are the unindented ones ending in `:`; member lines are
     * indented and deliberately ignored, as are comment lines (`#`).
     */
    private fun classEntries(mappingFileContent: String): List<Pair<String, String>> =
        mappingFileContent.lineSequence()
            .filter { line -> line.isNotBlank() && !line.startsWith("#") && !line.first().isWhitespace() }
            .mapNotNull { line ->
                val match = CLASS_LINE.matchEntire(line.trimEnd()) ?: return@mapNotNull null
                match.groupValues[1] to match.groupValues[2]
            }
            .toList()

    /** `com.example.Foo -> a.b.c:` — the class-mapping line shape. */
    private val CLASS_LINE = Regex("""^(\S+) -> (\S+):$""")
}
