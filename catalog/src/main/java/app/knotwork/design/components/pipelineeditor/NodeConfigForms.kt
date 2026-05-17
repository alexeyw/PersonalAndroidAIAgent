@file:Suppress("LongMethod", "TooManyFunctions") // 12 forms by spec; splitting per-file would only add ceremony.

package app.knotwork.design.components.pipelineeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.chips.ChipStyle
import app.knotwork.design.components.chips.KnotworkChip
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Lower bound on IntentRouter class count, mirrors `NodeConfigValidation.INTENT_CLASSES_RANGE.first`. */
private const val INTENT_ROUTER_MIN_CLASSES = 2

/** Upper bound on IntentRouter class count, mirrors `NodeConfigValidation.INTENT_CLASSES_RANGE.last`. */
private const val INTENT_ROUTER_MAX_CLASSES = 6

/**
 * Per-type form bodies for [NodeConfigSheet].
 *
 * Each `when` arm dispatches to a private composable named
 * `<Type>FormBody` so a search for "FormBody" surfaces the entire form
 * surface. The forms share a small set of helper composables ([FieldLabel],
 * [InlineError], [VariableChipsRow]) so each per-type body stays focused
 * on the spec's field list.
 */
object NodeConfigForms {

    /**
     * Renders the form body matching [config]'s runtime type.
     *
     * @param config current configuration value.
     * @param errors validator output keyed by field id; forms render the
     * matching inline error under the field via [InlineError].
     * @param onChange emit the next [NodeConfig] when the user edits any
     * field. Forms perform pure copy()-style updates — no internal state.
     * @param availableToolIds canonical tool ids exposed to [ToolFormBody] for its
     * dropdown. Empty list (the default) falls back to a free-text input — useful for
     * the catalog harness which has no app-level [ai.agent.android.domain.repositories.ToolRepository].
     * @param onPickFromLibrary optional hook to open the prompt-library picker from
     * any prompt-bearing field. The catalog form invokes it with
     * `(category, applySelected)` where `category` matches a `PromptTemplate.category`
     * and `applySelected` is the lambda the form wants run when the user picks a
     * prompt; the screen renders its own picker dialog and calls `applySelected`.
     * `null` (the default) hides the library button entirely.
     */
    @Composable
    fun Body(
        config: NodeConfig,
        errors: Map<FieldId, ValidationFailure>,
        onChange: (NodeConfig) -> Unit,
        availableToolIds: List<String> = emptyList(),
        onPickFromLibrary: ((category: String, apply: (String) -> Unit) -> Unit)? = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        ) {
            TitleField(
                title = config.title,
                error = errors[FieldId.TITLE],
                onChange = { next -> onChange(config.withTitle(next)) },
            )
            when (config) {
                is InputConfig -> InputFormBody(config, errors, onChange)
                is OutputConfig -> OutputFormBody(config, onChange)
                is LiteRtConfig -> LiteRtFormBody(config, errors, onChange, onPickFromLibrary)
                is CloudConfig -> CloudFormBody(config, errors, onChange, onPickFromLibrary)
                is IntentRouterConfig -> IntentRouterFormBody(config, errors, onChange, onPickFromLibrary)
                is IfConditionConfig -> IfConditionFormBody(config, errors, onChange)
                is ClarificationConfig -> ClarificationFormBody(config, errors, onChange, onPickFromLibrary)
                is ToolConfig -> ToolFormBody(config, errors, onChange, availableToolIds)
                is DecompositionConfig -> DecompositionFormBody(config, errors, onChange, onPickFromLibrary)
                is QueueProcessorConfig -> QueueProcessorFormBody(config, errors, onChange)
                is EvaluationConfig -> EvaluationFormBody(config, errors, onChange, onPickFromLibrary)
                is SummaryConfig -> SummaryFormBody(config, errors, onChange, onPickFromLibrary)
            }
        }
    }
}

/**
 * Typed shorthand for the optional prompt-library callback the screen passes down to
 * forms. The form invokes it as `hook(category) { picked -> onChange(...) }`; the
 * screen surfaces its own dialog and calls the inner lambda with the chosen prompt.
 */
