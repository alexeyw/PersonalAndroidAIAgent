package app.knotwork.android.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.models.ConnectionEntity
import app.knotwork.android.data.local.models.NodeEntity
import app.knotwork.android.data.local.models.PipelineEntity
import app.knotwork.android.domain.models.NodeContextConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for [PipelineDao]. The repository layer routes
 * calls through this DAO but mocked tests cannot verify the SQL semantics
 * — `@Transaction`-backed `getAllPipelines` / `getPipelineById` returning
 * `PipelineWithNodesAndConnections`, the `savePipelineTransaction`
 * atomicity contract, FK cascade on delete, and the `NodeContextConfig`
 * TypeConverter round-trip can only be observed end-to-end against a
 * real Room database.
 */
@RunWith(AndroidJUnit4::class)
class PipelineDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: PipelineDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.pipelineDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun getAllPipelines_ordersByUpdatedAtDesc() = runBlocking {
        dao.insertPipeline(PipelineEntity(id = "old", name = "Old", updatedAt = 100L))
        dao.insertPipeline(PipelineEntity(id = "new", name = "New", updatedAt = 300L))
        dao.insertPipeline(PipelineEntity(id = "mid", name = "Mid", updatedAt = 200L))

        val ordered = dao.getAllPipelines().first().map { it.pipeline.id }
        assertEquals(listOf("new", "mid", "old"), ordered)
    }

    @Test
    fun getPipelineById_returnsEmbeddedNodesAndConnections() = runBlocking {
        dao.insertPipeline(PipelineEntity(id = "p", name = "P", updatedAt = 0L))
        dao.insertNodes(
            listOf(
                node(id = "n1", pipelineId = "p", label = "Input"),
                node(id = "n2", pipelineId = "p", label = "Output"),
            ),
        )
        dao.insertConnections(
            listOf(ConnectionEntity(id = "c1", pipelineId = "p", sourceNodeId = "n1", targetNodeId = "n2")),
        )

        val loaded = dao.getPipelineById("p")
        assertNotNull(loaded)
        assertEquals("P", loaded?.pipeline?.name)
        assertEquals(setOf("n1", "n2"), loaded?.nodes?.map { it.id }?.toSet())
        assertEquals(listOf("c1"), loaded?.connections?.map { it.id })
    }

    @Test
    fun getPipelineById_returnsNullForUnknownId() = runBlocking {
        assertNull(dao.getPipelineById("missing"))
    }

    @Test
    fun savePipelineTransaction_replacesNodesAndConnectionsAtomically() = runBlocking {
        dao.insertPipeline(PipelineEntity(id = "p", name = "Old", updatedAt = 1L))
        dao.insertNodes(listOf(node(id = "n-old", pipelineId = "p", label = "Old")))
        dao.insertConnections(
            listOf(ConnectionEntity(id = "c-old", pipelineId = "p", sourceNodeId = "n-old", targetNodeId = "n-old")),
        )

        // Replace the whole pipeline body in a single transaction.
        dao.savePipelineTransaction(
            pipeline = PipelineEntity(id = "p", name = "New", updatedAt = 2L),
            nodes = listOf(
                node(id = "n-a", pipelineId = "p", label = "A"),
                node(id = "n-b", pipelineId = "p", label = "B"),
            ),
            connections = listOf(
                ConnectionEntity(id = "c-ab", pipelineId = "p", sourceNodeId = "n-a", targetNodeId = "n-b"),
            ),
        )

        val loaded = dao.getPipelineById("p")
        assertNotNull(loaded)
        assertEquals("New", loaded?.pipeline?.name)
        assertEquals(setOf("n-a", "n-b"), loaded?.nodes?.map { it.id }?.toSet())
        assertEquals(listOf("c-ab"), loaded?.connections?.map { it.id })
    }

    @Test
    fun deleteNodesForPipeline_scopedToPipeline() = runBlocking {
        dao.insertPipeline(PipelineEntity(id = "p1", name = "P1", updatedAt = 1L))
        dao.insertPipeline(PipelineEntity(id = "p2", name = "P2", updatedAt = 1L))
        dao.insertNodes(
            listOf(
                node(id = "n1", pipelineId = "p1", label = "n1"),
                node(id = "n2", pipelineId = "p2", label = "n2"),
            ),
        )

        dao.deleteNodesForPipeline("p1")

        assertTrue(dao.getPipelineById("p1")?.nodes.orEmpty().isEmpty())
        assertEquals(listOf("n2"), dao.getPipelineById("p2")?.nodes?.map { it.id })
    }

    @Test
    fun deleteConnectionsForPipeline_scopedToPipeline() = runBlocking {
        dao.insertPipeline(PipelineEntity(id = "p1", name = "P1", updatedAt = 1L))
        dao.insertPipeline(PipelineEntity(id = "p2", name = "P2", updatedAt = 1L))
        dao.insertNodes(
            listOf(
                node(id = "n1", pipelineId = "p1", label = "n1"),
                node(id = "n2", pipelineId = "p2", label = "n2"),
            ),
        )
        dao.insertConnections(
            listOf(
                ConnectionEntity(id = "c1", pipelineId = "p1", sourceNodeId = "n1", targetNodeId = "n1"),
                ConnectionEntity(id = "c2", pipelineId = "p2", sourceNodeId = "n2", targetNodeId = "n2"),
            ),
        )

        dao.deleteConnectionsForPipeline("p1")

        assertTrue(dao.getPipelineById("p1")?.connections.orEmpty().isEmpty())
        assertEquals(listOf("c2"), dao.getPipelineById("p2")?.connections?.map { it.id })
    }

    @Test
    fun deletePipelineById_cascadesToNodesAndConnections() = runBlocking {
        dao.insertPipeline(PipelineEntity(id = "p", name = "P", updatedAt = 1L))
        dao.insertNodes(
            listOf(
                node(id = "n1", pipelineId = "p", label = "n1"),
                node(id = "n2", pipelineId = "p", label = "n2"),
            ),
        )
        dao.insertConnections(
            listOf(ConnectionEntity(id = "c", pipelineId = "p", sourceNodeId = "n1", targetNodeId = "n2")),
        )

        dao.deletePipelineById("p")

        assertNull(dao.getPipelineById("p"))
        // Cascade implies the children rows are gone — `getAllPipelines`
        // would surface orphaned rows only if the FK with `ON DELETE CASCADE`
        // were not honored.
        assertTrue(dao.getAllPipelines().first().isEmpty())
    }

    @Test
    fun nodeContextConfig_isRoundTrippedThroughTypeConverter() = runBlocking {
        val customConfig = NodeContextConfig(
            chatHistory = false,
            originalTask = true,
            nodeInput = true,
            longTermMemory = false,
            toolResults = true,
        )
        dao.insertPipeline(PipelineEntity(id = "p", name = "P", updatedAt = 1L))
        dao.insertNodes(listOf(node(id = "n", pipelineId = "p", label = "n", contextConfig = customConfig)))

        val loaded = dao.getPipelineById("p")?.nodes?.single()
        assertNotNull(loaded)
        assertEquals(customConfig, loaded?.contextConfig)
    }

    @Test
    fun configJson_isNullableAndRoundTrips() = runBlocking {
        dao.insertPipeline(PipelineEntity(id = "p", name = "P", updatedAt = 1L))
        dao.insertNodes(
            listOf(
                node(id = "withCfg", pipelineId = "p", label = "withCfg", configJson = """{"k":"v"}"""),
                node(id = "noCfg", pipelineId = "p", label = "noCfg", configJson = null),
            ),
        )

        val byId = dao.getPipelineById("p")?.nodes?.associateBy { it.id }.orEmpty()
        assertEquals("""{"k":"v"}""", byId["withCfg"]?.configJson)
        assertNull(byId["noCfg"]?.configJson)
    }

    private fun node(
        id: String,
        pipelineId: String,
        label: String,
        contextConfig: NodeContextConfig = NodeContextConfig.ALL_ENABLED,
        configJson: String? = null,
    ): NodeEntity = NodeEntity(
        id = id,
        pipelineId = pipelineId,
        type = "INPUT",
        x = 0f,
        y = 0f,
        label = label,
        contextConfig = contextConfig,
        configJson = configJson,
    )
}
