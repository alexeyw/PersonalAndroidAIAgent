package app.knotwork.android.domain.models

/**
 * An OS-level entry point that can launch a pipeline run from outside the app's
 * chat UI.
 *
 * Each surface optionally binds to a user-chosen pipeline (persisted in
 * [app.knotwork.android.domain.repositories.SettingsRepository]). The binding
 * defaults to **unset**, which keeps the surface inert: a privacy-first default
 * means a surface does nothing until the user deliberately points it at a
 * pipeline. The surface set here is deliberately limited to the surfaces that
 * actually run a pipeline; static/dynamic launcher shortcuts only open existing
 * screens and so are not modelled as bindable surfaces.
 */
enum class EntrySurface {
    /**
     * The Android share sheet (`ACTION_SEND`). Shared text / image is fed into
     * the bound pipeline as the user message.
     */
    SHARE,

    /**
     * The Quick Settings tile. A single tap launches the bound "duty" pipeline
     * in the background.
     */
    QUICK_TILE,
}
