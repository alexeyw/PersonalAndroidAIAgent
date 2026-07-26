package app.knotwork.android.data.engine

import app.knotwork.android.domain.engine.HardwareAccelerationProbe
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [HardwareAccelerationProbe] backed by the platform's OpenCL implementation —
 * the library LiteRT-LM's GPU backend ultimately dispatches through.
 *
 * The probe answers in two escalating steps, both of which stay clear of the
 * native inference stack (so neither can abort the process the way a real GPU
 * engine init can):
 *
 *  1. **Load the library.** `System.loadLibrary("OpenCL")` resolves against the
 *     platform namespace opened for us by the `<uses-native-library>` entries in
 *     the manifest. A device that ships OpenCL links it here; one that does not
 *     raises `UnsatisfiedLinkError`, which is an ordinary catchable JVM error.
 *     This is the authoritative signal.
 *  2. **Look for the file.** Vendors occasionally ship OpenCL under a
 *     non-canonical soname that step 1 cannot resolve, so a miss falls back to
 *     probing the conventional vendor locations. This step is best-effort by
 *     construction: SELinux may deny the stat, which simply reads as "absent"
 *     and keeps the answer conservative.
 *
 * The result is memoised — the platform cannot gain or lose OpenCL while the
 * process lives, and step 1 has the side effect of loading a native library.
 *
 * @property loadLibrary Loads a system library by soname stem; throws when the
 *   library cannot be linked. Injected as a seam so unit tests can drive both
 *   branches on the JVM.
 * @property fileExists Reports whether a path exists; injected for the same
 *   reason.
 */
@Singleton
class OpenClAccelerationProbe(private val loadLibrary: (String) -> Unit, private val fileExists: (String) -> Boolean) :
    HardwareAccelerationProbe {

    /** Production wiring: the real dynamic linker and the real filesystem. */
    @Inject
    constructor() : this(
        loadLibrary = { System.loadLibrary(it) },
        fileExists = { File(it).exists() },
    )

    private val gpuAvailable: Boolean by lazy { detectGpu() }

    override fun isGpuAvailable(): Boolean = gpuAvailable

    private fun detectGpu(): Boolean {
        if (canLinkOpenCl()) {
            Timber.i("GPU acceleration probe: OpenCL linked — GPU backend is plausible.")
            return true
        }
        val foundAt = VENDOR_OPENCL_PATHS.firstOrNull { path ->
            // A denied stat is indistinguishable from an absent file here, and
            // both mean the same thing for our purposes: no evidence of OpenCL.
            runCatching { fileExists(path) }.getOrDefault(false)
        }
        if (foundAt != null) {
            Timber.i("GPU acceleration probe: OpenCL found at %s — GPU backend is plausible.", foundAt)
            return true
        }
        Timber.i("GPU acceleration probe: no OpenCL implementation found — staying on CPU.")
        return false
    }

    /**
     * Attempts to link the platform OpenCL library.
     *
     * Catches [Throwable] rather than [Exception] because the failure mode is
     * `UnsatisfiedLinkError` — an [Error], not an exception — and a probe that
     * let it escape would turn "this device has no GPU" into a crash.
     */
    private fun canLinkOpenCl(): Boolean = try {
        loadLibrary(OPENCL_LIBRARY)
        true
    } catch (e: Throwable) {
        Timber.d(e, "OpenCL could not be linked by soname")
        false
    }

    private companion object {
        /** Soname stem passed to the dynamic linker (`libOpenCL.so`). */
        const val OPENCL_LIBRARY = "OpenCL"

        /**
         * Conventional vendor locations of an OpenCL implementation, checked
         * only when the linker could not resolve the canonical soname. 64-bit
         * paths lead because every device this app supports is 64-bit; the
         * 32-bit twins are kept for vendor images that only populate `lib/`.
         *
         * Every entry is an OpenCL library proper. Graphics drivers that merely
         * *tend* to ship OpenCL (`libGLES_mali.so` and friends) are deliberately
         * excluded: a false positive here is not free — it buys a real GPU init
         * attempt, and that is precisely the thing that can abort the process.
         */
        val VENDOR_OPENCL_PATHS = listOf(
            "/vendor/lib64/libOpenCL.so",
            "/system/vendor/lib64/libOpenCL.so",
            "/system/lib64/libOpenCL.so",
            "/vendor/lib64/libOpenCL-pixel.so",
            "/vendor/lib64/libPVROCL.so",
            "/system/vendor/lib64/libPVROCL.so",
            "/vendor/lib/libOpenCL.so",
            "/system/vendor/lib/libOpenCL.so",
            "/system/lib/libOpenCL.so",
        )
    }
}
