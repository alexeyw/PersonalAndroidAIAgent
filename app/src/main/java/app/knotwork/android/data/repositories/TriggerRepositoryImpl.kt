package app.knotwork.android.data.repositories

import app.knotwork.android.data.local.dao.TriggerDao
import app.knotwork.android.data.local.models.TriggerEntity
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.repositories.TriggerRepository
import app.knotwork.android.domain.triggerio.TriggerConditionCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [TriggerRepository].
 *
 * Maps [TriggerEntity] ↔ [Trigger], routing the condition through
 * [TriggerConditionCodec]. A row whose stored condition cannot be decoded (an
 * unknown future type, or a corrupt value) is **skipped on read** rather than
 * crashing the load — mirroring how the preset repositories drop malformed rows
 * — so one bad trigger never hides the rest.
 */
@Singleton
class TriggerRepositoryImpl @Inject constructor(private val dao: TriggerDao) : TriggerRepository {

    override fun observeTriggers(): Flow<List<Trigger>> =
        dao.getAll().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override fun observeActiveTriggers(): Flow<List<Trigger>> =
        dao.getActive().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override suspend fun getTriggerById(id: String): Trigger? = dao.getById(id)?.toDomainOrNull()

    override suspend fun saveTrigger(trigger: Trigger) = dao.upsert(trigger.toEntity())

    override suspend fun deleteTrigger(id: String) = dao.deleteById(id)

    override suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled)

    override suspend fun setArmed(id: String, armed: Boolean) = dao.setArmed(id, armed)

    override suspend fun markFired(id: String, firedAt: Long) = dao.markFired(id, firedAt)

    /** Maps a domain [Trigger] to its persisted row, encoding the condition. */
    private fun Trigger.toEntity(): TriggerEntity = TriggerEntity(
        id = id,
        name = name,
        pipelineId = pipelineId,
        prompt = prompt,
        conditionJson = TriggerConditionCodec.encode(condition),
        enabled = enabled,
        armed = armed,
        createdAt = createdAt,
        lastFiredAt = lastFiredAt,
    )

    /**
     * Maps a row to its domain model, or `null` when the stored condition is
     * undecodable (the caller drops such rows).
     */
    private fun TriggerEntity.toDomainOrNull(): Trigger? {
        val condition = TriggerConditionCodec.decode(conditionJson)
        if (condition == null) {
            Timber.w("Skipping trigger %s: undecodable condition '%s'.", id, conditionJson)
            return null
        }
        return Trigger(
            id = id,
            name = name,
            condition = condition,
            pipelineId = pipelineId,
            prompt = prompt,
            enabled = enabled,
            armed = armed,
            createdAt = createdAt,
            lastFiredAt = lastFiredAt,
        )
    }
}
