package app.knotwork.android.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import app.knotwork.android.domain.models.NodeContextConfig
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Phase-15 [AppDatabase.MIGRATION_17_18] script.
 *
 * The migration must:
 * 1. Be registered for versions 17 → 18.
 * 2. Add a single `context_config` column to `pipeline_nodes`.
 * 3. Populate that column with a JSON default that — when read back through
 *    [Converters.toNodeContextConfig] — yields [NodeContextConfig.ALL_ENABLED].
 *    This is the contract that keeps pre-Phase-15 pipelines functionally
 *    identical after the upgrade.
 */
class AppDatabaseMigrationTest {

    @Test
    fun `MIGRATION_17_18 targets versions 17 to 18`() {
        val migration = AppDatabase.MIGRATION_17_18

        assertEquals(17, migration.startVersion)
        assertEquals(18, migration.endVersion)
    }

    @Test
    fun `MIGRATION_17_18 adds context_config column with JSON default`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()

        AppDatabase.MIGRATION_17_18.migrate(db)

        verify(exactly = 1) { db.execSQL(capture(sqlSlot)) }
        val sql = sqlSlot.captured.uppercase()
        assertTrue(
            "Expected ALTER TABLE on pipeline_nodes, got: ${sqlSlot.captured}",
            sql.contains("ALTER TABLE") && sql.contains("PIPELINE_NODES"),
        )
        assertTrue(
            "Expected new context_config column, got: ${sqlSlot.captured}",
            sql.contains("CONTEXT_CONFIG"),
        )
        assertTrue(
            "Column must be NOT NULL so existing rows keep working: ${sqlSlot.captured}",
            sql.contains("NOT NULL"),
        )
        assertTrue(
            "Migration must declare a DEFAULT value: ${sqlSlot.captured}",
            sql.contains("DEFAULT"),
        )
    }

    @Test
    fun `MIGRATION_21_22 targets versions 21 to 22`() {
        val migration = AppDatabase.MIGRATION_21_22

        assertEquals(21, migration.startVersion)
        assertEquals(22, migration.endVersion)
    }

    @Test
    fun `MIGRATION_21_22 adds isStarred column to chat_sessions with default 0`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()

        AppDatabase.MIGRATION_21_22.migrate(db)

        verify(exactly = 1) { db.execSQL(capture(sqlSlot)) }
        val sql = sqlSlot.captured.uppercase()
        assertTrue(
            "Expected ALTER TABLE on chat_sessions, got: ${sqlSlot.captured}",
            sql.contains("ALTER TABLE") && sql.contains("CHAT_SESSIONS"),
        )
        assertTrue(
            "Expected the new isStarred column, got: ${sqlSlot.captured}",
            sql.contains("ISSTARRED"),
        )
        assertTrue(
            "Column must be NOT NULL so existing rows keep working: ${sqlSlot.captured}",
            sql.contains("NOT NULL"),
        )
        assertTrue(
            "Migration must default to 0 (favorited only after the user opts in): ${sqlSlot.captured}",
            sql.contains("DEFAULT 0"),
        )
    }

    @Test
    fun `MIGRATION_17_18 default JSON parses to ALL_ENABLED`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()

        AppDatabase.MIGRATION_17_18.migrate(db)
        verify { db.execSQL(capture(sqlSlot)) }

        // Pull the literal between the first pair of single quotes — that is
        // the JSON value SQLite will write into every existing row.
        val raw = sqlSlot.captured
        val firstQuote = raw.indexOf('\'')
        val lastQuote = raw.lastIndexOf('\'')
        assertTrue("Default literal not found in SQL: $raw", firstQuote in 0 until lastQuote)
        val defaultJson = raw.substring(firstQuote + 1, lastQuote)

        val parsed = Converters().toNodeContextConfig(defaultJson)
        assertEquals(NodeContextConfig.ALL_ENABLED, parsed)
    }

    @Test
    fun `MIGRATION_29_30 targets versions 29 to 30`() {
        val migration = AppDatabase.MIGRATION_29_30

        assertEquals(29, migration.startVersion)
        assertEquals(30, migration.endVersion)
    }

    @Test
    fun `MIGRATION_29_30 creates pipeline_runs table with both indices`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()

        AppDatabase.MIGRATION_29_30.migrate(db)

        verify(exactly = 3) { db.execSQL(capture(statements)) }

        val createTable = statements.first().uppercase()
        assertTrue(
            "Expected CREATE TABLE pipeline_runs, got: ${statements.first()}",
            createTable.contains("CREATE TABLE") && createTable.contains("PIPELINE_RUNS"),
        )
        // Columns that anchor the run lifecycle and the checkpoint contract.
        listOf(
            "ID", "SESSIONID", "PIPELINEID", "ORIGIN", "STATUS",
            "CURRENTNODEID", "STARTEDAT", "FINISHEDAT", "ERRORMESSAGE", "GRAPHCONTENTHASH",
        ).forEach { column ->
            assertTrue("Missing column $column in: ${statements.first()}", createTable.contains(column))
        }
        assertTrue(
            "Primary key must be the run id: ${statements.first()}",
            createTable.contains("PRIMARY KEY(`ID`)"),
        )
        assertTrue(
            "sessionId must deliberately carry no FK (run may precede its session row): " +
                statements.first(),
            !createTable.contains("FOREIGN KEY"),
        )

        val indexSql = statements.drop(1).joinToString(" ").uppercase()
        assertTrue(
            "Missing sessionId index: $indexSql",
            indexSql.contains("INDEX_PIPELINE_RUNS_SESSIONID"),
        )
        assertTrue(
            "Missing status index (orphan sweep / reattach queries): $indexSql",
            indexSql.contains("INDEX_PIPELINE_RUNS_STATUS"),
        )
    }

    @Test
    fun `MIGRATION_30_31 targets versions 30 to 31`() {
        val migration = AppDatabase.MIGRATION_30_31

        assertEquals(30, migration.startVersion)
        assertEquals(31, migration.endVersion)
    }

    @Test
    fun `MIGRATION_30_31 recreates trace_steps with run attribution and preserves legacy rows`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()

        AppDatabase.MIGRATION_30_31.migrate(db)

        // CREATE new + INSERT SELECT + DROP + RENAME + 2 index recreations.
        verify(exactly = 6) { db.execSQL(capture(statements)) }

        val createTable = statements[0].uppercase()
        assertTrue(
            "Expected CREATE TABLE trace_steps_new, got: ${statements[0]}",
            createTable.contains("CREATE TABLE") && createTable.contains("TRACE_STEPS_NEW"),
        )
        // Legacy columns plus the new run-trace columns.
        listOf(
            "SESSIONID", "NODENAME", "OUTPUTTEXT", "TIMESTAMP", "DURATIONMS", "TOKENCOUNT",
            "RUNID", "SEQ", "RECORDKIND", "CONSOLEEVENTTYPE", "NODEID", "INPUTTEXT",
        ).forEach { column ->
            assertTrue("Missing column $column in: ${statements[0]}", createTable.contains(column))
        }
        // SQLite cannot ALTER-in a foreign key — the recreate must carry both
        // the legacy chat_sessions FK and the new pipeline_runs CASCADE FK.
        assertTrue(
            "Missing chat_sessions FK: ${statements[0]}",
            createTable.contains("REFERENCES `CHAT_SESSIONS`"),
        )
        assertTrue(
            "Missing pipeline_runs CASCADE FK (retention deletes traces with their run): ${statements[0]}",
            createTable.contains("REFERENCES `PIPELINE_RUNS`") && createTable.contains("ON DELETE CASCADE"),
        )

        val insert = statements[1].uppercase()
        assertTrue(
            "Legacy rows must be copied, not dropped: ${statements[1]}",
            insert.contains("INSERT INTO `TRACE_STEPS_NEW`") && insert.contains("FROM `TRACE_STEPS`"),
        )
        assertTrue(
            "Legacy rows keep NULL runId and NODE_IO kind: ${statements[1]}",
            insert.contains("NULL") && insert.contains("'NODE_IO'"),
        )

        assertTrue("Old table must be dropped: ${statements[2]}", statements[2].uppercase().contains("DROP TABLE"))
        assertTrue("New table must be renamed: ${statements[3]}", statements[3].uppercase().contains("RENAME TO"))

        val indexSql = statements.drop(4).joinToString(" ").uppercase()
        assertTrue(
            "Missing recreated sessionId index: $indexSql",
            indexSql.contains("INDEX_TRACE_STEPS_SESSIONID"),
        )
        assertTrue(
            "Missing runId index (replay + retention queries): $indexSql",
            indexSql.contains("INDEX_TRACE_STEPS_RUNID"),
        )
    }

    @Test
    fun `MIGRATION_34_35 targets versions 34 to 35`() {
        val migration = AppDatabase.MIGRATION_34_35

        assertEquals(34, migration.startVersion)
        assertEquals(35, migration.endVersion)
    }

    @Test
    fun `MIGRATION_34_35 adds parentRunId self-FK plus index and trace depth column`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()

        AppDatabase.MIGRATION_34_35.migrate(db)

        // ALTER pipeline_runs + CREATE INDEX + ALTER trace_steps.
        verify(exactly = 3) { db.execSQL(capture(statements)) }

        val addParent = statements[0].uppercase()
        assertTrue(
            "Expected ALTER pipeline_runs ADD parentRunId, got: ${statements[0]}",
            addParent.contains("ALTER TABLE") &&
                addParent.contains("PIPELINE_RUNS") &&
                addParent.contains("PARENTRUNID"),
        )
        assertTrue(
            "parentRunId must be a self-referential CASCADE FK so retention cascades the sub-tree: ${statements[0]}",
            addParent.contains("REFERENCES `PIPELINE_RUNS`") && addParent.contains("ON DELETE CASCADE"),
        )

        val index = statements[1].uppercase()
        assertTrue(
            "Missing parentRunId index (child-run tree lookups): ${statements[1]}",
            index.contains("INDEX_PIPELINE_RUNS_PARENTRUNID"),
        )

        val depth = statements[2].uppercase()
        assertTrue(
            "Expected ALTER trace_steps ADD depth, got: ${statements[2]}",
            depth.contains("ALTER TABLE") && depth.contains("TRACE_STEPS") && depth.contains("DEPTH"),
        )
        assertTrue(
            "depth must be NOT NULL DEFAULT 0 so legacy rows render at the top level: ${statements[2]}",
            depth.contains("NOT NULL") && depth.contains("DEFAULT 0"),
        )
    }

    @Test
    fun `MIGRATION_38_39 targets versions 38 to 39`() {
        val migration = AppDatabase.MIGRATION_38_39

        assertEquals(38, migration.startVersion)
        assertEquals(39, migration.endVersion)
    }

    @Test
    fun `MIGRATION_38_39 adds four nullable attachment columns to chat_messages`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()

        AppDatabase.MIGRATION_38_39.migrate(db)

        verify(exactly = 4) { db.execSQL(capture(statements)) }
        val joined = statements.joinToString(" | ").uppercase()
        listOf("ATTACHMENTPATH", "ATTACHMENTMIMETYPE", "ATTACHMENTWIDTH", "ATTACHMENTHEIGHT").forEach { column ->
            assertTrue(
                "Expected ALTER chat_messages ADD $column, got: $joined",
                joined.contains("ALTER TABLE `CHAT_MESSAGES` ADD COLUMN `$column`"),
            )
        }
        // Additive nullable columns — never NOT NULL, so existing rows keep NULL (no attachment).
        assertTrue(
            "Attachment columns must be nullable (no NOT NULL): $joined",
            !joined.contains("NOT NULL"),
        )
    }

    @Test
    fun `MIGRATION_39_40 targets versions 39 to 40`() {
        val migration = AppDatabase.MIGRATION_39_40

        assertEquals(39, migration.startVersion)
        assertEquals(40, migration.endVersion)
    }

    @Test
    fun `MIGRATION_39_40 adds supportsVision column to local_models with default 0`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()

        AppDatabase.MIGRATION_39_40.migrate(db)

        verify(exactly = 1) { db.execSQL(capture(statements)) }
        val sql = statements.single().uppercase()
        assertTrue(
            "Expected ALTER local_models ADD supportsVision, got: $sql",
            sql.contains("ALTER TABLE `LOCAL_MODELS` ADD COLUMN `SUPPORTSVISION`"),
        )
        // Backfilled to text-only (0) for every existing row.
        assertTrue("supportsVision must be NOT NULL DEFAULT 0: $sql", sql.contains("NOT NULL DEFAULT 0"))
    }

    @Test
    fun `MIGRATION_40_41 targets versions 40 to 41`() {
        val migration = AppDatabase.MIGRATION_40_41

        assertEquals(40, migration.startVersion)
        assertEquals(41, migration.endVersion)
    }

    @Test
    fun `MIGRATION_40_41 adds supportsAudio column to local_models with default 0`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()

        AppDatabase.MIGRATION_40_41.migrate(db)

        verify(exactly = 1) { db.execSQL(capture(statements)) }
        val sql = statements.single().uppercase()
        assertTrue(
            "Expected ALTER local_models ADD supportsAudio, got: $sql",
            sql.contains("ALTER TABLE `LOCAL_MODELS` ADD COLUMN `SUPPORTSAUDIO`"),
        )
        // Backfilled to audio-incapable (0) for every existing row.
        assertTrue("supportsAudio must be NOT NULL DEFAULT 0: $sql", sql.contains("NOT NULL DEFAULT 0"))
    }

    @Test
    fun `MIGRATION_41_42 targets versions 41 to 42`() {
        val migration = AppDatabase.MIGRATION_41_42

        assertEquals(41, migration.startVersion)
        assertEquals(42, migration.endVersion)
    }

    @Test
    fun `MIGRATION_41_42 creates model_performance_samples table with its index`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()

        AppDatabase.MIGRATION_41_42.migrate(db)

        // CREATE TABLE + CREATE INDEX.
        verify(exactly = 2) { db.execSQL(capture(statements)) }

        val createTable = statements.first().uppercase()
        assertTrue(
            "Expected CREATE TABLE model_performance_samples, got: ${statements.first()}",
            createTable.contains("CREATE TABLE") && createTable.contains("MODEL_PERFORMANCE_SAMPLES"),
        )
        listOf(
            "ID", "MODELPATH", "TTFTMS", "DECODETOKENSPERSEC", "TOTALMS",
            "TOKENCOUNT", "PEAKNATIVEHEAPBYTES", "ISBENCHMARK", "CREATEDAT",
        ).forEach { column ->
            assertTrue("Missing column $column in: ${statements.first()}", createTable.contains(column))
        }
        // Samples are keyed by path, not a foreign key onto local_models.
        assertTrue(
            "Samples must not carry a foreign key (keyed by model path): ${statements.first()}",
            !createTable.contains("FOREIGN KEY"),
        )

        val index = statements[1].uppercase()
        assertTrue(
            "Missing (modelPath, id) index for the rolling-window query: ${statements[1]}",
            index.contains("INDEX_MODEL_PERFORMANCE_SAMPLES_MODELPATH_ID"),
        )
    }

    @Test
    fun `MIGRATION_42_43 targets versions 42 to 43`() {
        val migration = AppDatabase.MIGRATION_42_43

        assertEquals(42, migration.startVersion)
        assertEquals(43, migration.endVersion)
    }

    @Test
    fun `MIGRATION_42_43 indexes chat_messages sessionId with Room's generated name`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()

        AppDatabase.MIGRATION_42_43.migrate(db)

        verify(exactly = 1) { db.execSQL(capture(sqlSlot)) }
        val sql = sqlSlot.captured.uppercase()
        assertTrue(
            "Expected CREATE INDEX on chat_messages, got: ${sqlSlot.captured}",
            sql.contains("CREATE INDEX") && sql.contains("CHAT_MESSAGES") && sql.contains("SESSIONID"),
        )
        // Name must match Room's generated index_<table>_<column> or schema
        // validation on the next open would reject the migrated DB.
        assertTrue(
            "Index name must match Room's generated name: ${sqlSlot.captured}",
            sql.contains("INDEX_CHAT_MESSAGES_SESSIONID"),
        )
    }

    @Test
    fun `MIGRATION_43_44 targets versions 43 to 44`() {
        val migration = AppDatabase.MIGRATION_43_44

        assertEquals(43, migration.startVersion)
        assertEquals(44, migration.endVersion)
    }

    @Test
    fun `MIGRATION_43_44 adds a nullable modelName column to chat_messages`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()

        AppDatabase.MIGRATION_43_44.migrate(db)

        verify(exactly = 1) { db.execSQL(capture(sqlSlot)) }
        val sql = sqlSlot.captured.uppercase()
        assertTrue(
            "Expected ALTER chat_messages ADD modelName, got: ${sqlSlot.captured}",
            sql.contains("ALTER TABLE `CHAT_MESSAGES` ADD COLUMN `MODELNAME`"),
        )
        // Additive + nullable so legacy rows keep NULL (no recorded model).
        assertTrue("modelName must be nullable (no NOT NULL): ${sqlSlot.captured}", !sql.contains("NOT NULL"))
    }

    @Test
    fun `MIGRATION_44_45 targets versions 44 to 45`() {
        val migration = AppDatabase.MIGRATION_44_45

        assertEquals(44, migration.startVersion)
        assertEquals(45, migration.endVersion)
    }

    @Test
    fun `MIGRATION_44_45 creates triggers table with enabled index and no pipeline FK`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()

        AppDatabase.MIGRATION_44_45.migrate(db)

        // CREATE TABLE + CREATE INDEX.
        verify(exactly = 2) { db.execSQL(capture(statements)) }

        val createTable = statements.first().uppercase()
        assertTrue(
            "Expected CREATE TABLE triggers, got: ${statements.first()}",
            createTable.contains("CREATE TABLE") && createTable.contains("TRIGGERS"),
        )
        listOf("ID", "NAME", "PIPELINEID", "PROMPT", "CONDITIONJSON", "ENABLED", "ARMED", "CREATEDAT", "LASTFIREDAT")
            .forEach { column ->
                assertTrue("Missing column $column in: ${statements.first()}", createTable.contains(column))
            }
        // armed defaults to 1 (a fresh trigger is ready to fire on the first edge).
        assertTrue(
            "armed must default to 1: ${statements.first()}",
            createTable.contains("`ARMED` INTEGER NOT NULL DEFAULT 1"),
        )
        assertTrue(
            "Primary key must be the trigger id: ${statements.first()}",
            createTable.contains("PRIMARY KEY(`ID`)"),
        )
        // A trigger may outlive its bound pipeline (the fire path auto-disables
        // it), so the column carries no foreign key.
        assertTrue(
            "triggers must not carry a pipeline foreign key: ${statements.first()}",
            !createTable.contains("FOREIGN KEY"),
        )

        val index = statements[1].uppercase()
        assertTrue(
            "Index name must match Room's generated name for the active-trigger query: ${statements[1]}",
            index.contains("INDEX_TRIGGERS_ENABLED"),
        )
    }

    @Test
    fun `MIGRATION_45_46 targets versions 45 to 46`() {
        val migration = AppDatabase.MIGRATION_45_46

        assertEquals(45, migration.startVersion)
        assertEquals(46, migration.endVersion)
    }

    @Test
    fun `MIGRATION_45_46 adds a nullable sessionId column to triggers`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()

        AppDatabase.MIGRATION_45_46.migrate(db)

        verify(exactly = 1) { db.execSQL(capture(sqlSlot)) }
        val sql = sqlSlot.captured.uppercase()
        assertTrue(
            "Expected ALTER triggers ADD sessionId, got: ${sqlSlot.captured}",
            sql.contains("ALTER TABLE `TRIGGERS` ADD COLUMN `SESSIONID`"),
        )
        // Additive + nullable so existing trigger rows keep NULL and lazily bind
        // a session on their next fire.
        assertTrue("sessionId must be nullable (no NOT NULL): ${sqlSlot.captured}", !sql.contains("NOT NULL"))
    }

    @Test
    fun `MIGRATION_46_47 targets versions 46 to 47`() {
        val migration = AppDatabase.MIGRATION_46_47

        assertEquals(46, migration.startVersion)
        assertEquals(47, migration.endVersion)
    }

    @Test
    fun `MIGRATION_46_47 creates the local usage-telemetry tables`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()

        AppDatabase.MIGRATION_46_47.migrate(db)

        // CREATE TABLE usage_counter + CREATE TABLE usage_active_day.
        verify(exactly = 2) { db.execSQL(capture(statements)) }

        val counterTable = statements.first().uppercase()
        assertTrue(
            "Expected CREATE TABLE usage_counter, got: ${statements.first()}",
            counterTable.contains("CREATE TABLE") && counterTable.contains("USAGE_COUNTER"),
        )
        listOf("CATEGORY", "COUNTERKEY", "COUNT").forEach { column ->
            assertTrue("Missing column $column in: ${statements.first()}", counterTable.contains(column))
        }
        assertTrue(
            "usage_counter must be keyed on (category, counterKey): ${statements.first()}",
            counterTable.contains("PRIMARY KEY(`CATEGORY`, `COUNTERKEY`)"),
        )

        val dayTable = statements[1].uppercase()
        assertTrue(
            "Expected CREATE TABLE usage_active_day, got: ${statements[1]}",
            dayTable.contains("CREATE TABLE") && dayTable.contains("USAGE_ACTIVE_DAY"),
        )
        assertTrue(
            "usage_active_day must be keyed on the day string: ${statements[1]}",
            dayTable.contains("PRIMARY KEY(`DAY`)"),
        )
    }

    @Test
    fun `MIGRATION_47_48 targets versions 47 to 48`() {
        val migration = AppDatabase.MIGRATION_47_48

        assertEquals(47, migration.startVersion)
        assertEquals(48, migration.endVersion)
    }

    @Test
    fun `MIGRATION_47_48 adds a nullable conditionHasImage column to pipeline_nodes`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()

        AppDatabase.MIGRATION_47_48.migrate(db)

        verify(exactly = 1) { db.execSQL(capture(sqlSlot)) }
        val sql = sqlSlot.captured.uppercase()
        assertTrue(
            "Expected ALTER pipeline_nodes ADD conditionHasImage, got: ${sqlSlot.captured}",
            sql.contains("ALTER TABLE `PIPELINE_NODES` ADD COLUMN `CONDITIONHASIMAGE`"),
        )
        // Additive + nullable (an already-shipped dev migration must not be tightened to
        // NOT NULL afterwards — that would crash Room's validation on devices already at v48).
        assertTrue("conditionHasImage must be nullable (no NOT NULL): ${sqlSlot.captured}", !sql.contains("NOT NULL"))
    }

    @Test
    fun `MIGRATION_48_49 targets versions 48 to 49`() {
        val migration = AppDatabase.MIGRATION_48_49

        assertEquals(48, migration.startVersion)
        assertEquals(49, migration.endVersion)
    }

    @Test
    fun `MIGRATION_48_49 adds a non-null hadImage column to pipeline_runs`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()

        AppDatabase.MIGRATION_48_49.migrate(db)

        verify(exactly = 1) { db.execSQL(capture(sqlSlot)) }
        val sql = sqlSlot.captured.uppercase()
        assertTrue(
            "Expected ALTER pipeline_runs ADD hadImage, got: ${sqlSlot.captured}",
            sql.contains("ALTER TABLE `PIPELINE_RUNS` ADD COLUMN `HADIMAGE`"),
        )
        // Additive + NOT NULL DEFAULT 0 so existing runs get false (no image).
        assertTrue("hadImage must be NOT NULL DEFAULT 0: ${sqlSlot.captured}", sql.contains("NOT NULL DEFAULT 0"))
    }
}