private typealias PromptLibraryHook = (category: String, apply: (String) -> Unit) -> Unit

// ─────────────────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────────────────

/** Field label rendered above each input. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = KnotworkTextStyles.LabelMd,
        color = KnotworkTheme.extended.onSurface2,
    )
}

/** Inline error rendered under a field when validation fails. */
@Composable
private fun InlineError(failure: ValidationFailure?) {
    if (failure != null) {
        Text(
            text = stringResource(failure.stringRes),
            style = KnotworkTextStyles.BodySm,
            color = KnotworkTheme.extended.signalError,
        )
    }
}

/**
 * Catalog-side variable chips row. The production app uses the
 * `presentation/components/VariableChipsRow` which talks to the prompt
 * variable providers in `:app`; the catalog version is a static
 * presentational stub so the design system surfaces the chips without
 * pulling in the `:app` module.
 *
 * @param onInsert invoked with the chip text when the user taps a chip;
 * forms route this to the field caret.
 */
@Composable
private fun VariableChipsRow(onInsert: (String) -> Unit) {
    val variables = listOf("\$DATE", "\$TIME", "\$TOOLS", "\$MODEL", "\$MEMORY_SUMMARY")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
    ) {
        variables.forEach { name ->
            KnotworkChip(label = name, onClick = { onInsert(name) }, style = ChipStyle.Outline)
        }
    }
}

/** Single-line title field — shared across every node type. */
@Composable
private fun TitleField(title: String, error: ValidationFailure?, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        FieldLabel(text = stringResource(R.string.knotwork_node_field_title))
        OutlinedTextField(
            value = title,
            onValueChange = onChange,
            singleLine = true,
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        InlineError(failure = error)
    }
}

/** Stringly-typed text field used for most per-type rows. */
@Composable
@Suppress("LongParameterList") // Adding the optional library hook here keeps every prompt field DRY.
private fun TextField(
    label: String,
    value: String,
    error: ValidationFailure?,
    singleLine: Boolean,
    monospace: Boolean,
    onChange: (String) -> Unit,
    libraryCategory: String? = null,
    onPickFromLibrary: PromptLibraryHook? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        // When the field is prompt-bearing and the screen provided a library hook,
        // render a small icon button next to the label so users can replace the field
        // contents with a saved prompt. Other (non-prompt) fields just show the label.
        if (libraryCategory != null && onPickFromLibrary != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FieldLabel(text = label)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onPickFromLibrary(libraryCategory) { picked -> onChange(picked) } },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoStories,
                        contentDescription = stringResource(R.string.knotwork_node_action_load_from_library),
                    )
                }
            }
        } else {
            FieldLabel(text = label)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            isError = error != null,
            textStyle = if (monospace) KnotworkTextStyles.MonoBase else KnotworkTextStyles.BodyBase,
            modifier = Modifier.fillMaxWidth(),
        )
        InlineError(failure = error)
    }
}

/** Float field rendered as a slider plus the resolved numeric value. */
@Suppress("LongParameterList") // Slider field has a stable contract; collapsing the params hides intent.
@Composable
private fun FloatSliderField(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    error: ValidationFailure?,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(text = label)
            Text(
                text = "  %.2f".format(value),
                style = KnotworkTextStyles.MonoSm,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
        InlineError(failure = error)
    }
}

