package ai.agent.android.domain.models

/**
 * Domain model representing a downloaded LLM model's metadata.
 * This class is independent of any persistence mechanism.
 *
 * @property id Unique identifier for the model record.
 * @property name Human-readable name of the model.
 * @property path Absolute path to the model file on the device.
 * @property size Size of the model file in bytes.
 * @property isActive Flag indicating if this model is currently active/selected.
 */
data class LocalModel(val id: Long = 0, val name: String, val path: String, val size: Long, val isActive: Boolean)
