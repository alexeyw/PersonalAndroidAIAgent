package ai.agent.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ai.agent.android.data.local.models.LocalModelEntity
import ai.agent.android.data.local.dao.LocalModelDao
import ai.agent.android.data.local.models.ChatMessageEntity
import ai.agent.android.data.local.models.ChatSessionEntity
import ai.agent.android.data.local.dao.ChatDao
import ai.agent.android.data.local.models.MemoryChunkEntity
import ai.agent.android.data.local.dao.MemoryDao
import ai.agent.android.data.local.models.PipelineEntity
import ai.agent.android.data.local.models.NodeEntity
import ai.agent.android.data.local.models.ConnectionEntity
import ai.agent.android.data.local.dao.PipelineDao
import ai.agent.android.data.local.models.PromptTemplateEntity
import ai.agent.android.data.local.dao.PromptTemplateDao

/**
 * Main Room Database for the Android AI Agent.
 * 
 * Future entities (e.g., PromptTemplates) will be registered here.
 */
@Database(
    entities = [
        LocalModelEntity::class, 
        ChatMessageEntity::class,
        ChatSessionEntity::class,
        MemoryChunkEntity::class,
        PipelineEntity::class,
        NodeEntity::class,
        ConnectionEntity::class,
        PromptTemplateEntity::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
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

    /**
     * Provides access to the MemoryDao.
     *
     * @return The [MemoryDao] instance.
     */
    abstract fun memoryDao(): MemoryDao

    /**
     * Provides access to the PipelineDao.
     *
     * @return The [PipelineDao] instance.
     */
    abstract fun pipelineDao(): PipelineDao

    /**
     * Provides access to the PromptTemplateDao.
     *
     * @return The [PromptTemplateDao] instance.
     */
    abstract fun promptTemplateDao(): PromptTemplateDao

    companion object {
        /**
         * Migration from version 9 to 10.
         * Adds the `prompt_templates` table.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `prompt_templates` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `text` TEXT NOT NULL, 
                        `category` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
