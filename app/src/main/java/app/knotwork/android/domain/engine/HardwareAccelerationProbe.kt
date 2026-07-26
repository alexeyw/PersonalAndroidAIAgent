package app.knotwork.android.domain.engine

/**
 * Answers the one question the on-device engine cannot answer safely by itself:
 * *is the GPU compute path plausibly usable on this device?*
 *
 * LiteRT-LM exposes no availability API — constructing `Backend.GPU()` succeeds
 * even on devices whose chipset ships no OpenCL implementation, and the failure
 * only surfaces later, either as a native abort during engine init or as an
 * error on the first generation. A *static* probe (one that inspects what the
 * platform ships, without touching the native inference stack) is therefore the
 * only way to gate the decision before anything can crash.
 *
 * The probe is deliberately a **plausibility** check, not a guarantee: devices
 * exist whose OpenCL library is present but whose driver still fails to compile
 * the runtime's kernels. Callers must treat a `true` answer as "worth trying,
 * with a fallback ready", never as "will work".
 */
interface HardwareAccelerationProbe {

    /**
     * Whether the GPU compute path looks available on this device.
     *
     * @return `true` when the platform's OpenCL implementation can be located,
     *   `false` when it demonstrably cannot. Never throws — an unusable or
     *   unreadable platform is reported as `false`.
     */
    fun isGpuAvailable(): Boolean
}