/** Integer field rendered as a slider over an `IntRange`. */
@Suppress("LongParameterList") // Same rationale as [FloatSliderField].
@Composable
private fun IntSliderField(
    label: String,
    value: Int,
    range: IntRange,
    error: ValidationFailure?,
    onChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(text = label)
            Text(
                text = "  $value",
                style = KnotworkTextStyles.MonoSm,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { next -> onChange(next.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
        InlineError(failure = error)
    }
}

/** Segmented chip row that picks one enum value out of a labelled set. */
@Composable
private fun <T> SegmentedChipRow(label: String, values: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        FieldLabel(text = label)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        ) {
            values.forEach { (value, label) ->
                val isSelected = value == selected
                KnotworkChip(
                    label = label,
                    selected = isSelected,
                    onClick = { onSelect(value) },
                    style = ChipStyle.Tonal,
                    // Compose Tonal chips at the project's primaryContainer tint are visibly
                    // similar to the unselected tonal surface in some Knotwork palettes,
                    // which made the Summary format (and other segmented-chip groups) look
                    // unmarked. A leading check unambiguously surfaces the active state
                    // regardless of theme contrast.
                    leadingIcon = if (isSelected) Icons.Outlined.Check else null,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Per-type forms
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun InputFormBody(
    config: InputConfig,
    errors: Map<FieldId, ValidationFailure>,
    onChange: (NodeConfig) -> Unit,
) {
    TextField(
        label = stringResource(R.string.knotwork_node_field_input_name),
        value = config.inputName,
        error = errors[FieldId.INPUT_NAME],
        singleLine = true,
        monospace = true,
        onChange = { next -> onChange(config.copy(inputName = next)) },
    )
    TextField(
        label = stringResource(R.string.knotwork_node_field_schema_json),
        value = config.schemaJson.orEmpty(),
        error = errors[FieldId.SCHEMA_JSON],
        singleLine = false,
        monospace = true,
        onChange = { next -> onChange(config.copy(schemaJson = next.takeIf { it.isNotBlank() })) },
    )
}

@Composable
private fun OutputFormBody(config: OutputConfig, onChange: (NodeConfig) -> Unit) {
    SegmentedChipRow(
        label = stringResource(R.string.knotwork_node_field_format),
        values = listOf(
            OutputFormat.PLAIN_TEXT to "Plain text",
            OutputFormat.MARKDOWN to "Markdown",
            OutputFormat.JSON to "JSON",
        ),
        selected = config.format,
        onSelect = { next -> onChange(config.copy(format = next)) },
    )
}

@Composable
private fun LiteRtFormBody(
    config: LiteRtConfig,
    errors: Map<FieldId, ValidationFailure>,
    onChange: (NodeConfig) -> Unit,
    onPickFromLibrary: PromptLibraryHook?,
) {
    TextField(
        label = stringResource(R.string.knotwork_node_field_model),
        value = config.modelId,
        error = errors[FieldId.MODEL_ID],
        singleLine = true,
        monospace = true,
        onChange = { next -> onChange(config.copy(modelId = next)) },
    )
    TextField(
        label = stringResource(R.string.knotwork_node_field_system_prompt),
        value = config.systemPrompt,
        error = errors[FieldId.SYSTEM_PROMPT],
        singleLine = false,
        monospace = true,
        onChange = { next -> onChange(config.copy(systemPrompt = next)) },
        libraryCategory = "LITE_RT",
        onPickFromLibrary = onPickFromLibrary,
    )
    VariableChipsRow(onInsert = { variable ->
        onChange(config.copy(systemPrompt = config.systemPrompt + variable))
    })
    FloatSliderField(
        label = stringResource(R.string.knotwork_node_field_temperature),
        value = config.temperature,
        range = 0f..2f,
        error = errors[FieldId.TEMPERATURE],
        onChange = { next -> onChange(config.copy(temperature = next)) },
    )
    FloatSliderField(
        label = stringResource(R.string.knotwork_node_field_top_p),
        value = config.topP,
        range = 0f..1f,
        error = errors[FieldId.TOP_P],
        onChange = { next -> onChange(config.copy(topP = next)) },
    )
    IntSliderField(
        label = stringResource(R.string.knotwork_node_field_max_new_tokens),
        value = config.maxNewTokens,
        range = 32..4_096,
        error = errors[FieldId.MAX_NEW_TOKENS],
        onChange = { next -> onChange(config.copy(maxNewTokens = next)) },
    )
}

@Composable
private fun CloudFormBody(
    config: CloudConfig,
    errors: Map<FieldId, ValidationFailure>,
    onChange: (NodeConfig) -> Unit,
    onPickFromLibrary: PromptLibraryHook?,
) {
    SegmentedChipRow(
        label = stringResource(R.string.knotwork_node_field_provider),
        values = listOf(
            CloudProvider.OPEN_AI to "OpenAI",
            CloudProvider.ANTHROPIC to "Anthropic",
            CloudProvider.GOOGLE to "Google",
            CloudProvider.COMPATIBLE to "Compatible",
        ),
        selected = config.provider,
        onSelect = { next -> onChange(config.copy(provider = next)) },
    )
    TextField(
        label = stringResource(R.string.knotwork_node_field_model),
        value = config.model,
        error = errors[FieldId.MODEL],
        singleLine = true,
        monospace = true,
        onChange = { next -> onChange(config.copy(model = next)) },
    )
    TextField(
        label = stringResource(R.string.knotwork_node_field_system_prompt),
        value = config.systemPrompt,
        error = errors[FieldId.SYSTEM_PROMPT],
        singleLine = false,
        monospace = true,
        onChange = { next -> onChange(config.copy(systemPrompt = next)) },
        libraryCategory = "CLOUD",
        onPickFromLibrary = onPickFromLibrary,
    )
    VariableChipsRow(onInsert = { variable ->
        onChange(config.copy(systemPrompt = config.systemPrompt + variable))
    })
    FloatSliderField(
        label = stringResource(R.string.knotwork_node_field_temperature),
        value = config.temperature,
        range = 0f..2f,
        error = errors[FieldId.TEMPERATURE],
        onChange = { next -> onChange(config.copy(temperature = next)) },
    )
    IntSliderField(
        label = stringResource(R.string.knotwork_node_field_max_tokens),
        value = config.maxTokens,
        range = 1..32_768,
        error = errors[FieldId.MAX_TOKENS],
        onChange = { next -> onChange(config.copy(maxTokens = next)) },
    )
    IntSliderField(
        label = stringResource(R.string.knotwork_node_field_timeout_ms),
        value = config.timeoutMs,
        range = 1_000..600_000,
        error = errors[FieldId.TIMEOUT_MS],
        onChange = { next -> onChange(config.copy(timeoutMs = next)) },
    )
}

@Composable
private fun IntentRouterFormBody(
    config: IntentRouterConfig,
    errors: Map<FieldId, ValidationFailure>,
    onChange: (NodeConfig) -> Unit,
    onPickFromLibrary: PromptLibraryHook?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        FieldLabel(text = stringResource(R.string.knotwork_node_field_classes))
        config.classes.forEachIndexed { index, intentClass ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            ) {
                OutlinedTextField(
                    value = intentClass.name,
                    onValueChange = { next ->
                        val updated = config.classes.toMutableList()
                        updated[index] = intentClass.copy(name = next)
                        onChange(config.copy(classes = updated))
                    },
                    singleLine = true,
                    textStyle = KnotworkTextStyles.MonoBase,
                    modifier = Modifier.weight(1f),
                    isError = intentClass.name.isBlank(),
                )
                // The remove control is disabled when removing would drop below the
                // 2-class minimum that `NodeConfigValidation` enforces — keeps the form
                // self-consistent without surfacing a "you cannot remove the last class"
                // error after the fact. The fallback selection is auto-cleared when its
                // class disappears so the now-stale dropdown does not silently survive.
                val canRemove = config.classes.size > INTENT_ROUTER_MIN_CLASSES
                IconButton(
                    onClick = {
                        val removedName = config.classes[index].name
                        val updated = config.classes.toMutableList().apply { removeAt(index) }
                        val nextFallback = config.fallbackClass?.takeIf { it != removedName }
                        onChange(config.copy(classes = updated, fallbackClass = nextFallback))
                    },
                    enabled = canRemove,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RemoveCircleOutline,
                        contentDescription = stringResource(R.string.knotwork_node_action_remove_class),
                    )
                }
            }
        }
        // Add-class is gated on the same 6-class ceiling the validator enforces; together
        // with the per-row remove gate this means the form never lets the user push the
        // config into an invalid state — `Save` stays enabled for every reachable edit.
        val canAdd = config.classes.size < INTENT_ROUTER_MAX_CLASSES
        KnotworkTextButton(
            text = stringResource(R.string.knotwork_node_action_add_class),
            onClick = {
                val updated = config.classes + IntentClass(name = "")
                onChange(config.copy(classes = updated))
            },
            enabled = canAdd,
            leadingIcon = Icons.Outlined.AddCircleOutline,
        )
        InlineError(failure = errors[FieldId.CLASSES])
    }
    TextField(
        label = stringResource(R.string.knotwork_node_field_classifier_prompt),
        value = config.classifierPrompt,
        error = errors[FieldId.CLASSIFIER_PROMPT],
        singleLine = false,
        monospace = true,
        onChange = { next -> onChange(config.copy(classifierPrompt = next)) },
        libraryCategory = "INTENT_ROUTER",
        onPickFromLibrary = onPickFromLibrary,
    )
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        SegmentedChipRow(
            label = stringResource(R.string.knotwork_node_field_fallback_class),
            // Stale fallback values (a class that was renamed or removed) are
            // still listed as the trailing option so the chip row reflects
            // the saved state — the inline error below tells the user the
            // value no longer resolves, and selecting <none> or another
            // class clears it. Without surfacing the stale value the user
            // would not see what they are about to fix.
            values = listOf<Pair<String?, String>>(null to "<none>") +
                config.classes.map { it.name to it.name } +
                listOfNotNull(
                    config.fallbackClass
                        ?.takeIf { it.isNotBlank() && it !in config.classes.map { c -> c.name } }
                        ?.let { it to it },
                ),
            selected = config.fallbackClass,
            onSelect = { next -> onChange(config.copy(fallbackClass = next)) },
        )
        InlineError(failure = errors[FieldId.FALLBACK_CLASS])
    }
}

@Composable
private fun IfConditionFormBody(
    config: IfConditionConfig,
    errors: Map<FieldId, ValidationFailure>,
    onChange: (NodeConfig) -> Unit,
) {
    TextField(
        label = stringResource(R.string.knotwork_node_field_expression),
        value = config.expression,
        error = errors[FieldId.EXPRESSION],
        singleLine = true,
        monospace = true,
        onChange = { next -> onChange(config.copy(expression = next)) },
    )
    TextField(
        label = stringResource(R.string.knotwork_node_field_label_true),
        value = config.labelTrue,
        error = errors[FieldId.LABEL_TRUE],
        singleLine = true,
        monospace = false,
        onChange = { next -> onChange(config.copy(labelTrue = next)) },
    )
    TextField(
        label = stringResource(R.string.knotwork_node_field_label_false),
        value = config.labelFalse,
        error = errors[FieldId.LABEL_FALSE],
        singleLine = true,
        monospace = false,
        onChange = { next -> onChange(config.copy(labelFalse = next)) },
    )
}

@Composable
private fun ClarificationFormBody(
    config: ClarificationConfig,
    errors: Map<FieldId, ValidationFailure>,
    onChange: (NodeConfig) -> Unit,
    onPickFromLibrary: PromptLibraryHook?,
) {
    TextField(
        label = stringResource(R.string.knotwork_node_field_question),
        value = config.questionTemplate,
        error = errors[FieldId.QUESTION_TEMPLATE],
        singleLine = false,
        monospace = true,
        onChange = { next -> onChange(config.copy(questionTemplate = next)) },
        libraryCategory = "CLARIFICATION",
        onPickFromLibrary = onPickFromLibrary,
    )
    QuickRepliesField(
        replies = config.quickReplies,
        error = errors[FieldId.QUICK_REPLIES],
        onChange = { next -> onChange(config.copy(quickReplies = next)) },
    )
    TextField(
        label = stringResource(R.string.knotwork_node_field_timeout_optional),
        value = config.timeoutMs?.toString().orEmpty(),
        error = errors[FieldId.TIMEOUT_OPTIONAL],
        singleLine = true,
        monospace = true,
        onChange = { next -> onChange(config.copy(timeoutMs = next.toIntOrNull())) },
    )
}

/**
 * Comma-separated quick-reply editor.
 *
 * Holds the user's raw text in a `remember`-backed local state so a
 * keystroke that adds a comma is not parsed-and-re-serialised on the
 * way back through the model. The previous implementation called
 * `text.split(',').map { it.trim() }.filter { it.isNotEmpty() }` on
 * every keystroke and then re-rendered `joinToString(", ")` from the
 * filtered list — so typing `yes,` produced `["yes"]`, which rendered
 * as `yes` and dropped the user's pending comma. The field was
 * effectively unable to accept more than one quick reply.
 *
 * The local-state approach is necessary because the model carries
 * `List<String>`: the trailing empty token a user types ahead of a new
 * reply has no canonical representation in the list. We sync the
 * trimmed-and-empty-filtered list to the model so validation
 * (`QUICK_REPLIES_RANGE`) still fires from the model, but the field
 * always shows what the user actually typed.
 *
 * If the caller hands in a new `replies` value (e.g. config loaded
 * from disk), the `LaunchedEffect` keyed on the serialised form
 * re-syncs the raw text so the field stays in sync with the model.
 */
@Composable
private fun QuickRepliesField(replies: List<String>, error: ValidationFailure?, onChange: (List<String>) -> Unit) {
    val serialised = replies.joinToString(", ")
    var rawText by remember { mutableStateOf(serialised) }
    LaunchedEffect(serialised) {
        if (serialised != rawText.parseQuickReplies().joinToString(", ")) {
            rawText = serialised
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        FieldLabel(text = stringResource(R.string.knotwork_node_field_quick_replies))
        OutlinedTextField(
            value = rawText,
            onValueChange = { next ->
                rawText = next
                onChange(next.parseQuickReplies())
            },
            singleLine = true,
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        InlineError(failure = error)
    }
}

/** Trims and drops empty tokens; the canonical model representation. */
private fun String.parseQuickReplies(): List<String> =
    if (isEmpty()) emptyList() else split(',').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Tool selector for [ToolFormBody]. When [availableToolIds] is non-empty, surfaces an
 * `ExposedDropdownMenu` with an "Auto" entry plus every registered tool — selecting one
 * commits the id straight into [ToolConfig.toolId]. The trailing "Custom tool id…"
 * option (and the empty-tools fallback) reveals a free-text input so the user can wire
 * an MCP tool that isn't yet enabled locally.
 *
 * Auto is modelled as `toolId = ""` (empty) — the runtime treats that as
 * "let the agent pick the tool"; the validator's `REQUIRED` check still surfaces a
 * Snackbar if the user saves without choosing, so blank stays a meaningful state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolPicker(
    config: ToolConfig,
    error: ValidationFailure?,
    availableToolIds: List<String>,
    onChange: (NodeConfig) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        FieldLabel(text = stringResource(R.string.knotwork_node_field_tool))
        var menuExpanded by remember { mutableStateOf(false) }
        val autoLabel = stringResource(R.string.knotwork_node_field_tool_auto)
        val customLabel = stringResource(R.string.knotwork_node_field_tool_custom)
        // "Custom" mode lets the user enter a tool id not in `availableToolIds` (e.g., an
        // MCP tool that's still being plumbed). Tracked locally because the catalog
        // NodeConfig schema only has `toolId: String` — there's no separate "auto" /
        // "custom" sentinel to derive this from on read.
        var customMode by remember(config.toolId, availableToolIds) {
            mutableStateOf(config.toolId.isNotBlank() && config.toolId !in availableToolIds)
        }
        val selectedLabel = when {
            customMode || (config.toolId.isNotBlank() && config.toolId !in availableToolIds) ->
                config.toolId.ifBlank { customLabel }
            config.toolId.isBlank() -> autoLabel
            else -> config.toolId
        }
        if (availableToolIds.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    isError = error != null,
                    textStyle = KnotworkTextStyles.MonoBase,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(text = autoLabel) },
                        onClick = {
                            customMode = false
                            onChange(config.copy(toolId = ""))
                            menuExpanded = false
                        },
                    )
                    availableToolIds.forEach { id ->
                        DropdownMenuItem(
                            text = { Text(text = id, style = KnotworkTextStyles.MonoBase) },
                            onClick = {
                                customMode = false
                                onChange(config.copy(toolId = id))
                                menuExpanded = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(text = customLabel) },
                        onClick = {
                            customMode = true
                            menuExpanded = false
                        },
                    )
                }
            }
        }
        if (customMode || availableToolIds.isEmpty()) {
            OutlinedTextField(
                value = config.toolId,
                onValueChange = { next -> onChange(config.copy(toolId = next)) },
                singleLine = true,
                isError = error != null,
                textStyle = KnotworkTextStyles.MonoBase,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        InlineError(failure = error)
    }
}

@Composable
private fun ToolFormBody(
    config: ToolConfig,
    errors: Map<FieldId, ValidationFailure>,
    onChange: (NodeConfig) -> Unit,
    availableToolIds: List<String>,
) {
    ToolPicker(
        config = config,
        error = errors[FieldId.TOOL_ID],
        availableToolIds = availableToolIds,
        onChange = onChange,
    )
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        FieldLabel(text = stringResource(R.string.knotwork_node_field_arg_mapping))
        config.argumentMapping.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            ) {
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { nextKey ->
                        onChange(
                            config.copy(
                                argumentMapping = config.argumentMapping.toMutableList().apply {
                                    this[index] = row.copy(name = nextKey)
                                },
                            ),
                        )
                    },
                    singleLine = true,
                    textStyle = KnotworkTextStyles.MonoBase,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isError = row.name.isBlank(),
                )
                OutlinedTextField(
                    value = row.expression,
                    onValueChange = { nextValue ->
                        onChange(
                            config.copy(
                                argumentMapping = config.argumentMapping.toMutableList().apply {
                                    this[index] = row.copy(expression = nextValue)
                                },
                            ),
                        )
                    },
                    singleLine = true,
                    textStyle = KnotworkTextStyles.MonoBase,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isError = row.expression.isBlank(),
                )
            }
        }
        InlineError(failure = errors[FieldId.ARGUMENT_MAPPING])
    }
    SegmentedChipRow(
        label = stringResource(R.string.knotwork_node_field_confirm_override),
        values = listOf<Pair<ConfirmPolicy?, String>>(
            null to "<inherit>",
            ConfirmPolicy.ALWAYS_CONFIRM to "Always confirm",
            ConfirmPolicy.ALLOW_READONLY to "Allow read-only",
            ConfirmPolicy.ALLOW_SENSITIVE to "Allow sensitive",
        ),
        selected = config.confirmOverride,
        onSelect = { next -> onChange(config.copy(confirmOverride = next)) },
    )
}

