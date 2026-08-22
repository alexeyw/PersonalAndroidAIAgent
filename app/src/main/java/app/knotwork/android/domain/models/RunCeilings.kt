package app.knotwork.android.domain.models

/**
 * The dimensions along which one autonomous run is bounded.
 *
 * Named rather than implied so a breach can say which ceiling bound without
 * the caller re-deriving it from whichever number happens to be largest.
 */
enum class RunCeilingAxis {
    /** Node executions across the whole run tree. */
    STEPS,

    /** Tokens accounted for across the whole run tree. */
    TOKENS,

    /** Money spent with cloud providers. */
    MONEY,
}

/**
 * The limit in force on one [RunCeilingAxis] for one run.
 *
 * Modelled as a sealed type rather than a nullable number because
 * "this axis cannot be measured" is a state the product has to *state*, not a
 * state it may quietly render as zero. A ceiling the user believes is
 * protecting them while it silently measures nothing is worse than no ceiling
 * at all.
 */
sealed interface RunCeilingLimit {

    /**
     * The axis is measured and enforced.
     *
     * @property soft The threshold at which the run is warned and marked but
     *   keeps going.
     * @property hard The threshold at which the run is deterministically ended.
     *   Always greater than or equal to [soft].
     */
    data class Enforced(val soft: Int, val hard: Int) : RunCeilingLimit

    /**
     * The axis cannot be measured on this build, so no limit is applied and
     * the product says so rather than implying protection it does not provide.
     *
     * This is the money axis today. The app is bring-your-own-key: it never
     * sees an invoice, and the only way to turn tokens into currency is a
     * per-model price table. There is no price source on the device, the LLM
     * client library computes none, and a table bundled into the APK would go
     * stale silently between releases — presenting a **wrong number as money**,
     * which is worse than presenting none. The token axis is the honest proxy
     * and is enforced.
     */
    data object Unavailable : RunCeilingLimit
}

/**
 * Every ceiling in force for one run, resolved once at the top of the run tree
 * and shared by every node at every nesting depth.
 *
 * Resolved from the run's [RunOrigin] because the exposure genuinely differs:
 * an interactive run has a person watching it who can stop it, and a
 * background run — a trigger firing at 3am against a cloud key — does not.
 * The distinction reuses `RunOrigin.isInteractive` rather than re-partitioning
 * the enum, so a new origin still cannot compile until somebody classifies it.
 *
 * @property origin The origin these ceilings were resolved for.
 * @property steps Ceiling on node executions across the run tree.
 * @property tokens Ceiling on accounted tokens across the run tree.
 * @property money Ceiling on money spent. [RunCeilingLimit.Unavailable] in this
 *   release — see that type for why.
 */
data class RunCeilings(
    val origin: RunOrigin,
    val steps: RunCeilingLimit.Enforced,
    val tokens: RunCeilingLimit.Enforced,
    val money: RunCeilingLimit = RunCeilingLimit.Unavailable,
) {
    /**
     * The limit in force on one axis.
     *
     * @param axis The axis to look up.
     * @return The limit, enforced or unavailable.
     */
    fun limitOn(axis: RunCeilingAxis): RunCeilingLimit = when (axis) {
        RunCeilingAxis.STEPS -> steps
        RunCeilingAxis.TOKENS -> tokens
        RunCeilingAxis.MONEY -> money
    }

    /** Shared soft-threshold arithmetic, so every axis derives it the same way. */
    companion object {
        /**
         * Derives the soft threshold that belongs to a hard ceiling.
         *
         * Kept as arithmetic rather than a second setting on purpose: doubling
         * every ceiling into a soft/hard pair would double a settings surface
         * whose whole difficulty is already that it has three axes and two
         * origins. The user picks the number that stops the run; the warning
         * arrives at a fixed fraction of it.
         *
         * @param hard The hard ceiling.
         * @return The soft threshold, never above [hard] and never below 1.
         */
        fun softFor(hard: Int): Int =
            (hard.toLong() * SOFT_THRESHOLD_PERCENT / PERCENT).toInt().coerceIn(1, maxOf(1, hard))

        /** Fraction of the hard ceiling at which the soft warning fires. */
        const val SOFT_THRESHOLD_PERCENT: Int = 75

        /** Denominator for [SOFT_THRESHOLD_PERCENT]. */
        private const val PERCENT: Int = 100
    }
}
