package app.knotwork.android.data.prompt

import app.knotwork.android.domain.prompt.PromptVariableProvider
import java.time.Clock
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the value for the `$TIME` placeholder.
 *
 * Resolves to the current local wall-clock time formatted as 24-hour `HH:mm`
 * (for example `09:07`).
 *
 * The [Clock] is obtained through [clockProvider] on every [resolve] call so
 * the device's current time zone applies even when it changes after process
 * start (travel across zones, manual zone change, DST transitions). A
 * `@Singleton` that captures `Clock.systemDefaultZone()` once would freeze the
 * zone for the app's lifetime and emit stale times until restart.
 *
 * @property clockProvider Returns the [Clock] used to read "now"; in production
 * it returns a fresh `Clock.systemDefaultZone()` each call. Tests override
 * with a fixed clock for deterministic assertions.
 */
@Singleton
class TimeVariableProvider internal constructor(private val clockProvider: () -> Clock) : PromptVariableProvider {

    /**
     * Hilt-visible no-arg constructor wiring the production default: a fresh
     * system-default zoned [Clock] resolved at each call. Kept as a secondary
     * constructor because Dagger/Hilt rejects classes with multiple `@Inject`
     * constructors, and a parameter default on the primary one would
     * synthesise a second `@Inject` no-arg constructor under the hood.
     */
    @Inject
    constructor() : this(clockProvider = { Clock.systemDefaultZone() })

    override fun key(): String = KEY

    /**
     * Computes the formatted current time.
     *
     * Reads a fresh [Clock] on every invocation, so the device's current zone
     * is picked up. The `HH:mm` pattern is locale-independent — hour/minute
     * formatting does not vary by language — so no [java.util.Locale] is
     * involved.
     *
     * @return The current time in the `HH:mm` 24-hour pattern with zero padding.
     */
    override suspend fun resolve(): String = LocalTime.now(clockProvider()).format(FORMATTER)

    private companion object {
        const val KEY = "TIME"
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
