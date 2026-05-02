package ai.agent.android.data.prompt

import ai.agent.android.domain.prompt.PromptVariableProvider
import ai.agent.android.domain.repositories.LocalModelRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the value for the `$MODEL` placeholder.
 *
 * Resolves to the human-readable name of the currently active local model
 * (`LocalModel.name`) — useful when a single template is reused across local
 * and cloud nodes and the agent needs to mention which engine is answering.
 *
 * If no model is marked active (e.g. before the first model is downloaded /
 * selected) the placeholder resolves to an empty string.
 *
 * @property localModelRepository Domain abstraction over the local model
 * registry; the repository persists the "active" flag so the same record is
 * returned across processes.
 */
@Singleton
class ModelVariableProvider @Inject constructor(
    private val localModelRepository: LocalModelRepository,
) : PromptVariableProvider {

    override fun key(): String = KEY

    /**
     * Returns the active model's display name.
     *
     * @return [ai.agent.android.domain.models.LocalModel.name] of the active
     * model, or an empty string when no model is currently active.
     */
    override suspend fun resolve(): String =
        localModelRepository.getActiveModel()?.name ?: ""

    private companion object {
        const val KEY = "MODEL"
    }
}
