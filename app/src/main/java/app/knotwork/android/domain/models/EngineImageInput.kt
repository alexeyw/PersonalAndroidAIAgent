package app.knotwork.android.domain.models

/**
 * The single image attached to a run's originating user message, resolved into
 * the shape the inference engine and console need.
 *
 * Built by the data layer (`TaskQueueManagerImpl`) from the saved
 * [MessageAttachment] just before the run starts: the store-relative attachment
 * path is resolved to an absolute filesystem path (LiteRT-LM's
 * `Content.ImageFile` requires one) and the on-disk byte size is read for the
 * console line. Carried into
 * [app.knotwork.android.domain.engine.GraphExecutionEngine] so the engine can
 * deliver the image to the first vision-eligible `LITE_RT` node and emit the
 * `Image input: W×H, N KB` console event at run start.
 *
 * Pure-Kotlin value object (no Android types), so the domain engine stays
 * framework-free.
 *
 * @property absolutePath Absolute path of the downscaled JPEG in the attachment
 *   store. Passed verbatim to `Content.ImageFile`.
 * @property width Pixel width of the stored (downscaled) image.
 * @property height Pixel height of the stored (downscaled) image.
 * @property sizeBytes On-disk size of the stored JPEG in bytes, used only to
 *   render the console event.
 */
data class EngineImageInput(val absolutePath: String, val width: Int, val height: Int, val sizeBytes: Long)
