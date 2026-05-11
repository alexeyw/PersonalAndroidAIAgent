package ai.agent.android.data.engine

import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.SettingsRepository
import android.content.ComponentCallbacks2
import android.content.Context
import com.google.ai.edge.litertlm.Engine
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for [LiteRTLlmEngine].
 */
class LiteRTLlmEngineTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var engine: LiteRTLlmEngine

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        every { context.cacheDir } returns File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        settingsRepository = mockk(relaxed = true)
        every { settingsRepository.maxContextLength } returns flowOf(4096)
        every { settingsRepository.localModelBackend } returns flowOf("CPU")

        mockkConstructor(Engine::class)
        every { anyConstructed<Engine>().initialize() } returns Unit
        every { anyConstructed<Engine>().close() } returns Unit

        engine = LiteRTLlmEngine(context, settingsRepository)
    }

    @After
    fun teardown() {
        engine.close()
        unmockkAll()
    }

    @Test
    fun `initialize returns Error when model file does not exist`() = runTest {
        val path = "/fake/path/model_does_not_exist.tflite"
        val result = engine.initialize(path)
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message!!.contains("does not exist"))
    }

    @Test
    fun `initialize returns Success when model file exists`() = runTest {
        val tempFile = File.createTempFile("model", ".tflite")
        tempFile.deleteOnExit()

        val result = engine.initialize(tempFile.absolutePath)

        assertTrue(result is Result.Success)
        assertEquals(tempFile.absolutePath, engine.currentModelPath)
        assertTrue(engine.isInitialized)

        verify { anyConstructed<Engine>().initialize() }
    }

    @Test
    fun `generateResponseStream throws IllegalStateException when not initialized`() = runTest {
        try {
            engine.generateResponseStream("Hello").toList()
            assert(false) { "Expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            assertEquals("LLM Engine not initialized", e.message)
        }
    }

    @Test
    fun `registers component callbacks on init`() {
        verify { context.registerComponentCallbacks(engine) }
    }

    @Test
    fun `onTrimMemory background level unloads engine`() = runTest {
        val tempFile = File.createTempFile("model", ".tflite")
        tempFile.deleteOnExit()
        engine.initialize(tempFile.absolutePath)
        assertTrue(engine.isInitialized)

        engine.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)

        assertTrue(!engine.isInitialized)
        verify { anyConstructed<Engine>().close() }

        engine.close()
        verify { context.unregisterComponentCallbacks(engine) }
    }
}
