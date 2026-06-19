package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.engine.TaskQueueManager
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.AppError
import app.knotwork.android.domain.models.LocalModel
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.services.AudioCaptureStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TranscribeAudioUseCase] covering every branch of the
 * voice-input transcription pre-flight: the engine-busy gate, the active-model
 * audio-capability checks, the success path, and the failure paths — plus the
 * ephemeral-clip cleanup contract.
 */
class TranscribeAudioUseCaseTest {

    private lateinit var localModelRepository: LocalModelRepository
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var llmInferenceEngine: LlmInferenceEngine
    private lateinit var audioCaptureStore: AudioCaptureStore
    private lateinit var taskQueueManager: TaskQueueManager
    private lateinit var useCase: TranscribeAudioUseCase

    private val audioPath = "/cache/audio/clip.wav"

    private fun audioModel(supportsAudio: Boolean) = LocalModel(
        id = 1L,
        name = "Gemma",
        path = "/models/gemma.litertlm",
        size = 100L,
        isActive = true,
        supportsVision = false,
        supportsAudio = supportsAudio,
    )

    @Before
    fun setup() {
        localModelRepository = mockk()
        loadModelUseCase = mockk()
        llmInferenceEngine = mockk()
        audioCaptureStore = mockk()
        taskQueueManager = mockk()
        useCase = TranscribeAudioUseCase(
            localModelRepository,
            loadModelUseCase,
            llmInferenceEngine,
            audioCaptureStore,
            taskQueueManager,
        )
        // Default: engine idle and cleanup succeeds.
        every { taskQueueManager.globalState } returns MutableStateFlow(AgentOrchestratorState.Idle)
        coEvery { audioCaptureStore.delete(any()) } returns kotlin.Result.success(Unit)
    }

    @Test
    fun `given an active run when invoked then returns EngineBusy and keeps the clip`() = runTest {
        every { taskQueueManager.globalState } returns
            MutableStateFlow(AgentOrchestratorState.Thinking("…"))

        val outcome = useCase(audioPath)

        assertEquals(TranscriptionOutcome.EngineBusy, outcome)
        // Clip preserved for retry; model never loaded.
        coVerify(exactly = 0) { audioCaptureStore.delete(any()) }
        coVerify(exactly = 0) { loadModelUseCase(requireAudio = any()) }
    }

    @Test
    fun `given no active model when invoked then returns NoActiveModel and deletes the clip`() = runTest {
        coEvery { localModelRepository.getActiveModel() } returns null

        val outcome = useCase(audioPath)

        assertEquals(TranscriptionOutcome.NoActiveModel, outcome)
        coVerify(exactly = 1) { audioCaptureStore.delete(audioPath) }
    }

    @Test
    fun `given a text-only model when invoked then returns ModelNotAudioCapable and deletes the clip`() = runTest {
        coEvery { localModelRepository.getActiveModel() } returns audioModel(supportsAudio = false)

        val outcome = useCase(audioPath)

        assertEquals(TranscriptionOutcome.ModelNotAudioCapable, outcome)
        coVerify(exactly = 1) { audioCaptureStore.delete(audioPath) }
    }

    @Test
    fun `given an audio model when transcription succeeds then returns trimmed transcript and deletes clip`() =
        runTest {
            coEvery { localModelRepository.getActiveModel() } returns audioModel(supportsAudio = true)
            coEvery { loadModelUseCase(requireAudio = true) } returns Result.Success(Unit)
            every { llmInferenceEngine.transcribe(audioPath, any()) } returns flowOf("  Hello ", "world  ")

            val outcome = useCase(audioPath)

            assertEquals(TranscriptionOutcome.Success("Hello world"), outcome)
            coVerify(exactly = 1) { loadModelUseCase(requireAudio = true) }
            coVerify(exactly = 1) { audioCaptureStore.delete(audioPath) }
        }

    @Test
    fun `given model load fails when invoked then returns Failed and deletes the clip`() = runTest {
        coEvery { localModelRepository.getActiveModel() } returns audioModel(supportsAudio = true)
        coEvery { loadModelUseCase(requireAudio = true) } returns Result.Error(
            error = object : AppError.System {},
            message = "model gone",
        )

        val outcome = useCase(audioPath)

        assertTrue(outcome is TranscriptionOutcome.Failed)
        assertEquals("model gone", (outcome as TranscriptionOutcome.Failed).message)
        coVerify(exactly = 1) { audioCaptureStore.delete(audioPath) }
    }

    @Test
    fun `given the engine throws during transcription then returns Failed and deletes the clip`() = runTest {
        coEvery { localModelRepository.getActiveModel() } returns audioModel(supportsAudio = true)
        coEvery { loadModelUseCase(requireAudio = true) } returns Result.Success(Unit)
        every { llmInferenceEngine.transcribe(audioPath, any()) } returns
            flow { throw RuntimeException("native blew up") }

        val outcome = useCase(audioPath)

        assertTrue(outcome is TranscriptionOutcome.Failed)
        coVerify(exactly = 1) { audioCaptureStore.delete(audioPath) }
    }

    @Test
    fun `given terminal Completed state then treated as idle and proceeds`() = runTest {
        every { taskQueueManager.globalState } returns
            MutableStateFlow(AgentOrchestratorState.Completed("done"))
        coEvery { localModelRepository.getActiveModel() } returns audioModel(supportsAudio = true)
        coEvery { loadModelUseCase(requireAudio = true) } returns Result.Success(Unit)
        every { llmInferenceEngine.transcribe(audioPath, any()) } returns flowOf("ok")

        val outcome = useCase(audioPath)

        assertEquals(TranscriptionOutcome.Success("ok"), outcome)
    }
}
