package app.knotwork.android.data.local

import android.os.Debug
import app.knotwork.android.domain.services.NativeMemorySampler
import javax.inject.Inject

/**
 * [NativeMemorySampler] backed by `android.os.Debug.getNativeHeapAllocatedSize()`.
 *
 * That call returns the number of bytes the native allocator currently has
 * handed out to the process. It is cheap and **not** rate-throttled, unlike
 * `Debug.getMemoryInfo()`/PSS (which newer Android limits to roughly one call
 * every five minutes), which is why it — not PSS — is sampled across an
 * inference window. See [NativeMemorySampler] for the full honesty contract:
 * the figure is process-wide, excludes the model's memory-mapped pages, and
 * reads low under ZRAM, so it is an approximate footprint indicator only.
 */
class AndroidNativeMemorySampler @Inject constructor() : NativeMemorySampler {
    override fun nativeHeapAllocatedBytes(): Long = Debug.getNativeHeapAllocatedSize()
}
