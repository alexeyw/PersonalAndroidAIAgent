package app.knotwork.android.buildtools

/**
 * Structural checker for the Mermaid diagrams embedded in the documentation.
 *
 * **What this is not.** It is not a Mermaid parser. A real parse needs
 * Mermaid's own grammar, which means a Node toolchain and an `npm install` on
 * the critical path of every build — a price the mandatory gate should not pay,
 * and a network dependency it must not have. So this checker verifies
 * *structure*: the defects that break a diagram at render time and are
 * invisible in review, expressed as rules that can be decided by reading the
 * block.
 *
 * **How the rules were chosen.** Every rule below was written against the real
 * parser rather than from memory: each was confirmed to reject the mutation it
 * describes, and the repository's diagrams were confirmed to pass both. Three
 * plausible rules were *dropped* at that step because Mermaid accepts what they
 * would have rejected — a `flowchart` with no direction is valid, and so are
 * unbalanced brackets and quotes in the free text of a `sequenceDiagram`
 * message or a `stateDiagram` note. That is why the bracket rules below are
 * scoped to flowcharts: a gate that fails valid documents is worse than no
 * gate, because it teaches everyone to distrust it.
 *
 * The rules:
 *
 *  1. The block is not empty.
 *  2. Its first significant line declares a known diagram type.
 *  3. A `flowchart` / `graph` direction, when written, is one of the five valid ones.
 *  4. Block openers and their `end` balance — `subgraph` in flowcharts, and
 *     `loop` / `alt` / `opt` / `par` / `critical` / `break` / `rect` / `box` in
 *     sequence diagrams.
 *  5. In a flowchart, brackets and quotes balance on each line.
 *  6. In a flowchart, an unquoted node label holds no parenthesis.
 *  7. In a flowchart, every arrow is a real arrow — a bare `->` is not one.
 */
object MermaidBlockChecker {

    /**
     * One structural defect.
     *
     * @property file Path of the document, relative to the repository root.
     * @property line 1-indexed line **in the document**, not in the block.
     * @property message What is wrong, in reviewer-facing terms.
     */
    data class Violation(val file: String, val line: Int, val message: String) {
        /** Renders the violation in the canonical `path:line: message` failure format. */
        fun format(): String = "$file:$line: $message"
    }

    /**
     * The outcome of one pass over the documentation set.
     *
     * @property violations Structural defects, in document order.
     * @property blockCount How many Mermaid blocks were read — reported on
     *   success, so a pass that found no diagrams at all cannot be mistaken for
     *   a pass over healthy ones.
     */
    data class Summary(val violations: List<Violation>, val blockCount: Int)

    /** Diagram types Mermaid understands; the opening keyword must be one of them. */
    private val DIAGRAM_TYPES = listOf(
        "flowchart", "graph", "sequenceDiagram", "classDiagram", "classDiagram-v2",
        "stateDiagram", "stateDiagram-v2", "erDiagram", "journey", "gantt", "pie",
        "requirementDiagram", "gitGraph", "mindmap", "timeline", "quadrantChart",
        "sankey-beta", "xychart-beta", "block-beta", "packet-beta", "kanban",
        "architecture-beta", "radar-beta", "treemap-beta", "zenuml",
        "C4Context", "C4Container", "C4Component", "C4Dynamic", "C4Deployment",
    )

    /** The five directions a flowchart may be laid out in. */
    private val DIRECTIONS = listOf("TB", "TD", "BT", "RL", "LR")

    /** Keywords that open an `end`-terminated block, by diagram type. */
    private val BLOCK_OPENERS = mapOf(
        "flowchart" to listOf("subgraph"),
        "graph" to listOf("subgraph"),
        "sequenceDiagram" to listOf("loop", "alt", "opt", "par", "critical", "break", "rect", "box"),
    )

    /** Opening fence of a Mermaid block. */
    private val MERMAID_FENCE = Regex("""^ {0,3}(`{3,})mermaid\s*$""")

    /** A configuration directive, which precedes the diagram type and is not one. */
    private val INIT_DIRECTIVE = Regex("""^\s*%%\{.*}%%\s*$""")

