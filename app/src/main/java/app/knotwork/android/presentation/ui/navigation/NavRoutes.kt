package app.knotwork.android.presentation.ui.navigation

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

    /** Onboarding flow shown on first launch. */
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

    /**
     * Deep-link uri for the chat tab without a specific thread, used by the
     * static "New chat" launcher shortcut. Distinct from [CHAT_DEEP_LINK_PATTERN]
     * (which carries a `{threadId}` path segment).
     */
    const val CHAT_TAB_DEEP_LINK_PATTERN: String = "$DEEP_LINK_SCHEME://chat"

    /**
     * Deep-link uri for the pipeline library, used by the static "Pipelines"
     * launcher shortcut.
     */
    const val PIPELINES_DEEP_LINK_PATTERN: String = "$DEEP_LINK_SCHEME://pipelines"

    /**
     * Deep-link uri for the static "New chat" launcher shortcut. Distinct from
     * [CHAT_TAB_DEEP_LINK_PATTERN] (`knotwork://chat`, which opens the chat home
     * on the *current* session): this one creates a fresh empty session first.
     * Handled explicitly by the deep-link router, not as a `navDeepLink`.
     */
    const val NEW_CHAT_DEEP_LINK_PATTERN: String = "$DEEP_LINK_SCHEME://new-chat"

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

    /**
     * MCP server configuration screen — both Add and Edit modes.
     * `originalUrl` is an optional query argument: absent ⇒ Add new
     * server; non-empty ⇒ Edit the server with that URL.
     */
    const val MCP_SERVER_CONFIG: String = "tools/mcp-config?originalUrl={originalUrl}"

    /** Query-argument key for [MCP_SERVER_CONFIG]. */
    const val MCP_SERVER_CONFIG_URL_ARG: String = "originalUrl"

    /** Route for Add mode (no `originalUrl` query argument). */
    const val MCP_SERVER_CONFIG_ADD: String = "tools/mcp-config"

    /** Builds the route for Edit mode against [originalUrl]. */
    fun mcpServerConfigEditRoute(originalUrl: String): String =
        "tools/mcp-config?originalUrl=${android.net.Uri.encode(originalUrl)}"

    /** Allowed-domains editor for the `http_request` tool — `tools/allowed-domains`. */
    const val ALLOWED_DOMAINS: String = "tools/allowed-domains"

    /** More tab — landing screen with secondary navigation. */
    const val MORE: String = "more"

    // ─── Secondary destinations under "More" ───────────────────────────────

    /** Local model management (under More). */
    const val MODELS: String = "models"

    /** Hugging Face model-discovery list (reached from Models). */
    const val DISCOVER: String = "discover"

    /**
     * Hugging Face model-discovery detail. The repository id contains a `/`,
     * so it travels as a URL-encoded query argument rather than a path segment.
     */
    const val DISCOVER_DETAIL: String = "discover/detail?repoId={repoId}"

    /** Query-argument key for [DISCOVER_DETAIL]. */
    const val DISCOVER_DETAIL_REPO_ID_ARG: String = "repoId"

    /** Builds the concrete discover-detail route for [repoId]. */
    fun discoverDetailRoute(repoId: String): String = "discover/detail?repoId=${android.net.Uri.encode(repoId)}"

    /** Long-term memory browser (under More). */
    const val MEMORY: String = "memory"

    /** Agent workspace file browser (under More). */
    const val FILES: String = "files"

    /** Live metrics monitoring screen (under More). */
    const val MONITORING: String = "monitoring"

    /** Background-task monitor (under More). */
    const val TASK_MONITOR: String = "taskmonitor"

    /**
     * App settings (under More). This is the route of the nested settings
     * navigation graph; navigating to it lands on the settings hub
     * ([SETTINGS_HUB], the graph start destination).
     */
    const val SETTINGS: String = "settings"

    /** Settings hub (category list + inline Basic controls); graph start destination. */
    const val SETTINGS_HUB: String = "settings/hub"

    /** Generation category sub-screen. */
    const val SETTINGS_GENERATION: String = "settings/generation"

    /** Models category sub-screen. */
    const val SETTINGS_MODELS: String = "settings/models"

    /** Memory category sub-screen. */
    const val SETTINGS_MEMORY: String = "settings/memory"

    /** Pipelines-&-structured-output category sub-screen. */
    const val SETTINGS_PIPELINES: String = "settings/pipelines"

    /** Tools-&-workspace category sub-screen. */
    const val SETTINGS_TOOLS: String = "settings/tools"

    /** Background-&-triggers category sub-screen. */
    const val SETTINGS_BACKGROUND: String = "settings/background"

    /** Privacy category sub-screen. */
    const val SETTINGS_PRIVACY: String = "settings/privacy"

    /** About category sub-screen. */
    const val SETTINGS_ABOUT: String = "settings/about"

    /** Prompt-template library (under More). */
    const val PROMPTS: String = "prompts"

    /** Skill library (under More). */
    const val SKILLS: String = "skills"

    /** Pipeline-preset manager (under More → Library). */
    const val PIPELINE_PRESETS: String = "pipeline-presets"

    /** About screen (under More). */
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

    // ─── Modal bottom-sheet routes ─────────────────────────────────────────
    // The [KnotworkModalRoute] wrapper is used by every modal surface.

    /** Node config sheet — opened from the pipeline editor. */
    const val SHEET_NODE_CONFIG: String = "sheet/node-config"

    /** Console pane sheet — opened from chat. */
    const val SHEET_CONSOLE: String = "sheet/console"
}
