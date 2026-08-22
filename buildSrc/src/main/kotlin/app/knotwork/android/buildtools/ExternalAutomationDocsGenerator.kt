package app.knotwork.android.buildtools

/**
 * Derives the reference tables of `docs/external-automation.md` from the Kotlin
 * declarations that define the external-automation contract, and injects them
 * between dedicated `AUTO-GEN` markers.
 *
 * The contract is a **public API with callers the project does not control**:
 * once a Tasker profile or an `adb` one-liner is written against a key, that key
 * is frozen. Documentation that disagrees with the code is therefore worse here
 * than elsewhere — a profile written against a stale key does not fail loudly,
 * it just looks malformed to the app, and the person who wrote it has no way to
 * see why. So the tables are generated rather than transcribed, and the verify
 * task fails `check` when the committed Markdown has drifted.
 *
 * Three blocks are generated, each from one source file:
 *
 *  - **`CONTRACT_KEYS`** — the action strings and extra keys, from the
 *    `const val` declarations of `domain/constants/ExternalAutomationContract.kt`.
 *    Both the constant name and its wire value are emitted, because callers
 *    write the value while the rest of the documentation refers to the name.
 *  - **`STATUSES`** — the members of the `ExternalAutomationStatus` sealed
 *    interface.
 *  - **`REJECTION_REASONS`** — the constants of the
 *    `ExternalAutomationRejectionReason` enum.
 *
 * Unlike [BrowserEditorConstantsGenerator] this generator needs **no
 * hand-maintained metadata table**: every row's description is the declaration's
 * own KDoc first paragraph. That removes the cross-check failure mode entirely —
 * there is no second list to forget to update. What replaces it is a stricter
 * demand on the source: a declaration with no KDoc fails generation, since an
 * undocumented row would silently publish an empty cell.
 *
 * Everything outside the markers — the prose, the worked Tasker / MacroDroid /
 * `adb` examples, the trust model — is hand-written and left untouched.
 *
 * The entry points are [render] (pure transform used by both Gradle tasks) and
 * [drift] (per-block comparison used by the verify task). Every helper is a pure
 * string transform to keep the module unit-testable.
 */
object ExternalAutomationDocsGenerator {

    /** Thrown when a contract source cannot be parsed or documents nothing. */
    class GenerationException(message: String) : RuntimeException(message)

    /** Generated block covering the action strings and extra keys. */
    const val BLOCK_CONTRACT_KEYS: String = "CONTRACT_KEYS"

    /** Generated block covering the reported statuses. */
    const val BLOCK_STATUSES: String = "STATUSES"

    /** Generated block covering the refusal reasons. */
    const val BLOCK_REJECTION_REASONS: String = "REJECTION_REASONS"

    /** Every block this generator owns, in the order it renders them. */
    val BLOCKS: List<String> = listOf(BLOCK_CONTRACT_KEYS, BLOCK_STATUSES, BLOCK_REJECTION_REASONS)

    /**
     * One documented declaration lifted out of a contract source file.
     *
     * @property name The declaration's Kotlin name.
     * @property wireValue The string literal the declaration is assigned, for
     *   `const val` declarations; `null` for enum constants and sealed members,
     *   whose wire form is their own name.
     * @property description The declaration's KDoc first paragraph, flattened to
     *   a single line of Markdown.
     */
    data class ContractEntry(
        val name: String,
        val wireValue: String?,
        val description: String,
    )

    /**
     * Rebuilds every `AUTO-GEN` block of the documentation.
     *
     * Pure and idempotent: `render(render(x)) == render(x)`.
     *
     * @param markdown Current contents of `docs/external-automation.md`.
     * @param contractSource Source of `ExternalAutomationContract.kt`.
     * @param statusSource Source of `ExternalAutomationStatus.kt`.
     * @param reasonSource Source of `ExternalAutomationRejectionReason.kt`.
     * @return The documentation with all generated blocks refreshed.
     */
    fun render(
        markdown: String,
        contractSource: String,
        statusSource: String,
        reasonSource: String,
    ): String {
        var result = markdown
        generateBlocks(contractSource, statusSource, reasonSource).forEach { (block, content) ->
            result = injectBlock(result, block, content)
        }
        return result
    }

    /**
     * Reports which generated blocks differ from what the sources imply.
     *
     * @param markdown Current contents of `docs/external-automation.md`.
     * @param contractSource Source of `ExternalAutomationContract.kt`.
     * @param statusSource Source of `ExternalAutomationStatus.kt`.
     * @param reasonSource Source of `ExternalAutomationRejectionReason.kt`.
     * @return Names of the drifted blocks; empty when the documentation is current.
     */
    fun drift(
        markdown: String,
        contractSource: String,
        statusSource: String,
        reasonSource: String,
    ): List<String> = generateBlocks(contractSource, statusSource, reasonSource)
        .filter { (block, content) -> extractBlock(markdown, block)?.trim() != content.trim() }
        .map { it.first }

