package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.domain.repositories.TriggerRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user-edited fields of a [Trigger], without the runtime lifecycle state
 * ([Trigger.armed] / [Trigger.lastFiredAt] / [Trigger.createdAt]). Those are
 * **derived** by [SaveTriggerUseCase], never set by the UI, so the edge-latch
 * and debounce invariants live in one place.
 *
 * @property id `null` to create a new trigger, or the id of the trigger being
 *   edited.
 * @property name display name.
 * @property condition the activation condition.
 * @property pipelineId the bound pipeline id, or `null` (inert).
 * @property prompt the input message handed to the pipeline on fire.
 * @property enabled the on/off flag.
 */
data class TriggerDraftIntent(
    val id: String?,
    val name: String,
    val condition: TriggerCondition,
    val pipelineId: String?,
    val prompt: String,
    val enabled: Boolean,
)

/**
 * Persists a created or edited [Trigger], deriving its runtime lifecycle fields
 * so the presentation layer never has to.
 *
 * The edge-latch ([Trigger.armed]) and the fire timestamp ([Trigger.lastFiredAt])
 * are meaningful only for the specific condition that set them, so changing the
 * condition during an edit must reset them — otherwise a disarmed event trigger
 * silently never fires after being reconfigured, and a stale fire time anchors
 * the new condition's debounce. Centralising the rule here means every save path
 * (the editor, a future import or duplicate action) inherits it for free.
 *
 * Lifecycle derivation:
 * - **Create** (`id == null`): stamp [Trigger.createdAt] now, arm it, and clear
 *   the fire time.
 * - **Edit, condition unchanged**: preserve the existing `createdAt`, `armed`
 *   and `lastFiredAt` so an in-flight edge latch / debounce window survives a
 *   rename or pipeline re-bind.
 * - **Edit, condition changed**: preserve `createdAt`, but **re-arm** and clear
 *   the fire time so the new condition starts fresh.
 *
 * The bound [Trigger.sessionId] is runtime-derived (set lazily on the first
 * fire) and is **preserved across every edit**, including a condition change:
 * reconfiguring a trigger keeps its accumulated result conversation rather than
 * orphaning it.
 */
@Singleton
class SaveTriggerUseCase @Inject constructor(private val triggerRepository: TriggerRepository) {

    /**
     * Derives the lifecycle fields for [intent], persists the resulting trigger,
     * and returns it.
     *
     * @param intent the user-edited trigger fields.
     * @param nowMillis creation timestamp for a new trigger (epoch-millis);
     *   defaults to the wall clock and is overridable for tests.
     * @return the persisted [Trigger] with its derived lifecycle fields.
     */
    suspend operator fun invoke(intent: TriggerDraftIntent, nowMillis: Long = System.currentTimeMillis()): Trigger {
        val existing = intent.id?.let { triggerRepository.getTriggerById(it) }
        val trigger = if (existing == null || existing.condition != intent.condition) {
            // Create, or an edit that changed the condition: start the edge latch
            // and debounce window fresh. A new trigger is stamped with `nowMillis`;
            // an edited one keeps its original creation time.
            intent.toTrigger(
                id = intent.id ?: UUID.randomUUID().toString(),
                armed = true,
                createdAt = existing?.createdAt ?: nowMillis,
                lastFiredAt = null,
                sessionId = existing?.sessionId,
            )
        } else {
            // Edit that left the condition untouched: preserve the in-flight latch
            // and debounce so a rename / pipeline re-bind doesn't reset firing.
            intent.toTrigger(
                id = existing.id,
                armed = existing.armed,
                createdAt = existing.createdAt,
                lastFiredAt = existing.lastFiredAt,
                sessionId = existing.sessionId,
            )
        }
        triggerRepository.saveTrigger(trigger)
        return trigger
    }

    /** Builds a [Trigger] from the intent's user fields plus derived lifecycle values. */
    private fun TriggerDraftIntent.toTrigger(
        id: String,
        armed: Boolean,
        createdAt: Long,
        lastFiredAt: Long?,
        sessionId: String?,
    ): Trigger = Trigger(
        id = id,
        name = name,
        condition = condition,
        pipelineId = pipelineId,
        prompt = prompt,
        enabled = enabled,
        armed = armed,
        createdAt = createdAt,
        lastFiredAt = lastFiredAt,
        sessionId = sessionId,
    )
}
