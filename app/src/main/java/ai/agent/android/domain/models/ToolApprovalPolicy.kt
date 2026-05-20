package ai.agent.android.domain.models

/**
 * Policy that drives the Human-in-the-Loop (HITL) approval gate inside
 * `ToolNodeExecutor`. Replaces the legacy boolean `requiresUserConfirmation`
 * flag (which collapsed two independent concerns — "ask on every call" vs
 * "ask on risky calls" — into a single switch).
 *
 * Wire key persists in DataStore so the value survives process death and
 * future evolutions of the enum order. Values added later MUST keep the
 * existing keys to avoid silently re-bucketing user preferences.
 *
 * Semantics consumed by `ToolNodeExecutor`:
 *  - [AllCalls] — every tool, regardless of risk, requires explicit approval.
 *    The most cautious posture; trades fluency for control.
 *  - [SensitiveOrDestructive] — `READ_ONLY` tools run silently;
 *    `SENSITIVE` and `DESTRUCTIVE` tools always prompt. Default for new
 *    installs and the recommended posture.
 *  - [NeverPrompt] — the gate never fires. The agent runs without any
 *    confirmation prompts, including destructive tools. Reserved for
 *    power-users running known-safe pipelines unattended.
 *
 * Migration from the legacy `Boolean` flag is one-shot and lives in
 * `SettingsManager`: `true` → [SensitiveOrDestructive] (default-with-care),
 * `false` → [NeverPrompt] (the only way the user could previously skip
 * destructive prompts was to disable the global override).
 */
enum class ToolApprovalPolicy(
    /** Wire identifier persisted to DataStore; stable across enum-order changes. */
    val key: String,
) {
    /** Every tool call requires explicit user approval, regardless of risk class. */
    AllCalls(key = "all_calls"),

    /** Default — prompt for `SENSITIVE` and `DESTRUCTIVE` calls only. */
    SensitiveOrDestructive(key = "sensitive_or_destructive"),

    /** Tool calls never trigger the HITL gate. */
    NeverPrompt(key = "never_prompt"),
    ;

    /** Wire-key parsing helpers + the [DEFAULT] sentinel. */
    companion object {
        /** Default for a fresh install — recommended posture. */
        val DEFAULT: ToolApprovalPolicy = SensitiveOrDestructive

        /**
         * Resolves the wire key back to an enum value. Returns [DEFAULT] for
         * unknown keys so a corrupt DataStore write cannot crash the app
         * (the user's choice is silently reset to the recommended posture).
         */
        fun fromKey(key: String?): ToolApprovalPolicy = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
