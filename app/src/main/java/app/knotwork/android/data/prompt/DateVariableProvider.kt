package app.knotwork.android.data.prompt

import app.knotwork.android.domain.prompt.PromptVariableProvider
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
 * `01 May 2026`) using the device's default [Locale]. The on-device LLM does
 * not have intrinsic knowledge of "today" — exposing the date as a
 * substitutable variable lets prompt authors keep templates static while the
 * engine injects a fresh value on every render.
 *
 * Both the [Clock] and the [Locale] are obtained through suppliers that are
 * invoked on every [resolve] call. That matters because the provider is a
 * `@Singleton`: snapshotting `Clock.systemDefaultZone()` once at construction
 * would freeze the time zone for the lifetime of the process, so a device that
 * crosses a time-zone border (or whose user changes the system zone, or DST
 * flips) would keep emitting a stale date until the app restarts.
 *
 * @property clockProvider Returns the [Clock] used to read "now"; in
 * production it returns a fresh `Clock.systemDefaultZone()` each call so the
 * current `ZoneId.systemDefault()` is picked up live. Tests override with a
 * fixed clock for deterministic assertions.
 * @property localeProvider Returns the [Locale] used to format month names so
 * the rendered date matches the user's regional settings; called on every
 * [resolve] so locale changes at runtime are reflected.
 */
@Singleton
class DateVariableProvider internal constructor(
    private val clockProvider: () -> Clock,
    private val localeProvider: () -> Locale,
) : PromptVariableProvider {

    /**
     * Hilt-visible no-arg constructor wiring the production defaults: a fresh
     * system-default zoned [Clock] and the JVM default [Locale] resolved at
     * each call. Kept as a secondary constructor because Dagger/Hilt rejects
     * classes with multiple `@Inject` constructors, and a parameter default
     * on the primary one would synthesise a second `@Inject` no-arg
     * constructor under the hood.
     */
    @Inject
    constructor() : this(
        clockProvider = { Clock.systemDefaultZone() },
        localeProvider = { Locale.getDefault() },
    )

    override fun key(): String = KEY

    /**
     * Computes the formatted current date.
     *
     * Reads a fresh [Clock] (so the current device time zone applies) and a
     * fresh [Locale] (so the month name follows the user's current language)
     * on every invocation. The formatter is rebuilt per call for the same
     * reason — runtime locale changes propagate immediately.
     *
     * @return The current date in the `dd MMMM yyyy` pattern.
     */
    override suspend fun resolve(): String {
        val formatter = DateTimeFormatter.ofPattern(PATTERN, localeProvider())
        return LocalDate.now(clockProvider()).format(formatter)
    }

    private companion object {
        const val KEY = "DATE"
        const val PATTERN = "dd MMMM yyyy"
    }
}
