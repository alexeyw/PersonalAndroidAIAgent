package ai.agent.android.data.mappers

import ai.agent.android.data.local.models.ChatMessageEntity
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageMapperTest {

    @Test
    fun `toDomain maps entity correctly`() {
        val entity = ChatMessageEntity(
            id = 1L,
            sessionId = "session123",
            role = "USER",
            content = "Hello there",
            timestamp = 1600000000L
        )

        val domain = entity.toDomain()

        assertEquals(1L, domain.id)
        assertEquals("session123", domain.sessionId)
        assertEquals(Role.USER, domain.role)
        assertEquals("Hello there", domain.content)
        assertEquals(1600000000L, domain.timestamp)
    }

    @Test
    fun `toDomain maps invalid role to SYSTEM`() {
        val entity = ChatMessageEntity(
            id = 2L,
            sessionId = "session123",
            role = "UNKNOWN_ROLE",
            content = "Error message",
            timestamp = 1600000000L
        )

        val domain = entity.toDomain()

        assertEquals(Role.SYSTEM, domain.role)
    }

    @Test
    fun `toEntity maps domain correctly`() {
        val domainModel = ChatMessage(
            id = 3L,
            sessionId = "session456",
            role = Role.AGENT,
            content = "How can I help?",
            timestamp = 1600000000L
        )

        val entity = domainModel.toEntity()

        assertEquals(3L, entity.id)
        assertEquals("session456", entity.sessionId)
        assertEquals("AGENT", entity.role)
        assertEquals("How can I help?", entity.content)
        assertEquals(1600000000L, entity.timestamp)
    }

    @Test
    fun `toEntity maps domain with null id to entity with id 0`() {
        val domain = ChatMessage(
            id = null,
            sessionId = "session789",
            role = Role.USER,
            content = "New message",
            timestamp = 1600000000L
        )

        val entity = domain.toEntity()

        assertEquals(0L, entity.id)
        assertEquals("session789", entity.sessionId)
        assertEquals("USER", entity.role)
        assertEquals("New message", entity.content)
        assertEquals(1600000000L, entity.timestamp)
    }

    @Test
    fun `toDomain preserves isFinal and isStarred flags`() {
        val entity = ChatMessageEntity(
            id = 10L,
            sessionId = "session-flags",
            role = "AGENT",
            content = "intermediate",
            timestamp = 1700000000L,
            isFinal = false,
            isStarred = true,
        )

        val domain = entity.toDomain()

        assertEquals(false, domain.isFinal)
        assertEquals(true, domain.isStarred)
    }

    @Test
    fun `toDomain defaults isFinal to true and isStarred to false for legacy entity`() {
        val entity = ChatMessageEntity(
            id = 11L,
            sessionId = "legacy",
            role = "USER",
            content = "legacy",
            timestamp = 1700000001L,
        )

        val domain = entity.toDomain()

        assertEquals(true, domain.isFinal)
        assertEquals(false, domain.isStarred)
    }

    @Test
    fun `toEntity round-trips isFinal and isStarred`() {
        val domain = ChatMessage(
            id = 12L,
            sessionId = "round-trip",
            role = Role.SYSTEM,
            content = "tool observation",
            timestamp = 1700000002L,
            isFinal = false,
            isStarred = true,
        )

        val entity = domain.toEntity()

        assertEquals(false, entity.isFinal)
        assertEquals(true, entity.isStarred)
    }
}