@Composable
private fun DecompositionFormBody(
    config: DecompositionConfig,
    errors: Map<FieldId, ValidationFailure>,
    onChange: (NodeConfig) -> Unit,
    onPickFromLibrary: PromptLibraryHook?,
) {
    TextField(
        label = stringResource(R.string.knotwork_node_field_planning_prompt),
        value = config.planningPrompt,
        error = errors[FieldId.PLANNING_PROMPT],
        singleLine = false,
        monospace = true,
        onChange = { next -> onChange(config.copy(planningPrompt = next)) },
        libraryCategory = "DECOMPOSITION",
        onPickFromLibrary = onPickFromLibrary,
    )
    IntSliderField(
        label = stringResource(R.string.knotwork_node_field_max_subtasks),
        value = config.maxSubtasks,
        range = 1..20,
        error = errors[FieldId.MAX_SUBTASKS],
        onChange = { next -> onChange(config.copy(maxSubtasks = next)) },
    )
    TextField(
        label = stringResource(R.string.knotwork_node_field_schema_json),
        value = config.outputSchemaJson.orEmpty(),
        error = errors[FieldId.OUTPUT_SCHEMA_JSON],
        singleLine = false,
        monospace = true,
        onChange = { next -> onChange(config.copy(outputSchemaJson = next.takeIf { it.isNotBlank() })) },
    )
}

