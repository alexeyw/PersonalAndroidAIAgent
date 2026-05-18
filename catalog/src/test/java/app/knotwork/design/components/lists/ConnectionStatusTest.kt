package app.knotwork.design.components.lists

import app.knotwork.design.components.chips.Status
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM mapping test for [ConnectionStatus.toStatus]. Catches accidental
 * regressions in the connection-status-to-status-pill projection without
 * requiring a Roborazzi run.
 */
class ConnectionStatusTest {
    @Test
    fun `Connected maps to Success`() {
        assertEquals(Status.Success, ConnectionStatus.Connected.toStatus())
    }

    @Test
    fun `Disconnected maps to Error`() {
        assertEquals(Status.Error, ConnectionStatus.Disconnected.toStatus())
    }

    @Test
    fun `Syncing maps to Running`() {
        assertEquals(Status.Running, ConnectionStatus.Syncing.toStatus())
    }

    @Test
    fun `Error maps to Error`() {
        assertEquals(Status.Error, ConnectionStatus.Error.toStatus())
    }
}
