package app.knotwork.android.data.engine

import android.content.ComponentCallbacks2
import android.content.Context
import app.knotwork.android.domain.models.LocalBackend
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.SettingsRepository
import com.google.ai.edge.litertlm.Engine
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
        every { settingsRepository.localBackendFailureStreak } returns flowOf(0)

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

    @Test
    fun `given an unload queued for an older engine when a newer one is loaded then it survives`() = runTest {
        val tempFile = File.createTempFile("model", ".tflite")
        tempFile.deleteOnExit()
        // The unload `onTrimMemory` launches must still be *pending* while the
        // next load completes — that ordering is the whole defect, and an eager
        // dispatcher would hide it by running the unload immediately. A standard
        // (queueing) dispatcher lets the test place the two in the observed
        // order: trim first, reload second, unload delivered last.
        val queued = StandardTestDispatcher(testScheduler)
        val deferredScope = CoroutineScope(queued)
        val subject = LiteRTLlmEngine(context, settingsRepository, deferredScope)

        subject.initialize(tempFile.absolutePath)
        subject.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        // The trigger-started background run reloads the engine before the
        // queued unload gets to run.
        subject.initialize(tempFile.absolutePath)
        advanceUntilIdle()

        assertTrue(
            "a stale unload must not free the engine a later run just loaded",
            subject.isInitialized,
        )
        deferredScope.cancel()
    }

    @Test
    fun `given a generation in flight when the app is backgrounded then the engine stays loaded`() = runTest {
        val tempFile = File.createTempFile("model", ".tflite")
        tempFile.deleteOnExit()
        engine.initialize(tempFile.absolutePath)
        activeGenerationJob().set(engine, Job())

        // TRIM_MEMORY_BACKGROUND is "you went to background", not memory
        // pressure — and a trigger-started background run arrives in exactly
        // that state, so it must not be torn down.
        engine.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)

        assertTrue("background transition must not unload a working engine", engine.isInitialized)
    }

    @Test
    fun `given a generation in flight when real memory pressure arrives then the engine is freed`() = runTest {
        val tempFile = File.createTempFile("model", ".tflite")
        tempFile.deleteOnExit()
        engine.initialize(tempFile.absolutePath)
        activeGenerationJob().set(engine, Job())

        // Being killed by the OS is worse than losing one generation, so genuine
        // pressure still wins over the running job.
        engine.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_MODERATE)

        assertTrue("real pressure must still free the engine", !engine.isInitialized)
    }

    @Test
    fun `given one unfinished init when reloading then CPU is used but the choice is kept`() = runTest {
        val tempFile = File.createTempFile("model", ".tflite")
        tempFile.deleteOnExit()
        every { settingsRepository.localModelBackend } returns flowOf("GPU")
        every { settingsRepository.lastInitBackendAttempt } returns flowOf("GPU")
        every { settingsRepository.localBackendFailureStreak } returns flowOf(0)

        engine.initialize(tempFile.absolutePath)

        // The breadcrumb proves the process died inside the init window, not
        // that the GPU is at fault — a swipe-away or an lmkd kill leaves the
        // same trace. Run on CPU now, but do not touch what the user chose.
        assertEquals(LocalBackend.CPU, engine.activeBackend)
        coVerify(exactly = 1) { settingsRepository.setLocalBackendFailureStreak(1) }
        coVerify(exactly = 0) { settingsRepository.setLocalModelBackend(LocalBackend.CPU.key) }
    }

    @Test
    fun `given two unfinished inits in a row when reloading then the backend is switched for good`() = runTest {
        val tempFile = File.createTempFile("model", ".tflite")
        tempFile.deleteOnExit()
        every { settingsRepository.localModelBackend } returns flowOf("GPU")
        every { settingsRepository.lastInitBackendAttempt } returns flowOf("GPU")
        every { settingsRepository.localBackendFailureStreak } returns flowOf(1)

        engine.initialize(tempFile.absolutePath)

        // Corroborated across two starts — now it is evidence about the hardware.
        assertEquals(LocalBackend.CPU, engine.activeBackend)
        coVerify(exactly = 1) { settingsRepository.setLocalModelBackend(LocalBackend.CPU.key) }
    }

    @Test
    fun `given a successful init when it completes then the failure streak is cleared`() = runTest {
        val tempFile = File.createTempFile("model", ".tflite")
        tempFile.deleteOnExit()
        every { settingsRepository.localModelBackend } returns flowOf("GPU")
        every { settingsRepository.lastInitBackendAttempt } returns flowOf(null)
        every { settingsRepository.localBackendFailureStreak } returns flowOf(0)
        every { settingsRepository.localBackendFailureStreak } returns flowOf(1)

        engine.initialize(tempFile.absolutePath)

        assertEquals(LocalBackend.GPU, engine.activeBackend)
        coVerify { settingsRepository.setLocalBackendFailureStreak(0) }
    }

    @Test
    fun `given no model loaded when asked for the backend then none is reported`() = runTest {
        // Better no hint in the status line than a guessed one.
        assertEquals(null, engine.activeBackend)
    }

    /** Reflective handle on the engine's private in-flight generation job. */
    private fun activeGenerationJob() = LiteRTLlmEngine::class.java.getDeclaredField("activeGenerationJob")
        .apply { isAccessible = true }
}
