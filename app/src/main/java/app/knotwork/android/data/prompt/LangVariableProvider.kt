package app.knotwork.android.data.prompt

import app.knotwork.android.domain.prompt.PromptVariableProvider
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the value for the `$LANG` placeholder.
 *
 * Resolves to the device's current language tag (e.g. `en-US`, `de-DE`,
 * `ja-JP`). Useful when the system prompt needs to bias the model toward
 * the user's preferred language without the user having to retype the
 * locale manually in every template.
 *
 * Reads [Locale.getDefault] on every [resolve] call so a runtime locale
 * change (Settings → Language) takes effect on the very next render.
 *
 * @property localeProvider Returns the [Locale] used at render time. Tests
 *   override with a fixed locale for deterministic assertions.
 */
@Singleton
class LangVariableProvider internal constructor(private val localeProvider: () -> Locale) : PromptVariableProvider {

    @Inject
    constructor() : this(localeProvider = { Locale.getDefault() })

    override fun key(): String = KEY

    override suspend fun resolve(): String = localeProvider().toLanguageTag()

    private companion object {
        const val KEY = "LANG"
    }
}
