package ai.agent.android.data.prompt

import ai.agent.android.domain.prompt.PromptVariableProvider
import ai.agent.android.domain.repositories.IdentityRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the value for the `$USER` placeholder.
 *
 * v0.1 has no signed-in account, so the variable resolves to the
 * identity card's `displayName` (currently the literal "Anonymous").
 * When account-aware identity lands, the same provider will start
 * returning the user's chosen display name without touching prompt
 * templates.
 *
 * @property identityRepository Source of truth for the current identity
 *   snapshot — kept as a dependency so the provider doesn't duplicate
 *   ANDROID_ID resolution logic.
 */
@Singleton
class UserVariableProvider @Inject constructor(private val identityRepository: IdentityRepository) :
    PromptVariableProvider {

    override fun key(): String = KEY

    override suspend fun resolve(): String = identityRepository.getIdentity(anonymousLabel = ANONYMOUS).displayName

    private companion object {
        const val KEY = "USER"
        const val ANONYMOUS = "Anonymous"
    }
}
