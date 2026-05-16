@file:Suppress("LongMethod", "TooManyFunctions") // 12 forms by spec; splitting per-file would only add ceremony.

package app.knotwork.design.components.pipelineeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.knotwork.design.R
import app.knotwork.design.components.chips.ChipStyle
import app.knotwork.design.components.chips.KnotworkChip
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

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
     */
    @Composable
    fun Body(config: NodeConfig, errors: Map<FieldId, ValidationFailure>, onChange: (NodeConfig) -> Unit) {
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
                is LiteRtConfig -> LiteRtFormBody(config, errors, onChange)
                is CloudConfig -> CloudFormBody(config, errors, onChange)
                is IntentRouterConfig -> IntentRouterFormBody(config, errors, onChange)
                is IfConditionConfig -> IfConditionFormBody(config, errors, onChange)
                is ClarificationConfig -> ClarificationFormBody(config, errors, onChange)
                is ToolConfig -> ToolFormBody(config, errors, onChange)
                is DecompositionConfig -> DecompositionFormBody(config, errors, onChange)
                is QueueProcessorConfig -> QueueProcessorFormBody(config, errors, onChange)
                is EvaluationConfig -> EvaluationFormBody(config, errors, onChange)
                is SummaryConfig -> SummaryFormBody(config, errors, onChange)
            }
        }
    }
}

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
private fun TextField(
    label: String,
    value: String,
    error: ValidationFailure?,
    singleLine: Boolean,
    monospace: Boolean,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        FieldLabel(text = label)
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
                KnotworkChip(
                    label = label,
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    style = ChipStyle.Tonal,
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        FieldLabel(text = stringResource(R.string.knotwork_node_field_classes))
        config.classes.forEachIndexed { index, intentClass ->
            OutlinedTextField(
                value = intentClass.name,
                onValueChange = { next ->
                    val updated = config.classes.toMutableList()
                    updated[index] = intentClass.copy(name = next)
                    onChange(config.copy(classes = updated))
                },
                singleLine = true,
                textStyle = KnotworkTextStyles.MonoBase,
                modifier = Modifier.fillMaxWidth(),
                isError = intentClass.name.isBlank(),
            )
        }
        InlineError(failure = errors[FieldId.CLASSES])
    }
    TextField(
        label = stringResource(R.string.knotwork_node_field_classifier_prompt),
        value = config.classifierPrompt,
        error = errors[FieldId.CLASSIFIER_PROMPT],
        singleLine = false,
        monospace = true,
        onChange = { next -> onChange(config.copy(classifierPrompt = next)) },
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
) {
    TextField(
        label = stringResource(R.string.knotwork_node_field_question),
        value = config.questionTemplate,
        error = errors[FieldId.QUESTION_TEMPLATE],
        singleLine = false,
        monospace = true,
        onChange = { next -> onChange(config.copy(questionTemplate = next)) },
    )
    TextField(
        label = stringResource(R.string.knotwork_node_field_quick_replies),
        value = config.quickReplies.joinToString(", "),
        error = errors[FieldId.QUICK_REPLIES],
        singleLine = true,
        monospace = false,
        onChange = { next ->
            val parts = next.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            onChange(config.copy(quickReplies = parts))
        },
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

@Composable
private fun ToolFormBody(config: ToolConfig, errors: Map<FieldId, ValidationFailure>, onChange: (NodeConfig) -> Unit) {
    TextField(
        label = stringResource(R.string.knotwork_node_field_tool),
        value = config.toolId,
        error = errors[FieldId.TOOL_ID],
        singleLine = true,
        monospace = true,
        onChange = { next -> onChange(config.copy(toolId = next)) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        FieldLabel(text = stringResource(R.string.knotwork_node_field_arg_mapping))
        // Materialise the entries into an ordered list so a key rename can be
        // applied positionally without colliding with a sibling key that the
        // user is about to type on the way to a new value (LinkedHashMap +
        // forEachIndexed gives stable iteration over the live snapshot).
        val rows = config.argumentMapping.entries.toList()
        rows.forEachIndexed { index, (key, expression) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            ) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { nextKey ->
                        onChange(config.copy(argumentMapping = rows.replaceKey(index, nextKey)))
                    },
                    singleLine = true,
                    textStyle = KnotworkTextStyles.MonoBase,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isError = key.isBlank(),
                )
                OutlinedTextField(
                    value = expression,
                    onValueChange = { nextValue ->
                        onChange(config.copy(argumentMapping = rows.replaceValue(index, nextValue)))
                    },
                    singleLine = true,
                    textStyle = KnotworkTextStyles.MonoBase,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isError = expression.isBlank(),
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
) {
    TextField(
        label = stringResource(R.string.knotwork_node_field_planning_prompt),
        value = config.planningPrompt,
        error = errors[FieldId.PLANNING_PROMPT],
        singleLine = false,
        monospace = true,
        onChange = { next -> onChange(config.copy(planningPrompt = next)) },
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
) {
    TextField(
        label = stringResource(R.string.knotwork_node_field_criteria_prompt),
        value = config.criteriaPrompt,
        error = errors[FieldId.CRITERIA_PROMPT],
        singleLine = false,
        monospace = true,
        onChange = { next -> onChange(config.copy(criteriaPrompt = next)) },
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
 * Returns a new `argumentMapping` with the entry at [index] re-keyed to
 * [nextKey], preserving original entry order so the row the user is
 * editing does not jump under the caret. Used by [ToolFormBody] when the
 * user renames a tool-argument key inline.
 */
private fun List<Map.Entry<String, String>>.replaceKey(index: Int, nextKey: String): Map<String, String> =
    mapIndexed { i, entry ->
        if (i == index) nextKey to entry.value else entry.key to entry.value
    }.toMap()

/**
 * Returns a new `argumentMapping` with the value at [index] replaced by
 * [nextValue], preserving entry order. Used by [ToolFormBody] when the
 * user edits the expression of an existing tool-argument row.
 */
private fun List<Map.Entry<String, String>>.replaceValue(index: Int, nextValue: String): Map<String, String> =
    mapIndexed { i, entry ->
        if (i == index) entry.key to nextValue else entry.key to entry.value
    }.toMap()

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
