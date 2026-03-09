package ai.agent.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ai.agent.android.data.local.models.LocalModelEntity
import ai.agent.android.data.local.dao.LocalModelDao

/**
 * Main Room Database for the Android AI Agent.
 * 
 * Future entities (e.g., ChatHistory, PromptTemplates) will be registered here.
 */
@Database(
    entities = [LocalModelEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    /**
     * Provides access to the LocalModelDao.
     * 
     * @return The [LocalModelDao] instance.
     */
    abstract fun localModelDao(): LocalModelDao
}
