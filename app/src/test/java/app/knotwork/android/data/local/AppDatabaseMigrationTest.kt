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
}
