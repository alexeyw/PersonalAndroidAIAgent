package app.knotwork.android.domain.services

import app.knotwork.android.domain.models.MemoryChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Unit tests for [CompactionCoverageVerifier].
 *
 * Embeddings are unit vectors at explicit angles, so "the summary sits as
 * centrally as the cluster's centroid" is exact arithmetic rather than a
 * plausible-looking float: the cosine of two such vectors is the cosine of the
 * angle between them.
 */
class CompactionCoverageVerifierTest {

    private val verifier = CompactionCoverageVerifier()

    /** A unit vector at [degrees] in the first two dimensions. */
    private fun unit(degrees: Double): FloatArray {
        val radians = degrees * PI / 180.0
        return floatArrayOf(cos(radians).toFloat(), sin(radians).toFloat())
    }

    private fun chunk(id: Long, embedding: FloatArray) =
        MemoryChunk(id = id, text = "fact-$id", embedding = embedding, timestamp = 0L)

    @Test
    fun `given a summary at the cluster centre when verified then every member is covered`() {
        val members = listOf(
            chunk(1, unit(0.0)),
            chunk(2, unit(10.0)),
            chunk(3, unit(20.0)),
        )

        val verdict = verifier.verify(members, summaryEmbedding = unit(10.0))

        assertEquals(listOf(1L, 2L, 3L), verdict.covered.map { it.id })
        assertTrue(verdict.uncovered.isEmpty())
    }

    @Test
    fun `given a summary that drifted off topic when verified then no member is covered`() {
        val members = listOf(
            chunk(1, unit(0.0)),
            chunk(2, unit(10.0)),
            chunk(3, unit(20.0)),
        )

        // 30 degrees off the far edge of the cluster: the model answered about
        // something else, and the originals must survive.
        val verdict = verifier.verify(members, summaryEmbedding = unit(50.0))

        assertTrue(verdict.covered.isEmpty())
        assertEquals(listOf(1L, 2L, 3L), verdict.uncovered.map { it.id })
    }

    @Test
    fun `given a summary that represents part of the cluster when verified then only that part is covered`() {
        // Two restatements of one fact plus an unrelated one dragged into the
        // cluster: centroid at 26.6 degrees, so the 90-degree member scores 0.45
        // against it and the summary's 0.0 falls far short.
        val members = listOf(
            chunk(1, unit(0.0)),
            chunk(2, unit(0.0)),
            chunk(3, unit(90.0)),
        )

        val verdict = verifier.verify(members, summaryEmbedding = unit(0.0))

        assertEquals(listOf(1L, 2L), verdict.covered.map { it.id })
        assertEquals(listOf(3L), verdict.uncovered.map { it.id })
    }

    @Test
    fun `given a summary slightly less central than the centroid when verified then the margin admits it`() {
        // A summary is a rewrite, not an average, so it lands a little further
        // out than the centroid; within the tolerance that still counts.
        val members = listOf(
            chunk(1, unit(0.0)),
            chunk(2, unit(2.0)),
            chunk(3, unit(4.0)),
        )

        // The furthest member sits 15 degrees away: cos(15) = 0.966 against a
        // centroid similarity of 0.999 — a gap of 0.033, admitted only because
        // of the 0.05 tolerance.
        val verdict = verifier.verify(members, summaryEmbedding = unit(15.0))

        assertEquals(listOf(1L, 2L, 3L), verdict.covered.map { it.id })
    }

    @Test
    fun `given a member with no usable vector when verified then it is not covered`() {
        // Absence of evidence must never authorise a deletion.
        val members = listOf(
            chunk(1, unit(0.0)),
            chunk(2, unit(0.0)),
            chunk(3, FloatArray(0)),
        )

        val verdict = verifier.verify(members, summaryEmbedding = unit(0.0))

        assertEquals(listOf(1L, 2L), verdict.covered.map { it.id })
        assertEquals(listOf(3L), verdict.uncovered.map { it.id })
    }

    @Test
    fun `given a member from another embedding space when verified then it is not covered`() {
        // A chunk awaiting the background re-embed: its vector cannot be
        // compared with the cluster's at all.
        val members = listOf(
            chunk(1, unit(0.0)),
            chunk(2, unit(0.0)),
            chunk(3, floatArrayOf(1f, 0f, 0f)),
        )

        val verdict = verifier.verify(members, summaryEmbedding = unit(0.0))

        assertEquals(listOf(1L, 2L), verdict.covered.map { it.id })
        assertEquals(listOf(3L), verdict.uncovered.map { it.id })
    }

    @Test
    fun `given a summary embedded by another provider when verified then nothing is covered`() {
        // Cross-space vectors score 0 against every member, so the gate fails
        // safe and the pass deletes nothing.
        val members = listOf(
            chunk(1, unit(0.0)),
            chunk(2, unit(10.0)),
            chunk(3, unit(20.0)),
        )

        val verdict = verifier.verify(members, summaryEmbedding = floatArrayOf(1f, 0f, 0f))

        assertTrue(verdict.covered.isEmpty())
        assertEquals(3, verdict.uncovered.size)
    }

    @Test
    fun `given an empty cluster when verified then the verdict is empty`() {
        val verdict = verifier.verify(members = emptyList(), summaryEmbedding = unit(0.0))

        assertTrue(verdict.covered.isEmpty())
        assertTrue(verdict.uncovered.isEmpty())
    }
}
