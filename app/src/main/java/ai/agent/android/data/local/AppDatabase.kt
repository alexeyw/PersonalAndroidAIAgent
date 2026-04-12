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
    version = 13,
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

        /**
         * Migration from version 10 to 11.
         * Updates the `prompt_templates` table to make `category` NOT NULL.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `prompt_templates_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `text` TEXT NOT NULL, 
                        `category` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `prompt_templates_new` (`id`, `name`, `text`, `category`)
                    SELECT `id`, `name`, `text`, COALESCE(`category`, 'Default') FROM `prompt_templates`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `prompt_templates`")
                db.execSQL("ALTER TABLE `prompt_templates_new` RENAME TO `prompt_templates`")
            }
        }

        /**
         * Migration from version 11 to 12.
         * Updates previously created default prompts that had the 'Default' category to their correct NodeType.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE `prompt_templates` SET `category` = 'INTENT_ROUTER' WHERE `category` = 'Default' AND `name` = 'Classifier'")
                db.execSQL("UPDATE `prompt_templates` SET `category` = 'DECOMPOSITION' WHERE `category` = 'Default' AND `name` = 'Decomposer'")
                db.execSQL("UPDATE `prompt_templates` SET `category` = 'SUMMARY' WHERE `category` = 'Default' AND `name` = 'Summarizer'")
                db.execSQL("UPDATE `prompt_templates` SET `category` = 'TOOL' WHERE `category` = 'Default' AND `name` = 'Tool Picker'")
                
                // For any other prompts that somehow ended up as 'Default', reassign them to CUSTOM or something safe, or leave them. 
                // We'll leave the rest as is, but users can edit them.
            }
        }

        /**
         * Migration from version 12 to 13.
         * Adds `modelPath` column to `pipeline_nodes` table.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pipeline_nodes` ADD COLUMN `modelPath` TEXT")
            }
        }

        /**
         * Migration from version 13 to 14.
         * Adds `cloudProvider` column to `pipeline_nodes` table.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pipeline_nodes` ADD COLUMN `cloudProvider` TEXT")
            }
        }
    }
}
