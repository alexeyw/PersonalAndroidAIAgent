package app.knotwork.android.domain.usecases.promptpack

import app.knotwork.android.domain.constants.PromptPresetConstants
import app.knotwork.android.domain.models.PromptPackCandidate
import app.knotwork.android.domain.models.PromptPackImportNotes
import app.knotwork.android.domain.repositories.PromptPresetRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Completes an import that stopped at
 * [PromptPackImportResult.NeedsDecision] — the file carried the id of a
 * prompt already in the library, and the two differ.
 *
 * Split out of [ImportPromptPackUseCase] rather than folded into it because
 * the two run at different moments: the first when a file is picked, this
 * one only after a person has answered a question. Keeping them apart means
 * the import path has no "and also maybe wait for a dialog" branch.
 *
 * @property promptPresetRepository Catalogue the resolved prompt is written to.
 */
class ResolvePromptPackCollisionUseCase @Inject constructor(
    private val promptPresetRepository: PromptPresetRepository,
) {

    /**
     * Persists [candidate] according to [choice].
     *
     * @param candidate The prompt as read from the file.
     * @param choice What the user decided.
     * @param notes What had to be left out of the file, carried through so
     *   the caller can still report it after the decision.
     * @return [PromptPackImportResult.Imported] holding the preset that was
     *   written — under the colliding id for
     *   [PromptPackCollisionChoice.REPLACE], under a fresh one with a
     *   disambiguated name for [PromptPackCollisionChoice.KEEP_BOTH].
     */
    suspend operator fun invoke(
        candidate: PromptPackCandidate,
        choice: PromptPackCollisionChoice,
        notes: PromptPackImportNotes? = null,
    ): PromptPackImportResult {
        val preset = when (choice) {
            PromptPackCollisionChoice.REPLACE -> candidate.toUserPreset()
            PromptPackCollisionChoice.KEEP_BOTH ->
                candidate
                    .toUserPreset(id = UUID.randomUUID().toString())
                    .copy(name = disambiguate(candidate.name))
        }
        promptPresetRepository.saveUserPreset(preset)
        return PromptPackImportResult.Imported(preset = preset, notes = notes)
    }

    /**
     * Appends the "kept both" marker to a name, keeping the result inside
     * [PromptPresetConstants.MAX_NAME_LENGTH].
     *
     * The base name is shortened rather than the marker dropped: two rows
     * reading exactly the same thing is the failure this action exists to
     * avoid, so the marker is the part that must survive.
     */
    private fun disambiguate(name: String): String {
        val room = PromptPresetConstants.MAX_NAME_LENGTH - IMPORTED_SUFFIX.length
        val base = if (name.length > room) name.take(room).trimEnd() else name
        return "$base$IMPORTED_SUFFIX"
    }

    private companion object {
        /**
         * Marker appended to a kept-both import.
         *
         * Not localised: it becomes part of a stored prompt name, and a name
         * that changes wording when the device language changes is a name
         * the user cannot search for twice.
         */
        const val IMPORTED_SUFFIX = " (imported)"
    }
}
