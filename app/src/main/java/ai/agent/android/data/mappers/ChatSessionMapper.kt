package ai.agent.android.data.mappers

import ai.agent.android.data.local.models.ChatSessionEntity
import ai.agent.android.domain.models.ChatSession

/**
 * Maps a [ChatSessionEntity] from the data layer to a [ChatSession] domain model.
 *
 * @return The mapped [ChatSession] instance.
 */
fun ChatSessionEntity.toDomain(): ChatSession = ChatSession(
    id = id,
    name = name,
    updatedAt = updatedAt,
    pipelineId = pipelineId,
    isStarred = isStarred,
)

/**
 * Maps a [ChatSession] domain model to a [ChatSessionEntity] for the data layer.
 *
 * @return The mapped [ChatSessionEntity] instance.
 */
fun ChatSession.toEntity(): ChatSessionEntity = ChatSessionEntity(
    id = id,
    name = name,
    updatedAt = updatedAt,
    pipelineId = pipelineId,
    isStarred = isStarred,
)
