package ai.agent.android.domain.usecases

import ai.agent.android.domain.repositories.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import javax.inject.Inject

/**
 * Serialises the full memory table into a portable JSON blob and writes
 * it to an arbitrary [OutputStream]. Backs the Settings → Memory →
 * Export base action; the screen owns the SAF launcher that produces the
 * stream so the use case stays free of Android imports.
 *
 * Format: a top-level JSON object with `schemaVersion: 1` and `chunks`
 * — an array of objects carrying `id`, `text`, `embedding` (as a JSON
 * array of floats), `timestamp`, and `isPinned`. Re-import lives in a
 * follow-up task; until then the export is informational / migratable
 * via external tools.
 *
 * @return Number of chunks written.
 */
class ExportMemoryBaseUseCase @Inject constructor(private val memoryRepository: MemoryRepository) {
    /**
     * Serialises every chunk into [target] and returns the number of
     * entries written. Closes the stream via `buffered().use { … }`.
     */
    suspend operator fun invoke(target: OutputStream): Int = withContext(Dispatchers.IO) {
        val memories = memoryRepository.getAllMemories()
        val chunksJson = JSONArray()
        for (memory in memories) {
            val chunk = JSONObject().apply {
                put("id", memory.id)
                put("text", memory.text)
                put("timestamp", memory.timestamp)
                put("isPinned", memory.isPinned)
                val embeddingJson = JSONArray()
                for (value in memory.embedding) embeddingJson.put(value.toDouble())
                put("embedding", embeddingJson)
            }
            chunksJson.put(chunk)
        }
        val payload = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("chunks", chunksJson)
        }
        target.bufferedWriter().use { writer ->
            writer.write(payload.toString())
        }
        memories.size
    }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}
