package ai.agent.android.domain.models

/**
 * Domain model representing a chunk of long-term memory.
 *
 * @property id The unique identifier of the memory chunk.
 * @property text The raw text of the memory.
 * @property embedding The vector embedding of the text.
 * @property timestamp The time the memory was created.
 */
data class MemoryChunk(
    val id: Long,
    val text: String,
    val embedding: FloatArray,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemoryChunk

        if (id != other.id) return false
        if (text != other.text) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
