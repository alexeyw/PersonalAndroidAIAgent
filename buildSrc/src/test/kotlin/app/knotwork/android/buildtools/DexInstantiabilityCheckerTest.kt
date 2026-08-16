package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DexInstantiabilityChecker].
 *
 * The fixtures are synthetic dex containers carrying only the three tables the
 * checker reads (`string_ids`, `type_ids`, `class_defs`). Hand-building them is
 * the point: it pins the parser against the format rather than against whatever
 * a particular build of R8 happens to emit.
 */
class DexInstantiabilityCheckerTest {

    @Test
    fun `given a concrete class when verified then no violations`() {
        val dex = dexOf("Lcom/google/protobuf/Any;" to ACC_PUBLIC)

        val violations = DexInstantiabilityChecker.verify(listOf(dex), listOf("com.google.protobuf.Any"))

        assertTrue("expected no violations, got $violations", violations.isEmpty())
    }

    @Test
    fun `given an abstract class when verified then it is reported`() {
        // The real regression: R8 kept the name and made the class abstract,
        // so a mapping-based check passes while the artefact is broken.
        val dex = dexOf("Lcom/google/protobuf/Any;" to (ACC_PUBLIC or ACC_ABSTRACT))

        val violations = DexInstantiabilityChecker.verify(listOf(dex), listOf("com.google.protobuf.Any"))

        assertEquals(1, violations.size)
        assertEquals("com.google.protobuf.Any", violations.first().className)
        assertTrue(
            "reason should name the abstract flag, was ${violations.first().reason}",
            violations.first().reason.contains("ABSTRACT"),
        )
    }

    @Test
    fun `given a missing class when verified then absence is a violation too`() {
        val dex = dexOf("Lcom/example/Other;" to ACC_PUBLIC)

        val violations = DexInstantiabilityChecker.verify(listOf(dex), listOf("com.google.protobuf.Any"))

        assertEquals(1, violations.size)
        assertTrue(
            "a removed class must fail as loudly as an abstract one, was ${violations.first().reason}",
            violations.first().reason.contains("not present"),
        )
    }

    @Test
    fun `given an interface when verified then it is reported`() {
        val dex = dexOf("Lcom/google/protobuf/Any;" to (ACC_PUBLIC or ACC_INTERFACE or ACC_ABSTRACT))

        val violations = DexInstantiabilityChecker.verify(listOf(dex), listOf("com.google.protobuf.Any"))

        assertEquals(1, violations.size)
        assertTrue("expected the interface reason", violations.first().reason.contains("interface"))
    }

    @Test
    fun `given a multidex artefact when the class lives in the second file then it is found`() {
        val first = dexOf("Lcom/example/Other;" to ACC_PUBLIC)
        val second = dexOf("Lcom/google/protobuf/Any;" to ACC_PUBLIC)

        val violations = DexInstantiabilityChecker.verify(listOf(first, second), listOf("com.google.protobuf.Any"))

        assertTrue("a class in classes2.dex must count as present, got $violations", violations.isEmpty())
    }

    /**
     * Builds a minimal dex container holding the given classes.
     *
     * Only the tables the checker reads are emitted; every other header field
     * stays zero, which is exactly what the parser is allowed to depend on.
     *
     * @param classes Type descriptor to `access_flags`.
     * @return Synthetic dex bytes.
     */
    private fun dexOf(vararg classes: Pair<String, Int>): ByteArray {
        val header = ByteArray(HEADER_SIZE)
        val strings = classes.map { it.first }

        val stringData = ArrayList<Byte>()
        val stringOffsets = ArrayList<Int>()
        var cursor = HEADER_SIZE
        strings.forEach { value ->
            stringOffsets += cursor
            stringData += value.length.toByte()          // ULEB128, single byte for short descriptors
            value.forEach { stringData += it.code.toByte() }
            cursor += 1 + value.length
        }

        val stringIdsOff = cursor
        cursor += strings.size * Int.SIZE_BYTES
        val typeIdsOff = cursor
        cursor += strings.size * Int.SIZE_BYTES
        val classDefsOff = cursor
        cursor += classes.size * CLASS_DEF_ITEM_SIZE

        val body = ByteArray(cursor - HEADER_SIZE)
        fun putInt(absolute: Int, value: Int) {
            val at = absolute - HEADER_SIZE
            body[at] = (value and 0xFF).toByte()
            body[at + 1] = ((value shr 8) and 0xFF).toByte()
            body[at + 2] = ((value shr 16) and 0xFF).toByte()
            body[at + 3] = ((value shr 24) and 0xFF).toByte()
        }
        stringData.forEachIndexed { i, b -> body[i] = b }
        stringOffsets.forEachIndexed { i, off -> putInt(stringIdsOff + i * Int.SIZE_BYTES, off) }
        strings.indices.forEach { i -> putInt(typeIdsOff + i * Int.SIZE_BYTES, i) }
        classes.forEachIndexed { i, (_, flags) ->
            val at = classDefsOff + i * CLASS_DEF_ITEM_SIZE
            putInt(at, i)                 // class_idx -> type index
            putInt(at + Int.SIZE_BYTES, flags)
        }

        fun putHeader(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
            header[offset + 2] = ((value shr 16) and 0xFF).toByte()
            header[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        putHeader(STRING_IDS_SIZE_OFFSET, strings.size)
        putHeader(STRING_IDS_SIZE_OFFSET + Int.SIZE_BYTES, stringIdsOff)
        putHeader(TYPE_IDS_SIZE_OFFSET, strings.size)
        putHeader(TYPE_IDS_SIZE_OFFSET + Int.SIZE_BYTES, typeIdsOff)
        putHeader(CLASS_DEFS_SIZE_OFFSET, classes.size)
        putHeader(CLASS_DEFS_SIZE_OFFSET + Int.SIZE_BYTES, classDefsOff)

        return header + body
    }

    private companion object {
        const val HEADER_SIZE = 112
        const val CLASS_DEF_ITEM_SIZE = 32
        const val STRING_IDS_SIZE_OFFSET = 56
        const val TYPE_IDS_SIZE_OFFSET = 64
        const val CLASS_DEFS_SIZE_OFFSET = 96

        const val ACC_PUBLIC = 0x0001
        const val ACC_INTERFACE = 0x0200
        const val ACC_ABSTRACT = 0x0400
    }
}
