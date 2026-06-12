package app.knotwork.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.knotwork.android.data.local.dao.ChatDao
import app.knotwork.android.data.local.dao.LocalModelDao
import app.knotwork.android.data.local.dao.MemoryDao
import app.knotwork.android.data.local.dao.PendingInteractionDao
import app.knotwork.android.data.local.dao.PipelineDao
import app.knotwork.android.data.local.dao.PipelinePresetDao
import app.knotwork.android.data.local.dao.PipelineRunDao
import app.knotwork.android.data.local.dao.PromptPresetDao
import app.knotwork.android.data.local.dao.PromptTemplateDao
import app.knotwork.android.data.local.dao.TraceStepDao
import app.knotwork.android.data.local.models.ChatMessageEntity
import app.knotwork.android.data.local.models.ChatSessionEntity
import app.knotwork.android.data.local.models.ConnectionEntity
import app.knotwork.android.data.local.models.LocalModelEntity
import app.knotwork.android.data.local.models.MemoryChunkEntity
import app.knotwork.android.data.local.models.NodeEntity
import app.knotwork.android.data.local.models.PendingInteractionEntity
import app.knotwork.android.data.local.models.PipelineEntity
import app.knotwork.android.data.local.models.PipelinePresetEntity
import app.knotwork.android.data.local.models.PipelineRunEntity
import app.knotwork.android.data.local.models.PromptPresetEntity
import app.knotwork.android.data.local.models.PromptTemplateEntity
import app.knotwork.android.data.local.models.TraceStepEntity

