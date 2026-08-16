package app.knotwork.android.buildtools

/**
 * Verifies that classes the app instantiates **reflectively** are still
 * instantiable in a minified artefact.
 *
 * ### Why a second R8 guard exists
 *
 * [R8MappingChecker] asserts that a protected package stays identity-mapped,
 * which catches a keep rule that stopped pinning *names*. It cannot catch the
 * failure this checker exists for, and that gap shipped: R8 in full mode left
 * `com.google.protobuf.Any` with its own name and made the class **abstract**,
 * because protobuf-javalite never calls a constructor — `getDefaultInstance()`
 * materialises the message through `Unsafe.allocateInstance`, so R8 sees no
 * allocation site. The mapping was clean; the class was unusable. MediaPipe
 * parses its task graph as a protobuf, so every `TextEmbedder.createFromOptions`
 * threw `InstantiationException`, long-term memory failed on every release
 * build, and the failure surfaced as a snackbar rather than a crash — invisible
 * to logcat, to Crashlytics, and to every test the JVM gate can run.
 *
 * Reading the packaged dex is the only place where "this class can still be
 * instantiated" is a checkable fact rather than an assumption.
 *
 * ### What it checks
 *
 * For each required type: the class is present in the dex **and** its
 * `access_flags` carry neither `ACC_ABSTRACT` nor `ACC_INTERFACE`. Absence is a
 * violation too — a class R8 removed entirely fails just as hard at runtime as
 * one it made abstract.
 */
object DexInstantiabilityChecker {

    /** `ACC_INTERFACE` in the dex `access_flags` bit set. */
    private const val ACC_INTERFACE = 0x0200

    /** `ACC_ABSTRACT` in the dex `access_flags` bit set. */
    private const val ACC_ABSTRACT = 0x0400

    /** Byte offset of `string_ids_size` in the dex header. */
    private const val STRING_IDS_SIZE_OFFSET = 56

    /** Byte offset of `type_ids_size` in the dex header. */
    private const val TYPE_IDS_SIZE_OFFSET = 64

    /** Byte offset of `class_defs_size` in the dex header. */
    private const val CLASS_DEFS_SIZE_OFFSET = 96

    /** Size of one `class_def_item`, in bytes. */
    private const val CLASS_DEF_ITEM_SIZE = 32

    /** Size of one `type_id_item` (a `descriptor_idx`), in bytes. */
    private const val TYPE_ID_ITEM_SIZE = 4

    /** Size of one `string_id_item` (a `string_data_off`), in bytes. */
    private const val STRING_ID_ITEM_SIZE = 4

    /** Continuation-bit mask of a ULEB128 byte. */
    private const val ULEB_CONTINUATION = 0x80

    /** Payload mask of a ULEB128 byte. */
    private const val ULEB_PAYLOAD = 0x7F

    /** Bits carried by one ULEB128 byte. */
    private const val ULEB_SHIFT = 7

    private const val BYTE_MASK = 0xFF

    /**
     * A class that cannot be instantiated at runtime.
     *
     * @property className Java class name, e.g. `com.google.protobuf.Any`.
     * @property reason What is wrong, phrased for a build-failure message.
     */
    data class Violation(val className: String, val reason: String) {
        /** @return One line naming the class and the reason, for the build log. */
        fun format(): String = "  - $className: $reason"
    }

    /**
     * Checks every required class against the dex files of one artefact.
     *
     * @param dexFiles Raw contents of every `classes*.dex` in the artefact.
     * @param requiredInstantiable Java class names that must be present and
     *   instantiable, e.g. `com.google.protobuf.Any`.
     * @return One violation per class that is missing, abstract, or an
     *   interface; empty when every class is instantiable.
     */
    fun verify(dexFiles: List<ByteArray>, requiredInstantiable: List<String>): List<Violation> {
        val found: Map<String, Int> = dexFiles
            .flatMap { dex -> classAccessFlags(dex, requiredInstantiable.map(::toDescriptor).toSet()).entries }
            .associate { it.key to it.value }

        return requiredInstantiable.mapNotNull { className ->
            val flags = found[toDescriptor(className)]
            when {
                flags == null -> Violation(
                    className,
                    "not present in the packaged dex — R8 removed a class that is instantiated reflectively",
                )
                flags and ACC_INTERFACE != 0 -> Violation(className, "packaged as an interface")
                flags and ACC_ABSTRACT != 0 -> Violation(
                    className,
                    "packaged as ABSTRACT — R8 saw no allocation site (reflective instantiation is invisible to it) " +
                        "and `Unsafe.allocateInstance` will throw InstantiationException at runtime",
                )
                else -> null
            }
        }
    }

