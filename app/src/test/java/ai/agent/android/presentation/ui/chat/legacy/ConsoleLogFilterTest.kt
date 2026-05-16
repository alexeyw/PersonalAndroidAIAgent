package ai.agent.android.presentation.ui.chat.legacy

import ai.agent.android.domain.models.ConsoleEvent
import ai.agent.android.domain.models.ConsoleEventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ConsoleLogFilter.matches].
 *
 * The matrix is small (5 filters × 5 event types) but the predicate is the
 * single source of truth used by the expanded-console list, so each row
 * gets explicit coverage to lock the mapping.
 */
class ConsoleLogFilterTest {

    private fun event(type: ConsoleEventType): ConsoleEvent = ConsoleEvent(
        timestamp = 0L,
        type = type,
        message = "ignored",
    )

    @Test
    fun `given All filter when matches called then every type passes`() {
        val filter = ConsoleLogFilter.All
        assertTrue(filter.matches(event(ConsoleEventType.NodeExecution)))
        assertTrue(filter.matches(event(ConsoleEventType.ToolCall)))
        assertTrue(filter.matches(event(ConsoleEventType.MemoryAccess)))
        assertTrue(filter.matches(event(ConsoleEventType.SystemMessage)))
        assertTrue(filter.matches(event(ConsoleEventType.Error)))
    }

    @Test
    fun `given Nodes filter when matches called then only NodeExecution passes`() {
        val filter = ConsoleLogFilter.Nodes
        assertTrue(filter.matches(event(ConsoleEventType.NodeExecution)))
        assertFalse(filter.matches(event(ConsoleEventType.ToolCall)))
        assertFalse(filter.matches(event(ConsoleEventType.MemoryAccess)))
        assertFalse(filter.matches(event(ConsoleEventType.SystemMessage)))
        assertFalse(filter.matches(event(ConsoleEventType.Error)))
    }

    @Test
    fun `given Tools filter when matches called then only ToolCall passes`() {
        val filter = ConsoleLogFilter.Tools
        assertFalse(filter.matches(event(ConsoleEventType.NodeExecution)))
        assertTrue(filter.matches(event(ConsoleEventType.ToolCall)))
        assertFalse(filter.matches(event(ConsoleEventType.MemoryAccess)))
        assertFalse(filter.matches(event(ConsoleEventType.SystemMessage)))
        assertFalse(filter.matches(event(ConsoleEventType.Error)))
    }

    @Test
    fun `given Memory filter when matches called then only MemoryAccess passes`() {
        val filter = ConsoleLogFilter.Memory
        assertFalse(filter.matches(event(ConsoleEventType.NodeExecution)))
        assertFalse(filter.matches(event(ConsoleEventType.ToolCall)))
        assertTrue(filter.matches(event(ConsoleEventType.MemoryAccess)))
        assertFalse(filter.matches(event(ConsoleEventType.SystemMessage)))
        assertFalse(filter.matches(event(ConsoleEventType.Error)))
    }

    @Test
    fun `given Errors filter when matches called then only Error passes`() {
        val filter = ConsoleLogFilter.Errors
        assertFalse(filter.matches(event(ConsoleEventType.NodeExecution)))
        assertFalse(filter.matches(event(ConsoleEventType.ToolCall)))
        assertFalse(filter.matches(event(ConsoleEventType.MemoryAccess)))
        assertFalse(filter.matches(event(ConsoleEventType.SystemMessage)))
        assertTrue(filter.matches(event(ConsoleEventType.Error)))
    }
}