    /**
     * Renders every block's body from the sources.
     *
     * @return Block name to rendered body, in [BLOCKS] order.
     */
    private fun generateBlocks(
        contractSource: String,
        statusSource: String,
        reasonSource: String,
    ): List<Pair<String, String>> = listOf(
        BLOCK_CONTRACT_KEYS to renderTable(
            headers = listOf("Constant", "Wire key", "Meaning"),
            rows = parseConstants(contractSource).map { entry ->
                listOf("`${entry.name}`", "`${entry.wireValue}`", entry.description)
            },
        ),
        BLOCK_STATUSES to renderTable(
            headers = listOf("Status", "Meaning"),
            rows = parseSealedMembers(statusSource, "ExternalAutomationStatus").map { entry ->
                listOf("`${entry.name}`", entry.description)
            },
        ),
        BLOCK_REJECTION_REASONS to renderTable(
            headers = listOf("Reason", "Meaning"),
            rows = parseEnumConstants(reasonSource, "ExternalAutomationRejectionReason").map { entry ->
                listOf("`${entry.name}`", entry.description)
            },
        ),
    )

    /**
     * Extracts every documented `const val NAME: String = "value"` declaration.
     *
     * @param source Source of the contract constants file.
     * @return The declarations in source order.
     * @throws GenerationException when the file declares nothing, or a
     *   declaration carries no KDoc.
     */
    fun parseConstants(source: String): List<ContractEntry> {
        val declaration = Regex("""^\s*const val ([A-Z][A-Z0-9_]*): String = "(.*)"\s*$""")
        val entries = collectEntries(source) { line ->
            declaration.find(line)?.let { match ->
                match.groupValues[1] to match.groupValues[2]
            }
        }
        if (entries.isEmpty()) {
            throw GenerationException("No `const val … : String` declarations found in the contract source.")
        }
        return entries
    }

    /**
     * Extracts every documented member of a sealed interface.
     *
     * @param source Source of the sealed-interface file.
     * @param parent Name of the sealed interface whose members are wanted.
     * @return The members in source order.
     * @throws GenerationException when the interface has no members, or a member
     *   carries no KDoc.
     */
    fun parseSealedMembers(source: String, parent: String): List<ContractEntry> {
        val body = source.substringAfter("sealed interface $parent {", missingDelimiterValue = "")
        if (body.isEmpty()) {
            throw GenerationException("`sealed interface $parent` not found in the sealed-interface source.")
        }
        // Scoped to the interface body and anchored on the member's own
        // indentation, deliberately without requiring `: $parent` on the same
        // line. A member whose declaration grew long enough for the formatter to
        // wrap the supertype onto the next line would otherwise vanish from the
        // published table — silently, since the drift check reads through this
        // same parser and would agree the table was current.
        val declaration = Regex("""^\s{4}data (?:object|class) ([A-Za-z][A-Za-z0-9]*)\b""")
        val entries = collectEntries(body) { line ->
            declaration.find(line)?.let { match -> match.groupValues[1] to null }
        }
        if (entries.isEmpty()) {
            throw GenerationException("No members of `$parent` found in the sealed-interface source.")
        }
        return entries
    }

    /**
     * Extracts every documented constant of an enum class.
     *
     * @param source Source of the enum file.
     * @param enumName Name of the enum whose constants are wanted.
     * @return The constants in declaration order.
     * @throws GenerationException when the enum has no constants, or a constant
     *   carries no KDoc.
     */
    fun parseEnumConstants(source: String, enumName: String): List<ContractEntry> {
        val body = source.substringAfter("enum class $enumName {", missingDelimiterValue = "")
        if (body.isEmpty()) {
            throw GenerationException("`enum class $enumName` not found in the enum source.")
        }
        val declaration = Regex("""^\s{4}([A-Z][A-Z0-9_]*),\s*$""")
        val entries = collectEntries(body) { line ->
            declaration.find(line)?.let { match -> match.groupValues[1] to null }
        }
        if (entries.isEmpty()) {
            throw GenerationException("No constants of `$enumName` found in the enum source.")
        }
        return entries
    }

