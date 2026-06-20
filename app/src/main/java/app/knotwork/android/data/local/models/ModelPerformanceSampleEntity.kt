package app.knotwork.android.data.local.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a single recorded inference performance sample
 * ([app.knotwork.android.domain.models.ModelPerformanceSample]).
 *
 * Keyed by [modelPath] (the on-disk path of the model that ran) rather than a
 * foreign key onto `local_models`, so attribution never depends on resolving the
 * "Active model" sentinel and a sample survives the model row being edited.
 * Indexed on `(modelPath, id)` to back the "most recent N for this model" query
 * cheaply.
 *
 * Added in schema v42 (`MIGRATION_41_42`).
 *
 * @property id Auto-generated row id; also the recency ordering key.
 * @property modelPath Absolute on-disk path of the model that produced the run.
 * @property ttftMs Time-to-first-token in milliseconds (model-load excluded).
 * @property decodeTokensPerSec Decode throughput in tokens per second.
 * @property totalMs Total inference wall-clock in milliseconds (model-load excluded).
 * @property tokenCount Number of tokens the run produced.
 * @property peakNativeHeapBytes Peak process-wide native-heap allocation, in bytes.
 * @property isBenchmark `true` for a controlled-benchmark run, `false` for an
 *   ordinary pipeline run.
 * @property createdAt Epoch-millis the sample was recorded.
 */
@Entity(
    tableName = "model_performance_samples",
    indices = [Index(value = ["modelPath", "id"])],
)
data class ModelPerformanceSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val modelPath: String,
    val ttftMs: Long,
    val decodeTokensPerSec: Float,
    val totalMs: Long,
    val tokenCount: Int,
    val peakNativeHeapBytes: Long,
    val isBenchmark: Boolean,
    val createdAt: Long,
)
