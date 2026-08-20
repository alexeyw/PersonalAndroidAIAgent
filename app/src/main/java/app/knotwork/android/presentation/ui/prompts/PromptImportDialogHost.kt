package app.knotwork.android.presentation.ui.prompts

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.knotwork.android.R
import app.knotwork.android.domain.models.PromptPackImportNotes
import app.knotwork.android.domain.models.PromptPackParseError
import app.knotwork.android.domain.models.RefusedCapability
import app.knotwork.android.domain.promptpack.FrontmatterParseResult
import app.knotwork.android.domain.usecases.promptpack.PromptPackCollisionChoice
import app.knotwork.design.components.dialogs.MAX_NAMED_LIST_ITEMS
import app.knotwork.design.components.dialogs.OutcomeAction
import app.knotwork.design.components.dialogs.OutcomeActionEmphasis
import app.knotwork.design.components.dialogs.OutcomeDialog
import app.knotwork.design.components.dialogs.OutcomeNamedList
import app.knotwork.design.components.dialogs.OutcomeTone

/**
 * Renders whichever import dialog is open, as an instance of the shared
 * [OutcomeDialog] family.
 *
 * Four outcomes and one question share one shape. The distinction that
 * carries the most weight is between [OutcomeTone.GUARD] and
 * [OutcomeTone.ERROR]: a file that asked for a capability and was refused
 * still imported, so it draws the shield that means "a limit did its job"
 * rather than the red that means nothing happened.
 *
 * @param dialog The dialog to show, or `null` for none.
 * @param viewModel Sink for the dismiss and decision callbacks.
 */
@Composable
fun PromptImportDialogHost(dialog: PromptImportDialog?, viewModel: PromptLibraryViewModel) {
    when (dialog) {
        null -> Unit

        is PromptImportDialog.Reported -> ReportedDialog(dialog = dialog, viewModel = viewModel)

        is PromptImportDialog.Failed -> OutcomeDialog(
            tone = OutcomeTone.ERROR,
            headline = stringResource(R.string.prompts_import_failed_title),
            body = failureBody(dialog.cause),
            confirm = OutcomeAction(
                label = stringResource(R.string.common_ok),
                onClick = viewModel::dismissImportDialog,
            ),
            onDismissRequest = viewModel::dismissImportDialog,
        )

        is PromptImportDialog.Collision -> OutcomeDialog(
            tone = OutcomeTone.QUESTION,
            headline = stringResource(R.string.prompts_import_collision_title_format, dialog.existingName),
            body = stringResource(R.string.prompts_import_collision_body),
            confirm = OutcomeAction(
                label = stringResource(R.string.prompts_import_collision_keep_both),
                onClick = {
                    viewModel.resolveImportCollision(
                        candidate = dialog.candidate,
                        choice = PromptPackCollisionChoice.KEEP_BOTH,
                        notes = dialog.notes,
                    )
                },
                // The only action that loses nothing, so the only one drawn
                // to be reached for.
                emphasis = OutcomeActionEmphasis.EMPHASISED,
            ),
            neutral = OutcomeAction(
                label = stringResource(R.string.prompts_import_collision_replace),
                onClick = {
                    viewModel.resolveImportCollision(
                        candidate = dialog.candidate,
                        choice = PromptPackCollisionChoice.REPLACE,
                        notes = dialog.notes,
                    )
                },
            ),
            dismiss = OutcomeAction(
                label = stringResource(R.string.common_cancel),
                onClick = viewModel::dismissImportDialog,
            ),
            onDismissRequest = viewModel::dismissImportDialog,
        )
    }
}

/**
 * The "it imported, but" dialog.
 *
 * A refusal outranks a version mismatch: "this file wanted to add tools" is
 * the more important sentence, and stacking two dialogs on one import is
 * worse than folding the lesser note into the greater one — so when a file
 * does both, the shield is what the user sees and the version appears in the
 * left-out list.
 */
@Composable
private fun ReportedDialog(dialog: PromptImportDialog.Reported, viewModel: PromptLibraryViewModel) {
    val notes = dialog.notes
    val refused = notes.hasRefusal
    val items = leftOutItems(notes)
    val hidden = items.size - MAX_NAMED_LIST_ITEMS
    OutcomeDialog(
        tone = if (refused) OutcomeTone.GUARD else OutcomeTone.INFO,
        headline = stringResource(
            if (refused) R.string.prompts_import_refused_title else R.string.prompts_import_mismatch_title,
        ),
        body = when {
            refused -> stringResource(
                R.string.prompts_import_refused_body_format,
                dialog.presetName,
                askedFor(notes.refused),
            )

            notes.versionMismatch != null -> stringResource(
                R.string.prompts_import_mismatch_body_format,
                dialog.presetName,
            )

            else -> stringResource(R.string.prompts_import_unknown_keys_body_format, dialog.presetName)
        },
        namedList = OutcomeNamedList(
            heading = stringResource(R.string.prompts_import_left_out_heading),
            items = items,
            moreLabel = hidden.takeIf { it > 0 }?.let {
                pluralStringResource(R.plurals.prompts_import_left_out_more, it, it)
            },
        ),
        confirm = OutcomeAction(
            label = stringResource(R.string.common_ok),
            onClick = viewModel::dismissImportDialog,
        ),
        onDismissRequest = viewModel::dismissImportDialog,
    )
}

