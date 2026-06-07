package app.knotwork.android.domain.models

/**
 * Parsed representation of a memory export file.
 *
 * Produced by [app.knotwork.android.domain.memoryio.MemoryJsonSerializer.parse] and
 * consumed by [app.knotwork.android.domain.usecases.MemoryImportUseCase] to load the
 * chunks back into the local store. Decoupled from the on-disk JSON so the
 * import use case never touches `org.json`.
 *
 * @property embeddingProviderId Id of the [app.knotwork.android.domain.services.EmbeddingProvider]
 *   that was active when the file was exported. When this differs from the
 *   importing device's active provider the stored embeddings live in a
 *   different vector space and must be re-computed before they can match a
 *   query (see [app.knotwork.android.domain.usecases.RecomputePendingEmbeddingsUseCase]).
 * @property exportedAt Epoch-millis the file was written. Informational only.
 * @property chunks The exported memory chunks, carrying their original id,
 *   text, embedding, provenance, timestamp, pin state, and tags.
 */
data class MemoryExportDocument(val embeddingProviderId: String, val exportedAt: Long, val chunks: List<MemoryChunk>)
