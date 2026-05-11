package ai.agent.android.data.mappers

import ai.agent.android.data.local.models.LocalModelEntity
import ai.agent.android.domain.models.LocalModel

/**
 * Maps a [LocalModelEntity] data transfer object to a [LocalModel] domain model.
 *
 * @return The mapped [LocalModel].
 */
fun LocalModelEntity.toDomain(): LocalModel = LocalModel(
    id = this.id,
    name = this.name,
    path = this.path,
    size = this.size,
    isActive = this.isActive,
)

/**
 * Maps a [LocalModel] domain model to a [LocalModelEntity] data transfer object.
 *
 * @return The mapped [LocalModelEntity].
 */
fun LocalModel.toEntity(): LocalModelEntity = LocalModelEntity(
    id = this.id,
    name = this.name,
    path = this.path,
    size = this.size,
    isActive = this.isActive,
)
