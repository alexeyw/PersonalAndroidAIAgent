package ai.agent.android.data.prompt

import ai.agent.android.domain.prompt.PromptVariableProvider
import java.time.Clock
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the value for the `$TIME` placeholder.
 *
 * Resolves to the current local wall-clock time formatted as 24-hour `HH:mm`
 * (for example `09:07`). The clock's configured time zone is used, which in
 * production matches the device's system time zone via
 * [Clock.systemDefaultZone].
 *
 * Constructor exposes [Clock] for deterministic unit testing.
 *
 * @property clock Source of the current instant and time zone; defaults to the
 * system clock in the device time zone, which is what production code uses.
 */
@Singleton
class TimeVariableProvider internal constructor(
    private val clock: Clock,
) : PromptVariableProvider {

    /**
     * Hilt-visible no-arg constructor that wires the production default: the
     * system-default zoned [Clock]. Kept as a secondary constructor because
     * Dagger/Hilt rejects classes with multiple `@Inject` constructors, and
     * a parameter default on the primary one would synthesise a second
     * `@Inject` no-arg constructor under the hood.
     */
    @Inject
    constructor() : this(clock = Clock.systemDefaultZone())

    override fun key(): String = KEY

    /**
     * Computes the formatted current time.
     *
     * Reads the wall-clock time from [clock] and formats it with a fixed,
     * locale-independent `HH:mm` pattern — hour and minute representation is
     * stable across locales, so no [java.util.Locale] is involved.
     *
     * @return The current time in the `HH:mm` 24-hour pattern with zero padding.
     */
    override suspend fun resolve(): String =
        LocalTime.now(clock).format(FORMATTER)

    private companion object {
        const val KEY = "TIME"
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