/**
 * Main Room Database for the Android AI Agent.
 *
 * Future entities (e.g., PromptTemplates) will be registered here.
 *
 * **Versioning & migrations.** The current schema [version] is the durability baseline:
 * every bump from here on must add a matching `MIGRATION_<old>_<new>` constant below and
 * register it in [app.knotwork.android.di.AppModule] via `addMigrations(...)`. There is no
 * destructive fallback on upgrade, so an unsupplied migration fails fast in development rather
 * than silently dropping user data. The exported `app/schemas/<package>/<version>.json`
 * snapshots back the `MigrationTestHelper` regression suite.
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
        PromptPresetEntity::class,
        PipelineRunEntity::class,
        PendingInteractionEntity::class,
    ],
    version = 33,
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
     * pipeline-preset catalogue.
     *
     * @return The [PipelinePresetDao] instance.
     */
    abstract fun pipelinePresetDao(): PipelinePresetDao

    /**
     * Provides access to the [PromptPresetDao] backing the user-saved
     * prompt-preset catalogue.
     *
     * @return The [PromptPresetDao] instance.
     */
    abstract fun promptPresetDao(): PromptPresetDao

    /**
     * Provides access to the [PipelineRunDao] backing the persistent
     * pipeline-run records.
     *
     * @return The [PipelineRunDao] instance.
     */
    abstract fun pipelineRunDao(): PipelineRunDao

    /**
     * Provides access to the [PendingInteractionDao] backing the parked
     * HITL interaction records of the two-phase waiting protocol.
     *
     * @return The [PendingInteractionDao] instance.
     */
    abstract fun pendingInteractionDao(): PendingInteractionDao

    companion object {
        /**
         * Canonical on-disk file name of the encrypted Room database. Single
         * source of truth shared by the Hilt provider, the passphrase
         * provider's "does the database already exist" invariant check, and
         * the explicit wipe path — so the three can never drift apart.
         */
        const val DATABASE_NAME: String = "agent_database.db"

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
         * per-node [app.knotwork.android.domain.models.NodeContextConfig] as a JSON
         * blob. The default value enables every flag (`chatHistory`,
         * `originalTask`, `nodeInput`, `longTermMemory`, `toolResults`) so that
         * existing pipelines keep receiving the full context on every node.
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
         * Migration from version 18 to 19 — pipeline binding to chat.
         *
         * Adds the nullable `pipelineId` column to `chat_sessions`. `NULL` means the
         * chat uses the application-wide default pipeline (the user-marked
         * `SettingsRepository.defaultPipelineId`), preserving the prior
         * default for every existing row without requiring a data backfill.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chat_sessions` ADD COLUMN `pipelineId` TEXT")
            }
        }

        /**
         * Adds the `isFinal` and `isStarred` columns to `chat_messages`.
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
         * Migration from version 20 to 21 — pipeline editor.
         *
         * Adds the nullable `config_json` column to `pipeline_nodes`. The column
         * stores the per-type `NodeConfig` payload edited by the
         * `NodeConfigSheet` (catalog `pipelineeditor.NodeConfig`) as a JSON blob.
         *
         * `NULL` is the canonical "no payload yet" value for every pre-existing
         * row; on first edit the editor derives a default config
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
         * Migration from version 21 to 22 — chat-session favorites.
         *
         * Adds the `isStarred` column to `chat_sessions` so the drawer can
         * surface favorited chats at the top of the list. Backfilled to `0`
         * for every existing row.
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
         * Migration from version 22 to 23 — memory chunk pinning.
         *
         * Adds the `isPinned` column to `memory_chunks` so users can mark a
         * memory chunk as pinned. Pinned rows sort ahead of unpinned rows on
         * the memory surface and are exempt from future `compactMemory()`
         * passes. Backfilled to `0` for every existing row.
         */
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `memory_chunks` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Migration from version 23 to 24 — pipeline presets.
         *
         * Adds the `pipeline_presets` table backing the user-saved
         * preset catalogue. Bundled presets live in
         * `assets/presets/pipelines` and never reach this table.
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

        /**
         * Migration from version 24 to 25 — prompt presets.
         *
         * Adds the `prompt_presets` table backing the user-saved
         * prompt-preset catalogue. Bundled presets live in
         * `assets/presets/prompts` and never reach this table.
         */
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `prompt_presets` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `nodeTypeKey` TEXT NOT NULL,
                        `systemPrompt` TEXT NOT NULL,
                        `tagsCsv` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Migration from version 25 to 26 — memory chunk provenance.
         *
         * Adds the `source` column to `memory_chunks` recording each chunk's
         * provenance ([app.knotwork.android.domain.models.MemorySource]) as a
         * compact JSON string. Existing rows predate source attribution, so
         * they are backfilled to the `Unknown` encoding — identical to what
         * `Converters.fromMemorySource(MemorySource.Unknown)` produces and to
         * the entity's column default, keeping the Room-generated schema and
         * this migration in agreement.
         */
        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `memory_chunks` ADD COLUMN `source` TEXT NOT NULL " +
                        "DEFAULT '{\"type\":\"unknown\"}'",
                )
            }
        }

        /**
         * Migration from version 26 to 27.
         *
         * Adds three additive columns to `memory_chunks` for the redesigned
         * Memory surface: `tagsCsv` (comma-separated tag list, default empty),
         * `useCount` (retrieval counter, default `0`) and `lastUsedAt`
         * (nullable epoch-millis of the most recent retrieval). All defaults
         * match the entity column defaults so the Room-generated schema and
         * this migration agree; existing rows keep their data untouched.
         */
        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `memory_chunks` ADD COLUMN `tagsCsv` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `memory_chunks` ADD COLUMN `useCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `memory_chunks` ADD COLUMN `lastUsedAt` INTEGER")
            }
        }

        /**
         * Migration from version 27 to 28 — memory export/import.
         *
         * Adds the `needsReembedding` column to `memory_chunks`. Set to `1` on
         * chunks imported from a device whose active embedding provider differs
         * from the local one: their stored vectors live in an incompatible
         * space and are re-computed lazily on the next retrieval
         * ([app.knotwork.android.domain.usecases.RecomputePendingEmbeddingsUseCase]).
         * Backfilled to `0` for every existing row — locally-written chunks are
         * already in the active provider's space. The default matches the entity
         * column default so the Room-generated schema and this migration agree.
         */
        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `memory_chunks` ADD COLUMN `needsReembedding` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration from version 28 to 29 — binary embedding storage.
         *
         * Rebuilds `memory_chunks` so the `embedding` column changes type from
         * TEXT (comma-separated floats) to BLOB (little-endian IEEE-754 floats,
         * 4 bytes per component, no header — see
         * [app.knotwork.android.data.local.EmbeddingBlobCodec]). SQLite cannot
         * change a column type in place, so the migration creates the new
         * table, streams every row through a Kotlin-side string → binary
         * conversion, then drops the old table and renames the new one.
         *
         * A legacy embedding string that cannot be parsed (blank or carrying a
         * non-numeric component) converts to a **zero-length blob** rather than
         * dropping the row: such rows are already invisible to similarity
         * retrieval, but their `text` is intact and the re-embedding repair
         * path can still recompute a fresh vector from it — deleting them here
         * would silently destroy user data. The empty blob decodes to `null`
         * at the entity boundary, preserving the pre-migration semantics
         * exactly.
         */
        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memory_chunks_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `text` TEXT NOT NULL,
                        `embedding` BLOB NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `isPinned` INTEGER NOT NULL,
                        `source` TEXT NOT NULL DEFAULT '{"type":"unknown"}',
                        `tagsCsv` TEXT NOT NULL DEFAULT '',
                        `useCount` INTEGER NOT NULL DEFAULT 0,
                        `lastUsedAt` INTEGER,
                        `needsReembedding` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                db.query(
                    "SELECT `id`, `text`, `embedding`, `timestamp`, `isPinned`, `source`, " +
                        "`tagsCsv`, `useCount`, `lastUsedAt`, `needsReembedding` FROM `memory_chunks`",
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        db.execSQL(
                            "INSERT INTO `memory_chunks_new` (`id`, `text`, `embedding`, `timestamp`, " +
                                "`isPinned`, `source`, `tagsCsv`, `useCount`, `lastUsedAt`, `needsReembedding`) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(
                                cursor.getLong(0),
                                cursor.getString(1),
                                parseLegacyEmbedding(cursor.getString(2)),
                                cursor.getLong(3),
                                cursor.getLong(4),
                                cursor.getString(5),
                                cursor.getString(6),
                                cursor.getLong(7),
                                if (cursor.isNull(8)) null else cursor.getLong(8),
                                cursor.getLong(9),
                            ),
                        )
                    }
                }
                db.execSQL("DROP TABLE `memory_chunks`")
                db.execSQL("ALTER TABLE `memory_chunks_new` RENAME TO `memory_chunks`")
            }

            /**
             * Parses the legacy comma-separated embedding string into the
             * binary BLOB form. This is the only remaining home of the
             * pre-BLOB string codec — kept private to the migration so no
             * production read/write path can resurrect the text encoding.
             *
             * @param value The legacy column value.
             * @return The encoded bytes, or a zero-length array when [value]
             *   is blank or contains a non-numeric component.
             */
            private fun parseLegacyEmbedding(value: String?): ByteArray {
                if (value.isNullOrBlank()) return ByteArray(0)
                val parts = value.split(",")
                val floats = FloatArray(parts.size)
                for (index in parts.indices) {
                    floats[index] = parts[index].toFloatOrNull() ?: return ByteArray(0)
                }
                return EmbeddingBlobCodec.encode(floats)
            }
        }

        /**
         * Migration from version 29 to 30.
         * Adds the `pipeline_runs` table — the persistent record of pipeline
         * runs that survives process death (see `PipelineRunEntity`). Purely
         * additive: no existing rows are touched. `sessionId` deliberately
         * carries no foreign key (a run may be created before its session row
         * exists — scheduler-originated sessions are materialised on first
         * message save); the index pair backs the per-session queries and the
         * status-driven orphan sweep.
         */
        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pipeline_runs` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `pipelineId` TEXT,
                        `origin` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `currentNodeId` TEXT,
                        `startedAt` INTEGER NOT NULL,
                        `finishedAt` INTEGER,
                        `errorMessage` TEXT,
                        `graphContentHash` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pipeline_runs_sessionId` " +
                        "ON `pipeline_runs` (`sessionId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pipeline_runs_status` " +
                        "ON `pipeline_runs` (`status`)",
                )
            }
        }

        /**
         * Migration from version 30 to 31 — persistent run trace.
         *
         * Extends `trace_steps` from per-session node outputs into the full
         * per-run trace (see `TraceStepEntity`): `runId` (FK to `pipeline_runs`
         * with CASCADE delete, indexed), `seq` (monotonic in-run position used
         * by the console replay/live seam), `recordKind` (`NODE_IO` vs
         * `CONSOLE_EVENT` discriminator), `consoleEventType`, `nodeId` and
         * `inputText`.
         *
         * SQLite cannot add a foreign key to an existing table, so the
         * migration recreates it: new table, `INSERT … SELECT` copy, drop,
         * rename, then index recreation. Legacy rows are preserved with
         * `runId = NULL` (no run attribution existed before this version),
         * `seq = 0` and `recordKind = 'NODE_IO'` — every pre-31 row is a node
         * output by construction.
         */
        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `trace_steps_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `nodeName` TEXT NOT NULL,
                        `outputText` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `tokenCount` INTEGER,
                        `runId` TEXT,
                        `seq` INTEGER NOT NULL,
                        `recordKind` TEXT NOT NULL,
                        `consoleEventType` TEXT,
                        `nodeId` TEXT,
                        `inputText` TEXT,
                        FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`runId`) REFERENCES `pipeline_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `trace_steps_new` (
                        `id`, `sessionId`, `nodeName`, `outputText`, `timestamp`,
                        `durationMs`, `tokenCount`, `runId`, `seq`, `recordKind`,
                        `consoleEventType`, `nodeId`, `inputText`
                    )
                    SELECT
                        `id`, `sessionId`, `nodeName`, `outputText`, `timestamp`,
                        `durationMs`, `tokenCount`, NULL, 0, 'NODE_IO',
                        NULL, NULL, NULL
                    FROM `trace_steps`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `trace_steps`")
                db.execSQL("ALTER TABLE `trace_steps_new` RENAME TO `trace_steps`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trace_steps_sessionId` ON `trace_steps` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trace_steps_runId` ON `trace_steps` (`runId`)")
            }
        }

        /**
         * Migration from version 31 to 32 — checkpoint/resume support.
         *
         * Purely additive `ALTER TABLE … ADD COLUMN` statements; no existing
         * rows are rewritten:
         *
         * - `trace_steps.conditionResult` / `routingKey` / `resolvedToolName`
         *   — the recorded routing verdicts and tool attribution of `NODE_IO`
         *   rows, needed to restore IF_CONDITION / INTENT_ROUTER / EVALUATION
         *   branches and tool observations when an interrupted run is replayed
         *   from its persisted trace (`NULL` for legacy rows: resume of runs
         *   interrupted under the previous schema falls back gracefully —
         *   branch restoration just lacks the recorded verdicts, exactly as if
         *   the nodes had never recorded them).
         * - `pipeline_runs.userPrompt` — the user message that started the
         *   run, captured at enqueue time; resume feeds it back to the engine
         *   as the immutable original prompt. `NULL` for legacy rows, which
         *   are therefore not resumable.
         */
        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trace_steps` ADD COLUMN `conditionResult` INTEGER")
                db.execSQL("ALTER TABLE `trace_steps` ADD COLUMN `routingKey` TEXT")
                db.execSQL("ALTER TABLE `trace_steps` ADD COLUMN `resolvedToolName` TEXT")
                db.execSQL("ALTER TABLE `pipeline_runs` ADD COLUMN `userPrompt` TEXT")
            }
        }

        /**
         * Migration from version 32 to 33 — persistent background HITL.
         *
         * Adds the `pending_interactions` table holding the parked HITL
         * interaction of a run whose live in-process waiting phase timed out
         * (see `PendingInteractionEntity`). One row per run (`runId` primary
         * key); indexed by `sessionId` for the chat reattach lookup.
         */
        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_interactions` (" +
                        "`runId` TEXT NOT NULL, " +
                        "`sessionId` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`toolName` TEXT, " +
                        "`toolArgs` TEXT, " +
                        "`risk` TEXT, " +
                        "`question` TEXT, " +
                        "`optionsJson` TEXT, " +
                        "`decision` TEXT, " +
                        "`answer` TEXT, " +
                        "`requestedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`runId`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_interactions_sessionId` " +
                        "ON `pending_interactions` (`sessionId`)",
                )
            }
        }
    }
}