    /** The asymmetric node shape `id>text]`, whose brackets deliberately do not pair. */
    private val ASYMMETRIC_SHAPE = Regex("""[A-Za-z0-9_]+>[^\[\]]*]""")

    /** A node label in the plain square-bracket shape, excluding the compound shapes. */
    private val SQUARE_LABEL = Regex("""(?<![\[(\\/])\[(?![\[(\\/*])([^\[\]]*)]""")

    /** Bracket pairs required to balance on a flowchart line. */
    private val BRACKET_PAIRS = listOf('[' to ']', '(' to ')', '{' to '}')

    /**
     * Checks every Mermaid block of every document.
     *
     * @param docs Document text by repository-relative path.
     * @return The violations found and the number of blocks read.
     */
    fun check(docs: Map<String, String>): Summary {
        val violations = mutableListOf<Violation>()
        var blocks = 0
        for ((path, text) in docs.entries.sortedBy { it.key }) {
            for (block in blocksOf(text)) {
                blocks++
                violations += validate(path, block)
            }
        }
        return Summary(violations, blocks)
    }

    /**
     * One fenced Mermaid block.
     *
     * @property lines Its lines, fences excluded.
     * @property firstLine 1-indexed document line of [lines]`[0]`.
     * @property fenceLine 1-indexed document line of the opening fence, used to
     *   report a defect about the block as a whole.
     */
    private data class Block(val lines: List<String>, val firstLine: Int, val fenceLine: Int)

    /**
     * Extracts the Mermaid blocks of a document.
     *
     * @param markdown The document's full text.
     * @return Its Mermaid blocks, in order.
     */
    private fun blocksOf(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val lines = markdown.split("\n")
        var index = 0
        while (index < lines.size) {
            val fence = MERMAID_FENCE.matchEntire(lines[index])?.groupValues?.get(1)
            if (fence == null) {
                index++
                continue
            }
            val fenceLine = index + 1
            val body = mutableListOf<String>()
            index++
            while (index < lines.size && !lines[index].trimStart().startsWith(fence)) {
                body += lines[index]
                index++
            }
            blocks += Block(body, fenceLine + 1, fenceLine)
            index++
        }
        return blocks
    }

    /**
     * Applies every rule to one block.
     *
     * @param file Path of the document holding it.
     * @param block The block.
     * @return Its violations, in line order.
     */
    private fun validate(file: String, block: Block): List<Violation> {
        val violations = mutableListOf<Violation>()
        val significant = block.lines.withIndex().filter { (_, line) ->
            line.isNotBlank() && !INIT_DIRECTIVE.matches(line) && !line.trimStart().startsWith("%%")
        }
        if (significant.isEmpty()) {
            return listOf(Violation(file, block.fenceLine, "empty mermaid block"))
        }
        val (headerOffset, header) = significant.first()
        val headerLine = block.firstLine + headerOffset
        val keyword = header.trim().substringBefore(' ').substringBefore(';').trim()
        if (keyword !in DIAGRAM_TYPES) {
            return listOf(
                Violation(
                    file,
                    headerLine,
                    "unknown mermaid diagram type `$keyword`; expected one of ${DIAGRAM_TYPES.joinToString(", ")}",
                ),
            )
        }
        val isFlowchart = keyword == "flowchart" || keyword == "graph"
        if (isFlowchart) {
            val direction = header.trim().removePrefix(keyword).trim().removeSuffix(";").trim()
            if (direction.isNotEmpty() && direction !in DIRECTIONS) {
                violations += Violation(
                    file,
                    headerLine,
                    "invalid flowchart direction `$direction`; expected one of ${DIRECTIONS.joinToString(", ")}",
                )
            }
        }
        violations += checkBlockBalance(file, block, keyword, significant)
        if (isFlowchart) {
            for ((offset, line) in significant) {
                violations += checkFlowchartLine(file, block.firstLine + offset, line)
            }
        }
        return violations.sortedBy { it.line }
    }

