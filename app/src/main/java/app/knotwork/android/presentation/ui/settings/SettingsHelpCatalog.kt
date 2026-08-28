package app.knotwork.android.presentation.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import app.knotwork.android.R
import app.knotwork.design.screens.settings.SettingsHint
import app.knotwork.design.screens.settings.SettingsHintController

/**
 * Why a settings row carries no explanation.
 *
 * Recorded rather than left implicit, so "this row has no hint" is a decision
 * someone made and can be argued with — not an omission that looks identical to
 * a forgotten string.
 */
enum class NoHint {
    /** The row is a door: the screen it opens explains itself. */
    LINK_ROW,

    /** The row displays, it does not decide — identity, version, licences. */
    DISPLAY_ROW,

    /** Verb plus plain object, with no effect off the row: "Export memories". */
    SELF_EVIDENT,

    /**
     * The row's behaviour does not currently happen, so there is nothing
     * truthful to explain. Writing a hint for one of these would document
     * behaviour the build does not have, which is the failure this whole task
     * exists to stop. Each is filed against the bug-fix container, and each is
     * owed a hint as soon as its row does something.
     *
     * Three rows qualify, all found by trying to write their explanation:
     *  - `LONG_RUNNING_TASKS_NOTIFICATIONS` — gates a notification production
     *    never posts (`LongRunningTaskNotifier.notify` is called from nothing
     *    but its own unit test).
     *  - `TOOL_USAGE_INSTRUCTION` — the edited text has no consumer; both LLM
     *    executors build the prompt from the system prefix and the node prompt
     *    only. `DefaultPrompts.TOOL_USAGE_INSTRUCTION` seeds the field and
     *    appears as a node-editor template, which is not the same thing.
     *  - `AUTO_SUMMARIZE_THRESHOLD` — read by the settings plumbing and by
     *    nothing else; moving the slider changes no behaviour.
     *  - `TEMPERATURE`, `TOP_K`, `TOP_P`, `REPETITION_PENALTY` — the sampling
     *    sliders. `LiteRTLlmEngine` opens an ordinary conversation with **no**
     *    `SamplerConfig` at all, leaving the model's native sampler in place;
     *    its own KDoc says the override "is only ever used for the
     *    structured-output repair loop", where the values are hardcoded. The
     *    cloud path builds no sampling params either. Four confident sentences
     *    about sampling behaviour were written here before this was checked —
     *    which is the failure this whole task exists to stop, so they are gone
     *    rather than softened.
     */
    BEHAVIOUR_NOT_SHIPPED,
}

/**
 * A settings row's explanation, or the recorded reason it has none.
 *
 * Deliberately **not** a nullable string: the search catalogue's `descRes` was
 * nullable and unasserted, so a row could lose its description and keep the
 * build green. Making the decision explicit is what lets
 * `SettingsHelpCatalogTest` demand that every registry row is decided.
 */
sealed interface SettingHelp {

    /** The row explains itself through [res]. */
    data class Text(@StringRes val res: Int) : SettingHelp

    /** The row carries no explanation, for [why]. */
    data class None(val why: NoHint) : SettingHelp
}

/**
 * Anchor to help text for every row in the [SettingsRegistry] — the canonical
 * source for what a setting means.
 *
 * The same strings feed three consumers and can no longer disagree: the in-app
 * hint panel, the settings-search index, and the generated `Settings` section of
 * `docs/user-guide.md`. Before this, one setting's meaning was written three
 * times over — the row subtitle, the search description and the guide — and
 * closed testing found them already saying three different things, one of them
 * quoting a threshold no constant in the code held.
 *
 * Which rows carry a hint is decided by criterion, not by a list, so nobody has
 * to maintain one. A row is explained when any of these holds:
 *  1. **It has a number.** What a number does is never in its name.
 *  2. **It borrows a word** from outside the product's vocabulary — Top-K,
 *     threshold, half-life, embedding, compaction, backend.
 *  3. **It has a consequence off the row** — data leaves the device, another
 *     feature is blocked, notifications start arriving.
 */
object SettingsHelpCatalog {

