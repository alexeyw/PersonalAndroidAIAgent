package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ExternalAutomationDocsGenerator].
 *
 * The generator publishes a contract whose callers live in other apps, so the
 * cases that matter are the silent ones: a row that renders empty, a block that
 * looks current while the source moved, a declaration that slipped in without
 * documentation. Each of those is asserted here rather than trusted to review.
 */
class ExternalAutomationDocsGeneratorTest {

    private val contractSource = """
        package app.knotwork.android.domain.constants

        /**
         * Contract KDoc that documents the object, not the first constant.
         */
        object ExternalAutomationContract {

            /** Broadcast action a caller sends. */
            const val ACTION_RUN_PIPELINE: String = "app.knotwork.android.action.RUN_PIPELINE"

            /**
             * Request key: id of the pipeline. Mutually exclusive with [EXTRA_PIPELINE_NAME].
             *
             * Second paragraph that must not reach the table.
             *
             * @param ignored Tags must not reach the table either.
             */
            const val EXTRA_PIPELINE_ID: String = "pipeline_id"
        }
    """.trimIndent()

    private val statusSource = """
        package app.knotwork.android.domain.models

        /** File-level KDoc that documents the interface. */
        sealed interface ExternalAutomationStatus {

            /** The request was admitted. */
            data object Accepted : ExternalAutomationStatus

            /**
             * The request was refused before anything started.
             */
            data class Rejected(val reason: ExternalAutomationRejectionReason) : ExternalAutomationStatus
        }
    """.trimIndent()

    private val reasonSource = """
        package app.knotwork.android.domain.models

        /** Enum-level KDoc that documents the enum. */
        enum class ExternalAutomationRejectionReason {
            /** The contract is switched off. */
            CONTRACT_DISABLED,

            /** No pipeline is bound. */
            SURFACE_NOT_BOUND,
        }
    """.trimIndent()

    private val markdown = """
        # External automation contract

        <!-- AUTO-GEN:CONTRACT_KEYS -->
        <!-- /AUTO-GEN:CONTRACT_KEYS -->

        <!-- AUTO-GEN:STATUSES -->
        <!-- /AUTO-GEN:STATUSES -->

        <!-- AUTO-GEN:REJECTION_REASONS -->
        <!-- /AUTO-GEN:REJECTION_REASONS -->
    """.trimIndent()

    private fun render(md: String = markdown): String =
        ExternalAutomationDocsGenerator.render(md, contractSource, statusSource, reasonSource)

    private fun drift(md: String): List<String> =
        ExternalAutomationDocsGenerator.drift(md, contractSource, statusSource, reasonSource)

    @Test
    fun `given contract constants when rendering then each constant becomes a row with name value and doc`() {
        val result = render()

        assertTrue(
            result,
            result.contains("| `ACTION_RUN_PIPELINE` | `app.knotwork.android.action.RUN_PIPELINE` |"),
        )
        assertTrue(result, result.contains("| `EXTRA_PIPELINE_ID` | `pipeline_id` |"))
    }

    @Test
    fun `given multi paragraph KDoc when rendering then only the first paragraph reaches the table`() {
        val result = render()

        assertTrue(result, result.contains("Request key: id of the pipeline."))
        assertTrue(result, !result.contains("Second paragraph"))
        assertTrue(result, !result.contains("Tags must not reach"))
    }

    @Test
    fun `given a KDoc reference when rendering then it becomes inline code rather than a broken link`() {
        val result = render()

        assertTrue(result, result.contains("Mutually exclusive with `EXTRA_PIPELINE_NAME`."))
        assertTrue(result, !result.contains("[EXTRA_PIPELINE_NAME]"))
    }

    @Test
    fun `given a sealed status hierarchy when rendering then objects and data classes both become rows`() {
        val result = render()

        assertTrue(result, result.contains("| `Accepted` | The request was admitted. |"))
        assertTrue(result, result.contains("| `Rejected` | The request was refused before anything started. |"))
    }

    @Test
    fun `given an enum when rendering then every constant becomes a row in declaration order`() {
        val result = render()

        val disabled = result.indexOf("| `CONTRACT_DISABLED` |")
        val unbound = result.indexOf("| `SURFACE_NOT_BOUND` |")
        assertTrue(result, disabled in 1..<unbound)
    }

