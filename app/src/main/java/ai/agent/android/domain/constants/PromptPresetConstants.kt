package ai.agent.android.domain.constants

import ai.agent.android.domain.models.NodeType

/**
 * Cross-module constants shared by the prompt-preset domain
 * (Phase 24 / Task 4): name / prompt-length limits and the set of node
 * types that can have a system-prompt preset.
 *
 * Co-locating the LLM-driven set with the limits keeps every prompt-preset
 * code path — domain validation (`SavePromptAsPresetUseCase`), bundled
 * catalogue validation (`PromptPresetCatalogValidationTest`), and the UI
 * filter in `PromptPresetPickerDialog` (Phase 24 / Task 5) — pointing at
 * one source of truth instead of redeclaring the rule each time.
 */
object PromptPresetConstants {

    /**
     * Maximum length of a preset display name after trim.
     *
     * Matches the limit enforced by `CreatePipelineUseCase`,
     * `RenamePipelineUseCase`, `SavePipelineAsPresetUseCase`, so the user
     * sees a single cross-feature ceiling instead of feature-specific
     * surprises.
     */
    const val MAX_NAME_LENGTH = 60

    /**
     * Maximum length of the [PromptPreset.systemPrompt] body.
     *
     * Soft ceiling chosen to keep bundled presets well within any
     * downstream model's context budget (8000 characters ≈ 2000 tokens —
     * still a fraction of even Gemma's 8K window). The limit applies to
     * the raw template *before* `$VARIABLE` substitution: post-render the
     * prompt may grow modestly, which is fine because runtime context
     * assembly already enforces its own per-model budgets via
     * `GetContextWindowUseCase`.
     */
    const val MAX_SYSTEM_PROMPT_LENGTH = 8000

    /**
     * The set of [NodeType]s that feed a user-authored prompt to the LLM
     * and therefore can have a prompt preset associated with them.
     *
     * For LITE_RT / CLOUD / OUTPUT / SUMMARY / INTENT_ROUTER /
     * DECOMPOSITION / EVALUATION / CLARIFICATION the preset body is the
     * node's `systemPrompt` template. For [NodeType.IF_CONDITION] the
     * preset body is the free-form **condition prompt** stored on
     * `NodeModel.conditionPrompt` (catalog `IfConditionConfig.expression`):
     * the `EvaluateIfConditionUseCase` wraps it in
     * `DefaultPrompts.IfCondition.EVALUATION_TEMPLATE` and asks the LLM
     * to classify the upstream text as `true` / `false`. That's still a
     * user-authored prompt template — exactly what presets exist for.
     *
     * The `TOOL`, `INPUT`, `QUEUE_PROCESSOR` types are excluded because
     * their executors never feed a user-authored prompt to a model —
     * saving a preset against them would be meaningless.
     */
    val LLM_DRIVEN_NODE_TYPES: Set<NodeType> = setOf(
        NodeType.LITE_RT,
        NodeType.CLOUD,
        NodeType.OUTPUT,
        NodeType.SUMMARY,
        NodeType.INTENT_ROUTER,
        NodeType.DECOMPOSITION,
        NodeType.EVALUATION,
        NodeType.CLARIFICATION,
        NodeType.IF_CONDITION,
    )
}
