package app.knotwork.android.domain.promptpack

/**
 * One value read out of a prompt-pack frontmatter block.
 *
 * The grammar this project accepts is a deliberately small subset of YAML
 * (documented on [PromptPackFrontmatterParser]) — there is no YAML library
 * in the dependency set and adding one to read six scalar keys would buy a
 * LICENSE audit and a supply-chain surface for nothing. Three shapes cover
 * every key the format defines plus the interop keys other runtimes emit.
 */
sealed class FrontmatterValue {

    /**
     * A single-line value: `name: Concise assistant`.
     *
     * @property text The value with surrounding whitespace and one optional
     *   layer of matching `"` / `'` quotes removed.
     */
    data class Scalar(val text: String) : FrontmatterValue()

    /**
     * A list, written inline (`tags: [concise, starter]`) or as a block of
     * `- item` lines. Both spellings produce this same shape so callers
     * never branch on which one the author used.
     *
     * @property values The list entries, each trimmed and unquoted.
     */
    data class Items(val values: List<String>) : FrontmatterValue()

    /**
     * A nested block map — `metadata:` followed by indented `key: value`
     * lines. The values are deliberately **not** interpreted: no key this
     * format defines is a map, and the only maps seen in practice are the
     * Agent Skills spec's `metadata`. Recording the child key names is
     * enough to report the block honestly without giving a foreign schema
     * a way into our model.
     *
     * @property keys Child key names, in document order.
     */
    data class Block(val keys: List<String>) : FrontmatterValue()
}

/**
 * Result of running [PromptPackFrontmatterParser] over a document.
 */
sealed class FrontmatterParseResult {

    /**
     * The frontmatter block was well-formed.
     *
     * @property entries Key → value, in document order. Keys are compared
     *   case-sensitively, exactly as written.
     * @property body Everything after the closing delimiter, verbatim except
     *   for leading blank lines and a trailing newline. This is the prompt
     *   text; it is never interpreted as markdown, only carried.
     */
    data class Parsed(val entries: Map<String, FrontmatterValue>, val body: String) : FrontmatterParseResult()

    /**
     * The frontmatter block could not be read at all.
     *
     * @property reason Machine-readable cause; the UI maps it to one
     *   sentence per cause rather than a generic "invalid file".
     */
    data class Invalid(val reason: Reason) : FrontmatterParseResult()

    /** Why a frontmatter block failed to parse. */
    enum class Reason {
        /** The document does not open with `---` on its very first line. */
        MISSING_DELIMITER,

        /** An opening `---` was found but never closed by a second one. */
        UNTERMINATED,

        /** A line inside the block is neither a comment, a `key: value`, a list item, nor blank. */
        MALFORMED_ENTRY,

        /** The same key appears twice — silently keeping one of them would be a guess. */
        DUPLICATE_KEY,
    }
}

/**
 * Reads the YAML-frontmatter block at the head of a prompt-pack file.
 *
 * ### The accepted grammar
 *
 * This is a documented subset, not YAML. Anything outside it is rejected
 * with a typed [FrontmatterParseResult.Reason] instead of being guessed at:
 *
 * ```
 * document    := "---" NL entry* "---" NL body
 * entry       := comment | blank | scalarEntry | inlineListEntry | blockEntry
 * comment     := WS* "#" ...                         (whole line only)
 * scalarEntry := KEY ":" WS* value
 * inlineList  := KEY ":" WS* "[" (value ("," value)*)? "]"
 * blockEntry  := KEY ":" WS* NL (WS+ "-" WS+ value)+  -- a list
 *              | KEY ":" WS* NL (WS+ KEY ":" ...)+    -- a map, keys only
 * KEY         := [A-Za-z][A-Za-z0-9_-]*
 * value       := bare text to end of line | "…" | '…'
 * ```
 *
 * Deliberate omissions, each because the format has no key that needs it:
 * anchors and aliases, multi-document streams, block scalars (`|`, `>`),
 * flow maps, tags, and multi-line quoted values. Comments are recognised
 * **only** on their own line, so a `#` inside a value stays part of the
 * value — a prompt body reference like `key: use #hashtags` survives.
 *
 * The parser is pure `domain`: no Android imports, no I/O, and it never
 * throws — every failure is a [FrontmatterParseResult.Invalid].
 */
object PromptPackFrontmatterParser {

    /** The delimiter line that opens and closes a frontmatter block. */
    private const val DELIMITER = "---"

