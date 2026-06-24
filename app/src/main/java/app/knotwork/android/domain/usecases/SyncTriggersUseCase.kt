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
 * registers each and cancels any stale watch. Idempotent by construction (the
 * scheduler uses replace/cancel semantics keyed by trigger id), so it is safe to
 * call on every cold start — the established pattern for this app's background
 * schedulers — and again after any trigger create / edit / enable / delete.
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
