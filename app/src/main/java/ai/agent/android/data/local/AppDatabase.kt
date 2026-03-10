package ai.agent.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ai.agent.android.data.local.models.LocalModelEntity
import ai.agent.android.data.local.dao.LocalModelDao
import ai.agent.android.data.local.models.ChatMessageEntity
import ai.agent.android.data.local.dao.ChatDao

/**
 * Main Room Database for the Android AI Agent.
 * 
 * Future entities (e.g., PromptTemplates) will be registered here.
 */
@Database(
    entities = [LocalModelEntity::class, ChatMessageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    /**
     * Provides access to the LocalModelDao.
     * 
     * @return The [LocalModelDao] instance.
     */
    abstract fun localModelDao(): LocalModelDao

    /**
     * Provides access to the ChatDao.
     *
     * @return The [ChatDao] instance.
     */
    abstract fun chatDao(): ChatDao
}