    /** Anchor -> explanation, or the recorded reason there is none. */
    val HELP: Map<String, SettingHelp> = mapOf(
        "SYSTEM_PROMPT_PREFIX" to text(R.string.settings_help_system_prompt_prefix),
        "TOOL_USAGE_INSTRUCTION" to none(NoHint.BEHAVIOUR_NOT_SHIPPED),
        "TEMPERATURE" to none(NoHint.BEHAVIOUR_NOT_SHIPPED),
        "TOP_K" to none(NoHint.BEHAVIOUR_NOT_SHIPPED),
        "TOP_P" to none(NoHint.BEHAVIOUR_NOT_SHIPPED),
        "REPETITION_PENALTY" to none(NoHint.BEHAVIOUR_NOT_SHIPPED),
        "MAX_CONTEXT_LENGTH" to text(R.string.settings_help_max_context_length),
        "AUDIO_MAX_DURATION_SEC" to text(R.string.settings_help_audio_max_duration_sec),
        "LOCAL_MODEL_BACKEND" to text(R.string.settings_help_local_model_backend),
        "LINK_PROVIDER_LIST" to none(NoHint.LINK_ROW),
        "DEFAULT_PIPELINE_ID" to none(NoHint.LINK_ROW),
        "AUTO_EXTRACT_ENABLED" to text(R.string.settings_help_auto_extract_enabled),
        "MEMORY_COMPACTION_ENABLED" to text(R.string.settings_help_memory_compaction_enabled),
        "CHAT_HISTORY_COMPRESSION_ENABLED" to text(R.string.settings_help_chat_history_compression_enabled),
        "AUTO_SUMMARIZE_THRESHOLD" to none(NoHint.BEHAVIOUR_NOT_SHIPPED),
        "MEMORY_SEARCH_TOP_K" to text(R.string.settings_help_memory_search_top_k),
        "MEMORY_SEARCH_THRESHOLD" to text(R.string.settings_help_memory_search_threshold),
        "MEMORY_RECENCY_HALF_LIFE_DAYS" to text(R.string.settings_help_memory_recency_half_life_days),
        "MEMORY_COMPACTION_AGE_DAYS" to text(R.string.settings_help_memory_compaction_age_days),
        "MAX_MEMORY_CHUNKS" to text(R.string.settings_help_max_memory_chunks),
        "CHAT_HISTORY_COMPRESSION_THRESHOLD_TOKENS" to
            text(R.string.settings_help_chat_history_compression_threshold_tokens),
        "CHAT_HISTORY_LIVE_WINDOW_SIZE" to text(R.string.settings_help_chat_history_live_window_size),
        "MEMORY_SUMMARY_DEFAULT_LIMIT" to text(R.string.settings_help_memory_summary_default_limit),
        "ACTIVE_EMBEDDING_PROVIDER_ID" to text(R.string.settings_help_active_embedding_provider_id),
        "VERBOSE_MEMORY_LOGGING_ENABLED" to text(R.string.settings_help_verbose_memory_logging_enabled),
        "MEMORY_ACTIONS" to text(R.string.settings_help_memory_actions),
        "LINK_RUN_LIMITS" to none(NoHint.LINK_ROW),
        "PIPELINE_MAX_NESTING_DEPTH" to text(R.string.settings_help_pipeline_max_nesting_depth),
        "STRUCTURED_OUTPUT_MAX_REPAIRS" to text(R.string.settings_help_structured_output_max_repairs),
        "LINK_PROVIDER_DETAIL" to none(NoHint.LINK_ROW),
        "TOOL_APPROVAL_POLICY" to text(R.string.settings_help_tool_approval_policy),
        "BLOCK_DESTRUCTIVE_TOOLS" to text(R.string.settings_help_block_destructive_tools),
        "BLOCK_NETWORK_FROM_LOCAL_MODEL" to text(R.string.settings_help_block_network_from_local_model),
        "LINK_TOOLS_SCREEN" to none(NoHint.LINK_ROW),
        "TOOL_CALL_TIMEOUT_MS" to text(R.string.settings_help_tool_call_timeout_ms),
        "WORKSPACE_MAX_FILE_SIZE_BYTES" to text(R.string.settings_help_workspace_max_file_size_bytes),
        "WORKSPACE_MAX_TOTAL_BYTES" to text(R.string.settings_help_workspace_max_total_bytes),
        "WORKSPACE_READ_TOKEN_BUDGET" to text(R.string.settings_help_workspace_read_token_budget),
        "HTTP_TOOL_MAX_RESPONSE_BYTES" to text(R.string.settings_help_http_tool_max_response_bytes),
        "LINK_FILES_DOMAINS" to none(NoHint.LINK_ROW),
        "LONG_RUNNING_TASKS_NOTIFICATIONS" to none(NoHint.BEHAVIOUR_NOT_SHIPPED),
        "SCHEDULED_TASK_NOTIFICATIONS" to text(R.string.settings_help_scheduled_task_notifications),
        "SHARE_TARGET_PIPELINE_ID" to text(R.string.settings_help_share_target_pipeline_id),
        "SHARE_REUSE_SESSION" to text(R.string.settings_help_share_reuse_session),
        "QUICK_SETTINGS_TILE_PIPELINE_ID" to text(R.string.settings_help_quick_settings_tile_pipeline_id),
        "EXTERNAL_AUTOMATION_ENABLED" to text(R.string.settings_help_external_automation_enabled),
        "EXTERNAL_AUTOMATION_PIPELINE_ID" to text(R.string.settings_help_external_automation_pipeline_id),
        "LINK_EXTERNAL_AUTOMATION_JOURNAL" to none(NoHint.LINK_ROW),
        "RESUME_MAX_AGE_HOURS" to text(R.string.settings_help_resume_max_age_hours),
        "BACKGROUND_APPROVAL_WINDOW_HOURS" to text(R.string.settings_help_background_approval_window_hours),
        "CRASH_REPORTING_ENABLED" to text(R.string.settings_help_crash_reporting_enabled),
        "TRACE_RETENTION_RUNS_PER_SESSION" to text(R.string.settings_help_trace_retention_runs_per_session),
        "TRACE_RETENTION_MAX_AGE_DAYS" to text(R.string.settings_help_trace_retention_max_age_days),
        "IDENTITY" to none(NoHint.DISPLAY_ROW),
        "LINK_LICENSES" to none(NoHint.LINK_ROW),
        "RESET" to none(NoHint.SELF_EVIDENT),
    )

    /**
     * Builds the hint controller a settings sub-screen provides to its rows.
     *
     * @param context Resource resolution for the localized help text.
     * @return A controller that resolves any registry anchor and owns which
     *   single hint is open on the screen.
     */
    fun controller(context: Context): SettingsHintController = SettingsHintController { anchorKey ->
        when (val help = HELP[anchorKey]) {
            is SettingHelp.Text -> SettingsHint(text = context.getString(help.res))
            else -> null
        }
    }
}

private fun text(@StringRes res: Int): SettingHelp = SettingHelp.Text(res)

private fun none(why: NoHint): SettingHelp = SettingHelp.None(why)
