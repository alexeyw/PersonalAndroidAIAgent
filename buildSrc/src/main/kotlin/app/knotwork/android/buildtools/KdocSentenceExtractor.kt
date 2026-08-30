package app.knotwork.android.buildtools

/**
 * Extracts the one-line summary a file map uses to describe a Kotlin file:
 * the first sentence of the KDoc on the declaration the file is named for.
 *
 * **Why the name has to match.** The obvious implementation — "take the first
 * KDoc in the file" — is wrong in a way that never fails loudly. Scanning this
 * repository with that rule produced, for
 * `catalog/.../screens/triggers/TriggerDetailContent.kt`, the description
 * *"Pending-dot pulse period, matching `StatusPill`."* — the KDoc of a private
 * layout constant declared above the composable the file exists for. The
 * sentence is real, well-formed, and about the wrong thing, so nothing
 * downstream can tell it is wrong. A file map full of such lines is worse than
 * one with gaps, because a gap advertises itself.
 *
 * So this extractor never guesses. It answers only when the answer is
 * unambiguous:
 *
 *  1. a KDoc'd top-level declaration whose name matches the file name
 *     (case-insensitively — `Undo.kt` legitimately declares `val I.undo`), or
 *  2. failing that, the file's *only* KDoc'd top-level declaration.
 *
 * Anything else returns `null`, and the caller renders an explicit
 * "no description" marker that a human resolves. [FileMapGenerator] counts
 * those markers and a ratchet keeps the count from growing.
 */
object KdocSentenceExtractor {

    /**
     * Returns the description for a file, or `null` when the source offers no
     * unambiguous one.
     *
     * @param fileName File name including the `.kt` extension, e.g. `ChatDao.kt`.
     * @param source Full text of the Kotlin file.
     * @return The first sentence of the matching declaration's KDoc, flattened
     *   to a single line with KDoc `[references]` rewritten as code spans, or
     *   `null` when no declaration matches by name and the file has more (or
     *   fewer) than one documented top-level declaration.
     */
    fun firstSentence(fileName: String, source: String): String? {
        val documented = documentedDeclarations(source)
        if (documented.isEmpty()) return null
        val baseName = fileName.removeSuffix(".kt")
        val byName = documented.filter { it.name.equals(baseName, ignoreCase = true) }
        // Several declarations may share the file's name — overloads of one
        // composable, most often. They document one idea, so the first wins.
        val chosen = byName.firstOrNull()
            ?: documented.singleOrNull()
            ?: return null
        return firstSentenceOf(chosen.doc)
    }

    /**
     * Every top-level declaration in [source] that carries a KDoc.
     *
     * Exposed for tests and for callers that want to report *why* a file has no
     * description.
     *
     * @param source Full text of the Kotlin file.
     * @return The documented declarations in source order.
     */
    fun documentedDeclarations(source: String): List<DocumentedDeclaration> {
        val result = mutableListOf<DocumentedDeclaration>()
        var index = 0
        while (true) {
            val start = source.indexOf(KDOC_OPEN, index)
            if (start < 0) break
            index = start + KDOC_OPEN.length
            // Top-level only: a KDoc that is indented documents a member.
            if (start != 0 && source[start - 1] != '\n') continue
            val end = source.indexOf(KDOC_CLOSE, start)
            if (end < 0) break
            index = end + KDOC_CLOSE.length
            val doc = source.substring(start + KDOC_OPEN.length, end)
            val name = declarationNameAt(source, index) ?: continue
            result += DocumentedDeclaration(name = name, doc = doc)
        }
        return result
    }

