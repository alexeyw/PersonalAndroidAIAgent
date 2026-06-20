package app.knotwork.android.domain.services

/**
 * Reads the process's current native-heap allocation, in bytes.
 *
 * Abstracted behind a domain interface so the `domain` layer (the LITE_RT node
 * executor and the benchmark use case) can sample memory during inference
 * without importing `android.os.Debug`. The data-layer implementation
 * ([app.knotwork.android.data.local.AndroidNativeMemorySampler]) reads
 * `Debug.getNativeHeapAllocatedSize()`.
 *
 * **Honesty contract (carried into the UI and SECURITY/docs).** The returned
 * value is the **process-wide native-heap allocator** size, not an isolated
 * measurement of the model: it includes every native allocation in the process,
 * **excludes** the model file's memory-mapped pages, and reads low on devices
 * with ZRAM compression. It is therefore an *approximate* footprint indicator,
 * never the model's exact memory cost. The cheap, unthrottled native-heap read
 * is chosen deliberately over `Debug.getMemoryInfo()`/PSS, which newer Android
 * rate-limits to roughly one call every five minutes and so cannot be sampled
 * across a generation window.
 */
fun interface NativeMemorySampler {

    /**
     * @return The current native-heap allocation of the process, in bytes.
     */
    fun nativeHeapAllocatedBytes(): Long
}
