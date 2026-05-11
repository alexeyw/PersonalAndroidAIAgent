package ai.agent.android.presentation.ui.navigation

/**
 * Canonical registry of all Jetpack Navigation Compose route strings used by the app.
 *
 * Navigation Compose's public API consumes raw [String]s (`navigate(route)`,
 * `composable(route)`, `startDestination = …`), so this is an `object` of `const val`s
 * rather than a `sealed class`. The flat-constant form keeps the route strings as
 * compile-time constants — usable in annotation parameters and grep-friendly — without
 * the wrapper noise a sealed hierarchy would require.
 *
 * Centralising the route literals here eliminates the typo class of bugs where a
 * `navigate("settngs")` would silently no-op at runtime: every reference now goes through
 * a named constant the compiler can verify.
 */
object NavRoutes {
    /** Cold-start splash / loading screen. */
    const val SPLASH: String = "splash"

    /** Main hub with buttons to every feature screen. */
    const val HOME: String = "home"

    /** Chat with the agent. */
    const val CHAT: String = "chat"

    /** Local model management. */
    const val MODELS: String = "models"

    /** Long-term memory browser. */
    const val MEMORY: String = "memory"

    /** Tools / AppFunctions catalog. */
    const val TOOLS: String = "tools"

    /** Live metrics monitoring screen. */
    const val MONITORING: String = "monitoring"

    /** Background-task monitor. */
    const val TASK_MONITOR: String = "taskmonitor"

    /** App settings. */
    const val SETTINGS: String = "settings"

    /** Prompt-template library. */
    const val PROMPTS: String = "prompts"

    /** Nested navigation graph hosting the pipeline library and editor. */
    const val PIPELINES_GRAPH: String = "pipelines"

    /** Pipeline library list (inside [PIPELINES_GRAPH]). */
    const val PIPELINE_LIBRARY: String = "pipeline-library"

    /** Visual pipeline editor (inside [PIPELINES_GRAPH]). */
    const val PIPELINE_EDITOR: String = "pipeline-editor"
}
