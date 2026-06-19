package app.knotwork.android.data.mappers

import app.knotwork.android.data.local.models.ModelPerformanceSampleEntity
import app.knotwork.android.domain.models.ModelPerformanceSample

/**
 * Maps a [ModelPerformanceSampleEntity] to its [ModelPerformanceSample] domain model.
 *
 * @return The mapped domain sample.
 */
fun ModelPerformanceSampleEntity.toDomain(): ModelPerformanceSample = ModelPerformanceSample(
    id = id,
    modelPath = modelPath,
    ttftMs = ttftMs,
    decodeTokensPerSec = decodeTokensPerSec,
    totalMs = totalMs,
    tokenCount = tokenCount,
    peakNativeHeapBytes = peakNativeHeapBytes,
    isBenchmark = isBenchmark,
    createdAt = createdAt,
)

/**
 * Maps a [ModelPerformanceSample] domain model to its [ModelPerformanceSampleEntity].
 * The `id` is carried through but ignored by Room on insert (auto-generated).
 *
 * @return The mapped entity.
 */
fun ModelPerformanceSample.toEntity(): ModelPerformanceSampleEntity = ModelPerformanceSampleEntity(
    id = id,
    modelPath = modelPath,
    ttftMs = ttftMs,
    decodeTokensPerSec = decodeTokensPerSec,
    totalMs = totalMs,
    tokenCount = tokenCount,
    peakNativeHeapBytes = peakNativeHeapBytes,
    isBenchmark = isBenchmark,
    createdAt = createdAt,
)