@Composable
private fun QueueProcessorFormBody(
    config: QueueProcessorConfig,
    errors: Map<FieldId, ValidationFailure>,
    onChange: (NodeConfig) -> Unit,
) {
    TextField(
        label = stringResource(R.string.knotwork_node_field_input_list),
        value = config.inputList,
        error = errors[FieldId.INPUT_LIST],
        singleLine = true,
        monospace = true,
        onChange = { next -> onChange(config.copy(inputList = next)) },
    )
    TextField(
        label = stringResource(R.string.knotwork_node_field_item_variable),
        value = config.itemVariable,
        error = errors[FieldId.ITEM_VARIABLE],
        singleLine = true,
        monospace = true,
        onChange = { next -> onChange(config.copy(itemVariable = next)) },
    )
    IntSliderField(
        label = stringResource(R.string.knotwork_node_field_parallelism),
        value = config.parallelism,
        range = 1..8,
        error = errors[FieldId.PARALLELISM],
        onChange = { next -> onChange(config.copy(parallelism = next)) },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldLabel(text = stringResource(R.string.knotwork_node_field_stop_on_error))
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.fillMaxWidth().weight(1f))
        Switch(
            checked = config.stopOnError,
            onCheckedChange = { next -> onChange(config.copy(stopOnError = next)) },
        )
    }
}