    /** Matches a legal frontmatter key. */
    private val KEY_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_-]*$")

    /**
     * Parses [document] into its frontmatter entries and its body.
     *
     * @param document The full file text. A leading UTF-8 byte-order mark is
     *   tolerated — some editors write one and the block would otherwise
     *   fail [FrontmatterParseResult.Reason.MISSING_DELIMITER] for a reason
     *   the user cannot see.
     * @return [FrontmatterParseResult.Parsed] on success, otherwise
     *   [FrontmatterParseResult.Invalid] naming the cause.
     */
    @Suppress("ReturnCount") // Guard clauses; one early return per named failure reads better than nesting.
    fun parse(document: String): FrontmatterParseResult {
        // Escaped rather than written literally: a raw U+FEFF in source is
        // invisible, and a formatter that strips it turns this line into a
        // no-op that no reader would question.
        val lines = document.removePrefix("\uFEFF").lines()
        if (lines.firstOrNull()?.trim() != DELIMITER) {
            return FrontmatterParseResult.Invalid(FrontmatterParseResult.Reason.MISSING_DELIMITER)
        }
        val closingIndex = (1 until lines.size).firstOrNull { lines[it].trim() == DELIMITER }
            ?: return FrontmatterParseResult.Invalid(FrontmatterParseResult.Reason.UNTERMINATED)

        val entries = linkedMapOf<String, FrontmatterValue>()
        var index = 1
        while (index < closingIndex) {
            val raw = lines[index]
            if (raw.isBlank() || raw.trimStart().startsWith("#")) {
                index++
                continue
            }
            // A line at this level must be `key:` — indented continuation
            // lines are consumed by the block reader below, never here.
            if (raw.first().isWhitespace()) {
                return FrontmatterParseResult.Invalid(FrontmatterParseResult.Reason.MALFORMED_ENTRY)
            }
            val separator = raw.indexOf(':')
            if (separator <= 0) {
                return FrontmatterParseResult.Invalid(FrontmatterParseResult.Reason.MALFORMED_ENTRY)
            }
            val key = raw.substring(0, separator).trim()
            if (!KEY_PATTERN.matches(key)) {
                return FrontmatterParseResult.Invalid(FrontmatterParseResult.Reason.MALFORMED_ENTRY)
            }
            if (entries.containsKey(key)) {
                return FrontmatterParseResult.Invalid(FrontmatterParseResult.Reason.DUPLICATE_KEY)
            }
            val inline = raw.substring(separator + 1).trim()
            if (inline.isNotEmpty()) {
                entries[key] = readInline(inline)
                index++
            } else {
                val block = readBlock(lines = lines, from = index + 1, until = closingIndex)
                    ?: return FrontmatterParseResult.Invalid(FrontmatterParseResult.Reason.MALFORMED_ENTRY)
                entries[key] = block.value
                index = block.nextIndex
            }
        }

        val body = lines.subList(closingIndex + 1, lines.size)
            .dropWhile { it.isBlank() }
            .joinToString(separator = "\n")
            .trimEnd()
        return FrontmatterParseResult.Parsed(entries = entries, body = body)
    }

    /**
     * Reads a value that sat on the same line as its key — either an inline
     * list in `[…]` brackets or a plain scalar.
     */
    private fun readInline(text: String): FrontmatterValue = if (text.startsWith("[") && text.endsWith("]")) {
        FrontmatterValue.Items(
            text.substring(1, text.length - 1)
                .split(',')
                .map(::unquote)
                .filter { it.isNotEmpty() },
        )
    } else {
        FrontmatterValue.Scalar(unquote(text))
    }

    /** A block value plus the index of the first line after it. */
    private data class Block(val value: FrontmatterValue, val nextIndex: Int)

    /**
     * Reads the indented lines following a bare `key:` as either a list of
     * `- item` entries or a map whose child key names are recorded.
     *
     * @return The block, or `null` when the following lines are neither —
     *   a bare key with nothing under it is malformed, not an empty value,
     *   because the author plainly meant to write something.
     */
    private fun readBlock(lines: List<String>, from: Int, until: Int): Block? {
        val items = mutableListOf<String>()
        val keys = mutableListOf<String>()
        var index = from
        while (index < until) {
            val raw = lines[index]
            if (raw.isBlank() || raw.trimStart().startsWith("#")) {
                index++
                continue
            }
            if (!raw.first().isWhitespace()) break
            val trimmed = raw.trim()
            when {
                trimmed.startsWith("- ") -> items += unquote(trimmed.removePrefix("- "))
                trimmed == "-" -> return null
                else -> {
                    val separator = trimmed.indexOf(':')
                    if (separator <= 0) return null
                    val childKey = trimmed.substring(0, separator).trim()
                    if (!KEY_PATTERN.matches(childKey)) return null
                    keys += childKey
                }
            }
            index++
        }
        return when {
            items.isNotEmpty() && keys.isEmpty() -> Block(FrontmatterValue.Items(items), index)
            keys.isNotEmpty() && items.isEmpty() -> Block(FrontmatterValue.Block(keys), index)
            // Neither (nothing indented followed the key) or both (a list and
            // a map under one key) — in each case the author's intent is not
            // recoverable, so we refuse rather than pick.
            else -> null
        }
    }

    /**
     * Strips surrounding whitespace and at most one matching layer of
     * single or double quotes.
     *
     * Inside a double-quoted value, `\\` and `\"` are unescaped — the
     * counterpart of the escaping `PromptPackMarkdownSerializer` applies on
     * the way out, so a name containing a quote survives a round trip.
     * Single-quoted values are taken literally, as in YAML.
     */
    private fun unquote(text: String): String {
        val trimmed = text.trim()
        if (trimmed.length < 2) return trimmed
        val inner = trimmed.substring(1, trimmed.length - 1)
        return when {
            trimmed.startsWith('"') && trimmed.endsWith('"') -> unescape(inner)
            trimmed.startsWith('\'') && trimmed.endsWith('\'') -> inner
            else -> trimmed
        }
    }

    /** Resolves `\\` and `\"` inside a double-quoted scalar. */
    private fun unescape(text: String): String {
        val out = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '\\' && index + 1 < text.length && text[index + 1] in charArrayOf('\\', '"')) {
                out.append(text[index + 1])
                index += 2
            } else {
                out.append(char)
                index++
            }
        }
        return out.toString()
    }
}
