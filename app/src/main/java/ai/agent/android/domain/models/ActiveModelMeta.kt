package ai.agent.android.domain.models

/**
 * Rich metadata for the currently active local model rendered inside the
 * Settings → Local model card.
 *
 * Constructed from a [LocalModel] plus on-device file inspection. Kept
 * separate from `LocalModel` so the in-memory cache the catalog renders
 * can carry display-friendly fields ("1.4 GB", "Q4_K_M") without polluting
 * the persistence schema.
 *
 * @property modelId Identifier of the backing [LocalModel] record.
 * @property name Human-readable model name (e.g. `gemma-2b-it-q4`).
 * @property sizeBytes Size of the model file on disk in bytes.
 * @property contextWindowTokens Best-effort context window in tokens
 *   (parsed from the model's manifest or sane default).
 * @property quantization Free-form quantization marker (`Q4_K_M`, `FP16`,
 *   …) parsed from the model filename. `null` when the filename does not
 *   match the conventional pattern.
 * @property downloadedAtMs `System.currentTimeMillis()` snapshot of when
 *   the model became available on the device. `null` when the file is not
 *   reachable (race during deletion).
 */
data class ActiveModelMeta(
    val modelId: Long,
    val name: String,
    val sizeBytes: Long,
    val contextWindowTokens: Int,
    val quantization: String?,
    val downloadedAtMs: Long?,
)
