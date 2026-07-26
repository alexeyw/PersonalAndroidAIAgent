package app.knotwork.android.data.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two-step OpenCL detection and, above all, its failure discipline:
 * the probe exists so a *missing* GPU never reaches the native engine, so any
 * path that let an error escape would defeat its entire purpose.
 */
class OpenClAccelerationProbeTest {

    @Test
    fun `given the linker resolves OpenCL when probed then GPU is reported available`() {
        var requested: String? = null
        val probe = OpenClAccelerationProbe(
            loadLibrary = { requested = it },
            fileExists = { false },
        )

        assertTrue(probe.isGpuAvailable())
        assertEquals("OpenCL", requested)
    }

    @Test
    fun `given the linker fails but a vendor library exists when probed then GPU is reported available`() {
        val probe = OpenClAccelerationProbe(
            loadLibrary = { throw UnsatisfiedLinkError("dlopen failed: library \"libOpenCL.so\" not found") },
            fileExists = { path -> path == "/vendor/lib64/libOpenCL.so" },
        )

        assertTrue(probe.isGpuAvailable())
    }

    @Test
    fun `given no OpenCL anywhere when probed then GPU is reported unavailable`() {
        val probe = OpenClAccelerationProbe(
            loadLibrary = { throw UnsatisfiedLinkError("no OpenCL") },
            fileExists = { false },
        )

        assertFalse(probe.isGpuAvailable())
    }

    @Test
    fun `given the filesystem check throws when probed then the probe answers false instead of propagating`() {
        // SELinux can deny the stat outright; a denied lookup is evidence of
        // nothing and must never surface as a crash on the onboarding path.
        val probe = OpenClAccelerationProbe(
            loadLibrary = { throw UnsatisfiedLinkError("no OpenCL") },
            fileExists = { throw SecurityException("denied") },
        )

        assertFalse(probe.isGpuAvailable())
    }

    @Test
    fun `given repeated probes when queried then the detection runs only once`() {
        var loadAttempts = 0
        val probe = OpenClAccelerationProbe(
            loadLibrary = { loadAttempts += 1 },
            fileExists = { false },
        )

        repeat(3) { probe.isGpuAvailable() }

        // Step 1 loads a native library — re-running it per query would be both
        // wasteful and, on a device, repeatedly dlopen the vendor driver.
        assertEquals(1, loadAttempts)
    }
}
