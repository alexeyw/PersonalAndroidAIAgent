package app.knotwork.android.domain.services

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped, in-memory tracker of the cosine-similarity scores observed
 * across recent memory similarity searches. Powers the AVG SCORE stat cell
 * in Settings → Memory.
 *
 * The tracker is deliberately a standalone domain service rather than state
 * inside `MemoryRepositoryImpl`: the repository's `observeStats` stream
 * reports only persistent table-level figures, while this volatile,
 * session-scoped rolling window is recorded by the search call sites
 * ([app.knotwork.android.domain.usecases.RetrieveRelevantMemoryUseCase] and
 * the extraction dedup check) and observed directly by the Settings
 * ViewModel.
 *
 * Semantics (unchanged from the previous repository-internal implementation):
 * each search contributes its strongest [STATS_SAMPLE_SIZE] scores, and the
 * average is computed over the last [RECENT_SIMILARITY_WINDOW] observations,
 * so the value reflects recent behaviour rather than the entire app lifetime.
 */
@Singleton
class MemorySearchStatsTracker @Inject constructor() {

    private val recentScores = MutableStateFlow<List<Float>>(emptyList())

    /**
     * Rolling average of the recorded similarity scores, or `null` until at
     * least one similarity search has been recorded (the UI then renders a
     * dash). Derived live from the rolling window, so every [record] and
     * [reset] is reflected on the next emission.
     */
    val averageScore: Flow<Float?> = recentScores.map { scores ->
        scores.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    }

    /**
     * Records the outcome of one similarity-search call.
     *
     * Only the head of the ranked list is sampled (not the whole scored set):
     * retrieval asks for the full scored pool to feed re-ranking, and folding
     * hundreds of low-similarity tail scores into the window would drag the
     * AVG SCORE cell toward zero.
     *
     * @param rankedScores Similarity scores of the search hits, ordered
     *   best-first (the order `findSimilarMemories` returns). An empty list
     *   is a no-op.
     */
    fun record(rankedScores: List<Float>) {
        val newScores = rankedScores.take(STATS_SAMPLE_SIZE)
        if (newScores.isEmpty()) return
        recentScores.update { previous ->
            (previous + newScores).takeLast(RECENT_SIMILARITY_WINDOW)
        }
    }

    /**
     * Clears the rolling window, e.g. after the user wipes all memories —
     * scores observed against a now-deleted corpus are no longer
     * representative. The AVG SCORE cell reverts to a dash.
     */
    fun reset() {
        recentScores.value = emptyList()
    }

    private companion object {
        /** Number of recent cosine-similarity scores tracked for the AVG SCORE stat cell. */
        const val RECENT_SIMILARITY_WINDOW: Int = 32

        /**
         * Per-call cap on how many of the ranked scores feed the AVG SCORE
         * window. Keeps the stat anchored to the strongest hits even when a
         * caller requests a large pool for re-ranking.
         */
        const val STATS_SAMPLE_SIZE: Int = 5
    }
}
