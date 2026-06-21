package app.knotwork.android.data.engine

import android.content.ComponentCallbacks2
import android.content.Context
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.SettingsRepository
import com.google.ai.edge.litertlm.Engine
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    // Drives the engine's fire-and-forget unload path (close / onTrimMemory).
    // An unconfined dispatcher runs the launched unload eagerly on the calling
    // thread until its first real suspension, so the uncontended teardown
    // completes synchronously within the test body.
    private val appScope = CoroutineScope(UnconfinedTestDispatcher())

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        every { context.cacheDir } returns File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        settingsRepository = mockk(relaxed = true)
        every { settingsRepository.maxContextLength } returns flowOf(4096)
        every { settingsRepository.localModelBackend } returns flowOf("CPU")
        // Crash-recovery breadcrumb stub: empty / `null` means no prior
        // attempt crashed mid-init, which is the desired baseline for
        // every test in this suite.
        every { settingsRepository.lastInitBackendAttempt } returns flowOf(null)

        mockkConstructor(Engine::class)
        every { anyConstructed<Engine>().initialize() } returns Unit
        every { anyConstructed<Engine>().close() } returns Unit

        engine = LiteRTLlmEngine(context, settingsRepository, appScope)
    }

    @After
    fun teardown() {
        engine.close()
        appScope.cancel()
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
    fun `transcribe throws IllegalStateException when not initialized`() = runTest {
        try {
            engine.transcribe("/cache/audio/clip.wav", "Transcribe this").toList()
            assert(false) { "Expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            assertEquals("LLM Engine not initialized", e.message)
        }
    }

    @Test
    fun `text-only init leaves audio disabled`() = runTest {
        val tempFile = File.createTempFile("model", ".tflite")
        tempFile.deleteOnExit()

        engine.initialize(tempFile.absolutePath)

        assertTrue(!engine.isAudioEnabled)
    }

    @Test
    fun `init with enableAudio marks the engine audio-enabled`() = runTest {
        val tempFile = File.createTempFile("model", ".tflite")
        tempFile.deleteOnExit()

        val result = engine.initialize(tempFile.absolutePath, enableAudio = true)

        assertTrue(result is Result.Success)
        assertTrue(engine.isAudioEnabled)
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
