package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.Trigger
import kotlinx.coroutines.flow.Flow

/**
 * Repository for user-defined automation [Trigger]s.
 *
 * The interface lives in `domain` (implemented in `data` by
 * `TriggerRepositoryImpl`) so the trigger use cases and the background runtime
 * depend only on this port, never on Room. Reads are reactive `Flow`s (the
 * trigger list and the active-trigger subset drive both the UI and the
 * scheduler sync); writes are `suspend` functions.
 */
interface TriggerRepository {

    /**
     * Observes every trigger, newest first. Backs the management UI.
     *
     * @return A hot [Flow] emitting the full trigger list on every change.
     */
    fun observeTriggers(): Flow<List<Trigger>>

    /**
     * Observes the triggers eligible to run in the background — those that are
     * [enabled][Trigger.enabled] **and** bound to a pipeline
     * ([Trigger.isActive]). Backs the scheduler sync, which registers exactly
     * this set with the background runtime.
     *
     * @return A hot [Flow] emitting the active-trigger subset on every change.
     */
    fun observeActiveTriggers(): Flow<List<Trigger>>

    /**
     * Loads a single trigger by id.
     *
     * @param id The trigger id.
     * @return The trigger, or `null` if no row matches (e.g. deleted between a
     *   background wakeup and its handling).
     */
    suspend fun getTriggerById(id: String): Trigger?

    /**
     * Inserts or replaces a trigger (upsert by [Trigger.id]).
     *
     * @param trigger The trigger to persist.
     */
    suspend fun saveTrigger(trigger: Trigger)

    /**
     * Deletes a trigger by id. Idempotent: deleting a missing id is a no-op.
     *
     * @param id The trigger id to remove.
     */
    suspend fun deleteTrigger(id: String)

    /**
     * Flips a trigger's [enabled][Trigger.enabled] flag.
     *
     * @param id The trigger id.
     * @param enabled The new enabled state.
     */
    suspend fun setEnabled(id: String, enabled: Boolean)

    /**
     * Records the most recent fire time, backing the re-arm debounce.
     *
     * @param id The trigger id.
     * @param firedAt Epoch-millis of the fire.
     */
    suspend fun markFired(id: String, firedAt: Long)
}