@Composable
private fun EvaluationFormBody(
    config: EvaluationConfig,
    errors: Map<FieldId, ValidationFailure>,
    onChange: (NodeConfig) -> Unit,
    onPickFromLibrary: PromptLibraryHook?,
) {
    TextField(
        label = stringResource(R.string.knotwork_node_field_criteria_prompt),
        value = config.criteriaPrompt,
        error = errors[FieldId.CRITERIA_PROMPT],
        singleLine = false,
        monospace = true,
        onChange = { next -> onChange(config.copy(criteriaPrompt = next)) },
        libraryCategory = "EVALUATION",
        onPickFromLibrary = onPickFromLibrary,
    )
    IntSliderField(
        label = stringResource(R.string.knotwork_node_field_max_retries),
        value = config.maxRetries,
        range = 0..5,
        error = errors[FieldId.MAX_RETRIES],
        onChange = { next -> onChange(config.copy(maxRetries = next)) },
    )
}

@Composable
private fun SummaryFormBody(
    config: SummaryConfig,
    errors: Map<FieldId, ValidationFailure>,
    onChange: (NodeConfig) -> Unit,
    onPickFromLibrary: PromptLibraryHook?,
) {
    SegmentedChipRow(
        label = stringResource(R.string.knotwork_node_field_format),
        values = listOf(
            SummaryFormat.BULLETS to "Bullets",
            SummaryFormat.PARAGRAPH to "Paragraph",
            SummaryFormat.CUSTOM to "Custom",
        ),
        selected = config.format,
        onSelect = { next -> onChange(config.copy(format = next)) },
    )
    if (config.format == SummaryFormat.CUSTOM) {
        TextField(
            label = stringResource(R.string.knotwork_node_field_custom_prompt),
            value = config.customPrompt.orEmpty(),
            error = errors[FieldId.CUSTOM_PROMPT],
            singleLine = false,
            monospace = true,
            onChange = { next -> onChange(config.copy(customPrompt = next.takeIf { it.isNotBlank() })) },
            libraryCategory = "SUMMARY",
            onPickFromLibrary = onPickFromLibrary,
        )
    }
    IntSliderField(
        label = stringResource(R.string.knotwork_node_field_target_length),
        value = config.targetLengthChars,
        range = 200..4_000,
        error = errors[FieldId.TARGET_LENGTH_CHARS],
        onChange = { next -> onChange(config.copy(targetLengthChars = next)) },
    )
}

/**
 * Returns a copy of [this] with [title] swapped in. Each sealed variant
 * needs its own `copy()` call because Kotlin does not synthesise a shared
 * copy method on a sealed interface.
 */
private fun NodeConfig.withTitle(title: String): NodeConfig = when (this) {
    is InputConfig -> copy(title = title)
    is OutputConfig -> copy(title = title)
    is LiteRtConfig -> copy(title = title)
    is CloudConfig -> copy(title = title)
    is IntentRouterConfig -> copy(title = title)
    is IfConditionConfig -> copy(title = title)
    is ClarificationConfig -> copy(title = title)
    is ToolConfig -> copy(title = title)
    is DecompositionConfig -> copy(title = title)
    is QueueProcessorConfig -> copy(title = title)
    is EvaluationConfig -> copy(title = title)
    is SummaryConfig -> copy(title = title)
}
