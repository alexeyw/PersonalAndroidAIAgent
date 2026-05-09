package ai.agent.android.data.mappers

import ai.agent.android.data.local.models.ChatMessageEntity
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role

/**
 * Converts a [ChatMessageEntity] database model to a [ChatMessage] domain model.
 *
 * @return The corresponding [ChatMessage].
 */
fun ChatMessageEntity.toDomain(): ChatMessage {
    return ChatMessage(
        id = id,
        sessionId = sessionId,
        role = try { Role.valueOf(role) } catch (e: IllegalArgumentException) { Role.SYSTEM },
        content = content,
        timestamp = timestamp,
        isFinal = isFinal,
        isStarred = isStarred,
    )
}

/**
 * Converts a [ChatMessage] domain model to a [ChatMessageEntity] database model.
 *
 * @return The corresponding [ChatMessageEntity].
 */
fun ChatMessage.toEntity(): ChatMessageEntity {
    return ChatMessageEntity(
        id = id ?: 0,
        sessionId = sessionId,
        role = role.name,
        content = content,
        timestamp = timestamp,
        isFinal = isFinal,
        isStarred = isStarred,
    )
}
