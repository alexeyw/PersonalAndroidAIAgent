package app.knotwork.android.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frozen-schema migration regression suite built on Room's
 * [MigrationTestHelper]. Unlike the hand-transcribed-DDL
 * [AppDatabaseMigrationTest] (which covers pre-baseline versions that were
 * never exported), this suite drives every migration whose **starting**
 * schema JSON is committed under `app/schemas/` — i.e. the
 * baseline-and-up range `23 → 29`.
 *
 * For each step it:
 * 1. Materialises the database at the older version straight from the
 *    frozen `<old>.json` snapshot ([MigrationTestHelper.createDatabase]).
 * 2. Seeds representative rows into the tables the migration touches.
 * 3. Runs the migration under test and **validates the resulting schema**
 *    against the frozen `<new>.json` snapshot
 *    ([MigrationTestHelper.runMigrationsAndValidate] with
 *    `validateDroppedTables = true`).
 * 4. Asserts that pre-existing data survived and that newly added columns
 *    carry their documented defaults.
 *
 * Because every migration is invoked through the same algorithm Room uses
 * on a real device, a green run here is direct evidence that an in-place
 * upgrade across the baseline range preserves user data — the guarantee
 * that replaced the former `fallbackToDestructiveMigration(true)`.
 *
 * The helper uses the default [androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory]
 * (plaintext); migrations operate on the schema regardless of the
 * SQLCipher cipher used by the production [app.knotwork.android.di.AppModule] builder, so the
 * SQL logic is exercised faithfully.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationHelperTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate23to24_addsPipelinePresetsTableAndPreservesMemory() {
        helper.createDatabase(TEST_DB, 23).use { db ->
            seedMemoryChunkV23(db)
        }

        helper.runMigrationsAndValidate(TEST_DB, 24, true, AppDatabase.MIGRATION_23_24).use { db ->
            assertMemoryChunkPreserved(db)
            // The new table must accept a full row round-trip.
            db.execSQL(
                "INSERT INTO pipeline_presets" +
                    "(id, name, description, categoryKey, graphJson, tagsCsv, createdAt) " +
                    "VALUES('p1', 'Test', 'desc', 'local', '{}', 'a,b', 42)",
            )
            assertEquals("p1", querySingleString(db, "SELECT id FROM pipeline_presets"))
        }
    }

    @Test
    fun migrate24to25_addsPromptPresetsTableAndPreservesMemory() {
        helper.createDatabase(TEST_DB, 24).use { db ->
            seedMemoryChunkV23(db)
        }

        helper.runMigrationsAndValidate(TEST_DB, 25, true, AppDatabase.MIGRATION_24_25).use { db ->
            assertMemoryChunkPreserved(db)
            db.execSQL(
                "INSERT INTO prompt_presets" +
                    "(id, name, description, nodeTypeKey, systemPrompt, tagsCsv, createdAt) " +
                    "VALUES('q1', 'Test', 'desc', 'LITE_RT', 'You are helpful', '', 7)",
            )
            assertEquals("q1", querySingleString(db, "SELECT id FROM prompt_presets"))
        }
    }

    @Test
    fun migrate25to26_addsSourceColumnBackfilledToUnknown() {
        helper.createDatabase(TEST_DB, 25).use { db ->
            seedMemoryChunkV23(db)
        }

        helper.runMigrationsAndValidate(TEST_DB, 26, true, AppDatabase.MIGRATION_25_26).use { db ->
            assertMemoryChunkPreserved(db)
            assertEquals(
                "source must back-fill to the Unknown encoding",
                "{\"type\":\"unknown\"}",
                querySingleString(db, "SELECT source FROM memory_chunks"),
            )
        }
    }

    @Test
    fun migrate26to27_addsTagsUseCountAndLastUsedWithDefaults() {
        helper.createDatabase(TEST_DB, 26).use { db ->
            // v26 added the `source` column, so seed it explicitly.
            db.execSQL(
                "INSERT INTO memory_chunks(text, embedding, timestamp, isPinned, source) " +
                    "VALUES('$CHUNK_TEXT', '$CHUNK_EMBEDDING', $CHUNK_TS, 0, '{\"type\":\"manual\"}')",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 27, true, AppDatabase.MIGRATION_26_27).use { db ->
            assertMemoryChunkPreserved(db)
            db.query("SELECT tagsCsv, useCount, lastUsedAt FROM memory_chunks").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("tagsCsv must back-fill to empty", "", c.getString(0))
                assertEquals("useCount must back-fill to 0", 0, c.getInt(1))
                assertTrue("lastUsedAt must back-fill to NULL", c.isNull(2))
            }
        }
    }

    @Test
    fun migrate27to28_addsNeedsReembeddingBackfilledToZero() {
        helper.createDatabase(TEST_DB, 27).use { db ->
            // v27 added tagsCsv/useCount/lastUsedAt on top of v26's source.
            db.execSQL(
                "INSERT INTO memory_chunks" +
                    "(text, embedding, timestamp, isPinned, source, tagsCsv, useCount, lastUsedAt) " +
                    "VALUES('$CHUNK_TEXT', '$CHUNK_EMBEDDING', $CHUNK_TS, 0, '{\"type\":\"manual\"}', 'x', 3, 99)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 28, true, AppDatabase.MIGRATION_27_28).use { db ->
            assertMemoryChunkPreserved(db)
            db.query("SELECT needsReembedding FROM memory_chunks").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("needsReembedding must back-fill to 0", 0, c.getInt(0))
            }
        }
    }

    @Test
    fun migrate28to29_convertsEmbeddingStringsToLittleEndianBlobs() {
        helper.createDatabase(TEST_DB, 28).use { db ->
            // Two representative legacy rows: one healthy comma-encoded
            // embedding and one malformed string (the kind a hand-edited or
            // partially corrupted DB can carry).
            db.execSQL(
                "INSERT INTO memory_chunks" +
                    "(id, text, embedding, timestamp, isPinned, source, tagsCsv, useCount, lastUsedAt, " +
                    "needsReembedding) " +
                    "VALUES(1, '$CHUNK_TEXT', '$CHUNK_EMBEDDING', $CHUNK_TS, 1, '{\"type\":\"manual\"}', " +
                    "'tag-a,tag-b', 3, 99, 0)",
            )
            db.execSQL(
                "INSERT INTO memory_chunks" +
                    "(id, text, embedding, timestamp, isPinned, source, tagsCsv, useCount, lastUsedAt, " +
                    "needsReembedding) " +
                    "VALUES(2, 'corrupt but repairable', 'not,a,float', $CHUNK_TS, 0, '{\"type\":\"unknown\"}', " +
                    "'', 0, NULL, 1)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 29, true, AppDatabase.MIGRATION_28_29).use { db ->
            db.query(
                "SELECT text, embedding, timestamp, isPinned, source, tagsCsv, useCount, lastUsedAt, " +
                    "needsReembedding FROM memory_chunks WHERE id = 1",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(CHUNK_TEXT, c.getString(0))
                // The blob must be the exact little-endian encoding of the
                // legacy comma-separated values.
                assertArrayEquals(
                    EmbeddingBlobCodec.encode(floatArrayOf(0.1f, 0.2f, 0.3f)),
                    c.getBlob(1),
                )
                assertEquals(CHUNK_TS, c.getLong(2))
                assertEquals(1, c.getInt(3))
                assertEquals("{\"type\":\"manual\"}", c.getString(4))
                assertEquals("tag-a,tag-b", c.getString(5))
                assertEquals(3, c.getInt(6))
                assertEquals(99L, c.getLong(7))
                assertEquals(0, c.getInt(8))
            }
            // The malformed row survives with the zero-length "no usable
            // embedding" marker — its text stays repairable by the re-embed
            // path instead of being deleted.
            db.query("SELECT text, embedding, needsReembedding FROM memory_chunks WHERE id = 2").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("corrupt but repairable", c.getString(0))
                assertEquals(0, c.getBlob(1).size)
                assertEquals(1, c.getInt(2))
            }
        }
    }

    @Test
    fun migrateFullChain23to29_preservesDataAndValidatesFinalSchema() {
        helper.createDatabase(TEST_DB, 23).use { db ->
            seedMemoryChunkV23(db)
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            29,
            true,
            AppDatabase.MIGRATION_23_24,
            AppDatabase.MIGRATION_24_25,
            AppDatabase.MIGRATION_25_26,
            AppDatabase.MIGRATION_26_27,
            AppDatabase.MIGRATION_27_28,
            AppDatabase.MIGRATION_28_29,
        ).use { db ->
            // The original v23 row must survive every step with the new
            // columns filled by their documented defaults and the embedding
            // re-encoded into its binary form.
            db.query(
                "SELECT text, source, tagsCsv, useCount, lastUsedAt, needsReembedding, embedding " +
                    "FROM memory_chunks",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(CHUNK_TEXT, c.getString(0))
                assertEquals("{\"type\":\"unknown\"}", c.getString(1))
                assertEquals("", c.getString(2))
                assertEquals(0, c.getInt(3))
                assertTrue(c.isNull(4))
                assertEquals(0, c.getInt(5))
                assertArrayEquals(
                    EmbeddingBlobCodec.encode(floatArrayOf(0.1f, 0.2f, 0.3f)),
                    c.getBlob(6),
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Inserts a single representative chunk using the v23-era column set
     * (`text`, `embedding`, `timestamp`, `isPinned`). Valid for any
     * starting version `23 ≤ v ≤ 25`, where these columns are still the
     * only non-defaulted ones on `memory_chunks`.
     */
    private fun seedMemoryChunkV23(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO memory_chunks(text, embedding, timestamp, isPinned) " +
                "VALUES('$CHUNK_TEXT', '$CHUNK_EMBEDDING', $CHUNK_TS, 0)",
        )
    }

    /** Asserts the seeded chunk's identity columns survived a migration unchanged. */
    private fun assertMemoryChunkPreserved(db: SupportSQLiteDatabase) {
        db.query("SELECT text, embedding, timestamp FROM memory_chunks").use { c ->
            assertTrue("seeded chunk must survive the migration", c.moveToFirst())
            assertEquals(CHUNK_TEXT, c.getString(0))
            assertEquals(CHUNK_EMBEDDING, c.getString(1))
            assertEquals(CHUNK_TS, c.getLong(2))
        }
    }

    private fun querySingleString(db: SupportSQLiteDatabase, sql: String): String {
        db.query(sql).use { c ->
            assertTrue(c.moveToFirst())
            return c.getString(0)
        }
    }

    private companion object {
        const val TEST_DB = "migration-helper-test.db"
        const val CHUNK_TEXT = "remember me"
        const val CHUNK_EMBEDDING = "0.1,0.2,0.3"
        const val CHUNK_TS = 1_700_000_000_000L
    }
}
