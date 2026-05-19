package app.knotwork.design.components.console

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Documents the discrete [ConsoleSnap] heights as preview-only fixtures.
 * The production console is hosted inside an M3 `ModalBottomSheet` that
 * owns its own anchored heights — the constants here drive
 * snapshot/preview rendering only.
 */
class ConsoleSnapTest {

    @Test
    fun `partial snap is 360 dp`() {
        assertEquals(360.dp, ConsoleSnap.Partial.height)
    }

    @Test
    fun `full snap is 720 dp`() {
        assertEquals(720.dp, ConsoleSnap.Full.height)
    }

    @Test
    fun `snaps grow monotonically`() {
        assertEquals(true, ConsoleSnap.Partial.height < ConsoleSnap.Full.height)
    }
}
