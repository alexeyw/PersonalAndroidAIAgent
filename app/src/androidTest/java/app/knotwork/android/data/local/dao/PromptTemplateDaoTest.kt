package app.knotwork.android.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.models.PromptTemplateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for [PromptTemplateDao]. Verifies the
 * `getAllPrompts` ordering contract (`category, name ASC`), the
 * `REPLACE` conflict strategy on `insertPrompt`, scoped deletion and
 * the row count helper used by first-launch seeding.
 */
@RunWith(AndroidJUnit4::class)
class PromptTemplateDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: PromptTemplateDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.promptTemplateDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertPrompt_andGetAllPrompts_emitsRow() = runBlocking {
        dao.insertPrompt(PromptTemplateEntity(name = "Classifier", text = "txt", category = "INTENT_ROUTER"))

        val rows = dao.getAllPrompts().first()
        assertEquals(1, rows.size)
        assertEquals("Classifier", rows.single().name)
        assertTrue("Auto-generated id must be positive", rows.single().id > 0L)
    }

    @Test
    fun getAllPrompts_ordersByCategoryThenName() = runBlocking {
        // Insert intentionally out of order; verify category ASC, then name ASC.
        dao.insertPrompt(PromptTemplateEntity(name = "Decomposer", text = "d", category = "DECOMPOSITION"))
        dao.insertPrompt(PromptTemplateEntity(name = "Bravo", text = "b", category = "CUSTOM"))
        dao.insertPrompt(PromptTemplateEntity(name = "Alpha", text = "a", category = "CUSTOM"))
        dao.insertPrompt(PromptTemplateEntity(name = "Picker", text = "p", category = "TOOL"))

        val rows = dao.getAllPrompts().first()
        assertEquals(
            listOf("CUSTOM/Alpha", "CUSTOM/Bravo", "DECOMPOSITION/Decomposer", "TOOL/Picker"),
            rows.map { "${it.category}/${it.name}" },
        )
    }

    @Test
    fun insertPrompt_replacesOnSameId() = runBlocking {
        dao.insertPrompt(PromptTemplateEntity(id = 1L, name = "First", text = "v1", category = "CUSTOM"))
        dao.insertPrompt(PromptTemplateEntity(id = 1L, name = "Second", text = "v2", category = "CUSTOM"))

        val rows = dao.getAllPrompts().first()
        assertEquals(1, rows.size)
        val only = rows.single()
        assertEquals("Second", only.name)
        assertEquals("v2", only.text)
    }

    @Test
    fun deletePrompt_removesOnlyMatchingRow() = runBlocking {
        dao.insertPrompt(PromptTemplateEntity(name = "Keep", text = "k", category = "CUSTOM"))
        dao.insertPrompt(PromptTemplateEntity(name = "Drop", text = "d", category = "CUSTOM"))

        val rows = dao.getAllPrompts().first()
        val drop = rows.first { it.name == "Drop" }
        dao.deletePrompt(drop.id)

        val survivors = dao.getAllPrompts().first()
        assertEquals(1, survivors.size)
        assertEquals("Keep", survivors.single().name)
    }

    @Test
    fun getPromptsCount_returnsRowCount() = runBlocking {
        assertEquals(0, dao.getPromptsCount())

        dao.insertPrompt(PromptTemplateEntity(name = "A", text = "a", category = "CUSTOM"))
        dao.insertPrompt(PromptTemplateEntity(name = "B", text = "b", category = "CUSTOM"))

        assertEquals(2, dao.getPromptsCount())
    }

    @Test
    fun deletePrompt_noOpForUnknownId() = runBlocking {
        dao.insertPrompt(PromptTemplateEntity(name = "Keep", text = "k", category = "CUSTOM"))

        dao.deletePrompt(id = 9999L)

        assertNotNull(dao.getAllPrompts().first().singleOrNull())
    }
}
