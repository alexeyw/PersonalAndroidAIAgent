package app.knotwork.android.domain.usecases.promptpack

import app.knotwork.android.domain.models.PromptPackCandidate
import app.knotwork.android.domain.models.PromptPackImportNotes
import app.knotwork.android.domain.models.PromptPackParseError
import app.knotwork.android.domain.models.PromptPreset

/**
 * What happened when a prompt-pack file was handed to
 * [ImportPromptPackUseCase].
 *
 * Four shapes, because the user sees four different things: a prompt landed,
 * a prompt landed with something to say about it, nothing changed, or a
 * question has to be answered before anything can change.
 */
sealed class PromptPackImportResult {

    /**
     * The prompt is in the library.
     *
     * @property preset The persisted preset, with its final id — which may
     *   differ from the one the file asked for, see
     *   [ImportPromptPackUseCase].
     * @property notes What had to be left out, or `null` when the file was
     *   clean. A non-null value with [PromptPackImportNotes.hasRefusal] set
     *   is the case the format's capability ceiling exists for.
     */
    data class Imported(val preset: PromptPreset, val notes: PromptPackImportNotes?) : PromptPackImportResult()

    /**
     * The file is byte-for-byte the prompt already saved under that id, so
     * nothing was written and nothing needs deciding.
     *
     * @property preset The prompt already in the library.
     */
    data class Unchanged(val preset: PromptPreset) : PromptPackImportResult()

    /**
     * The file carries the id of a prompt already in the library, and the
     * two differ. Replacing could destroy an edit made in the app and
     * keeping both could quietly grow a library of near-duplicates, so the
     * user chooses — see [ResolvePromptPackCollisionUseCase].
     *
     * @property candidate The prompt as read from the file.
     * @property existing The prompt already saved under the same id.
     * @property notes What had to be left out of [candidate], carried across
     *   the decision so it is still reported once the user answers.
     */
    data class NeedsDecision(
        val candidate: PromptPackCandidate,
        val existing: PromptPreset,
        val notes: PromptPackImportNotes?,
    ) : PromptPackImportResult()

    /**
     * Nothing was imported.
     *
     * @property cause Which recognised failure occurred, so the UI can say
     *   it in one sentence.
     */
    data class Failed(val cause: PromptPackParseError) : PromptPackImportResult()
}

/**
 * How the user chose to resolve a [PromptPackImportResult.NeedsDecision].
 */
enum class PromptPackCollisionChoice {
    /** Overwrite the saved prompt with the file's version, keeping its id. */
    REPLACE,

    /** Save the file's version alongside the existing one under a fresh id. */
    KEEP_BOTH,
}