    @Test
    fun `given a member whose supertype wrapped onto the next line when rendering then it still becomes a row`() {
        // A formatter wrapping a long declaration must not silently drop the row:
        // the drift check reads through the same parser and would call the
        // now-incomplete table current.
        val wrapped = statusSource.replace(
            "    data class Rejected(val reason: ExternalAutomationRejectionReason) : ExternalAutomationStatus",
            "    data class Rejected(\n" +
                "        val reason: ExternalAutomationRejectionReason,\n" +
                "    ) : ExternalAutomationStatus",
        )

        val result = ExternalAutomationDocsGenerator.render(markdown, contractSource, wrapped, reasonSource)

        assertTrue(result, result.contains("| `Rejected` | The request was refused before anything started. |"))
    }

    @Test
    fun `given a sealed source without the named interface when rendering then generation fails`() {
        val other = "package app.knotwork.android.domain.models\n\nsealed interface SomethingElse\n"

        val error = runCatching {
            ExternalAutomationDocsGenerator.render(markdown, contractSource, other, reasonSource)
        }.exceptionOrNull()

        assertTrue(
            "expected a GenerationException, got $error",
            error is ExternalAutomationDocsGenerator.GenerationException,
        )
    }

    @Test
    fun `given already rendered markdown when rendering again then the result is unchanged`() {
        val once = render()

        assertEquals(once, render(once))
    }

    @Test
    fun `given up to date markdown when checking drift then no block is reported`() {
        assertEquals(emptyList<String>(), drift(render()))
    }

    @Test
    fun `given an empty document when checking drift then every block is reported`() {
        assertEquals(ExternalAutomationDocsGenerator.BLOCKS, drift(markdown))
    }

    @Test
    fun `given an edited generated table when checking drift then only that block is reported`() {
        val tampered = render().replace("`CONTRACT_DISABLED`", "`CONTRACT_TURNED_OFF`")

        assertEquals(listOf(ExternalAutomationDocsGenerator.BLOCK_REJECTION_REASONS), drift(tampered))
    }

    @Test
    fun `given a constant without KDoc when rendering then generation fails naming the constant`() {
        val undocumented = contractSource.replace("/** Broadcast action a caller sends. */\n", "")

        val error = runCatching {
            ExternalAutomationDocsGenerator.render(markdown, undocumented, statusSource, reasonSource)
        }.exceptionOrNull()

        assertTrue(
            "expected a GenerationException, got $error",
            error is ExternalAutomationDocsGenerator.GenerationException,
        )
        assertTrue(error?.message.orEmpty(), error?.message.orEmpty().contains("ACTION_RUN_PIPELINE"))
    }

    @Test
    fun `given a source declaring nothing when rendering then generation fails rather than emitting an empty table`() {
        val empty = "package app.knotwork.android.domain.constants\n\nobject ExternalAutomationContract\n"

        val error = runCatching {
            ExternalAutomationDocsGenerator.render(markdown, empty, statusSource, reasonSource)
        }.exceptionOrNull()

        assertTrue(
            "expected a GenerationException, got $error",
            error is ExternalAutomationDocsGenerator.GenerationException,
        )
    }

    @Test
    fun `given markdown without markers when rendering then generation fails naming the block`() {
        val error = runCatching { render("# No markers here\n") }.exceptionOrNull()

        assertTrue(
            "expected a GenerationException, got $error",
            error is ExternalAutomationDocsGenerator.GenerationException,
        )
        assertTrue(
            error?.message.orEmpty(),
            error?.message.orEmpty().contains(ExternalAutomationDocsGenerator.BLOCK_CONTRACT_KEYS),
        )
    }

    @Test
    fun `given markdown without markers when extracting a block then null is returned`() {
        assertNull(
            ExternalAutomationDocsGenerator.extractBlock("# nothing", ExternalAutomationDocsGenerator.BLOCK_STATUSES),
        )
    }

    @Test
    fun `given a pipe inside a KDoc when rendering then it is escaped instead of splitting the row`() {
        val piped = contractSource.replace(
            "/** Broadcast action a caller sends. */",
            "/** Broadcast action | with a pipe. */",
        )

        val result = ExternalAutomationDocsGenerator.render(markdown, piped, statusSource, reasonSource)

        assertTrue(result, result.contains("""Broadcast action \| with a pipe."""))
    }

    @Test
    fun `given hand written prose around the markers when rendering then it survives untouched`() {
        val withProse = markdown.replace(
            "<!-- AUTO-GEN:STATUSES -->",
            "Hand-written sentence that must survive.\n\n<!-- AUTO-GEN:STATUSES -->",
        )

        assertTrue(render(withProse).contains("Hand-written sentence that must survive."))
    }
}
