package app.knotwork.android.domain.models

/**
 * Domain model representing a downloaded LLM model's metadata.
 * This class is independent of any persistence mechanism.
 *
 * @property id Unique identifier for the model record.
 * @property name Human-readable name of the model.
 * @property path Absolute path to the model file on the device.
 * @property size Size of the model file in bytes.
 * @property isActive Flag indicating if this model is currently active/selected.
 * @property supportsVision Whether this model can accept an image alongside the
 *   text prompt (a vision-capable LiteRT-LM model such as Gemma 4). Defaults to
 *   `false`: the LiteRT-LM 0.13.1 runtime exposes no capability probe (its
 *   `Capabilities` surface reports only speculative-decoding support), so vision
 *   support cannot be auto-detected from the `.litertlm` file and is instead a
 *   user-set flag on the model screen. The pre-flight send guard reads it to
 *   block an image message against a text-only active model before a run starts.
 */
data class LocalModel(
    val id: Long = 0,
    val name: String,
    val path: String,
    val size: Long,
    val isActive: Boolean,
    val supportsVision: Boolean = false,
)
