package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.TriggerRepository
import app.knotwork.android.domain.services.TriggerScheduler
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconciles the background trigger watches with the persisted active-trigger
 * set.
 *
 * Reads the current [TriggerRepository.observeActiveTriggers] snapshot (enabled
 * and bound triggers) and hands it to [TriggerScheduler.syncAll], which
 * (re)registers each active trigger's watch. Idempotent by construction (the
 * scheduler uses the replace policy keyed by trigger id), so it is safe to call
 * on every cold start — the established pattern for this app's background
 * schedulers, and currently the only entry point.
 *
 * **Scope note.** This task ships the cold-start re-registration only. Reacting
 * to a trigger created/edited/enabled/deleted *while the app is running* (an
 * immediate [TriggerScheduler.register] / [TriggerScheduler.cancel] from the
 * mutation path) lands with the trigger-management UI; until then a runtime
 * mutation takes effect on the next cold start, and a removed trigger's watch is
 * reclaimed by the self-cancelling worker on its next wake.
 */
@Singleton
class SyncTriggersUseCase @Inject constructor(
    private val triggerRepository: TriggerRepository,
    private val triggerScheduler: TriggerScheduler,
) {

    /**
     * Performs one reconciliation pass against the current active-trigger set.
     */
    suspend operator fun invoke() {
        val active = triggerRepository.observeActiveTriggers().first()
        triggerScheduler.syncAll(active)
    }
}
