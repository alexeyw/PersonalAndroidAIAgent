package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PromptTemplate
import app.knotwork.android.domain.repositories.PromptRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/**
 * UseCase for retrieving all prompt templates.
 *
 * @property repository The repository for managing prompt templates.
 */
class GetPromptTemplatesUseCase @Inject constructor(private val repository: PromptRepository) {
    /**
     * Invokes the use case to get all prompt templates.
     * Seeds missing default prompts on first observation — additive: only
     * inserts categories that aren't already present, so existing users
     * who installed before a new default landed pick up the new entry on
     * the next library open instead of waiting for a DB wipe.
     *
     * @return A Flow emitting a list of [PromptTemplate] objects.
     */
    operator fun invoke(): Flow<List<PromptTemplate>> = repository.getAllPrompts().onStart {
        seedMissingDefaults()
    }

    /**
     * Inserts the default `PromptTemplate` for every node type whose category
     * is not yet present in the repository. Idempotent — running it on a
     * populated DB is a no-op, running it on a fresh DB inserts the full set.
     *
     * The category set covers every node type that surfaces a prompt field in
     * `NodeConfigSheet`. INPUT / OUTPUT / IF_CONDITION / QUEUE_PROCESSOR are
     * intentionally omitted: those forms have no prompt-bearing fields, so
     * the library button never opens with that filter.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun seedMissingDefaults() {
        try {
            val existingCategories = repository.getAllPrompts().first().map { it.category }.toSet()
            DEFAULT_SEED.forEach { template ->
                if (template.category !in existingCategories) {
                    repository.savePrompt(template)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort seed — a transient DB error here just means the user
            // sees an empty library this open; next open retries.
        }
    }

    private companion object {
        private val DEFAULT_SEED: List<PromptTemplate> = listOf(
            PromptTemplate(
                name = "Classifier",
                text = DefaultPrompts.INTENT_ROUTER_PROMPT,
                category = NodeType.INTENT_ROUTER.name,
            ),
            PromptTemplate(
                name = "Decomposer",
                text = DefaultPrompts.DECOMPOSITION_PROMPT,
                category = NodeType.DECOMPOSITION.name,
            ),
            PromptTemplate(
                name = "Evaluator",
                text = DefaultPrompts.EVALUATION_PROMPT,
                category = NodeType.EVALUATION.name,
            ),
            PromptTemplate(
                name = "Summarizer",
                text = DefaultPrompts.SUMMARY_PROMPT,
                category = NodeType.SUMMARY.name,
            ),
            PromptTemplate(
                name = "Clarifier",
                text = DefaultPrompts.CLARIFICATION_PROMPT,
                category = NodeType.CLARIFICATION.name,
            ),
            PromptTemplate(
                name = "On-device assistant",
                text = DefaultPrompts.SYSTEM_PROMPT_PREFIX,
                category = NodeType.LITE_RT.name,
            ),
            PromptTemplate(
                name = "Cloud assistant",
                text = DefaultPrompts.SYSTEM_PROMPT_PREFIX,
                category = NodeType.CLOUD.name,
            ),
            PromptTemplate(
                name = "Output formatter",
                text = DefaultPrompts.OUTPUT_FORMAT_PROMPT,
                category = NodeType.OUTPUT.name,
            ),
            PromptTemplate(
                name = "Tool Picker",
                text = DefaultPrompts.TOOL_USAGE_INSTRUCTION,
                category = NodeType.TOOL.name,
            ),
        )
    }
}
