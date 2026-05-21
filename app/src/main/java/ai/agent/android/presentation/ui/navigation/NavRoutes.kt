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
 * `navigate("settngs")` would silently no-op at runtime: every reference now goes
 * through a named constant the compiler can verify.
 */
object NavRoutes {
    /** Cold-start splash / loading screen. */
    const val SPLASH: String = "splash"

    /** Onboarding flow shown on first launch (Phase 21 / Task 4 stub; full pager in Task 10). */
    const val ONBOARDING: String = "onboarding"

    // ─── Top-level tab destinations ────────────────────────────────────────
    // The four entries that the bottom nav switches between. Each tab's
    // start-destination is the tab route itself; deeper screens live as
    // additional `composable(...)` entries reachable from inside the tab.

    /** Chat tab — entry route (without thread argument). */
    const val CHAT_TAB: String = "chat-tab"

    /** Parameterised chat route used for deep-links: `chat/{threadId}`. */
    const val CHAT_WITH_THREAD: String = "chat/{threadId}"

    /** Path-argument key for [CHAT_WITH_THREAD]. */
    const val CHAT_THREAD_ARG: String = "threadId"

    /** Scheme used by chat deep-links (e.g. `knotwork://chat/thread-42`). */
    const val DEEP_LINK_SCHEME: String = "knotwork"

    /** Deep-link uri pattern matching [CHAT_WITH_THREAD]. */
    const val CHAT_DEEP_LINK_PATTERN: String = "$DEEP_LINK_SCHEME://chat/{$CHAT_THREAD_ARG}"

    /** Builds a concrete `chat/<id>` route for `navController.navigate`. */
    fun chatRoute(threadId: String): String = "chat/$threadId"

    /** Pipelines tab — nested-graph route hosting library and editor. */
    const val PIPELINES_GRAPH: String = "pipelines"

    /** Pipeline library list (inside [PIPELINES_GRAPH]). */
    const val PIPELINE_LIBRARY: String = "pipeline-library"

    /** Visual pipeline editor (inside [PIPELINES_GRAPH]). */
    const val PIPELINE_EDITOR: String = "pipeline-editor"

    /** Parameterised editor route alias: `pipeline/{id}/edit`. Path arg = [PIPELINE_EDIT_ID_ARG]. */
    const val PIPELINE_EDIT_WITH_ID: String = "pipeline/{id}/edit"

    /** Path-argument key for [PIPELINE_EDIT_WITH_ID]. */
    const val PIPELINE_EDIT_ID_ARG: String = "id"

    /** Tools tab. */
    const val TOOLS: String = "tools"

    /** Tool detail screen — `tools/{toolId}`. */
    const val TOOL_DETAIL: String = "tools/{toolId}"

    /** Path-argument key for [TOOL_DETAIL]. */
    const val TOOL_DETAIL_ID_ARG: String = "toolId"

    /** More tab — landing screen with secondary navigation. */
    const val MORE: String = "more"

    // ─── Secondary destinations under "More" ───────────────────────────────

    /** Local model management (under More). */
    const val MODELS: String = "models"

    /** Long-term memory browser (under More). */
    const val MEMORY: String = "memory"

    /** Live metrics monitoring screen (under More). */
    const val MONITORING: String = "monitoring"

    /** Background-task monitor (under More). */
    const val TASK_MONITOR: String = "taskmonitor"

    /** App settings (under More). */
    const val SETTINGS: String = "settings"

    /** Prompt-template library (under More). */
    const val PROMPTS: String = "prompts"

    /** About screen (under More). Phase 21 / Task 4 stub; full body in Task 10. */
    const val ABOUT: String = "about"

    /**
     * Standalone external-LLM provider editor reached from the Settings
     * → External providers nav-rows.
     */
    const val PROVIDER_DETAIL: String = "settings/provider/{providerId}"

    /** Navigation argument carrying the [ProviderId.cloudProvider]'s wire id. */
    const val PROVIDER_DETAIL_ID_ARG: String = "providerId"

    /** Picker sheet shown when the user taps "+ Add provider". */
    const val ADD_PROVIDER: String = "settings/provider/add"

    /** Search-in-settings modal sheet. */
    const val SETTINGS_SEARCH: String = "settings/search"

    // ─── Modal bottom-sheet placeholder routes ─────────────────────────────
    // Phase 21 / Task 4 introduces the [KnotworkModalRoute] wrapper used by
    // every modal surface. The three sheets below are registered as empty
    // placeholders so navigation wiring (and `BottomNavVisibility`) is
    // testable from this task; their bodies arrive in Tasks 6 / 7 / 10.

    /** Node config sheet — opened from the pipeline editor (filled in Task 7). */
    const val SHEET_NODE_CONFIG: String = "sheet/node-config"

    /** Console pane sheet — opened from chat (filled in Task 6). */
    const val SHEET_CONSOLE: String = "sheet/console"
}