    /**
     * One top-level declaration together with the KDoc block above it.
     *
     * @property name The declared name, with any receiver and type parameters
     *   stripped (`val I.undo` yields `undo`).
     * @property doc The raw KDoc body, `/**` and `*/` already removed.
     */
    data class DocumentedDeclaration(val name: String, val doc: String)

    /**
     * Reads the name of the declaration that starts at [from], skipping the
     * annotations and modifiers between it and the KDoc block.
     *
     * @param source Full text of the Kotlin file.
     * @param from Index just past the KDoc's closing marker.
     * @return The declared name, or `null` when what follows the KDoc is not a
     *   declaration this extractor recognises (a second comment, a `package`
     *   line, an unparsable construct).
     */
    private fun declarationNameAt(source: String, from: Int): String? {
        var cursor = skipTrivia(source, from)
        // Annotations, possibly several, possibly with arguments spanning lines.
        while (cursor < source.length && source[cursor] == '@') {
            cursor = skipAnnotation(source, cursor) ?: return null
            cursor = skipTrivia(source, cursor)
        }
        while (cursor < source.length) {
            val word = readWord(source, cursor) ?: return null
            cursor = skipTrivia(source, cursor + word.length)
            if (word in DECLARATION_KEYWORDS) {
                return readDeclaredName(source, cursor)
            }
            if (word !in MODIFIER_KEYWORDS) return null
        }
        return null
    }

    /**
     * Skips whitespace and comments between the KDoc and the declaration.
     *
     * Comments belong here because they really occur there — a `@Suppress`
     * annotation carrying a trailing `//` justification sits between the two in
     * this codebase, and a parser that stops at the slash silently reports the
     * file as undocumented. Block comments nest in Kotlin, so the depth is
     * counted rather than stopping at the first closing marker.
     *
     * @param source Full text of the Kotlin file.
     * @param from Index to start skipping from.
     * @return The index of the first character that is neither whitespace nor
     *   part of a comment.
     */
    private fun skipTrivia(source: String, from: Int): Int {
        var cursor = from
        while (cursor < source.length) {
            when {
                source[cursor].isWhitespace() -> cursor++
                source.startsWith("//", cursor) -> {
                    val newline = source.indexOf('\n', cursor)
                    cursor = if (newline < 0) source.length else newline + 1
                }
                source.startsWith("/*", cursor) -> {
                    var depth = 0
                    while (cursor < source.length) {
                        when {
                            source.startsWith("/*", cursor) -> { depth++; cursor += 2 }
                            source.startsWith("*/", cursor) -> {
                                depth--
                                cursor += 2
                                if (depth == 0) break
                            }
                            else -> cursor++
                        }
                    }
                }
                else -> return cursor
            }
        }
        return cursor
    }

    /**
     * Reads the declared name that follows a declaration keyword.
     *
     * Skips type parameters (`fun <T> …`) and a receiver (`val I.undo`,
     * `fun List<String>.joined()`), both of which sit between the keyword and
     * the name.
     *
     * @param source Full text of the Kotlin file.
     * @param from Index of the first character after the declaration keyword.
     * @return The declared name, or `null` when none can be read.
     */
    private fun readDeclaredName(source: String, from: Int): String? {
        var cursor = from
        if (cursor < source.length && source[cursor] == '<') {
            cursor = skipBalanced(source, cursor, '<', '>') ?: return null
            cursor = skipTrivia(source, cursor)
        }
        // A receiver is everything up to the last dot before the parameter list
        // or the end of the name; walk segments until one is not followed by a dot.
        while (true) {
            val name = readName(source, cursor) ?: return null
            var after = cursor + rawNameLength(source, cursor)
            if (after < source.length && source[after] == '<') {
                after = skipBalanced(source, after, '<', '>') ?: return name
            }
            if (after < source.length && source[after] == '.') {
                cursor = after + 1
                continue
            }
            return name
        }
    }

    /**
     * Flattens a KDoc block's first paragraph and cuts it at the first sentence.
     *
     * @param doc The raw KDoc body.
     * @return The first sentence as one line, or `null` when the block is empty
     *   or starts with a block tag (`@param`, `@return`), which documents a part
     *   rather than the declaration itself.
     */
    private fun firstSentenceOf(doc: String): String? {
        val paragraph = StringBuilder()
        for (rawLine in doc.lines()) {
            val line = rawLine.trim().removePrefix("*").trim()
            if (line.startsWith("@")) break // Block tags end the description.
            if (line.isEmpty()) {
                if (paragraph.isNotEmpty()) break // Blank line ends the first paragraph.
                continue
            }
            if (paragraph.isNotEmpty()) paragraph.append(' ')
            paragraph.append(line)
        }
        val flattened = rewriteReferences(paragraph.toString()).trim()
        if (flattened.isEmpty()) return null
        return cutAtFirstSentence(flattened)
    }

    /**
     * Cuts [text] at its first sentence terminator.
     *
     * A period only terminates a sentence when it is followed by whitespace or
     * the end of the text *and* is not part of a known abbreviation or a dotted
     * identifier — otherwise `Log.WARN`, `0.8.0` and `e.g.` would each end the
     * sentence early. Sentences are also never cut inside a code span, since a
     * span like `` `Settings.Global` `` reads as prose punctuation otherwise.
     *
     * @param text The flattened first paragraph.
     * @return The first sentence, terminator included; the whole paragraph when
     *   it contains no terminator.
     */
    private fun cutAtFirstSentence(text: String): String {
        var backticks = 0
        for (index in text.indices) {
            val char = text[index]
            if (char == '`') backticks++
            if (backticks % 2 != 0) continue // Inside a code span.
            if (char != '.' && char != '!' && char != '?') continue
            val next = text.getOrNull(index + 1)
            if (next != null && !next.isWhitespace()) continue
            if (char == '.' && endsWithAbbreviation(text, index)) continue
            return text.substring(0, index + 1)
        }
        return text
    }