    /**
     * Walks [source] line by line, pairing each declaration [match] recognises
     * with the KDoc block immediately above it.
     *
     * @param source The Kotlin source to walk.
     * @param match Recognises a declaration line, returning its name and
     *   optional wire value.
     * @return One [ContractEntry] per recognised declaration, in source order.
     * @throws GenerationException when a recognised declaration has no KDoc.
     */
    private fun collectEntries(
        source: String,
        match: (String) -> Pair<String, String?>?,
    ): List<ContractEntry> {
        val entries = mutableListOf<ContractEntry>()
        val doc = mutableListOf<String>()
        var inDoc = false
        source.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("/**") -> {
                    doc.clear()
                    inDoc = true
                    doc += trimmed.removePrefix("/**").removeSuffix("*/")
                    if (trimmed.endsWith("*/")) inDoc = false
                }

                inDoc && trimmed.endsWith("*/") -> {
                    // Suffix first: on a bare `*/` closing line, stripping the
                    // `*` prefix first would leave a stray `/` in the text.
                    doc += trimmed.removeSuffix("*/").removePrefix("*")
                    inDoc = false
                }

                inDoc -> doc += trimmed.removePrefix("*")

                else -> {
                    val declaration = match(line)
                    if (declaration != null) {
                        val description = firstParagraph(doc)
                        if (description.isEmpty()) {
                            throw GenerationException(
                                "`${declaration.first}` has no KDoc; every documented contract member needs one.",
                            )
                        }
                        entries += ContractEntry(declaration.first, declaration.second, description)
                    }
                    // A KDoc block documents whatever immediately follows it. Any
                    // other code line consumes it, so an undocumented declaration
                    // cannot silently inherit the doc of the construct above it.
                    if (line.isNotBlank()) doc.clear()
                }
            }
        }
        return entries
    }

    /**
     * Flattens a KDoc block's first paragraph into one line of Markdown.
     *
     * The first paragraph rather than the first sentence: a single sentence
     * routinely leaves out the constraint that matters (which key excludes which,
     * whether a key is optional), and the paragraph break is the author's own
     * mark for "the rest is detail".
     *
     * @param doc The KDoc lines, `*` prefixes already stripped.
     * @return The first paragraph as a single line, or an empty string when the
     *   block documents nothing.
     */
    fun firstParagraph(doc: List<String>): String = doc
        .map { it.trim() }
        .dropWhile { it.isEmpty() }
        .takeWhile { it.isNotEmpty() && !it.startsWith("@") }
        .joinToString(separator = " ")
        .let { KDOC_LINK.replace(it) { match -> "`${match.groupValues[1]}`" } }
        .replace("|", "\\|")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /**
     * Renders a Markdown table.
     *
     * @param headers Column headers.
     * @param rows Cell values, one list per row.
     * @return The table as Markdown, without surrounding blank lines.
     */
    fun renderTable(headers: List<String>, rows: List<List<String>>): String = buildString {
        append(headers.joinToString(prefix = "| ", separator = " | ", postfix = " |"))
        append("\n")
        append(headers.joinToString(prefix = "| ", separator = " | ", postfix = " |") { "---" })
        rows.forEach { row ->
            append("\n")
            append(row.joinToString(prefix = "| ", separator = " | ", postfix = " |"))
        }
    }

    /**
     * Opening marker of a generated block.
     *
     * @param block Block name.
     * @return The Markdown comment that opens it.
     */
    fun openMarker(block: String): String = "<!-- AUTO-GEN:$block -->"

    /**
     * Closing marker of a generated block.
     *
     * @param block Block name.
     * @return The Markdown comment that closes it.
     */
    fun closeMarker(block: String): String = "<!-- /AUTO-GEN:$block -->"

    /**
     * Replaces one block's body, leaving its markers and everything else intact.
     *
     * @param markdown The documentation.
     * @param block Block name.
     * @param content The rendered body.
     * @return The documentation with [block] replaced.
     * @throws GenerationException when the markers are missing or inverted.
     */
    fun injectBlock(markdown: String, block: String, content: String): String {
        val open = openMarker(block)
        val close = closeMarker(block)
        val start = markdown.indexOf(open)
        val end = markdown.indexOf(close)
        if (start < 0 || end < 0 || end < start) {
            throw GenerationException("Marker pair for AUTO-GEN block `$block` not found in the documentation.")
        }
        return markdown.substring(0, start + open.length) +
            "\n\n" + content.trim() + "\n\n" +
            markdown.substring(end)
    }

    /**
     * Reads one block's current body, used by [drift].
     *
     * @param markdown The documentation.
     * @param block Block name.
     * @return The body between the markers, or `null` when they are absent.
     */
    fun extractBlock(markdown: String, block: String): String? {
        val open = openMarker(block)
        val close = closeMarker(block)
        val start = markdown.indexOf(open)
        val end = markdown.indexOf(close)
        if (start < 0 || end < 0 || end < start) return null
        return markdown.substring(start + open.length, end)
    }

    /** Matches a KDoc `[Reference]`, rewritten to Markdown code formatting. */
    private val KDOC_LINK = Regex("""\[([A-Za-z][A-Za-z0-9_.]*)]""")
}
