package app.knotwork.design.components.topbar

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import app.knotwork.design.icons.AppIcons

/**
 * The pair of top-bar actions that hands a journal to the user: **share it** and
 * **save it to a file**.
 *
 * One component rather than a copy on each journal screen, because the two
 * journals — trigger evaluations and inbound external requests — produce the same
 * kind of artefact for the same reasons, and a reader who learned the pair on one
 * screen must find it identical on the other. Divergence here would read as two
 * different features.
 *
 * Two buttons rather than one behind a menu: the two destinations are not
 * variants of one action. Share is how the document reaches a bug report or a
 * chat; save is how it reaches a folder the owner can pull off the device
 * afterwards. Both are one tap because both are the whole point of the screen
 * having an export at all.
 *
 * @param shareContentDescription Accessibility label of the share action.
 * @param saveContentDescription Accessibility label of the save action.
 * @param onShare Invoked when the user asks to share the journal.
 * @param onSave Invoked when the user asks to save the journal to a file.
 */
@Composable
fun JournalExportActions(
    shareContentDescription: String,
    saveContentDescription: String,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    IconButton(onClick = onShare) {
        Icon(
            imageVector = AppIcons.Share,
            contentDescription = shareContentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
    IconButton(onClick = onSave) {
        Icon(
            imageVector = AppIcons.ExportFile,
            contentDescription = saveContentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