    /**
     * Whether the period at [index] belongs to an abbreviation rather than a
     * sentence end.
     *
     * @param text The flattened paragraph.
     * @param index Index of the period.
     * @return `true` when the period continues a word rather than closing a
     *   sentence.
     */
    private fun endsWithAbbreviation(text: String, index: Int): Boolean {
        val prefix = text.substring(0, index + 1)
        if (ABBREVIATIONS.any { prefix.endsWith(it, ignoreCase = true) }) return true
        // A single letter before the period continues a dotted form: `e.g.`, `i.e.`.
        val word = prefix.takeLastWhile { !it.isWhitespace() }
        return word.length <= 2 && word.dropLast(1).all { it.isLetter() }
    }

    /**
     * Rewrites KDoc `[references]` as Markdown code spans.
     *
     * A file map is read as prose, and an unresolved `[Foo]` renders as a broken
     * link rather than as the type name it names.
     *
     * @param text The flattened paragraph.
     * @return The same text with `[Foo]` and `[Foo.bar]` rewritten to code spans.
     */
    private fun rewriteReferences(text: String): String =
        KDOC_REFERENCE.replace(text) { match -> "`${match.groupValues[1]}`" }

    private fun skipWhitespace(source: String, from: Int): Int {
        var cursor = from
        while (cursor < source.length && source[cursor].isWhitespace()) cursor++
        return cursor
    }

    /**
     * Skips one annotation, including a balanced argument list.
     *
     * @return The index just past the annotation, or `null` when its arguments
     *   are unbalanced (an unparsable file must not be guessed at).
     */
    private fun skipAnnotation(source: String, from: Int): Int? {
        var cursor = from + 1 // the '@'
        while (cursor < source.length && (source[cursor].isLetterOrDigit() || source[cursor] in "._:")) cursor++
        val afterName = skipSpacesOnly(source, cursor)
        if (afterName < source.length && source[afterName] == '(') {
            return skipBalanced(source, afterName, '(', ')')
        }
        if (afterName < source.length && source[afterName] == '[') {
            return skipBalanced(source, afterName, '[', ']')
        }
        return cursor
    }

    private fun skipSpacesOnly(source: String, from: Int): Int {
        var cursor = from
        while (cursor < source.length && (source[cursor] == ' ' || source[cursor] == '\t')) cursor++
        return cursor
    }

    /**
     * Skips a balanced bracket pair starting at [from].
     *
     * @return The index just past the closing bracket, or `null` when it is
     *   never reached.
     */
    private fun skipBalanced(source: String, from: Int, open: Char, close: Char): Int? {
        var depth = 0
        var cursor = from
        while (cursor < source.length) {
            when (source[cursor]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return cursor + 1
                }
            }
            cursor++
        }
        return null
    }

    /** Reads the identifier word at [from], or `null` when there is none. */
    private fun readWord(source: String, from: Int): String? {
        var cursor = from
        while (cursor < source.length && (source[cursor].isLetterOrDigit() || source[cursor] == '_')) cursor++
        return if (cursor == from) null else source.substring(from, cursor)
    }

    /** Reads a declared name at [from], unwrapping a backtick-quoted one. */
    private fun readName(source: String, from: Int): String? {
        if (from >= source.length) return null
        if (source[from] == '`') {
            val end = source.indexOf('`', from + 1)
            return if (end < 0) null else source.substring(from + 1, end)
        }
        return readWord(source, from)
    }

    /** Length of the name at [from] *in the source*, including any backticks. */
    private fun rawNameLength(source: String, from: Int): Int {
        if (source.getOrNull(from) == '`') {
            val end = source.indexOf('`', from + 1)
            return if (end < 0) 1 else end - from + 1
        }
        return readWord(source, from)?.length ?: 0
    }

    private const val KDOC_OPEN = "/**"
    private const val KDOC_CLOSE = "*/"

    /** Declaration keywords whose following identifier names the declaration. */
    private val DECLARATION_KEYWORDS = setOf("class", "interface", "object", "fun", "val", "var", "typealias")

    /**
     * Modifiers that may sit between the KDoc and the declaration keyword.
     *
     * `companion` is absent on purpose: a companion object is never top-level.
     */
    private val MODIFIER_KEYWORDS = setOf(
        "public", "internal", "private", "protected",
        "abstract", "open", "sealed", "data", "enum", "annotation", "value",
        "inline", "noinline", "crossinline", "external", "const", "lateinit",
        "expect", "actual", "suspend", "operator", "infix", "tailrec",
    )

    /** Abbreviations whose trailing period does not end a sentence. */
    private val ABBREVIATIONS = listOf("e.g.", "i.e.", "etc.", "vs.", "cf.", "approx.", "no.", "fig.")

    /** A KDoc `[Reference]` or `[Reference.member]`, rewritten to a code span. */
    private val KDOC_REFERENCE = Regex("""\[([A-Za-z_][\w.]*)]""")
}
