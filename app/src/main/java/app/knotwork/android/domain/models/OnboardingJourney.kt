package app.knotwork.android.domain.models

/**
 * One write-once marker on the install → first-value path measured by the
 * repeatable onboarding metric.
 *
 * Each constant is recorded **at most once per install**: the store writes it
 * with an `INSERT OR IGNORE`, so re-entering onboarding (or running a second
 * scenario) never moves an already-recorded marker. That write-once contract is
 * what makes the measurement reproducible — the numbers always describe the
 * *first* journey through the funnel, not the latest one.
 *
 * The constant names are **persisted** as primary keys of the
 * `onboarding_milestone` table, so they must never be renamed.
 */
enum class OnboardingMilestone {

    /** The onboarding flow was opened for the first time (start of the metric). */
    ONBOARDING_STARTED,

    /**
     * A scenario was materialised from the value gallery. The marker carries the
     * id of the pipeline it created, which scopes the [FIRST_VALUE] attribution.
     */
    SCENARIO_CHOSEN,

    /** A model download was started from the onboarding download step. */
    MODEL_DOWNLOAD_STARTED,

    /** The onboarding model download completed successfully. */
    MODEL_DOWNLOAD_FINISHED,

    /**
     * The first pipeline run reached `COMPLETED` — "first value" in the sense of
     * `VISION §7.2`. Attribution rules live in
     * [OnboardingJourney.acceptsFirstValueFrom].
     */
    FIRST_VALUE,
}

/**
 * The recorded install → first-value journey, aggregated from the write-once
 * [OnboardingMilestone] markers.
 *
 * Backs the repeatable measurement of the "< 10 minutes to first value" metric.
 * The headline figure ([totalToValueMillis]) is dominated by the model download,
 * which is a network property rather than a product one, so the journey also
 * exposes the download interval ([modelDownloadMillis]) and the download-free
 * remainder ([productToValueMillis]) — the latter is the product criterion, the
 * former is judged separately as a download-UX checklist.
 *
 * Every value is derived from local, on-device markers; nothing here is ever
 * transmitted.
 *
 * @property milestones Recorded markers with their device wall-clock epoch-millis.
 *   A missing key means the marker was never recorded (e.g. no download happened
 *   because the model was already installed).
 * @property scenarioPipelineId Id of the pipeline materialised by the chosen
 *   onboarding scenario, or `null` when the user never set one up (they skipped
 *   the gallery or started from scratch).
 */
data class OnboardingJourney(val milestones: Map<OnboardingMilestone, Long>, val scenarioPipelineId: String?) {

    /** Epoch-millis the onboarding flow was first opened, or `null` if never. */
    val startedAtMillis: Long? get() = milestones[OnboardingMilestone.ONBOARDING_STARTED]

    /** Epoch-millis the first successful run finished, or `null` if not reached yet. */
    val firstValueAtMillis: Long? get() = milestones[OnboardingMilestone.FIRST_VALUE]

    /**
     * Wall-clock milliseconds from opening onboarding to first value — the full
     * `§7.2` figure, model download included. `null` until both ends are
     * recorded. Clamped at zero so a device clock adjustment mid-journey can
     * never produce a negative duration.
     */
    val totalToValueMillis: Long?
        get() = span(OnboardingMilestone.ONBOARDING_STARTED, OnboardingMilestone.FIRST_VALUE)

    /**
     * Milliseconds spent downloading the model during onboarding, or `null` when
     * no download happened on this journey (the picked model was already
     * installed) — in which case [productToValueMillis] equals
     * [totalToValueMillis].
     */
    val modelDownloadMillis: Long?
        get() = span(OnboardingMilestone.MODEL_DOWNLOAD_STARTED, OnboardingMilestone.MODEL_DOWNLOAD_FINISHED)

    /**
     * Time to first value **excluding** the model download — the product-owned
     * half of the metric, insulated from the user's network speed. `null` until
     * [totalToValueMillis] is known.
     */
    val productToValueMillis: Long?
        get() = totalToValueMillis?.let { total -> (total - (modelDownloadMillis ?: 0L)).coerceAtLeast(0L) }

    /** Whether nothing has been recorded yet (drives the empty state). */
    val isEmpty: Boolean get() = milestones.isEmpty()

    /**
     * Whether a `COMPLETED` run of [pipelineId] should be recorded as this
     * journey's [OnboardingMilestone.FIRST_VALUE].
     *
     * Three rules, in order:
     *  - onboarding must have started (there is no journey to measure otherwise);
     *  - first value must not be recorded yet (the marker is write-once, so a
     *    later run never overwrites the measured one);
     *  - when a scenario was set up, only *its* pipeline counts — a run of some
     *    other pipeline is not the value the scenario promised. When no scenario
     *    was set up (skip / start from scratch), the first successful run of any
     *    pipeline counts, so the metric still has an end point.
     *
     * @param pipelineId Id of the pipeline whose run just completed.
     * @return `true` when the run terminates this journey's measurement.
     */
    fun acceptsFirstValueFrom(pipelineId: String): Boolean {
        if (startedAtMillis == null) return false
        if (firstValueAtMillis != null) return false
        return scenarioPipelineId == null || scenarioPipelineId == pipelineId
    }

    /** Non-negative millisecond span between two recorded markers, or `null`. */
    private fun span(from: OnboardingMilestone, to: OnboardingMilestone): Long? {
        val start = milestones[from] ?: return null
        val end = milestones[to] ?: return null
        return (end - start).coerceAtLeast(0L)
    }

    /** Constants for [OnboardingJourney]. */
    companion object {
        /** The empty journey: no marker recorded yet (or statistics just reset). */
        val EMPTY = OnboardingJourney(milestones = emptyMap(), scenarioPipelineId = null)
    }
}
