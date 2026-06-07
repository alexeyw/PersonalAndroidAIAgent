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
}
