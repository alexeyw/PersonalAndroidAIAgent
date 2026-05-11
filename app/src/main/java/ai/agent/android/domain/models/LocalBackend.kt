package ai.agent.android.domain.models

/**
 * Type-safe identifier for the on-device LiteRT execution backend.
 *
 * Each entry carries the wire key that is persisted in `DataStore` under
 * `local_model_backend` and surfaced to the user in the Settings screen. Owning the
 * mapping here lets every consumer (settings UI, repository default, LiteRT engine
 * factory) round-trip through the same typed enum and dispatch with exhaustive `when`.
 *
 * The wire form is preserved as the existing `"CPU" / "GPU" / "NPU"` strings so the
 * refactor does not invalidate values already persisted on user devices.
 */
enum class LocalBackend(val key: String) {
    /** CPU execution via XNNPACK; the safe default supported on every device. */
    CPU("CPU"),

    /** GPU delegate; faster on supported hardware. */
    GPU("GPU"),

    /** Neural Processing Unit delegate; available only on a subset of recent chipsets. */
    NPU("NPU"),
    ;

    companion object {
        /**
         * Parses a wire/UI backend key into a typed [LocalBackend].
         *
         * Unknown keys and `null` return `null` — callers decide whether the absence
         * means "fall back to default" ([CPU]) or "raise a validation error".
         */
        fun fromKey(key: String?): LocalBackend? = entries.firstOrNull { it.key == key }
    }
}