    /**
     * Converts a Java class name to its dex type descriptor.
     *
     * @param className e.g. `com.google.protobuf.Any`.
     * @return e.g. `Lcom/google/protobuf/Any;`.
     */
    private fun toDescriptor(className: String): String = "L${className.replace('.', '/')};"

    /**
     * Reads the `access_flags` of the requested types out of one dex file.
     *
     * Walks only the three tables it needs — `string_ids`, `type_ids` and
     * `class_defs` — rather than decoding the whole container.
     *
     * @param dex Raw dex bytes.
     * @param wantedDescriptors Type descriptors to look for.
     * @return Descriptor to `access_flags`, for the wanted types present here.
     */
    private fun classAccessFlags(dex: ByteArray, wantedDescriptors: Set<String>): Map<String, Int> {
        val stringIdsSize = readInt(dex, STRING_IDS_SIZE_OFFSET)
        val stringIdsOff = readInt(dex, STRING_IDS_SIZE_OFFSET + Int.SIZE_BYTES)
        val typeIdsSize = readInt(dex, TYPE_IDS_SIZE_OFFSET)
        val typeIdsOff = readInt(dex, TYPE_IDS_SIZE_OFFSET + Int.SIZE_BYTES)
        val classDefsSize = readInt(dex, CLASS_DEFS_SIZE_OFFSET)
        val classDefsOff = readInt(dex, CLASS_DEFS_SIZE_OFFSET + Int.SIZE_BYTES)

        // Resolve only the type indices whose descriptor is wanted: one pass over
        // type_ids, each entry costing one string read.
        val wantedTypeIndices = HashMap<Int, String>()
        for (typeIndex in 0 until typeIdsSize) {
            val descriptorIdx = readInt(dex, typeIdsOff + typeIndex * TYPE_ID_ITEM_SIZE)
            if (descriptorIdx >= stringIdsSize) continue
            val descriptor = readString(dex, readInt(dex, stringIdsOff + descriptorIdx * STRING_ID_ITEM_SIZE))
            if (descriptor in wantedDescriptors) wantedTypeIndices[typeIndex] = descriptor
        }
        if (wantedTypeIndices.isEmpty()) return emptyMap()

        val result = HashMap<String, Int>()
        for (classIndex in 0 until classDefsSize) {
            val offset = classDefsOff + classIndex * CLASS_DEF_ITEM_SIZE
            val typeIndex = readInt(dex, offset)
            val descriptor = wantedTypeIndices[typeIndex] ?: continue
            result[descriptor] = readInt(dex, offset + Int.SIZE_BYTES)
        }
        return result
    }

    /**
     * Reads a MUTF-8 string from a `string_data_item`.
     *
     * @param dex Raw dex bytes.
     * @param offset Offset of the `string_data_item`.
     * @return The decoded string. ASCII is assumed — every type descriptor is.
     */
    private fun readString(dex: ByteArray, offset: Int): String {
        var cursor = offset
        var length = 0
        var shift = 0
        while (true) {
            val byte = dex[cursor++].toInt() and BYTE_MASK
            length = length or ((byte and ULEB_PAYLOAD) shl shift)
            if (byte and ULEB_CONTINUATION == 0) break
            shift += ULEB_SHIFT
        }
        return String(dex, cursor, length, Charsets.UTF_8)
    }

    /**
     * @param dex Raw dex bytes.
     * @param offset Position of the first byte.
     * @return Little-endian 32-bit integer at [offset].
     */
    private fun readInt(dex: ByteArray, offset: Int): Int =
        (dex[offset].toInt() and BYTE_MASK) or
            ((dex[offset + 1].toInt() and BYTE_MASK) shl 8) or
            ((dex[offset + 2].toInt() and BYTE_MASK) shl 16) or
            ((dex[offset + 3].toInt() and BYTE_MASK) shl 24)
}
