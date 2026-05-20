package ai.agent.android.data.prompt

import ai.agent.android.domain.prompt.PromptVariableProvider
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the value for the `$LOCATION` placeholder.
 *
 * Privacy-conservative implementation — resolves to the device's coarse
 * region (the country portion of the current [Locale], e.g. `US`, `DE`)
 * rather than a fine-grained GPS coordinate. This keeps the variable
 * informative ("the user is in Germany so prefer metric units") while
 * never requiring an Android location permission.
 *
 * Reads [Locale.getDefault] on every [resolve] call so a runtime locale
 * change reflects immediately.
 *
 * @property localeProvider Returns the [Locale] used at render time.
 */
@Singleton
class LocationVariableProvider internal constructor(private val localeProvider: () -> Locale) :
    PromptVariableProvider {

    @Inject
    constructor() : this(localeProvider = { Locale.getDefault() })

    override fun key(): String = KEY

    override suspend fun resolve(): String = localeProvider().country.ifBlank { UNKNOWN }

    private companion object {
        const val KEY = "LOCATION"
        const val UNKNOWN = "unknown"
    }
}
