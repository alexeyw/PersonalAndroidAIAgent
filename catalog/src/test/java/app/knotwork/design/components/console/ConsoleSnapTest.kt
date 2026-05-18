package app.knotwork.design.components.console

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Documents the discrete [ConsoleSnap] heights so an accidental tweak shows
 * up as a unit-test breakage rather than a silent snapshot diff. The
 * canonical values are pulled from `compose/components/README.md` §Chat
 * surface §ConsolePane (Peek 44 dp / Partial 360 dp ≈ 40 % / Full 720 dp ≈
 * 90 %).
 */
class ConsoleSnapTest {

    @Test
    fun `peek snap is 44 dp`() {
        assertEquals(44.dp, ConsoleSnap.Peek.height)
    }

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
        assertEquals(true, ConsoleSnap.Peek.height < ConsoleSnap.Partial.height)
        assertEquals(true, ConsoleSnap.Partial.height < ConsoleSnap.Full.height)
    }
}
