package ai.agent.android.data.prompt

import ai.agent.android.domain.prompt.PromptVariableProvider
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the value for the `$DATE` placeholder.
 *
 * Resolves to the current local date formatted as `dd MMMM yyyy` (for example
 * `01 May 2026`) using the device's default [Locale]. The on-device LLM does not
 * have intrinsic knowledge of "today" — exposing the date as a substitutable
 * variable lets prompt authors keep templates static while the engine injects a
 * fresh value on every render.
 *
 * The [Clock] and [localeProvider] are constructor parameters so unit tests can
 * pin the date and locale deterministically without touching the system clock.
 *
 * @property clock Source of the current instant; defaults to the system clock in
 * the device time zone, which is what production code uses.
 * @property localeProvider Returns the [Locale] used to format month names so the
 * date matches the user's regional settings; called on every [resolve] so locale
 * changes at runtime are picked up.
 */
@Singleton
class DateVariableProvider internal constructor(
    private val clock: Clock,
    private val localeProvider: () -> Locale,
) : PromptVariableProvider {

    /**
     * Hilt-visible no-arg constructor that wires the production defaults: the
     * system-default zoned [Clock] and the JVM default [Locale]. Kept as a
     * secondary constructor (rather than parameter defaults on the primary)
     * because Dagger/Hilt rejects classes with multiple `@Inject` constructors,
     * and Kotlin would synthesise an extra no-arg constructor if defaults were
     * declared on the primary one.
     */
    @Inject
    constructor() : this(
        clock = Clock.systemDefaultZone(),
        localeProvider = { Locale.getDefault() },
    )

    override fun key(): String = KEY

    /**
     * Computes the formatted current date.
     *
     * Reads the wall-clock date from [clock] in its configured time zone and
     * formats it using a dedicated [DateTimeFormatter] built with the current
     * [Locale]. The formatter is rebuilt per call so a runtime locale change
     * (e.g. user changes language) is reflected immediately.
     *
     * @return The current date in the `dd MMMM yyyy` pattern.
     */
    override suspend fun resolve(): String {
        val formatter = DateTimeFormatter.ofPattern(PATTERN, localeProvider())
        return LocalDate.now(clock).format(formatter)
    }

    private companion object {
        const val KEY = "DATE"
        const val PATTERN = "dd MMMM yyyy"
    }
}