/**
 * Names the families a file asked for, in the sentence's own words — "tools",
 * "steps", "scripts" — joined for the two- and three-family cases.
 */
@Composable
private fun askedFor(refused: List<RefusedCapability>): String {
    val separator = stringResource(R.string.common_list_separator)
    // A plain `for` rather than `joinToString`: its transform is not an
    // inline lambda, so a `stringResource` call inside it is not in a
    // composition scope.
    val names = mutableListOf<String>()
    for (capability in refused) {
        names += stringResource(
            when (capability.kind) {
                RefusedCapability.Kind.TOOLS -> R.string.prompts_import_refused_asked_tools
                RefusedCapability.Kind.STEPS -> R.string.prompts_import_refused_asked_steps
                RefusedCapability.Kind.SCRIPTS -> R.string.prompts_import_refused_asked_scripts
            },
        )
    }
    return names.joinToString(separator = separator)
}

/**
 * Builds the "left out" list: refused capabilities first (tools by name,
 * structure by count), then the version, then any unrecognised keys.
 *
 * Tool names come from the file, so they are shown as the sanitised strings
 * the reader produced — never re-parsed, never resolved into anything that
 * could be mistaken for the app's own words.
 */
@Composable
private fun leftOutItems(notes: PromptPackImportNotes): List<String> = buildList {
    // A key can be present with nothing readable under it (`tools: []`, or
    // values that sanitise away entirely). The body still says the file asked
    // for tools; a list line reading "Tools:" with nothing after it would say
    // less than nothing. Filtered here rather than inside the `when`, so that
    // `when` stays exhaustive over the enum and a new capability family is a
    // compile error instead of silently rendering as scripts.
    notes.refused.filter { it.values.isNotEmpty() }.forEach { capability ->
        when (capability.kind) {
            RefusedCapability.Kind.TOOLS -> add(
                stringResource(
                    R.string.prompts_import_refused_tools_format,
                    capability.values.joinToString(separator = stringResource(R.string.common_list_separator)),
                ),
            )

            RefusedCapability.Kind.STEPS -> add(
                stringResource(R.string.prompts_import_refused_steps_format, capability.values.size),
            )

            RefusedCapability.Kind.SCRIPTS -> add(
                stringResource(R.string.prompts_import_refused_scripts_format, capability.values.size),
            )
        }
    }
    notes.versionMismatch?.let { mismatch ->
        add(stringResource(R.string.prompts_import_version_left_out_format, mismatch.foundVersion))
    }
    addAll(notes.droppedKeys)
}

/**
 * One sentence per recognised parse failure.
 *
 * Node types appear as their raw enum names, matching the category tabs and
 * the in-card pill: this app does not localise node types anywhere, and a
 * friendly name here would be the only place it did.
 */
@Composable
private fun failureBody(cause: PromptPackParseError): String = when (cause) {
    is PromptPackParseError.MalformedFrontmatter -> when (cause.reason) {
        FrontmatterParseResult.Reason.DUPLICATE_KEY ->
            stringResource(R.string.prompts_import_failed_duplicate_key)

        else -> stringResource(R.string.prompts_import_failed_frontmatter)
    }

    is PromptPackParseError.MissingRequiredKey ->
        stringResource(R.string.prompts_import_failed_missing_key_format, cause.key)

    PromptPackParseError.MissingPromptText -> stringResource(R.string.prompts_import_failed_no_prompt)

    is PromptPackParseError.UnknownNodeType ->
        stringResource(R.string.prompts_import_failed_unknown_type_format, cause.value)

    is PromptPackParseError.NonLlmNodeType ->
        stringResource(R.string.prompts_import_failed_not_llm_format, cause.nodeType.name)

    is PromptPackParseError.NameTooLong ->
        stringResource(R.string.prompts_import_failed_name_too_long_format, cause.limit)

    is PromptPackParseError.PromptTooLong ->
        stringResource(R.string.prompts_import_failed_prompt_too_long_format, cause.limit)
}
