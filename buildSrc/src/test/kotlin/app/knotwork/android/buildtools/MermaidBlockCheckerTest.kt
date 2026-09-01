package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MermaidBlockChecker].
 *
 * Every fixture below was run through the real Mermaid parser before it was
 * written down: the valid ones parse, the broken ones do not. That matters more
 * than usual here, because a structural checker is only worth having while its
 * rules agree with the renderer — the three "valid" cases at the end
 * (a directionless flowchart, free text in a sequence message, the asymmetric
 * node shape) are exactly the plausible rules that had to be dropped.
 *
 * Run with `./gradlew -p buildSrc test`.
 */
class MermaidBlockCheckerTest {

    /** Wraps a diagram in a fenced block inside a one-document corpus. */
    private fun document(diagram: String): Map<String, String> =
        mapOf("docs/architecture.md" to "# Title\n\nText.\n\n```mermaid\n$diagram\n```\n")

    private fun violations(diagram: String) = MermaidBlockChecker.check(document(diagram)).violations

    @Test
    fun `given a valid flowchart when checked then no violations`() {
        val diagram = """
            flowchart LR
                subgraph App[":app"]
                    Presentation["presentation/<br/>Compose · ViewModels"] --> Domain[domain/]
                end
                Domain --> Data[(Room)]
        """.trimIndent()

        assertTrue(violations(diagram).isEmpty())
    }

    @Test
    fun `given no mermaid blocks when checked then the count is zero`() {
        val summary = MermaidBlockChecker.check(mapOf("README.md" to "# Title\n\n```kotlin\nval a = 1\n```\n"))

        assertEquals(0, summary.blockCount)
        assertTrue(summary.violations.isEmpty())
    }

    @Test
    fun `given an unknown diagram type when checked then reported once`() {
        val violations = violations("flowchrt LR\n    A --> B")

        assertEquals(1, violations.size)
        assertTrue(violations[0].message.contains("unknown mermaid diagram type `flowchrt`"))
        assertEquals(6, violations[0].line)
    }

    @Test
    fun `given an empty block when checked then reported`() {
        val violations = MermaidBlockChecker.check(mapOf("a.md" to "```mermaid\n\n```\n")).violations

        assertEquals(listOf("empty mermaid block"), violations.map { it.message })
    }

    @Test
    fun `given an invalid flowchart direction when checked then reported`() {
        assertTrue(violations("flowchart XY\n    A --> B").any { it.message.contains("invalid flowchart direction") })
    }

    @Test
    fun `given a subgraph without end when checked then reported`() {
        val violations = violations("flowchart LR\n    subgraph S[Group]\n        A --> B")

        assertTrue(violations.any { it.message.contains("unterminated block") })
    }

    @Test
    fun `given an end without an opener when checked then reported`() {
        val violations = violations("flowchart LR\n    A --> B\n    end")

        assertTrue(violations.any { it.message.contains("without a matching block opener") })
    }

    @Test
    fun `given a sequence loop without end when checked then reported`() {
        val violations = violations("sequenceDiagram\n    A->>B: hi\n    loop every day\n        A->>B: again")

        assertTrue(violations.any { it.message.contains("unterminated block") })
    }

    @Test
    fun `given a balanced sequence diagram when checked then no violations`() {
        val diagram = """
            sequenceDiagram
                autonumber
                actor User
                User->>App: ask
                loop every day
                    App->>Engine: run
                end
                alt success
                    App-->>User: answer
                else failure
                    App-->>User: error
                end
        """.trimIndent()

        assertTrue(violations(diagram).isEmpty())
    }

    @Test
    fun `given unbalanced brackets in a flowchart when checked then reported`() {
        assertTrue(violations("flowchart LR\n    A[Text --> B").any { it.message.contains("unbalanced") })
    }

    @Test
    fun `given an unquoted parenthesis in a node label when checked then reported`() {
        val violations = violations("flowchart LR\n    A[Text (with parens)] --> B")

        assertTrue(violations.any { it.message.contains("holds a parenthesis and is not quoted") })
    }

    @Test
    fun `given a quoted parenthesis in a node label when checked then no violations`() {
        assertTrue(violations("flowchart LR\n    A[\"Text (with parens)\"] --> B").isEmpty())
    }

    @Test
    fun `given the database and rounded shapes when checked then no violations`() {
        assertTrue(violations("flowchart LR\n    A[(Database)] --> B([Rounded])\n    B --> C{{Hex}}").isEmpty())
    }

    @Test
    fun `given a bare arrow in a flowchart when checked then each offending line is reported once`() {
        val oneLine = violations("flowchart LR\n    A -> B -> C")
        val twoLines = violations("flowchart LR\n    A -> B\n    B -> C")

        assertEquals(1, oneLine.count { it.message.contains("is not a mermaid arrow") })
        assertEquals(2, twoLines.count { it.message.contains("is not a mermaid arrow") })
    }

    @Test
    fun `given dotted and thick arrows when checked then no violations`() {
        assertTrue(violations("flowchart LR\n    A -.-> B\n    B ==> C\n    C --> D\n    D <--> E").isEmpty())
    }

    @Test
    fun `given an unclosed quote in a flowchart when checked then reported`() {
        assertTrue(violations("flowchart LR\n    A[say \"hi] --> B").any { it.message.contains("odd number") })
    }

    @Test
    fun `given a comment holding brackets when checked then it is ignored`() {
        assertTrue(violations("flowchart LR\n    %% a comment with [ unbalanced\n    A --> B").isEmpty())
    }

    @Test
    fun `given an init directive before the type when checked then the type is still found`() {
        val diagram = "%%{init: {\"theme\":\"neutral\"}}%%\nflowchart LR\n    A --> B"

        assertTrue(violations(diagram).isEmpty())
    }

    @Test
    fun `given a flowchart with no direction when checked then it is accepted`() {
        // Mermaid accepts this; a rule rejecting it would fail a valid document.
        assertTrue(violations("flowchart\n    A[One] --> B[Two]").isEmpty())
    }

    @Test
    fun `given unbalanced free text in a sequence message when checked then it is accepted`() {
        // Mermaid accepts this too, which is why the bracket rules are flowchart-only.
        assertTrue(violations("sequenceDiagram\n    A->>B: hello (world").isEmpty())
    }

    @Test
    fun `given the asymmetric node shape when checked then it is accepted`() {
        assertTrue(violations("flowchart LR\n    A>Flag] --> B").isEmpty())
    }

    @Test
    fun `given a state diagram when checked then free text does not trip the bracket rules`() {
        val diagram = """
            stateDiagram-v2
                [*] --> QUEUED : enqueueTask
                QUEUED --> RUNNING : pipeline resolved,<br/>graph hash captured
                RUNNING --> [*] : note (unbalanced on purpose
        """.trimIndent()

        assertTrue(violations(diagram).isEmpty())
    }

    @Test
    fun `given several documents when checked then every block is counted`() {
        val docs = mapOf(
            "a.md" to "```mermaid\nflowchart LR\n    A --> B\n```\n",
            "b.md" to "```mermaid\ngraph TD\n    A --> B\n```\n\n```mermaid\npie\n    \"a\" : 1\n```\n",
        )

        assertEquals(3, MermaidBlockChecker.check(docs).blockCount)
    }
}
