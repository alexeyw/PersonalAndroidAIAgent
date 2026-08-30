package app.knotwork.android.buildtools

/**
 * The ratchet that keeps documentation gaps in the file maps from accumulating.
 *
 * Two numbers are tracked per key: how many map entries carry the
 * "no description" marker, and how many Kotlin files offer the generator no
 * usable KDoc sentence to seed one from. Both are allowed to fall and not to
 * rise.
 *
 * **Why a ratchet rather than a floor of zero.** Requiring every file to be
 * described would have made this change a 550-line writing exercise before the
 * gate could be switched on, and a gate that cannot be switched on is not a
 * gate. The ratchet turns the backlog into a number that only moves one way.
 *
 * **Why generation lowers it and never raises it.** [lowered] takes the minimum
 * of the measured and recorded values, so an improvement is recorded by simply
 * re-running the generator, while a regression leaves the recorded number
 * untouched and the verify task fails. Raising a number is therefore always a
 * deliberate, reviewable edit to the committed file — never a side effect of
 * running a build task.
 */
object FileMapBaseline {

    /** Thrown when the baseline file cannot be read. */
    class ParseException(message: String) : RuntimeException(message)

    /**
     * Reads the recorded counts.
     *
     * @param text Contents of the baseline file. Blank lines and `#` comments
     *   are ignored.
     * @return Recorded count by key.
     * @throws ParseException when a non-comment line is not `key=<integer>`.
     */
    fun parse(text: String): Map<String, Int> {
        val result = linkedMapOf<String, String>()
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val separator = line.indexOf('=')
            if (separator <= 0) throw ParseException("Baseline line is not `key=value`: `$line`.")
            result[line.substring(0, separator).trim()] = line.substring(separator + 1).trim()
        }
        return result.mapValues { (key, value) ->
            value.toIntOrNull() ?: throw ParseException("Baseline value for `$key` is not a whole number: `$value`.")
        }
    }

    /**
     * Renders the baseline file.
     *
     * @param counts Recorded count by key.
     * @return The file contents, keys in sorted order so the file has one form.
     */
    fun render(counts: Map<String, Int>): String = buildString {
        append(HEADER)
        for ((key, value) in counts.toSortedMap()) {
            append(key).append('=').append(value).append('\n')
        }
    }

    /**
     * Keys where the measured count exceeds the recorded one.
     *
     * A key measured but absent from the baseline counts as a violation when it
     * is non-zero: a new map may not arrive with gaps already in it.
     *
     * @param measured Counts measured on this run.
     * @param baseline Counts recorded in the committed file.
     * @return Human-readable descriptions of each regression, in key order.
     */
    fun violations(measured: Map<String, Int>, baseline: Map<String, Int>): List<String> =
        measured.toSortedMap()
            .filter { (key, count) -> count > (baseline[key] ?: 0) }
            .map { (key, count) -> "$key: $count, recorded ${baseline[key] ?: 0}" }

    /**
     * The baseline after an improvement, never after a regression.
     *
     * @param measured Counts measured on this run.
     * @param baseline Counts recorded in the committed file.
     * @return Every key measured, each at the lower of the two values. Keys that
     *   disappeared from the measurement are dropped: the map they described is
     *   gone.
     */
    fun lowered(measured: Map<String, Int>, baseline: Map<String, Int>): Map<String, Int> =
        measured.mapValues { (key, count) -> minOf(count, baseline[key] ?: count) }

    private const val HEADER: String =
        "# Documentation ratchet for the generated FILE_MAP.md blocks.\n" +
            "#\n" +
            "# `<block>.undescribed` — entries rendered with the \"no description\" marker.\n" +
            "# `<block>.no-kdoc-seed` — Kotlin files offering no unambiguous KDoc sentence to\n" +
            "#                        seed a description from. A superset of \"has no KDoc\":\n" +
            "#                        a file documenting several unrelated declarations counts too.\n" +
            "#\n" +
            "# `./gradlew :app:generateFileMap` lowers these numbers and never raises one.\n" +
            "# Raising a number is a deliberate edit here, reviewed like any other change.\n" +
            "# `:app:verifyFileMap` fails when a measured count exceeds its recorded value.\n"
}
