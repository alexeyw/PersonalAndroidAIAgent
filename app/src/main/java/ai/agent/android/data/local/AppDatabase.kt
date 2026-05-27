package ai.agent.android.data.local

import ai.agent.android.data.local.dao.ChatDao
import ai.agent.android.data.local.dao.LocalModelDao
import ai.agent.android.data.local.dao.MemoryDao
import ai.agent.android.data.local.dao.PipelineDao
import ai.agent.android.data.local.dao.PipelinePresetDao
import ai.agent.android.data.local.dao.PromptTemplateDao
import ai.agent.android.data.local.dao.TraceStepDao
import ai.agent.android.data.local.models.ChatMessageEntity
import ai.agent.android.data.local.models.ChatSessionEntity
import ai.agent.android.data.local.models.ConnectionEntity
import ai.agent.android.data.local.models.LocalModelEntity
import ai.agent.android.data.local.models.MemoryChunkEntity
import ai.agent.android.data.local.models.NodeEntity
import ai.agent.android.data.local.models.PipelineEntity
import ai.agent.android.data.local.models.PipelinePresetEntity
import ai.agent.android.data.local.models.PromptTemplateEntity
import ai.agent.android.data.local.models.TraceStepEntity
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        PromptTemplateEntity::class,
        TraceStepEntity::class,
        PipelinePresetEntity::class,
    ],
    version = 24,
    exportSchema = true,
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

    /**
     * Provides access to the TraceStepDao.
     *
     * @return The [TraceStepDao] instance.
     */
    abstract fun traceStepDao(): TraceStepDao

    /**
     * Provides access to the [PipelinePresetDao] backing the user-saved
     * pipeline-preset catalogue (Phase 24 / Task 1).
     *
     * @return The [PipelinePresetDao] instance.
     */
    abstract fun pipelinePresetDao(): PipelinePresetDao

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
                    """.trimIndent(),
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
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `prompt_templates_new` (`id`, `name`, `text`, `category`)
                    SELECT `id`, `name`, `text`, COALESCE(`category`, 'Default') FROM `prompt_templates`
                    """.trimIndent(),
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
                db.execSQL(
                    "UPDATE `prompt_templates` SET `category` = 'INTENT_ROUTER' " +
                        "WHERE `category` = 'Default' AND `name` = 'Classifier'",
                )
                db.execSQL(
                    "UPDATE `prompt_templates` SET `category` = 'DECOMPOSITION' " +
                        "WHERE `category` = 'Default' AND `name` = 'Decomposer'",
                )
                db.execSQL(
                    "UPDATE `prompt_templates` SET `category` = 'SUMMARY' " +
                        "WHERE `category` = 'Default' AND `name` = 'Summarizer'",
                )
                db.execSQL(
                    "UPDATE `prompt_templates` SET `category` = 'TOOL' " +
                        "WHERE `category` = 'Default' AND `name` = 'Tool Picker'",
                )

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

        /**
         * Migration from version 14 to 15.
         * Adds `trace_steps` table.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `trace_steps` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `nodeName` TEXT NOT NULL,
                        `outputText` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trace_steps_sessionId` ON `trace_steps` (`sessionId`)")
            }
        }

        /**
         * Migration from version 15 to 16.
         * Adds `durationMs` and `tokenCount` columns to `trace_steps` so that per-node
         * execution time and token usage can be persisted alongside the trace output.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trace_steps` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `trace_steps` ADD COLUMN `tokenCount` INTEGER")
            }
        }

        /**
         * Migration from version 16 to 17.
         * Adds `clarificationTimeoutMs` column to `pipeline_nodes` for the new
         * CLARIFICATION node type, which suspends the pipeline until the user replies
         * (or the configured timeout elapses).
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pipeline_nodes` ADD COLUMN `clarificationTimeoutMs` INTEGER")
            }
        }

        /**
         * Migration from version 17 to 18.
         *
         * Adds the `context_config` column to `pipeline_nodes` that stores the
         * per-node [ai.agent.android.domain.models.NodeContextConfig] as a JSON
         * blob. The default value enables every flag (`chatHistory`,
         * `originalTask`, `nodeInput`, `longTermMemory`, `toolResults`) so that
         * existing pipelines keep their pre-Phase-15 behaviour: every node
         * receives the full context it used to receive.
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `pipeline_nodes` ADD COLUMN `context_config` TEXT NOT NULL " +
                        "DEFAULT '{\"chatHistory\":true,\"originalTask\":true,\"nodeInput\":true," +
                        "\"longTermMemory\":true,\"toolResults\":true}'",
                )
            }
        }

        /**
         * Migration from version 18 to 19 (Phase 17.2 — Pipeline binding to chat).
         *
         * Adds the nullable `pipelineId` column to `chat_sessions`. `NULL` means the
         * chat uses the application-wide default pipeline (the first pipeline
         * returned by `PipelineRepository.getAllPipelines()`), preserving the
         * pre-Phase-17.2 behaviour for every existing row without requiring a
         * data backfill.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chat_sessions` ADD COLUMN `pipelineId` TEXT")
            }
        }

        /**
         * Adds the `isFinal` and `isStarred` columns to `chat_messages` (Phase 17.3).
         *
         * - `isFinal` — distinguishes user-facing messages (USER input, final AGENT
         *   answers) from intermediate node outputs (tool observations, internal
         *   SYSTEM logs). Backfilled to `1` so every pre-existing message keeps
         *   rendering in the main chat list.
         * - `isStarred` — backs the new "save message" action; defaults to `0`
         *   for all legacy rows.
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `chat_messages` ADD COLUMN `isFinal` INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE `chat_messages` ADD COLUMN `isStarred` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Migration from version 20 to 21 (Phase 21 / Task 9 — Pipeline editor).
         *
         * Adds the nullable `config_json` column to `pipeline_nodes`. The column
         * stores the per-type `NodeConfig` payload edited by the new
         * `NodeConfigSheet` (catalog `pipelineeditor.NodeConfig`) as a JSON blob
         * — schema in `project_docs/design/compose/components/node-specs.md`.
         *
         * `NULL` is the canonical "no payload yet" value for every row created
         * before Phase 21; on first edit the editor derives a default config
         * from the flat columns (`systemPrompt`, `cloudProvider`, `toolName`,
         * `conditionComplexity`, …) and writes the encoded payload here. The
         * flat columns are kept untouched so the orchestrator runtime path
         * (which still reads them) keeps working unchanged.
         */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pipeline_nodes` ADD COLUMN `config_json` TEXT")
            }
        }

        /**
         * Migration from version 21 to 22 (Phase 22 / Task 4 — Chat home
         * secondary affordances).
         *
         * Adds the `isStarred` column to `chat_sessions` so the drawer can
         * surface favorited chats at the top of the list. Backfilled to `0`
         * for every existing row — the legacy chat surface had no
         * session-level favorite affordance, so no historical data needs to
         * be carried over.
         *
         * Distinct from the message-level `isStarred` introduced in
         * `MIGRATION_19_20` on `chat_messages`.
         */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `chat_sessions` ADD COLUMN `isStarred` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Migration from version 22 to 23 (Phase 22 / Task 6 — Memory edit +
         * pin persistence).
         *
         * Adds the `isPinned` column to `memory_chunks` so users can mark a
         * memory chunk as pinned. Pinned rows sort ahead of unpinned rows on
         * the memory surface and are exempt from future `compactMemory()`
         * passes. Backfilled to `0` for every existing row — before this
         * migration the schema had no notion of pinning, so no historical
         * data needs to be carried over.
         */
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `memory_chunks` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Migration from version 23 to 24 (Phase 24 / Task 1 — Pipeline
         * presets).
         *
         * Adds the `pipeline_presets` table backing the user-saved
         * preset catalogue. Bundled presets live in
         * `assets/presets/pipelines/*.json` and never reach this table.
         *
         * The graph is stored as a JSON blob produced by
         * `PipelinePresetJsonSerializer.serialize(...)` so preset rows
         * are self-contained and the existing pipeline schema can evolve
         * independently of stored presets.
         */
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pipeline_presets` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `categoryKey` TEXT NOT NULL,
                        `graphJson` TEXT NOT NULL,
                        `tagsCsv` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
