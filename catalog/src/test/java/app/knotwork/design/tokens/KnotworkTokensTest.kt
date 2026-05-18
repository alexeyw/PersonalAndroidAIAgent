package app.knotwork.design.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pure-JVM sanity tests for the token data classes — no Compose runtime,
 * no Robolectric.
 *
 * These guard against the kind of regressions that would silently swap a
 * token's value (copy-paste between the light / dark factories,
 * accidentally pinning a default to the wrong dp value) without needing to
 * pay the Compose-render setup cost just to assert a few constants.
 */
class KnotworkTokensTest {
    @Test
    fun `given knotworkExtendedColorsLight and dark when compared then they differ`() {
        val light = knotworkExtendedColorsLight()
        val dark = knotworkExtendedColorsDark()
        assertNotEquals(light, dark)
    }

    @Test
    fun `given knotworkExtendedColorsLight when read nodeIntentRouter then equals expected hue`() {
        assertEquals(KnotworkPalette.NodeIntentRouter, knotworkExtendedColorsLight().nodeIntentRouter)
    }

    @Test
    fun `given knotworkExtendedColorsDark when read nodeIntentRouter then equals same hue as light`() {
        // Node hues are intentionally hue-locked across themes so a pipeline
        // graph reads identically in light and dark.
        assertEquals(
            knotworkExtendedColorsLight().nodeIntentRouter,
            knotworkExtendedColorsDark().nodeIntentRouter,
        )
    }

    @Test
    fun `given DefaultKnotworkSpacing when read sp4 then equals 16dp`() {
        assertEquals(16.dp, DefaultKnotworkSpacing.sp4)
    }

    @Test
    fun `given DefaultKnotworkSpacing when read sp16 then equals 64dp`() {
        assertEquals(64.dp, DefaultKnotworkSpacing.sp16)
    }

    @Test
    fun `given DefaultKnotworkShapes when read md then equals RoundedCornerShape 12dp`() {
        assertEquals(RoundedCornerShape(12.dp), DefaultKnotworkShapes.md)
    }

    @Test
    fun `given MaterialKnotworkShapes when read medium then equals KnotworkShapes md`() {
        assertEquals(DefaultKnotworkShapes.md, MaterialKnotworkShapes.medium)
    }

    @Test
    fun `given DefaultKnotworkElevation when read el3 then equals 6dp`() {
        assertEquals(6.dp, DefaultKnotworkElevation.el3)
    }

    @Test
    fun `given DefaultKnotworkMotion when read dur3 then equals 280ms`() {
        assertEquals(280, DefaultKnotworkMotion.dur3)
    }
}
