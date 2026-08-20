package app.knotwork.android.domain.usecases.promptpack

import app.knotwork.android.domain.models.PromptPackCandidate
import app.knotwork.android.domain.models.PromptPackImportNotes
import app.knotwork.android.domain.models.PromptPackImportOutcome
import app.knotwork.android.domain.models.PromptPreset
import app.knotwork.android.domain.promptpack.PromptPackMarkdownSerializer
import app.knotwork.android.domain.repositories.PromptPresetRepository
import kotlinx.coroutines.CancellationException
import java.util.UUID
import javax.inject.Inject

/**
 * Reads a prompt-pack file and puts the prompt it carries into the user
 * catalogue.
 *
 * The use case owns three things the parser deliberately does not:
 *
 * 1. **Where the id comes from.** A file may name its own `id`; when it does
 *    not, the file name's stem is used, so re-importing the same file updates
 *    the same prompt instead of breeding copies.
 * 2. **What happens when that id is taken.** A collision with a *bundled*
 *    preset is re-keyed to a fresh UUID without asking: bundled ids address
 *    the read-only catalogue, `getPresetById` resolves them bundled-first,
 *    and a user preset saved under one would be permanently unreachable. The
 *    id is not user-visible, so nothing the user can perceive is changed by
 *    the re-key. A collision with one of the *user's own* prompts is a
 *    question only the user can answer, and it becomes
 *    [PromptPackImportResult.NeedsDecision].
 * 3. **That an imported prompt is never bundled.** Everything written here
 *    carries `isBundled = false`; a file cannot promote itself into the tier
 *    the user is not allowed to edit or delete.
 *
 * What it does **not** do is widen what a prompt pack can carry. The file
 * supplies wording; tools, steps and scripts are refused by the parser and
 * reported through [PromptPackImportResult.Imported.notes].
 *
 * @property promptPresetRepository Catalogue the prompt is written to and
 *   the collision is checked against.
 */
class ImportPromptPackUseCase @Inject constructor(private val promptPresetRepository: PromptPresetRepository) {

    /**
     * Parses [document] and, unless the user has to decide something first,
     * persists the prompt it carries.
     *
     * @param document The full text of the picked file.
     * @param fileName The picked document's display name. Only its stem is
     *   used, and only when the frontmatter omits `id`.
     * @return The outcome; see [PromptPackImportResult].
     */
    suspend operator fun invoke(document: String, fileName: String): PromptPackImportResult {
        val fallbackId = fallbackIdFrom(fileName)
        val (candidate, notes) = when (val outcome = PromptPackMarkdownSerializer.parse(document, fallbackId)) {
            is PromptPackImportOutcome.Failure -> return PromptPackImportResult.Failed(outcome.cause)
            is PromptPackImportOutcome.Success -> outcome.preset to null
            is PromptPackImportOutcome.Partial -> outcome.preset to outcome.notes
        }

        val existing = try {
            promptPresetRepository.getPresetById(candidate.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A lookup failure must not become a silent overwrite: treat the
            // id as unknown and let the save below decide. The repository's
            // upsert is the same operation either way.
            null
        }

        return when {
            existing == null -> persist(candidate.toUserPreset(), notes)
            existing.isBundled -> persist(candidate.toUserPreset(id = UUID.randomUUID().toString()), notes)
            existing.matches(candidate) -> PromptPackImportResult.Unchanged(existing)
            else -> PromptPackImportResult.NeedsDecision(candidate = candidate, existing = existing, notes = notes)
        }
    }

    /**
     * Writes [preset] and reports it as imported.
     *
     * A persistence failure surfaces as a thrown exception rather than a
     * quiet success — the caller shows the generic unexpected-error message,
     * and a prompt the user believes was imported but was not is the one
     * outcome worth crashing the flow over.
     */
    private suspend fun persist(preset: PromptPreset, notes: PromptPackImportNotes?): PromptPackImportResult {
        promptPresetRepository.saveUserPreset(preset)
        return PromptPackImportResult.Imported(preset = preset, notes = notes)
    }

    /**
     * Derives the fallback id from a document's display name: the stem, with
     * runs of characters that are not letters, digits, `-` or `_` collapsed
     * to single hyphens.
     *
     * @return The derived id, or a fresh UUID when the name yields nothing
     *   usable — an empty id would collide with every other empty id.
     */
    private fun fallbackIdFrom(fileName: String): String {
        val stem = fileName.substringBeforeLast('.', fileName)
            .lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
        return stem.ifEmpty { UUID.randomUUID().toString() }
    }

    /**
     * Whether this saved preset already says exactly what the file says.
     *
     * The id is excluded deliberately — it is what brought the two together
     * — and so is `isBundled`, which the file never gets to set.
     */
    private fun PromptPreset.matches(candidate: PromptPackCandidate): Boolean = name == candidate.name &&
        description == candidate.description &&
        nodeType == candidate.nodeType &&
        systemPrompt == candidate.systemPrompt &&
        tags == candidate.tags
}