    /**
     * Checks that every block opener is closed by exactly one `end`.
     *
     * @param file Path of the document.
     * @param block The block being validated.
     * @param keyword Its diagram type.
     * @param significant Its significant lines, with their offsets in the block.
     * @return Violations for an unmatched `end` or an unclosed opener.
     */
    private fun checkBlockBalance(
        file: String,
        block: Block,
        keyword: String,
        significant: List<IndexedValue<String>>,
    ): List<Violation> {
        val openers = BLOCK_OPENERS[keyword] ?: return emptyList()
        val violations = mutableListOf<Violation>()
        var depth = 0
        var lastOpenLine = block.fenceLine
        for ((offset, line) in significant) {
            val first = line.trim().substringBefore(' ').substringBefore(';').trim()
            val documentLine = block.firstLine + offset
            if (first in openers) {
                depth++
                lastOpenLine = documentLine
            } else if (first == "end") {
                depth--
                if (depth < 0) {
                    violations += Violation(file, documentLine, "`end` without a matching block opener")
                    depth = 0
                }
            }
        }
        if (depth > 0) {
            violations += Violation(
                file,
                lastOpenLine,
                "$depth unterminated block(s): every ${openers.joinToString(" / ")} needs its own `end`",
            )
        }
        return violations
    }

    /**
     * Applies the flowchart-only line rules: balanced brackets and quotes,
     * parenthesis-free unquoted labels, and real arrows.
     *
     * @param file Path of the document.
     * @param line 1-indexed document line.
     * @param raw The line as written.
     * @return Its violations.
     */
    private fun checkFlowchartLine(file: String, line: Int, raw: String): List<Violation> {
        val violations = mutableListOf<Violation>()
        val code = stripComment(raw)
        if (code.isBlank()) return violations
        if (code.count { it == '"' } % 2 != 0) {
            violations += Violation(file, line, "odd number of `\"` on the line: a quoted label is not closed")
            return violations
        }
        val unquoted = ASYMMETRIC_SHAPE.replace(blankQuoted(code), " ")
        for ((open, close) in BRACKET_PAIRS) {
            val opened = unquoted.count { it == open }
            val closed = unquoted.count { it == close }
            if (opened != closed) {
                violations += Violation(
                    file,
                    line,
                    "unbalanced `$open` / `$close` on the line ($opened vs $closed)",
                )
            }
        }
        for (label in SQUARE_LABEL.findAll(unquoted)) {
            val text = label.groupValues[1]
            if (text.contains('(') || text.contains(')')) {
                violations += Violation(
                    file,
                    line,
                    "node label `[$text]` holds a parenthesis and is not quoted; wrap it in `\"…\"`",
                )
            }
        }
        var index = unquoted.indexOf("->")
        while (index >= 0) {
            val previous = if (index == 0) ' ' else unquoted[index - 1]
            if (previous != '-' && previous != '.') {
                violations += Violation(
                    file,
                    line,
                    "`->` is not a mermaid arrow; flowchart edges are `-->`, `-.->` or `==>`",
                )
                break
            }
            index = unquoted.indexOf("->", index + 1)
        }
        return violations
    }

    /**
     * Drops a trailing `%%` comment, which may hold anything.
     *
     * @param line The line as written.
     * @return The line up to its comment, or the whole line when it has none.
     */
    private fun stripComment(line: String): String {
        if (INIT_DIRECTIVE.matches(line)) return ""
        val comment = line.indexOf("%%")
        return if (comment >= 0) line.substring(0, comment) else line
    }

    /**
     * Blanks the contents of double-quoted strings, so quoted text cannot make
     * a line look unbalanced.
     *
     * @param line The line, comment already stripped.
     * @return The line with quoted contents replaced by spaces.
     */
    private fun blankQuoted(line: String): String {
        val out = StringBuilder()
        var inQuotes = false
        for (character in line) {
            if (character == '"') {
                inQuotes = !inQuotes
                out.append(' ')
            } else {
                out.append(if (inQuotes) ' ' else character)
            }
        }
        return out.toString()
    }
}
